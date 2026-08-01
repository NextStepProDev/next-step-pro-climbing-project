package pl.nextsteppro.climbing.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.athleteweight.AthleteWeight;
import pl.nextsteppro.climbing.domain.athleteweight.AthleteWeightRepository;
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

    /** Chart window. Long enough to show a season's trend, short enough to stay one payload. */
    static final int DEFAULT_HISTORY_DAYS = 120;
    private static final int MIN_HISTORY_DAYS = 7;
    private static final int MAX_HISTORY_DAYS = 365;

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
    public AthleteWeightSeriesDto getMySeries(UUID userId, @Nullable Integer days) {
        calendarService.requireAthlete(userId);
        return buildSeries(userId, days);
    }

    @Transactional(readOnly = true)
    public AthleteWeightSeriesDto getSeriesForAthlete(UUID athleteId, @Nullable Integer days) {
        calendarService.requireFlaggedAthlete(athleteId);
        return buildSeries(athleteId, days);
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
            throw new IllegalArgumentException(msg.get("training.weight.too.old", DEFAULT_HISTORY_DAYS));
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

        NavigableMap<LocalDate, BigDecimal> byDate = loadWindow(userId, today, DEFAULT_HISTORY_DAYS);
        goalService.evaluateWeightGoals(userId, WeightTrendCalculator.confirmedTrendOn(byDate, today));
        return buildSeries(byDate, today, DEFAULT_HISTORY_DAYS);
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

    private AthleteWeightSeriesDto buildSeries(UUID athleteId, @Nullable Integer requestedDays) {
        int days = clampDays(requestedDays);
        LocalDate today = LocalDate.now(WARSAW);
        return buildSeries(loadWindow(athleteId, today, days), today, days);
    }

    private AthleteWeightSeriesDto buildSeries(NavigableMap<LocalDate, BigDecimal> byDate,
                                               LocalDate today, int days) {
        // The loaded window may reach further back than asked (see loadWindow) so the
        // week-over-week trend still has a comparison point; the CHART shows only what was asked
        LocalDate chartFrom = today.minusDays(days - 1L);
        List<AthleteWeightEntryDto> entries = byDate.tailMap(chartFrom, true).entrySet().stream()
            // The trend of the earliest chart points still draws on readings from before
            // chartFrom (loadWindow deliberately reaches further back), so the line does not
            // start with an artificial dip
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
            days
        );
    }

    /**
     * Earliest day a reading may still be backfilled for: the left edge of the chart the
     * frontend renders. Kept in one place and shipped in the DTO as {@code historyDays} so
     * the date picker's lower bound cannot drift away from what the server accepts.
     */
    private static LocalDate oldestRecordableDay(LocalDate today) {
        return today.minusDays(DEFAULT_HISTORY_DAYS - 1L);
    }

    /** Trend on a day that is itself a reading, so the average always exists. */
    private static BigDecimal trendAt(NavigableMap<LocalDate, BigDecimal> byDate, LocalDate day) {
        TrendPoint point = WeightTrendCalculator.trendOn(byDate, day);
        return point != null ? point.average() : byDate.get(day);
    }

    /**
     * The window always reaches back far enough for the week-over-week comparison, even when
     * the caller asked for a short chart — otherwise the percentage would vanish at days=7.
     */
    private NavigableMap<LocalDate, BigDecimal> loadWindow(UUID athleteId, LocalDate today, int days) {
        LocalDate from = today.minusDays(Math.max(days, 2L * WeightTrendCalculator.WINDOW_DAYS) - 1L);
        return WeightTrendCalculator.index(weightRepository.findRange(athleteId, from, today));
    }

    private static int clampDays(@Nullable Integer requestedDays) {
        if (requestedDays == null) return DEFAULT_HISTORY_DAYS;
        return Math.clamp(requestedDays, MIN_HISTORY_DAYS, MAX_HISTORY_DAYS);
    }
}
