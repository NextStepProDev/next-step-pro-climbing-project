package pl.nextsteppro.climbing.domain.athleteweight;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * All body-weight arithmetic in one pure, clock-free place: no Spring, no {@code now()}.
 * Every method takes the reference day explicitly, so the tests are deterministic and the
 * Warsaw-vs-UTC trap lives at the call site instead of here.
 *
 * <p><b>Two trends, on purpose.</b> Displaying a trend and closing a goal with it are different
 * responsibilities with different risk:
 * <ul>
 *   <li>{@link #trendOn} — DISPLAY. Averages whatever exists, even a single reading. Being
 *       slightly noisy on a chart costs nothing.</li>
 *   <li>{@link #confirmedTrendOn} — GOAL CLOSING. Returns {@code null} below
 *       {@link #MIN_SAMPLES_FOR_GOAL} readings, so a lone weigh-in after a week off can never
 *       close a goal the athlete has not actually reached.</li>
 * </ul>
 * The separation is enforced by the return TYPE, not by a convention someone must remember:
 * {@code AthleteGoal.isMetBy} accepts only {@link ConfirmedTrend}, so closing a goal off a
 * two-reading trend is a compile error.
 */
public final class WeightTrendCalculator {

    /** Trailing window, inclusive of the reference day: [day-6, day]. */
    public static final int WINDOW_DAYS = 7;

    /**
     * How far back {@link #lowestConfirmedTrend} looks. A FIXED policy, deliberately not the
     * range the athlete happens to be viewing: the tile is labelled "3 months" and that label
     * has to stay true when somebody switches the chart to a year. Same reasoning as
     * {@code AthleteWeightService.BACKFILL_DAYS}.
     */
    public static final int LOWEST_WINDOW_DAYS = 90;

    /**
     * A trend may only close a goal when it rests on at least this many readings inside the
     * window. Weight swings a kilo on water alone; one lucky morning is not an achievement.
     */
    public static final int MIN_SAMPLES_FOR_GOAL = 3;

    /** Losing faster than this is worth a word from the coach — muscle goes with the fat. */
    private static final BigDecimal RAPID_LOSS_PERCENT_PER_WEEK = new BigDecimal("1.0");

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private WeightTrendCalculator() {}

    /** A displayable trend: the average plus how many readings back it. */
    public record TrendPoint(BigDecimal average, int samples) {}

    /**
     * A trend strong enough to close a goal. The compact constructor is the second lock: even
     * hand-built instances cannot carry fewer than {@link #MIN_SAMPLES_FOR_GOAL} readings.
     */
    public record ConfirmedTrend(BigDecimal value, int samples) {
        public ConfirmedTrend {
            if (samples < MIN_SAMPLES_FOR_GOAL) {
                throw new IllegalStateException(
                    "A confirmed trend needs at least " + MIN_SAMPLES_FOR_GOAL + " readings, got " + samples);
            }
        }
    }

    /** The lowest confirmed trend inside the window, and the day it was reached. */
    public record LowestTrend(BigDecimal value, LocalDate day) {}

    /** Readings keyed by day, for O(1) window lookups. */
    public static NavigableMap<LocalDate, BigDecimal> index(List<AthleteWeight> weights) {
        NavigableMap<LocalDate, BigDecimal> byDate = new TreeMap<>();
        for (AthleteWeight weight : weights) {
            byDate.put(weight.getMeasuredOn(), weight.getWeightKg());
        }
        return byDate;
    }

    /**
     * Trailing {@link #WINDOW_DAYS}-day average ending on {@code day}, over the readings that
     * actually exist — gaps are skipped, never forward-filled (an invented reading would make
     * a stale trend look fresh). Null when the window is empty.
     */
    @Nullable
    public static TrendPoint trendOn(Map<LocalDate, BigDecimal> byDate, LocalDate day) {
        BigDecimal sum = BigDecimal.ZERO;
        int samples = 0;
        for (int i = 0; i < WINDOW_DAYS; i++) {
            BigDecimal value = byDate.get(day.minusDays(i));
            if (value != null) {
                sum = sum.add(value);
                samples++;
            }
        }
        if (samples == 0) return null;
        return new TrendPoint(sum.divide(BigDecimal.valueOf(samples), AthleteWeight.SCALE, RoundingMode.HALF_UP), samples);
    }

    /**
     * The same average, but only when it rests on enough readings to be trusted with closing
     * a goal. Null below the threshold — the goal path is then a plain null check.
     */
    @Nullable
    public static ConfirmedTrend confirmedTrendOn(Map<LocalDate, BigDecimal> byDate, LocalDate day) {
        TrendPoint point = trendOn(byDate, day);
        if (point == null || point.samples() < MIN_SAMPLES_FOR_GOAL) return null;
        return new ConfirmedTrend(point.average(), point.samples());
    }

    /**
     * Week-over-week change of the trend, in percent (negative = losing). Null until both ends
     * of the comparison have readings — a percentage against nothing would be a fabricated number.
     */
    @Nullable
    public static BigDecimal weeklyChangePercent(Map<LocalDate, BigDecimal> byDate, LocalDate today) {
        TrendPoint now = trendOn(byDate, today);
        TrendPoint weekAgo = trendOn(byDate, today.minusDays(WINDOW_DAYS));
        if (now == null || weekAgo == null || weekAgo.average().signum() == 0) return null;
        return now.average()
            .subtract(weekAgo.average())
            .multiply(HUNDRED)
            .divide(weekAgo.average(), 1, RoundingMode.HALF_UP);
    }

    /**
     * Lowest trend reached in the trailing {@code windowDays}, or null if none was ever
     * confirmed there.
     *
     * <p><b>Confirmed, like a goal.</b> This number sits on the same screen as the weight
     * goals, so it is built from {@link #confirmedTrendOn}: a value that could not have closed
     * a goal must never be displayed as a personal best next to a goal that stayed open. It
     * also keeps the tile from rewarding the frequent weigher — a minimum over raw readings
     * drops the more often you step on the scale, which measures diligence, not progress.
     *
     * <p>Evaluated only on days that HAVE a reading, matching the chart's trend line; a trailing
     * average on an empty day would put the low on a day nothing happened. Ties go to the most
     * recent day — being back at your best is news, having once been there is not.
     */
    @Nullable
    public static LowestTrend lowestConfirmedTrend(NavigableMap<LocalDate, BigDecimal> byDate,
                                                   LocalDate today, int windowDays) {
        LowestTrend lowest = null;
        for (LocalDate day : byDate.subMap(today.minusDays(windowDays - 1L), true, today, true).keySet()) {
            ConfirmedTrend trend = confirmedTrendOn(byDate, day);
            if (trend == null) continue;
            if (lowest == null || trend.value().compareTo(lowest.value()) <= 0) {
                lowest = new LowestTrend(trend.value(), day);
            }
        }
        return lowest;
    }

    /**
     * One-directional on purpose: fast loss is a health flag, fast gain is not this feature's
     * business. Exactly -1.0%/week does not fire — only strictly worse.
     */
    public static boolean isRapidLoss(@Nullable BigDecimal weeklyChangePercent) {
        return weeklyChangePercent != null
            && weeklyChangePercent.negate().compareTo(RAPID_LOSS_PERCENT_PER_WEEK) > 0;
    }
}
