package pl.nextsteppro.climbing.api.trainingcalendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.nextsteppro.climbing.domain.athleteweight.AthleteWeight;
import pl.nextsteppro.climbing.domain.athleteweight.AthleteWeightRepository;
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

    private SaveWeightRequest weighIn(String weight) {
        return new SaveWeightRequest(today(), new BigDecimal(weight));
    }

    // ---------- upsert ----------

    @Test
    void shouldCorrectTheExistingReadingWhenWeighingTwiceOnTheSameDay() {
        // Given: today already has a reading
        AthleteWeight existing = new AthleteWeight(athlete, today(), new BigDecimal("70.00"));
        when(weightRepository.findByAthleteIdAndMeasuredOn(athleteId, today())).thenReturn(Optional.of(existing));
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(List.of(existing));

        // When
        service.recordMyWeight(athleteId, weighIn("71.5"));

        // Then: corrected in place, never a second row
        assertEquals(0, new BigDecimal("71.50").compareTo(existing.getWeightKg()));
        verify(weightRepository, never()).save(any());
    }

    @Test
    void shouldCreateAReadingWhenTheDayIsStillEmpty() {
        // Given
        when(weightRepository.findByAthleteIdAndMeasuredOn(athleteId, today())).thenReturn(Optional.empty());
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(List.of());

        // When
        service.recordMyWeight(athleteId, weighIn("70.0"));

        // Then
        verify(weightRepository).save(any(AthleteWeight.class));
    }

    @Test
    void shouldRoundToTwoDecimalsInsteadOfRejectingAThirdOne() {
        // Given: the column is NUMERIC(5,2) — 70.333 must not be a 400
        when(weightRepository.findByAthleteIdAndMeasuredOn(athleteId, today())).thenReturn(Optional.empty());
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(List.of());
        ArgumentCaptor<AthleteWeight> saved = ArgumentCaptor.forClass(AthleteWeight.class);

        // When
        service.recordMyWeight(athleteId, weighIn("70.333"));

        // Then
        verify(weightRepository).save(saved.capture());
        assertEquals("70.33", saved.getValue().getWeightKg().toPlainString());
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
        verify(weightRepository, never()).save(any());
    }

    @Test
    void shouldAcceptAReadingBackfilledForAnEarlierDay() {
        // Given: the athlete forgot to weigh in on Tuesday and catches up today
        LocalDate missedDay = today().minusDays(3);
        when(weightRepository.findByAthleteIdAndMeasuredOn(athleteId, missedDay)).thenReturn(Optional.empty());
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(List.of());
        ArgumentCaptor<AthleteWeight> saved = ArgumentCaptor.forClass(AthleteWeight.class);

        // When
        service.recordMyWeight(athleteId, new SaveWeightRequest(missedDay, new BigDecimal("70.0")));

        // Then: stored against the day it was measured, not the day it was typed
        verify(weightRepository).save(saved.capture());
        assertEquals(missedDay, saved.getValue().getMeasuredOn());
    }

    @Test
    void shouldLetABackfilledReadingCountTowardsTodaysTrend() {
        // Given: two readings, and the athlete backfills a third from two days ago
        LocalDate missedDay = today().minusDays(2);
        when(weightRepository.findByAthleteIdAndMeasuredOn(athleteId, missedDay)).thenReturn(Optional.empty());
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
        verify(weightRepository, never()).save(any());
    }

    @Test
    void shouldAcceptAReadingOnTheOldestDayTheChartStillShows() {
        // Given: the exact left edge of the window must remain reachable
        LocalDate oldest = today().minusDays(AthleteWeightService.DEFAULT_HISTORY_DAYS - 1L);
        when(weightRepository.findByAthleteIdAndMeasuredOn(athleteId, oldest)).thenReturn(Optional.empty());
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(List.of());

        // When
        service.recordMyWeight(athleteId, new SaveWeightRequest(oldest, new BigDecimal("70.0")));

        // Then
        verify(weightRepository).save(any(AthleteWeight.class));
    }

    @Test
    void shouldTellTheClientHowFarBackItMayBackfill() {
        // Given
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(List.of());

        // When
        AthleteWeightSeriesDto series = service.getMySeries(athleteId, null);

        // Then: the date picker derives its lower bound from this, so the two cannot drift
        assertEquals(AthleteWeightService.DEFAULT_HISTORY_DAYS, series.historyDays());
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
        when(weightRepository.findByAthleteIdAndMeasuredOn(athleteId, today()))
            .thenReturn(Optional.of(readings.get(readings.size() - 1)));
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
        when(weightRepository.findByAthleteIdAndMeasuredOn(athleteId, today()))
            .thenReturn(Optional.of(readings.get(readings.size() - 1)));
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
        AthleteWeightSeriesDto series = service.getMySeries(athleteId, null);

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
        AthleteWeightSeriesDto series = service.getMySeries(athleteId, null);

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
}
