package pl.nextsteppro.climbing.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.athleteweight.AthleteWeight;
import pl.nextsteppro.climbing.domain.athleteweight.AthleteWeightRepository;
import pl.nextsteppro.climbing.domain.athleteweight.WeightRange;
import pl.nextsteppro.climbing.domain.athleteweight.WeightTrendCalculator;
import pl.nextsteppro.climbing.domain.athleteweight.WeightTrendCalculator.TrendPoint;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NavigableMap;
import java.util.UUID;

/**
 * Morning body weight: the athlete's own log, and a read-only window into it for the coach.
 *
 * <p><b>Only the athlete writes.</b> There is deliberately no admin write path anywhere in this
 * class — recording somebody else's body weight is not the coach's call. The coach gets the
 * same series plus the rapid-loss flag, which the athlete never sees (a health nudge belongs
 * in a conversation, not in a scary red box on the athlete's own dashboard).
 *
 * <p>Writing a weight is also the moment weight goals are evaluated, so the trophy appears in
 * the same render as the reading that earned it.
 */
@Service
@Transactional
public class AthleteWeightService {

    // Container runs UTC; "today" for a Polish athlete weighing in at 7am must be Warsaw's
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    /**
     * How far back a reading may be backfilled. Deliberately a FIXED policy, not the range the
     * athlete happens to be looking at: switching the chart to a year must not silently widen
     * what the date picker will accept (the server would then reject what the picker offered).
     */
    static final int BACKFILL_DAYS = WeightRange.RECENT.days();

    private final AthleteWeightRepository weightRepository;
    private final AthleteGoalService goalService;
    private final TrainingCalendarService calendarService;
    private final MessageService msg;

