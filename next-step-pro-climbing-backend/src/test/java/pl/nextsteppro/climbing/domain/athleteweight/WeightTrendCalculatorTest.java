package pl.nextsteppro.climbing.domain.athleteweight;

import org.junit.jupiter.api.Test;
import pl.nextsteppro.climbing.domain.athleteweight.WeightTrendCalculator.ConfirmedTrend;
import pl.nextsteppro.climbing.domain.athleteweight.WeightTrendCalculator.LowestTrend;
import pl.nextsteppro.climbing.domain.athleteweight.WeightTrendCalculator.TrendPoint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the body-weight arithmetic. Every case passes an explicit reference day —
 * the calculator is clock-free, so nothing here may call LocalDate.now().
 *
 * <p>The heart of this file is the {@link WeightTrendCalculator#MIN_SAMPLES_FOR_GOAL} boundary:
 * that rule is invisible on the happy path, so it gets the densest coverage.
 */
class WeightTrendCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 1);

    private static Map<LocalDate, BigDecimal> readings(Object... daysAgoAndWeight) {
        Map<LocalDate, BigDecimal> byDate = new LinkedHashMap<>();
        for (int i = 0; i < daysAgoAndWeight.length; i += 2) {
            byDate.put(TODAY.minusDays((int) daysAgoAndWeight[i]), new BigDecimal(String.valueOf(daysAgoAndWeight[i + 1])));
        }
        return byDate;
    }

    /** The lowest-trend path needs the sorted map the service actually passes in. */
    private static NavigableMap<LocalDate, BigDecimal> sortedReadings(Object... daysAgoAndWeight) {
        return new TreeMap<>(readings(daysAgoAndWeight));
    }

    private static LowestTrend lowest(NavigableMap<LocalDate, BigDecimal> byDate) {
        return WeightTrendCalculator.lowestConfirmedTrend(
            byDate, TODAY, WeightTrendCalculator.LOWEST_WINDOW_DAYS);
    }

    // ---------- trendOn: the display path ----------

    @Test
    void shouldAverageTheWholeWindowWhenEveryDayHasAReading() {
        // Given: 7 consecutive readings summing to 490.00
        Map<LocalDate, BigDecimal> byDate = readings(0, "70", 1, "71", 2, "69", 3, "70", 4, "70", 5, "70", 6, "70");

        // When
        TrendPoint trend = WeightTrendCalculator.trendOn(byDate, TODAY);

        // Then
        assertNotNull(trend);
        assertEquals(0, new BigDecimal("70.00").compareTo(trend.average()));
        assertEquals(7, trend.samples());
    }

    @Test
    void shouldIncludeTheSixthDayBackButExcludeTheSeventh() {
        // Given: the window is [day-6, day] inclusive — day-7 is outside it
        Map<LocalDate, BigDecimal> insideEdge = readings(6, "60");
        Map<LocalDate, BigDecimal> outsideEdge = readings(7, "60");

        // When / Then
        assertNotNull(WeightTrendCalculator.trendOn(insideEdge, TODAY));
        assertNull(WeightTrendCalculator.trendOn(outsideEdge, TODAY));
    }

    @Test
    void shouldAverageOnlyTheDaysThatExistWithoutForwardFillingGaps() {
        // Given: only two readings in a seven-day window
        Map<LocalDate, BigDecimal> byDate = readings(0, "70", 4, "72");

        // When
        TrendPoint trend = WeightTrendCalculator.trendOn(byDate, TODAY);

        // Then: the average is of those two, not of two values spread over seven days
        assertNotNull(trend);
        assertEquals(0, new BigDecimal("71.00").compareTo(trend.average()));
        assertEquals(2, trend.samples());
    }

    @Test
    void shouldRoundHalfUpToTwoDecimals() {
        // Given: 70.00 + 70.01 + 70.02 = 210.03, divided by 3 = 70.01
        Map<LocalDate, BigDecimal> byDate = readings(0, "70.00", 1, "70.01", 2, "70.02");

        // When
        TrendPoint trend = WeightTrendCalculator.trendOn(byDate, TODAY);

        // Then
        assertNotNull(trend);
        assertEquals("70.01", trend.average().toPlainString());
    }

    @Test
    void shouldSmoothOutASingleWildReading() {
        // Given: one salty-dinner spike among steady readings
        Map<LocalDate, BigDecimal> byDate = readings(0, "70", 1, "70", 2, "76", 3, "70", 4, "70", 5, "70", 6, "70");

        // When
        TrendPoint trend = WeightTrendCalculator.trendOn(byDate, TODAY);

        // Then: the trend barely moves — that is the entire point of showing it
        assertNotNull(trend);
        assertEquals(0, new BigDecimal("70.86").compareTo(trend.average()));
    }

    @Test
    void shouldReturnNullTrendWhenThereAreNoReadingsInTheWindow() {
        assertNull(WeightTrendCalculator.trendOn(Map.of(), TODAY));
    }

    // ---------- confirmedTrendOn: the goal-closing path ----------

    @Test
    void shouldRefuseToConfirmATrendBelowThreeReadings() {
        // Given / When / Then: 0, 1 and 2 readings are all too thin to close a goal
        assertNull(WeightTrendCalculator.confirmedTrendOn(Map.of(), TODAY));
        assertNull(WeightTrendCalculator.confirmedTrendOn(readings(0, "70"), TODAY));
        assertNull(WeightTrendCalculator.confirmedTrendOn(readings(0, "70", 1, "70"), TODAY));
    }

    @Test
    void shouldConfirmATrendAtExactlyThreeReadings() {
        // Given: the boundary the user explicitly asked for
        Map<LocalDate, BigDecimal> byDate = readings(0, "70", 3, "71", 6, "72");

        // When
        ConfirmedTrend trend = WeightTrendCalculator.confirmedTrendOn(byDate, TODAY);

        // Then
        assertNotNull(trend);
        assertEquals(3, trend.samples());
        assertEquals(0, new BigDecimal("71.00").compareTo(trend.value()));
    }

    @Test
    void shouldCountOnlyReadingsInsideTheWindowTowardsConfirmation() {
        // Given: three readings, but one of them is older than the window
        Map<LocalDate, BigDecimal> byDate = readings(0, "70", 1, "70", 7, "70");

        // When / Then: two readings count, so the trend stays unconfirmed
        assertNull(WeightTrendCalculator.confirmedTrendOn(byDate, TODAY));
    }

    @Test
    void shouldRejectAHandBuiltConfirmedTrendBelowThreshold() {
        // Given / When / Then: the invariant survives even a caller bypassing the factory
        assertThrows(IllegalStateException.class, () -> new ConfirmedTrend(new BigDecimal("70.00"), 2));
    }

    // ---------- weeklyChangePercent ----------

    @Test
    void shouldComputeWeeklyChangeAsNegativeWhenLosing() {
        // Given: trend a week ago 72.00, trend today 71.28 → -1.0%
        Map<LocalDate, BigDecimal> byDate = readings(
            0, "71.28", 1, "71.28", 2, "71.28",
            7, "72", 8, "72", 9, "72");

        // When
        BigDecimal change = WeightTrendCalculator.weeklyChangePercent(byDate, TODAY);

        // Then
        assertNotNull(change);
        assertEquals("-1.0", change.toPlainString());
    }

    @Test
    void shouldReturnNullWeeklyChangeUntilBothWindowsHaveReadings() {
        // Given: only recent readings — there is nothing to compare against
        Map<LocalDate, BigDecimal> byDate = readings(0, "70", 1, "70", 2, "70");

        // When / Then
        assertNull(WeightTrendCalculator.weeklyChangePercent(byDate, TODAY));
    }

    // ---------- isRapidLoss ----------

    @Test
    void shouldNotFlagRapidLossExactlyAtOnePercent() {
        assertFalse(WeightTrendCalculator.isRapidLoss(new BigDecimal("-1.0")));
    }

    @Test
    void shouldFlagRapidLossBeyondOnePercent() {
        assertTrue(WeightTrendCalculator.isRapidLoss(new BigDecimal("-1.1")));
    }

    @Test
    void shouldNeverFlagGainingWeightHoweverFast() {
        assertFalse(WeightTrendCalculator.isRapidLoss(new BigDecimal("5.0")));
    }

    @Test
    void shouldNotFlagRapidLossWithoutData() {
        assertFalse(WeightTrendCalculator.isRapidLoss(null));
    }

    // ---------- lowestConfirmedTrend: the personal-best tile ----------

    @Test
    void shouldReportTheLowestConfirmedTrendAndTheDayItWasReached() {
        // Given: 70 kg this week, and a genuine 68 kg dip a month ago backed by three readings
        NavigableMap<LocalDate, BigDecimal> byDate = sortedReadings(
            0, "70", 1, "70", 2, "70",
            30, "68", 31, "68", 32, "68");

        // When
        LowestTrend result = lowest(byDate);

        // Then
        assertNotNull(result);
        assertEquals(0, new BigDecimal("68.00").compareTo(result.value()));
        assertEquals(TODAY.minusDays(30), result.day());
    }

    @Test
    void shouldIgnoreADipThatCouldNotHaveClosedAGoal() {
        // Given: a single 60 kg morning after a long break — a low nobody held for a week
        NavigableMap<LocalDate, BigDecimal> byDate = sortedReadings(
            0, "70", 1, "70", 2, "70",
            40, "60");

        // When
        LowestTrend result = lowest(byDate);

        // Then: the tile can never show a value a weight goal would have refused to close
        assertNotNull(result);
        assertEquals(0, new BigDecimal("70.00").compareTo(result.value()));
        assertEquals(TODAY, result.day());
    }

    @Test
    void shouldReturnNothingWhenNoDayInTheWindowEverCarriedEnoughReadings() {
        // Given: someone who weighs in twice a week never reaches three inside seven days
        NavigableMap<LocalDate, BigDecimal> byDate = sortedReadings(0, "70", 4, "71", 8, "70", 12, "71");

        // When / Then: null, not the best of the unconfirmed ones
        assertNull(lowest(byDate));
    }

    @Test
    void shouldPreferTheMostRecentDayWhenTheLowIsTied() {
        // Given: the same 70.00 trend reached three weeks ago and again today
        NavigableMap<LocalDate, BigDecimal> byDate = sortedReadings(
            0, "70", 1, "70", 2, "70",
            20, "70", 21, "70", 22, "70");

        // When
        LowestTrend result = lowest(byDate);

        // Then: being back at your best is the news, having once been there is not
        assertNotNull(result);
        assertEquals(TODAY, result.day());
    }

    @Test
    void shouldIncludeTheLastDayOfTheWindowButNotTheDayBeforeIt() {
        // Given: the only confirmed low sits exactly on the far edge of the 90-day window
        NavigableMap<LocalDate, BigDecimal> onEdge = sortedReadings(89, "60", 90, "60", 91, "60");
        // ...and the same low one day further back, i.e. just outside it
        NavigableMap<LocalDate, BigDecimal> pastEdge = sortedReadings(90, "60", 91, "60", 92, "60");

        // When / Then
        LowestTrend included = lowest(onEdge);
        assertNotNull(included);
        assertEquals(TODAY.minusDays(89), included.day());
        assertNull(lowest(pastEdge));
    }

    @Test
    void shouldReportNothingWithoutAnyReadings() {
        assertNull(lowest(new TreeMap<>()));
    }
}
