package pl.nextsteppro.climbing.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the shared "has it happened yet" predicates. Every temporal decision in the app —
 * booking guards, admin mail decisions, archive cleanup — routes through here, so the boundaries
 * are what matter: both predicates mean "the LAST MINUTE has passed", at their own resolution.
 */
class BookingTimeValidatorTest {

    // Wednesday 2026-08-05, 12:00 Warsaw
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 12, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    // ========== isPast: minute resolution ==========

    @Test
    void shouldTreatAMomentEarlierTodayAsPast() {
        assertTrue(BookingTimeValidator.isPast(TODAY, LocalTime.of(10, 0), NOW));
    }

    @Test
    void shouldTreatAMomentLaterTodayAsNotPast() {
        assertFalse(BookingTimeValidator.isPast(TODAY, LocalTime.of(18, 0), NOW));
    }

    @Test
    void shouldTreatThisExactMinuteAsNotPast() {
        // Boundary: "before now" is strict, so the minute itself has not passed yet
        assertFalse(BookingTimeValidator.isPast(TODAY, NOW.toLocalTime(), NOW));
    }

    @Test
    void shouldTreatYesterdayEveningAsPast() {
        assertTrue(BookingTimeValidator.isPast(TODAY.minusDays(1), LocalTime.of(23, 59), NOW));
    }

    // ========== dayHasPassed: day resolution ==========

    @Test
    void shouldTreatYesterdayAsPassed() {
        assertTrue(BookingTimeValidator.dayHasPassed(TODAY.minusDays(1), TODAY));
    }

    @Test
    void shouldTreatTodayAsNotPassed() {
        // The day still has hours left in it — an all-day event ending today is still ahead
        assertFalse(BookingTimeValidator.dayHasPassed(TODAY, TODAY));
    }

    @Test
    void shouldTreatTomorrowAsNotPassed() {
        assertFalse(BookingTimeValidator.dayHasPassed(TODAY.plusDays(1), TODAY));
    }
}