    public AthleteWeightService(AthleteWeightRepository weightRepository,
                                AthleteGoalService goalService,
                                TrainingCalendarService calendarService,
                                MessageService msg) {
        this.weightRepository = weightRepository;
        this.goalService = goalService;
        this.calendarService = calendarService;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public AthleteWeightSeriesDto getMySeries(UUID userId, @Nullable WeightRange range) {
        calendarService.requireAthlete(userId);
        return buildSeries(userId, range);
    }

    @Transactional(readOnly = true)
    public AthleteWeightSeriesDto getSeriesForAthlete(UUID athleteId, @Nullable WeightRange range) {
        calendarService.requireFlaggedAthlete(athleteId);
        return buildSeries(athleteId, range);
    }

    /**
     * Records or corrects one morning reading, then closes any weight goal it just reached.
     * Returns the whole recomputed series so the panel refreshes from this response alone.
     */
    public AthleteWeightSeriesDto recordMyWeight(UUID userId, SaveWeightRequest request) {
        User athlete = calendarService.requireAthlete(userId);
        LocalDate today = LocalDate.now(WARSAW);
        if (request.measuredOn().isAfter(today)) {
            throw new IllegalArgumentException(msg.get("training.weight.future"));
        }
        // Older than the chart window would save and then never appear anywhere — a silent
        // black hole is worse for the athlete than a plain refusal
        if (request.measuredOn().isBefore(oldestRecordableDay(today))) {
            throw new IllegalArgumentException(msg.get("training.weight.too.old", BACKFILL_DAYS));
        }
        BigDecimal weight = AthleteWeight.normalize(request.weightKg());
        if (weight.compareTo(AthleteWeight.MIN_KG) < 0 || weight.compareTo(AthleteWeight.MAX_KG) > 0) {
            throw new IllegalArgumentException(msg.get("training.weight.range"));
        }

        weightRepository.findByAthleteIdAndMeasuredOn(userId, request.measuredOn())
            .ifPresentOrElse(
                existing -> existing.correctTo(weight),
                () -> weightRepository.save(new AthleteWeight(athlete, request.measuredOn(), weight)));
        // The evaluation below reads the window back, so the new row must already be visible
        weightRepository.flush();

        NavigableMap<LocalDate, BigDecimal> byDate = loadWindow(userId, today, WeightRange.DEFAULT.days());
        goalService.evaluateWeightGoals(userId, WeightTrendCalculator.confirmedTrendOn(byDate, today));
        return buildSeries(byDate, today, WeightRange.DEFAULT.days());
    }

    /**
     * Removes a mistaken reading. Deliberately does NOT re-evaluate goals: deleting can only
     * shrink the sample count, and an achieved goal must never silently un-achieve itself.
     * Idempotent — deleting a day with no reading is not an error.
     */
    public AthleteWeightSeriesDto deleteMyWeight(UUID userId, LocalDate measuredOn) {
        calendarService.requireAthlete(userId);
        weightRepository.deleteByAthleteIdAndMeasuredOn(userId, measuredOn);
        weightRepository.flush();
        return buildSeries(userId, null);
    }

    private AthleteWeightSeriesDto buildSeries(UUID athleteId, @Nullable WeightRange requested) {
        WeightRange range = requested != null ? requested : WeightRange.DEFAULT;
        LocalDate today = LocalDate.now(WARSAW);
        return buildSeries(loadWindow(athleteId, today, range.days()), today, range.days());
    }

    private AthleteWeightSeriesDto buildSeries(NavigableMap<LocalDate, BigDecimal> byDate,
                                               LocalDate today, int days) {
        // loadWindow reads further back than the chart starts; only the asked-for range is sent
        LocalDate chartFrom = today.minusDays(days - 1L);
        List<AthleteWeightEntryDto> entries = byDate.tailMap(chartFrom, true).entrySet().stream()
            // The trailing average of the FIRST chart point still averages readings from before
            // chartFrom — otherwise the left edge would show each point averaged against a
            // window that is mostly missing, bending the line for no reason
            .map(e -> new AthleteWeightEntryDto(e.getKey(), e.getValue(), trendAt(byDate, e.getKey())))
            .toList();

        TrendPoint trend = WeightTrendCalculator.trendOn(byDate, today);
        BigDecimal weeklyChange = WeightTrendCalculator.weeklyChangePercent(byDate, today);
        LocalDate latestDay = byDate.isEmpty() ? null : byDate.lastKey();

        return new AthleteWeightSeriesDto(
            entries,
            trend != null ? trend.average() : null,
            trend != null ? trend.samples() : 0,
            WeightTrendCalculator.confirmedTrendOn(byDate, today) != null,
            weeklyChange,
            WeightTrendCalculator.isRapidLoss(weeklyChange),
            latestDay != null ? byDate.get(latestDay) : null,
            latestDay,
            BACKFILL_DAYS
        );
    }

    /**
     * Earliest day a reading may still be backfilled for. Shipped in the DTO as
     * {@code backfillDays} — NOT the selected chart range, so widening the view never widens
     * what the picker offers, and the picker's lower bound cannot drift from what is accepted.
     */
    private static LocalDate oldestRecordableDay(LocalDate today) {
        return today.minusDays(BACKFILL_DAYS - 1L);
    }

    /** Trend on a day that is itself a reading, so the average always exists. */
    private static BigDecimal trendAt(NavigableMap<LocalDate, BigDecimal> byDate, LocalDate day) {
        TrendPoint point = WeightTrendCalculator.trendOn(byDate, day);
        return point != null ? point.average() : byDate.get(day);
    }

    /**
     * Reads further back than the chart shows, for two independent reasons:
     *
     * <ul>
     *   <li>the trailing average of the FIRST charted day needs the {@code WINDOW_DAYS - 1}
     *       days before it, or the left edge of the line is computed from a window that is
     *       mostly missing and bends downward for no real reason;</li>
     *   <li>the week-over-week percentage compares today's trend with the trend a week ago,
     *       so at least {@code 2 * WINDOW_DAYS} days must be on hand however short the chart.</li>
     * </ul>
     *
     * The extra days are read but never sent — {@code buildSeries} trims to the range.
     */
    private NavigableMap<LocalDate, BigDecimal> loadWindow(UUID athleteId, LocalDate today, int days) {
        long lookback = Math.max(days + WeightTrendCalculator.WINDOW_DAYS - 1L,
                                 2L * WeightTrendCalculator.WINDOW_DAYS);
        LocalDate from = today.minusDays(lookback - 1L);
        return WeightTrendCalculator.index(weightRepository.findRange(athleteId, from, today));
    }
}
