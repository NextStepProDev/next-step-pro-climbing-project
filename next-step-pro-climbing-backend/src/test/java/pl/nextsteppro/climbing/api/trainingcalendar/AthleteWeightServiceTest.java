package pl.nextsteppro.climbing.api.trainingcalendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.nextsteppro.climbing.domain.athleteweight.AthleteWeight;
import pl.nextsteppro.climbing.domain.athleteweight.AthleteWeightRepository;
import pl.nextsteppro.climbing.domain.athleteweight.WeightRange;
import pl.nextsteppro.climbing.domain.athleteweight.WeightTrendCalculator;
import pl.nextsteppro.climbing.domain.athleteweight.WeightTrendCalculator.ConfirmedTrend;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AthleteWeightService: the upsert-per-day contract, the guards, and — most
 * importantly — that a weigh-in only closes goals when the trend rests on enough readings.
 */
@ExtendWith(MockitoExtension.class)
class AthleteWeightServiceTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Mock private AthleteWeightRepository weightRepository;
    @Mock private AthleteGoalService goalService;
    @Mock private TrainingCalendarService calendarService;
    @Mock private MessageService msg;

    private AthleteWeightService service;

    private UUID athleteId;
    private User athlete;

    @BeforeEach
    void setUp() {
        service = new AthleteWeightService(weightRepository, goalService, calendarService, msg);

        athleteId = UUID.randomUUID();
        athlete = new User("athlete@example.com", "Anna", "Wspinaczka", "+48123456789", "anna");
        athlete.setAthlete(true);
        // Athlete-side endpoints sit behind the GDPR art. 9 consent gate (V76)
        athlete.grantTrainingConsent();
        setField(athlete, "id", athleteId);

        lenient().when(calendarService.requireAthlete(athleteId)).thenReturn(athlete);
        lenient().when(msg.get(anyString())).thenAnswer(inv -> inv.getArgument(0));
        // Messages that interpolate an argument go through the varargs overload, which the
        // stub above does not match — without this they would silently resolve to null
        lenient().when(msg.get(anyString(), any(Object[].class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static LocalDate today() {
        return LocalDate.now(WARSAW);
    }

    /** Readings on consecutive days ending today, newest weight last. */
    private List<AthleteWeight> recentReadings(String... weights) {
        List<AthleteWeight> readings = new ArrayList<>();
        for (int i = 0; i < weights.length; i++) {
            LocalDate day = today().minusDays(weights.length - 1L - i);
            readings.add(new AthleteWeight(athlete, day, new BigDecimal(weights[i])));
        }
        return readings;
    }

    /** Readings on arbitrary days back, given as (daysAgo, weight) pairs. */
    private List<AthleteWeight> readingsOn(Object... daysAgoAndWeight) {
        List<AthleteWeight> readings = new ArrayList<>();
        for (int i = 0; i < daysAgoAndWeight.length; i += 2) {
            readings.add(new AthleteWeight(athlete,
                today().minusDays((int) daysAgoAndWeight[i]),
                new BigDecimal(String.valueOf(daysAgoAndWeight[i + 1]))));
        }
        return readings;
    }

    private SaveWeightRequest weighIn(String weight) {
        return new SaveWeightRequest(today(), new BigDecimal(weight));
    }

    /**
     * Repository stub that actually applies the [from, to] filter. Needed wherever the test is
     * about HOW FAR BACK the service reads — a stub that ignores its bounds would happily
     * return pre-range readings the real query never fetched, and green the very bug we hunt.
     */
    private void stubRangeHonouringBounds(List<AthleteWeight> all) {
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenAnswer(inv -> {
            LocalDate from = inv.getArgument(1);
            LocalDate to = inv.getArgument(2);
            return all.stream()
                .filter(w -> !w.getMeasuredOn().isBefore(from) && !w.getMeasuredOn().isAfter(to))
                .toList();
        });
    }

    // ---------- upsert ----------

    /**
     * The day already having a reading is NOT a branch the service takes — a single upsert
     * covers both cases. That is the whole point: read-then-save let two concurrent weigh-ins
     * (double tap, second tab) collide on uq_athlete_weights_day and surface as a 500.
     */
    @Test
    void shouldCorrectTheExistingReadingWhenWeighingTwiceOnTheSameDay() {
        // Given: today already has a reading
        AthleteWeight existing = new AthleteWeight(athlete, today(), new BigDecimal("70.00"));
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(List.of(existing));

        // When
        service.recordMyWeight(athleteId, weighIn("71.5"));

        // Then: one statement settles it, and it never reads the day first
        // (the repository has no find-by-day method at all — that is the guard)
        verify(weightRepository).upsertReading(eq(athleteId), eq(today()), eq(new BigDecimal("71.50")), any());
        verify(weightRepository, never()).save(any());
    }

    @Test
    void shouldCreateAReadingWhenTheDayIsStillEmpty() {
        // Given
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(List.of());

        // When
        service.recordMyWeight(athleteId, weighIn("70.0"));

        // Then
        verify(weightRepository).upsertReading(eq(athleteId), eq(today()), eq(new BigDecimal("70.00")), any());
    }

    @Test
    void shouldRoundToTwoDecimalsInsteadOfRejectingAThirdOne() {
        // Given: the column is NUMERIC(5,2) — 70.333 must not be a 400
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(List.of());
        ArgumentCaptor<BigDecimal> saved = ArgumentCaptor.forClass(BigDecimal.class);

        // When
        service.recordMyWeight(athleteId, weighIn("70.333"));

        // Then
        verify(weightRepository).upsertReading(eq(athleteId), eq(today()), saved.capture(), any());
        assertEquals("70.33", saved.getValue().toPlainString());
    }

    // ---------- guards ----------

    @Test
    void shouldRejectAReadingDatedInTheFuture() {
        // Given
        SaveWeightRequest request = new SaveWeightRequest(today().plusDays(1), new BigDecimal("70"));

        // When / Then
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.recordMyWeight(athleteId, request));
        assertEquals("training.weight.future", e.getMessage());
        verify(weightRepository, never()).upsertReading(any(), any(), any(), any());
    }

    @Test
    void shouldAcceptAReadingBackfilledForAnEarlierDay() {
        // Given: the athlete forgot to weigh in on Tuesday and catches up today
        LocalDate missedDay = today().minusDays(3);
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(List.of());

        // When
        service.recordMyWeight(athleteId, new SaveWeightRequest(missedDay, new BigDecimal("70.0")));

        // Then: stored against the day it was measured, not the day it was typed
        verify(weightRepository).upsertReading(eq(athleteId), eq(missedDay), any(), any());
    }

    @Test
    void shouldLetABackfilledReadingCountTowardsTodaysTrend() {
        // Given: two readings, and the athlete backfills a third from two days ago
        LocalDate missedDay = today().minusDays(2);
        when(weightRepository.findRange(eq(athleteId), any(), any()))
            .thenReturn(recentReadings("70.0", "69.0", "68.0"));
        ArgumentCaptor<ConfirmedTrend> trend = ArgumentCaptor.forClass(ConfirmedTrend.class);

        // When
        service.recordMyWeight(athleteId, new SaveWeightRequest(missedDay, new BigDecimal("70.0")));

        // Then: the window is re-read after the save, so catching up can confirm the trend
        // and close a goal — filling a gap is exactly as valid as weighing in today
        verify(goalService).evaluateWeightGoals(eq(athleteId), trend.capture());
        assertEquals(3, trend.getValue().samples());
    }

    @Test
    void shouldRejectAReadingOlderThanTheChartWindow() {
        // Given: older than the 120-day window — it would save and then never render
        SaveWeightRequest request = new SaveWeightRequest(today().minusDays(120), new BigDecimal("70"));

        // When / Then
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.recordMyWeight(athleteId, request));
        assertEquals("training.weight.too.old", e.getMessage());
        verify(weightRepository, never()).upsertReading(any(), any(), any(), any());
    }

    @Test
    void shouldAcceptAReadingOnTheOldestDayTheChartStillShows() {
        // Given: the exact left edge of the window must remain reachable
        LocalDate oldest = today().minusDays(AthleteWeightService.BACKFILL_DAYS - 1L);
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(List.of());

        // When
        service.recordMyWeight(athleteId, new SaveWeightRequest(oldest, new BigDecimal("70.0")));

        // Then
        verify(weightRepository).upsertReading(eq(athleteId), eq(oldest), any(), any());
    }

    // ---------- ranges + the trailing-average edge ----------

    @Test
    void shouldNotTruncateTheTrailingAverageAtTheStartOfTheRange() {
        // Given: a steady 70.0 kg for a week, then one clearly different reading landing
        // exactly on the first day the chart shows
        LocalDate chartFrom = today().minusDays(WeightRange.RECENT.days() - 1L);
        List<AthleteWeight> readings = new ArrayList<>();
        for (int i = 7; i >= 1; i--) {
            readings.add(new AthleteWeight(athlete, chartFrom.minusDays(i), new BigDecimal("70.0")));
        }
        readings.add(new AthleteWeight(athlete, chartFrom, new BigDecimal("76.0")));
        // The stub HONOURS the requested window — otherwise this test would pass even with a
        // lookback that stops at chartFrom, which is exactly the bug it exists to catch
        stubRangeHonouringBounds(readings);

        // When
        AthleteWeightSeriesDto series = service.getMySeries(athleteId, WeightRange.RECENT);

        // Then: the first charted point averages the full window, not just itself. Truncating
        // it would show 76.0 and bend the left edge of the line for no real reason.
        AthleteWeightEntryDto first = series.entries().get(0);
        assertEquals(chartFrom, first.measuredOn());
        assertEquals(0, new BigDecimal("76.00").compareTo(first.weightKg()));
        assertEquals(0, new BigDecimal("70.86").compareTo(first.trendKg()),
            "expected the mean of 6x70.0 + 76.0, got " + first.trendKg());
    }

    @Test
    void shouldReadBeforeTheRangeButNeverSendThoseDays() {
        // Given: readings on both sides of the chart's left edge
        LocalDate chartFrom = today().minusDays(WeightRange.RECENT.days() - 1L);
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(List.of(
            new AthleteWeight(athlete, chartFrom.minusDays(3), new BigDecimal("70.0")),
            new AthleteWeight(athlete, chartFrom, new BigDecimal("71.0"))));
        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);

        // When
        AthleteWeightSeriesDto series = service.getMySeries(athleteId, WeightRange.RECENT);

        // Then: the query reaches back past the chart, the payload does not
        verify(weightRepository).findRange(eq(athleteId), from.capture(), any());
        assertTrue(from.getValue().isBefore(chartFrom), "lookback must start before the chart");
        assertEquals(1, series.entries().size());
        assertEquals(chartFrom, series.entries().get(0).measuredOn());
    }

    @Test
    void shouldNotSeeOlderReadingsInTheDefaultRangeButSeeThemInALongerOne() {
        // Given: one reading 200 days back — outside RECENT, inside YEAR
        LocalDate old = today().minusDays(200);
        stubRangeHonouringBounds(List.of(new AthleteWeight(athlete, old, new BigDecimal("74.0"))));

        // When / Then
        assertTrue(service.getMySeries(athleteId, WeightRange.RECENT).entries().isEmpty());
        assertEquals(1, service.getMySeries(athleteId, WeightRange.YEAR).entries().size());
    }

    @Test
    void shouldDefaultToTheRecentRangeWhenNoneIsGiven() {
        // Given
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(List.of());
        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);

        // When: no range at all — nobody's chart may silently grow
        service.getMySeries(athleteId, (WeightRange) null);

        // Then: the lookback is RECENT plus the trailing-average tail, nothing wider
        verify(weightRepository).findRange(eq(athleteId), from.capture(), any());
        long lookback = today().toEpochDay() - from.getValue().toEpochDay() + 1;
        assertEquals(WeightRange.RECENT.days() + WeightTrendCalculator.WINDOW_DAYS - 1, lookback);
    }

    @Test
    void shouldKeepTheBackfillBoundFixedWhateverRangeIsBeingViewed() {
        // Given: the widest range on screen
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(List.of());

        // When
        AthleteWeightSeriesDto series = service.getMySeries(athleteId, WeightRange.ALL);

        // Then: looking at five years must NOT let the date picker offer five years of
        // backfilling — the server would reject anything past the fixed bound
        assertEquals(AthleteWeightService.BACKFILL_DAYS, series.backfillDays());
        assertEquals(WeightRange.RECENT.days(), series.backfillDays());
    }

    @Test
    void shouldTellTheClientHowFarBackItMayBackfill() {
        // Given
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(List.of());

        // When
        AthleteWeightSeriesDto series = service.getMySeries(athleteId, (WeightRange) null);

        // Then: the date picker derives its lower bound from this, so the two cannot drift
        assertEquals(AthleteWeightService.BACKFILL_DAYS, series.backfillDays());
    }

    @Test
    void shouldRejectAWeightWithASlippedDecimalPoint() {
        // Given: 742 instead of 74.2
        SaveWeightRequest request = new SaveWeightRequest(today(), new BigDecimal("742"));

        // When / Then
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.recordMyWeight(athleteId, request));
        assertEquals("training.weight.range", e.getMessage());
    }

    @Test
    void shouldRefuseTheWholeSeriesWhenTheUserIsNotAnAthlete() {
        // Given
        UUID strangerId = UUID.randomUUID();
        when(calendarService.requireAthlete(strangerId)).thenThrow(new IllegalStateException("training.not.athlete"));

        // When / Then
        assertThrows(IllegalStateException.class, () -> service.getMySeries(strangerId, null));
    }

    // ---------- the min-3-readings rule ----------

    @Test
    void shouldNotCloseGoalsWhenTheTrendRestsOnTwoReadings() {
        // Given: only two readings in the window, even though the latest is well under target
        List<AthleteWeight> readings = recentReadings("70.0", "69.0");
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(readings);

        // When
        service.recordMyWeight(athleteId, weighIn("69.0"));

        // Then: the goal evaluation is handed nothing to act on
        verify(goalService).evaluateWeightGoals(eq(athleteId), isNull());
    }

    @Test
    void shouldCloseGoalsOnceTheThirdReadingConfirmsTheTrend() {
        // Given: three readings in the window
        List<AthleteWeight> readings = recentReadings("70.0", "69.0", "68.0");
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(readings);
        ArgumentCaptor<ConfirmedTrend> trend = ArgumentCaptor.forClass(ConfirmedTrend.class);

        // When
        service.recordMyWeight(athleteId, weighIn("68.0"));

        // Then: a confirmed trend of exactly the three readings reaches the goal service
        verify(goalService).evaluateWeightGoals(eq(athleteId), trend.capture());
        assertNotNull(trend.getValue());
        assertEquals(3, trend.getValue().samples());
        assertEquals(0, new BigDecimal("69.00").compareTo(trend.getValue().value()));
    }

    @Test
    void shouldReportTheTrendAsUnconfirmedButStillShowItBelowThreeReadings() {
        // Given
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(recentReadings("70.0", "71.0"));

        // When
        AthleteWeightSeriesDto series = service.getMySeries(athleteId, (WeightRange) null);

        // Then: the athlete sees a trend, but it carries no authority over goals
        assertNotNull(series.currentTrendKg());
        assertEquals(2, series.trendSampleCount());
        assertFalse(series.trendConfirmed());
    }

    // ---------- deletion ----------

    @Test
    void shouldNeverReopenAGoalWhenAReadingIsDeleted() {
        // Given / When: removing data must not take a trophy away
        service.deleteMyWeight(athleteId, today());

        // Then
        verify(weightRepository).deleteByAthleteIdAndMeasuredOn(athleteId, today());
        verify(goalService, never()).evaluateWeightGoals(any(), any());
    }

    // ---------- series shape ----------

    @Test
    void shouldReturnAnEmptySeriesForAnAthleteWhoNeverWeighedIn() {
        // Given
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(List.of());

        // When
        AthleteWeightSeriesDto series = service.getMySeries(athleteId, (WeightRange) null);

        // Then: nullable fields mean "no data", so the panel can render its empty state
        assertTrue(series.entries().isEmpty());
        assertNull(series.currentTrendKg());
        assertNull(series.weeklyChangePercent());
        assertNull(series.latestWeightKg());
        assertFalse(series.rapidLoss());
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- the 90-day low ----------

    @Test
    void shouldReportTheLowestConfirmedTrendAndTheDayItWasReached() {
        // Given: 70 kg now, and a real 68 kg spell a month ago held over three readings
        stubRangeHonouringBounds(readingsOn(0, "70", 1, "70", 2, "70", 30, "68", 31, "68", 32, "68"));

        // When
        AthleteWeightSeriesDto series = service.getMySeries(athleteId, WeightRange.RECENT);

        // Then
        assertNotNull(series.lowestTrendKg());
        assertEquals(0, new BigDecimal("68.00").compareTo(series.lowestTrendKg()));
        assertEquals(today().minusDays(30), series.lowestTrendOn());
        assertEquals(WeightTrendCalculator.LOWEST_WINDOW_DAYS, series.lowestWindowDays());
    }

    @Test
    void shouldKeepTheLowestWindowFixedWhateverRangeIsBeingViewed() {
        // Given: a confirmed 65 kg spell 100 days back — on the chart under ALL, outside the
        // 90 days the tile is labelled with
        stubRangeHonouringBounds(readingsOn(0, "70", 1, "70", 2, "70", 100, "65", 101, "65", 102, "65"));

        // When
        AthleteWeightSeriesDto series = service.getMySeries(athleteId, WeightRange.ALL);

        // Then: the older spell is drawn, but a tile that says "3 months" must not quote it
        assertEquals(6, series.entries().size());
        assertNotNull(series.lowestTrendKg());
        assertEquals(0, new BigDecimal("70.00").compareTo(series.lowestTrendKg()));
        assertEquals(today(), series.lowestTrendOn());
    }

    @Test
    void shouldNotClaimALowestWeightThatCouldNotHaveClosedAGoal() {
        // Given: two readings — enough to draw a trend, never enough to confirm one
        stubRangeHonouringBounds(readingsOn(0, "70", 1, "71"));

        // When
        AthleteWeightSeriesDto series = service.getMySeries(athleteId, WeightRange.RECENT);

        // Then: the trend still shows, but no personal best is claimed alongside it
        assertNotNull(series.currentTrendKg());
        assertFalse(series.trendConfirmed());
        assertNull(series.lowestTrendKg());
        assertNull(series.lowestTrendOn());
    }
}
