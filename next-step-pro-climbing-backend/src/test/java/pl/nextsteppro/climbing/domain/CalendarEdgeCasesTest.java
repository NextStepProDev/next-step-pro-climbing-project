package pl.nextsteppro.climbing.domain;

import org.junit.jupiter.api.Test;
import pl.nextsteppro.climbing.domain.athleteweight.WeightTrendCalculator;
import pl.nextsteppro.climbing.domain.settlement.Subscription;
import pl.nextsteppro.climbing.domain.user.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two calendar edges no test touched before the 2026-10-19 audit: the turn of the year and
 * 29 February. Everything here passes on today's code — by construction (months normalised to the
 * 1st, Monday-to-Monday arithmetic, day-keyed maps). These pin that construction down, so a
 * "simplification" to day-of-year maths or to {@code date.getYear()} comparisons fails here
 * instead of on the night it matters.
 */
class CalendarEdgeCasesTest {

    // ---------------------------------------------------------------- the turn of the year

    @Test
    void shouldTreatASessionEndingAtMidnightOnNewYearsEveAsRunningUntilItsLastMinute() {
        LocalDate eve = LocalDate.of(2026, 12, 31);

        assertFalse(BookingTimeValidator.isPast(eve, LocalTime.of(23, 59), LocalDateTime.of(2026, 12, 31, 23, 58)));
        assertTrue(BookingTimeValidator.isPast(eve, LocalTime.of(23, 59), LocalDateTime.of(2027, 1, 1, 0, 0)));
    }

    @Test
    void shouldKeepAnEventEndingOnNewYearsEveCurrentUntilTheNewYear() {
        LocalDate eve = LocalDate.of(2026, 12, 31);

        assertFalse(BookingTimeValidator.dayHasPassed(eve, eve), "Ending today is not over");
        assertTrue(BookingTimeValidator.dayHasPassed(eve, LocalDate.of(2027, 1, 1)));
    }

    @Test
    void shouldCoverEveryMonthOfASubscriptionThatCrossesTheYear() {
        Subscription winter = new Subscription(
            new User("a@example.com", "A", "B", "+48100100100", "a"),
            new BigDecimal("400"), LocalDate.of(2026, 11, 1), LocalDate.of(2027, 2, 1));

        assertTrue(winter.covers(LocalDate.of(2026, 12, 1)));
        assertTrue(winter.covers(LocalDate.of(2027, 1, 1)), "January belongs to the next YEAR, not to nothing");
        assertTrue(winter.covers(LocalDate.of(2027, 2, 1)), "The last month is inclusive");
        assertFalse(winter.covers(LocalDate.of(2027, 3, 1)));
        assertFalse(winter.covers(LocalDate.of(2026, 10, 1)));
    }

    @Test
    void shouldCarryTheWeightTrendOverTheTurnOfTheYear() {
        Map<LocalDate, BigDecimal> readings = new TreeMap<>(Map.of(
            LocalDate.of(2026, 12, 30), new BigDecimal("70.0"),
            LocalDate.of(2026, 12, 31), new BigDecimal("71.0"),
            LocalDate.of(2027, 1, 1), new BigDecimal("72.0")));

        WeightTrendCalculator.ConfirmedTrend trend =
            WeightTrendCalculator.confirmedTrendOn(readings, LocalDate.of(2027, 1, 1));

        assertNotNull(trend, "Three readings in the window, two of them in the old year");
        assertEquals(3, trend.samples());
        assertEquals(0, new BigDecimal("71.0").compareTo(trend.value()));
    }

    // ---------------------------------------------------------------- 29 February

    @Test
    void shouldPutASessionOnTheLeapDayInFebruary() {
        LocalDate leapDay = LocalDate.of(2028, 2, 29);

        assertEquals(LocalDate.of(2028, 2, 1), Subscription.normalizeMonth(leapDay));
        Subscription february = new Subscription(
            new User("b@example.com", "A", "B", "+48100100101", "b"),
            new BigDecimal("400"), LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 1));
        assertTrue(february.covers(Subscription.normalizeMonth(leapDay)));
    }

    @Test
    void shouldCountTheLeapDayAsOneOfTheSevenDaysOfTheTrendWindow() {
        // 23 Feb .. 29 Feb is the whole window on the leap day; 1 March drops 23 Feb and keeps 29 Feb.
        Map<LocalDate, BigDecimal> readings = new TreeMap<>(Map.of(
            LocalDate.of(2028, 2, 23), new BigDecimal("80.0"),
            LocalDate.of(2028, 2, 29), new BigDecimal("70.0"),
            LocalDate.of(2028, 3, 1), new BigDecimal("70.0")));

        assertEquals(2, WeightTrendCalculator.trendOn(readings, LocalDate.of(2028, 2, 29)).samples());
        WeightTrendCalculator.TrendPoint march = WeightTrendCalculator.trendOn(readings, LocalDate.of(2028, 3, 1));
        assertEquals(2, march.samples(), "23 Feb has left the window; the leap day has not");
        assertEquals(0, new BigDecimal("70.0").compareTo(march.average()));
    }

    @Test
    void shouldNotTreatALeapDaySessionAsOverBeforeItEnds() {
        LocalDate leapDay = LocalDate.of(2028, 2, 29);

        assertFalse(BookingTimeValidator.isPast(leapDay, LocalTime.of(20, 0), LocalDateTime.of(2028, 2, 29, 19, 0)));
        assertTrue(BookingTimeValidator.isPast(leapDay, LocalTime.of(20, 0), LocalDateTime.of(2028, 3, 1, 0, 0)));
    }
}
