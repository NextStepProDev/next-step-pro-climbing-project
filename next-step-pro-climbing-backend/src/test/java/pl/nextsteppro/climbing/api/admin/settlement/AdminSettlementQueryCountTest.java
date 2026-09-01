package pl.nextsteppro.climbing.api.admin.settlement;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reservation.GuestReservation;
import pl.nextsteppro.climbing.domain.reservation.GuestReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Settlements tab reads every settlement of the selected year plus the whole outstanding
 * history, so its cost has to be flat in the number of settlements rather than linear in it.
 *
 * <p>This is the gate the feature would otherwise lack. The overview is assembled in one Java pass
 * today, but the shape that replaces it under maintenance is always the same: a loop over payers
 * calling a per-person counter that already exists next door. That reads perfectly well and turns
 * four queries into one per payer, which nothing else in the build would notice.
 *
 * <p>The budget is a <b>constant</b>, deliberately, not a function of {@code SETTLEMENTS}: a ceiling
 * that grows with the fixture is a ceiling a per-row cost fits under.
 */
class AdminSettlementQueryCountTest extends BaseIntegrationTest {

    private static final int SETTLEMENTS = 30;

    /**
     * Rows of the year, the outstanding history, the two distinct-date reads behind the year picker,
     * the four reads behind the "to be priced" queue (two session kinds x two payer kinds) and the
     * four behind bulk payouts (the payer list, transfers by arrival, transfers by work month, and
     * the sessions they cover) — one query each, plus slack for Spring Data's own round trips.
     *
     * <p>Twelve is a lot for one endpoint and it is deliberate: the tab answers four independent
     * questions in one read so its figures cannot disagree with each other. What matters is that the
     * number does not move with the data, which is what this gate holds.
     */
    private static final int MAX_QUERIES = 14;

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 31);

    /** Enough people, over enough days, that anything per-person or per-day shows up clearly. */
    private static final int SECTION_PARTICIPANTS = 10;

    /**
     * Event, its days, their confirmed bookings, guests on the event, guests on its days, the saved
     * amounts, the prefill, who settles it in bulk, and the two balance reads (one for registered
     * payers, one for guests) — one query each, plus slack for Spring Data's own round trips.
     *
     * <p>The balances were briefly a query PER PAYER, which took this to nineteen on a fixture of
     * ten. This gate is what caught it; the ceiling stays a constant so the same mistake cannot hide
     * behind a bigger fixture.
     */
    private static final int MAX_SECTION_QUERIES = 12;

    @Autowired private AdminSettlementStatsService service;
    @Autowired private AdminSettlementService settlementService;
    @Autowired private GuestReservationRepository guestReservationRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM settlements");
        guestReservationRepository.deleteAll();
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        eventRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        for (int i = 0; i < SETTLEMENTS; i++) {
            User payer = new User("client" + i + "@example.com", "Klient", "Numer" + i,
                "+48123456789", "client" + i);
            payer.setRole(UserRole.USER);
            payer.setEmailVerified(true);
            payer = userRepository.saveAndFlush(payer);

            LocalDate date = TODAY.minusDays(i * 7L);
            TimeSlot slot = timeSlotRepository.saveAndFlush(
                new TimeSlot(date, LocalTime.of(18, 0), LocalTime.of(20, 0), 4));

            // Every third one left unpaid, so the outstanding read has work to do as well.
            jdbc.update("INSERT INTO settlements (time_slot_id, user_id, amount, paid_amount, settled_on) "
                    + "VALUES (?, ?, ?, ?, ?)",
                slot.getId(), payer.getId(), new BigDecimal("150.00"),
                i % 3 == 0 ? BigDecimal.ZERO : new BigDecimal("150.00"),
                i % 3 == 0 ? null : date);
        }

        entityManager.flush();
        entityManager.clear();
    }

    /**
     * The section is the hot path — it loads every time an admin opens a slot or an event, which is
     * many times more often than the tab is visited. Its cost has to be flat in the number of
     * participants, and for an event also flat in the number of DAYS, since a booking writes one
     * reservation row per day.
     *
     * <p>The shape to guard against is a loop over participants asking for each person's last
     * amount, or dereferencing {@code reservation.getUser()} without the fetch join.
     */
    @Test
    @DisplayName("shouldBuildAnEventSectionInAConstantNumberOfQueries")
    void shouldBuildAnEventSectionInAConstantNumberOfQueries() {
        LocalDate start = TODAY.plusDays(30);
        Event event = eventRepository.saveAndFlush(
            new Event("Kurs skalny", EventType.COURSE, start, start.plusDays(2), 20));

        List<TimeSlot> days = new ArrayList<>();
        for (int day = 0; day <= 2; day++) {
            days.add(timeSlotRepository.saveAndFlush(
                new TimeSlot(event, start.plusDays(day), LocalTime.of(9, 0), LocalTime.of(17, 0), 20)));
        }
        for (int i = 0; i < SECTION_PARTICIPANTS; i++) {
            User payer = new User("attendee" + i + "@example.com", "Uczestnik", "Numer" + i,
                "+48123456789", "attendee" + i);
            payer.setRole(UserRole.USER);
            payer.setEmailVerified(true);
            payer = userRepository.saveAndFlush(payer);
            for (TimeSlot day : days) {
                reservationRepository.saveAndFlush(new Reservation(payer, day));
            }
        }
        guestReservationRepository.saveAndFlush(new GuestReservation(event, "Ekipa z Krakowa", 3));

        entityManager.flush();
        entityManager.clear();

        Statistics stats = statistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        SettlementSectionDto section = settlementService.getSection("event", event.getId());

        long queries = stats.getPrepareStatementCount();
        assertTrue(queries > 0, "Hibernate statistics collected nothing — the measurement is broken");

        // Counter-assertion: one line per PERSON, not per booking row. Ten people over three days is
        // thirty reservations and must still be eleven lines.
        assertEquals(SECTION_PARTICIPANTS + 1, section.lines().size(),
            "A multi-day event must collapse to one line per person, plus the guest");

        assertTrue(queries <= MAX_SECTION_QUERIES,
            "Opening the section took " + queries + " queries for " + SECTION_PARTICIPANTS
                + " participants over three days; the budget is " + MAX_SECTION_QUERIES
                + " regardless of either number. A per-person lookup in a loop reads well and costs "
                + "one query each.");
    }

    @Test
    @DisplayName("shouldBuildTheOverviewInAConstantNumberOfQueries")
    void shouldBuildTheOverviewInAConstantNumberOfQueries() {
        Statistics stats = statistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        SettlementOverviewDto overview = service.buildOverview("2026", TODAY);

        long queries = stats.getPrepareStatementCount();
        assertTrue(queries > 0, "Hibernate statistics collected nothing — the measurement is broken");

        // Counter-assertions: a method that returned an empty overview would meet any budget.
        assertEquals(2026, overview.year());
        assertTrue(overview.people().size() > 1,
            "The per-person breakdown must actually be populated, or the budget proves nothing");
        assertTrue(overview.outstanding().count() > 0,
            "The outstanding list must actually be populated, or the budget proves nothing");

        assertTrue(queries <= MAX_QUERIES,
            "The Settlements overview took " + queries + " queries for " + SETTLEMENTS
                + " settlements; the budget is " + MAX_QUERIES + " regardless of how many exist. "
                + "A per-payer counter in a loop reads well and costs one query each.");
    }
}
