package pl.nextsteppro.climbing.api.calendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a client was charged must never appear in a calendar payload.
 *
 * <p>{@code SettlementIsolationTest} guards the same rule from the other side, by keeping the
 * settlement types unreachable outside their own two packages. This one is the direct evidence: it
 * takes the real shared records — {@code MonthViewDto}, {@code WeekViewDto}, {@code DayViewDto},
 * {@code TimeSlotDetailDto} — serialises them the way the browser would receive them, and looks for
 * the amount.
 *
 * <p>Three viewers, because they are three different exposures: the anonymous one is the shape that
 * gets <b>cached</b> under {@code calendarMonth/Week/Day}, the participant is the person the amount
 * is about, and the admin is the only one entitled to it — and even there it must arrive through the
 * settlements endpoint rather than by riding along here.
 *
 * <p>The assertion runs on the SERIALISED JSON rather than on the records' components on purpose,
 * the same way {@code AdminUserHistoryIntegrationTest} guards the user card: a field somebody adds
 * later reaches the browser whether or not they remembered this test existed.
 *
 * <p>Lives in this package because the calendar DTOs are package-private.
 */
class SettlementNotInSharedPayloadTest extends BaseIntegrationTest {

    /** Distinctive enough that a substring search cannot match it by accident. */
    private static final BigDecimal SECRET_AMOUNT = new BigDecimal("1337.42");
    private static final String SECRET_TEXT = "1337.42";

    @Autowired private CalendarService calendarService;
    @Autowired private JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private User client;
    private User admin;
    private TimeSlot slot;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM settlements");
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        client = new User("client@example.com", "Anna", "Kowalska", "+48123456789", "anna");
        client.setRole(UserRole.USER);
        client.setEmailVerified(true);
        client = userRepository.saveAndFlush(client);

        admin = new User("admin@example.com", "Trener", "Glowny", "+48111111111", "coach");
        admin.setRole(UserRole.ADMIN);
        admin.setEmailVerified(true);
        admin = userRepository.saveAndFlush(admin);

        date = LocalDate.now().plusDays(1);
        slot = timeSlotRepository.saveAndFlush(
            new TimeSlot(date, LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        reservationRepository.saveAndFlush(new Reservation(client, slot));

        // Written straight into the table rather than through AdminSettlementService: this test is
        // about what leaves the calendar, and it must keep working even if the settlement's own API
        // moves.
        jdbc.update("INSERT INTO settlements (time_slot_id, user_id, amount) VALUES (?, ?, ?)",
            slot.getId(), client.getId(), SECRET_AMOUNT);

        // Without this, a fixture that silently failed to write would make every assertion below
        // pass for the wrong reason: "the amount is not in the payload" is only evidence when there
        // is an amount to leak.
        assertEquals(0, SECRET_AMOUNT.compareTo(jdbc.queryForObject(
            "SELECT amount FROM settlements WHERE time_slot_id = ?", BigDecimal.class, slot.getId())));
    }

    @Test
    @DisplayName("shouldNotSerialiseAnAmountIntoTheCalendarForAnyViewer")
    void shouldNotSerialiseAnAmountIntoTheCalendarForAnyViewer() throws Exception {
        for (UUID viewer : new UUID[]{null, client.getId(), admin.getId()}) {
            String who = viewer == null ? "anonymous" : viewer.equals(admin.getId()) ? "admin" : "participant";

            assertClean(who + " month", mapper.writeValueAsString(
                calendarService.getMonthView(YearMonth.from(date), viewer)));
            assertClean(who + " week", mapper.writeValueAsString(
                calendarService.getWeekView(date.minusDays(date.getDayOfWeek().getValue() - 1L), viewer)));
            assertClean(who + " day", mapper.writeValueAsString(
                calendarService.getDayView(date, viewer)));
            assertClean(who + " slot detail", mapper.writeValueAsString(
                calendarService.getSlotDetails(slot.getId(), viewer)));
        }
    }

    @Test
    @DisplayName("shouldStillCarryTheOrdinaryCalendarDataItIsAssertingAbout")
    void shouldStillCarryTheOrdinaryCalendarDataItIsAssertingAbout() throws Exception {
        // Counter-assertion: a payload that lost its slots would pass the test above trivially.
        String json = mapper.writeValueAsString(calendarService.getDayView(date, admin.getId()));
        assertTrue(json.contains(slot.getId().toString()),
            "The day view no longer carries the slot, so the leak assertion proves nothing: " + json);
    }

    private void assertClean(String what, String json) {
        String lower = json.toLowerCase();
        assertFalse(json.contains(SECRET_TEXT),
            "The " + what + " payload carries the amount a client was charged: " + json);
        assertFalse(lower.contains("settle") || lower.contains("amount"),
            "The " + what + " payload has grown a settlement field. Money must reach the admin "
                + "through /api/admin/settlements — these shapes are served to anonymous visitors "
                + "and cached under calendarMonth/Week/Day. Payload: " + json);
    }
}
