package pl.nextsteppro.climbing.api.admin.userhistory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The user card is the heaviest read in the admin panel and it renders a whole history, so the
 * cost has to be flat in the number of rows, not linear.
 *
 * <p>Counting queries rather than eyeballing JPQL: {@code Reservation.timeSlot} and
 * {@code TimeSlot.event} are both LAZY, so a mapper that touches the slot and its event turns one
 * query into 2N+1 without anything in the code looking wrong.
 */
class AdminUserHistoryQueryCountTest extends BaseIntegrationTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final int ROWS = 48;
    /** Five history sections, each one query, plus a little slack for Spring Data's own round trips. */
    private static final int MAX_QUERIES = 9;

    @Autowired private AdminUserHistoryService service;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private EntityManager entityManager;

    private User user;

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        eventRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        user = new User("counter@example.com", "Jan", "Kowalski", "+48123456789", "janek");
        user.setRole(UserRole.USER);
        user.setEmailVerified(true);
        user = userRepository.save(user);

        LocalDateTime now = LocalDateTime.now(WARSAW);
        for (int i = 1; i <= ROWS; i++) {
            // Half the slots hang off an event and carry no title of their own, so getDisplayTitle()
            // has to reach for event.getTitle() — that is the second lazy hop.
            Event event = null;
            if (i % 2 == 0) {
                event = new Event("Kurs " + i, EventType.COURSE,
                    now.toLocalDate().plusDays(i), now.toLocalDate().plusDays(i), 10);
                event = eventRepository.save(event);
            }
            LocalDate date = now.toLocalDate().plusDays(i);
            TimeSlot slot = event != null
                ? new TimeSlot(event, date, LocalTime.of(18, 0), LocalTime.of(20, 0), 10)
                : new TimeSlot(date, LocalTime.of(18, 0), LocalTime.of(20, 0), 10);
            slot = timeSlotRepository.save(slot);
            reservationRepository.save(new Reservation(user, slot));
        }
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("shouldNotScaleQueryCountWithTheNumberOfReservations")
    void shouldNotScaleQueryCountWithTheNumberOfReservations() {
        Statistics stats = statistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        UserReservationHistoryDto history = service.getReservationHistory(user.getId()).orElseThrow();

        long queries = stats.getPrepareStatementCount();
        assertTrue(queries > 0, "Hibernate statistics collected nothing — the measurement is broken, not the code");
        assertTrue(history.upcoming().size() == ROWS,
            "fixture did not land as expected: " + history.upcoming().size());

        // One query per section and nothing per row. The ceiling is a constant on purpose: with
        // ROWS at 48 a per-row cost could not hide under it, and batch_fetch_size=16 would show up
        // as a slope (10 queries at 12 rows, 14 at 48) rather than as an obvious N+1.
        assertTrue(queries <= MAX_QUERIES,
            "getReservationHistory ran " + queries + " queries for " + ROWS + " reservations (budget "
                + MAX_QUERIES + "). Something dereferences a LAZY association per row — the finders "
                + "need JOIN FETCH on what the mapper actually reads.");
    }

    @Test
    @DisplayName("shouldKeepUserDetailAtAConstantNumberOfQueries")
    void shouldKeepUserDetailAtAConstantNumberOfQueries() {
        Statistics stats = statistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        UUID id = user.getId();
        service.getUserDetail(id).orElseThrow();

        long queries = stats.getPrepareStatementCount();
        System.out.println(">>> getUserDetail queries=" + queries);
        assertTrue(queries > 0, "Hibernate statistics collected nothing — the measurement is broken, not the code");
        // user + 2 counts; a non-athlete skips the training/ascent counts entirely
        assertTrue(queries <= 4,
            "getUserDetail ran " + queries + " queries — expected the user row plus its counts");
    }
}
