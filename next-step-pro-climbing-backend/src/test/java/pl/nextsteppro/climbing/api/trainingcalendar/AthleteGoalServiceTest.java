package pl.nextsteppro.climbing.api.trainingcalendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import pl.nextsteppro.climbing.domain.athletegoal.AthleteGoal;
import pl.nextsteppro.climbing.domain.athletegoal.AthleteGoalRepository;
import pl.nextsteppro.climbing.domain.athletegoal.GoalHorizon;
import pl.nextsteppro.climbing.domain.athletegoal.GoalKind;
import pl.nextsteppro.climbing.domain.athleteweight.AthleteWeight;
import pl.nextsteppro.climbing.domain.athleteweight.AthleteWeightRepository;
import pl.nextsteppro.climbing.domain.athleteweight.WeightTrendCalculator.ConfirmedTrend;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AthleteGoalService: one active goal per horizon, immutable achieved
 * goals (trophy chest), sanitization and athlete-flag guards.
 */
@ExtendWith(MockitoExtension.class)
class AthleteGoalServiceTest {

    @Mock private AthleteGoalRepository goalRepository;
    @Mock private AthleteWeightRepository weightRepository;
    @Mock private TrainingCalendarService calendarService;
    @Mock private MessageService msg;

    /** A training goal: no kind (defaults to GENERAL) and no weight target. */
    private static SaveGoalRequest generalGoalRequest(GoalHorizon horizon, String content, LocalDate targetDate) {
        return new SaveGoalRequest(null, horizon, content, targetDate, null);
    }

    private AthleteGoalService service;

    private UUID athleteId;
    private User athlete;

    @BeforeEach
    void setUp() {
        service = new AthleteGoalService(goalRepository, weightRepository, calendarService, msg);

        athleteId = UUID.randomUUID();
        athlete = new User("athlete@example.com", "Anna", "Wspinaczka", "+48123456789", "anna");
        athlete.setAthlete(true);
        // Athlete-side endpoints sit behind the GDPR art. 9 consent gate (V76)
        athlete.grantTrainingConsent();
        setField(athlete, "id", athleteId);

        lenient().when(msg.get(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(calendarService.requireAthlete(athleteId)).thenReturn(athlete);
        lenient().when(calendarService.requireFlaggedAthlete(athleteId)).thenReturn(athlete);
    }

    // ---------- create ----------

    @Test
    void shouldCreateGoalWhenHorizonFree() {
        // Given
        when(goalRepository.findByAthleteIdAndAchievedAtIsNull(athleteId)).thenReturn(List.of());
        when(goalRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        SaveGoalRequest request = generalGoalRequest(GoalHorizon.SHORT, "Przejść 7a na wędkę", LocalDate.of(2026, 9, 15));

        // When
        AthleteGoalDto dto = service.createGoal(athleteId, request);

        // Then
        assertEquals("SHORT", dto.horizon());
        assertEquals("Przejść 7a na wędkę", dto.content());
        assertEquals(LocalDate.of(2026, 9, 15), dto.targetDate());
        assertNull(dto.achievedAt());
    }

    @Test
    void shouldRejectCreateWhenActiveGoalExistsForHorizon() {
        // Given
        AthleteGoal existing = new AthleteGoal(athlete, GoalHorizon.SHORT, "Stary cel", LocalDate.of(2026, 8, 1));
        when(goalRepository.findByAthleteIdAndAchievedAtIsNull(athleteId)).thenReturn(List.of(existing));
        SaveGoalRequest request = generalGoalRequest(GoalHorizon.SHORT, "Nowy cel", LocalDate.of(2026, 9, 1));

        // When / Then
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> service.createGoal(athleteId, request));
        assertEquals("training.goal.active.exists", e.getMessage());
        verify(goalRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldAllowCreateWhenOnlyOtherHorizonsTaken() {
        // Given
        AthleteGoal medium = new AthleteGoal(athlete, GoalHorizon.MEDIUM, "Zawody", LocalDate.of(2026, 12, 20));
        when(goalRepository.findByAthleteIdAndAchievedAtIsNull(athleteId)).thenReturn(List.of(medium));
        when(goalRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        SaveGoalRequest request = generalGoalRequest(GoalHorizon.SHORT, "Nowy cel", LocalDate.of(2026, 9, 1));

        // When
        AthleteGoalDto dto = service.createGoal(athleteId, request);

        // Then
        assertEquals("SHORT", dto.horizon());
    }

    @Test
    void shouldMapUniqueViolationToConflictWhenConcurrentCreate() {
        // Given: pre-check passes, but a concurrent request wins the race — the partial
        // unique index rejects the flush
        when(goalRepository.findByAthleteIdAndAchievedAtIsNull(athleteId)).thenReturn(List.of());
        when(goalRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        SaveGoalRequest request = generalGoalRequest(GoalHorizon.LONG, "Cel", LocalDate.of(2027, 3, 1));

        // When / Then
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> service.createGoal(athleteId, request));
        assertEquals("training.goal.active.exists", e.getMessage());
    }

    @Test
    void shouldRejectCreateWhenContentBlank() {
        // Given
        when(goalRepository.findByAthleteIdAndAchievedAtIsNull(athleteId)).thenReturn(List.of());
        SaveGoalRequest request = generalGoalRequest(GoalHorizon.SHORT, "   ", LocalDate.of(2026, 9, 1));

        // When / Then
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.createGoal(athleteId, request));
        assertEquals("training.goal.content.empty", e.getMessage());
    }

    @Test
    void shouldSanitizeContentWhenCreating() {
        // Given
        when(goalRepository.findByAthleteIdAndAchievedAtIsNull(athleteId)).thenReturn(List.of());
        when(goalRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        SaveGoalRequest request = generalGoalRequest(
            GoalHorizon.SHORT, "<script>alert(1)</script> 7a", LocalDate.of(2026, 9, 1));

        // When
        AthleteGoalDto dto = service.createGoal(athleteId, request);

        // Then: HTML escaped, Polish text survives untouched elsewhere (UTF-8 escape variant)
        assertFalse(dto.content().contains("<script>"));
        assertTrue(dto.content().contains("&lt;script&gt;"));
    }

    // ---------- update / delete / achieve ----------

    @Test
    void shouldUpdateActiveGoalAndKeepHorizon() {
        // Given
        UUID goalId = UUID.randomUUID();
        AthleteGoal goal = new AthleteGoal(athlete, GoalHorizon.MEDIUM, "Stara treść", LocalDate.of(2026, 10, 1));
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        // Horizon in the request differs — must be IGNORED (fixed for the goal's lifetime)
        SaveGoalRequest request = generalGoalRequest(GoalHorizon.LONG, "Nowa treść", LocalDate.of(2026, 11, 15));

        // When
        AthleteGoalDto dto = service.updateGoal(goalId, request);

        // Then
        assertEquals("MEDIUM", dto.horizon());
        assertEquals("Nowa treść", dto.content());
        assertEquals(LocalDate.of(2026, 11, 15), dto.targetDate());
    }

    @Test
    void shouldRejectUpdateWhenGoalAchieved() {
        // Given
        UUID goalId = UUID.randomUUID();
        AthleteGoal goal = achievedGoal();
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        SaveGoalRequest request = generalGoalRequest(GoalHorizon.SHORT, "Zmiana", LocalDate.of(2026, 9, 1));

        // When / Then
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> service.updateGoal(goalId, request));
        assertEquals("training.goal.achieved.immutable", e.getMessage());
    }

    /** A trophy cannot be rewritten, but the coach may bin one awarded by mistake. */
    @Test
    void shouldDeleteAchievedGoal() {
        // Given
        UUID goalId = UUID.randomUUID();
        AthleteGoal goal = achievedGoal();
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));

        // When
        service.deleteGoal(goalId);

        // Then
        verify(goalRepository).delete(goal);
    }

    @Test
    void shouldDeleteActiveGoal() {
        // Given
        UUID goalId = UUID.randomUUID();
        AthleteGoal goal = new AthleteGoal(athlete, GoalHorizon.SHORT, "Cel", LocalDate.of(2026, 9, 1));
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));

        // When
        service.deleteGoal(goalId);

        // Then
        verify(goalRepository).delete(goal);
    }

    @Test
    void shouldMarkAchievedNowWhenNoDateGiven() {
        // Given
        UUID goalId = UUID.randomUUID();
        AthleteGoal goal = new AthleteGoal(athlete, GoalHorizon.LONG, "7c przed 30-tką", LocalDate.of(2027, 3, 1));
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));

        // When
        AthleteGoalDto dto = service.achieveGoal(goalId, null);

        // Then: achieving moves the row to the chest, it never removes it
        assertNotNull(dto.achievedAt());
        assertTrue(goal.isAchieved());
        verify(goalRepository, never()).delete(any(AthleteGoal.class));
    }

    @Test
    void shouldBackdateAchievementWhenPastDateGiven() {
        // Given
        UUID goalId = UUID.randomUUID();
        AthleteGoal goal = new AthleteGoal(athlete, GoalHorizon.SHORT, "Boulder 7B", LocalDate.of(2026, 8, 1));
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));

        // When: coach records the achievement a few days late
        AthleteGoalDto dto = service.achieveGoal(goalId, LocalDate.of(2026, 7, 10));

        // Then: achievedAt lands on that day's start in Warsaw time
        Instant expected = LocalDate.of(2026, 7, 10).atStartOfDay(java.time.ZoneId.of("Europe/Warsaw")).toInstant();
        assertEquals(expected, dto.achievedAt());
    }

    @Test
    void shouldAcceptTodayAsAchievementDate() {
        // Given
        UUID goalId = UUID.randomUUID();
        AthleteGoal goal = new AthleteGoal(athlete, GoalHorizon.MEDIUM, "Cel", LocalDate.of(2026, 12, 1));
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Europe/Warsaw"));

        // When / Then: today is allowed (boundary), no exception
        AthleteGoalDto dto = service.achieveGoal(goalId, today);
        assertNotNull(dto.achievedAt());
    }

    @Test
    void shouldRejectFutureAchievementDate() {
        // Given
        UUID goalId = UUID.randomUUID();
        AthleteGoal goal = new AthleteGoal(athlete, GoalHorizon.SHORT, "Cel", LocalDate.of(2026, 8, 1));
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        LocalDate tomorrow = LocalDate.now(java.time.ZoneId.of("Europe/Warsaw")).plusDays(1);

        // When / Then
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.achieveGoal(goalId, tomorrow));
        assertEquals("training.goal.achieved.future", e.getMessage());
        assertFalse(goal.isAchieved());
    }

    @Test
    void shouldRejectAchieveWhenAlreadyAchieved() {
        // Given
        UUID goalId = UUID.randomUUID();
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(achievedGoal()));

        // When / Then
        assertThrows(IllegalStateException.class, () -> service.achieveGoal(goalId, null));
    }

    @Test
    void shouldRejectWhenGoalNotFound() {
        // Given
        UUID goalId = UUID.randomUUID();
        when(goalRepository.findById(goalId)).thenReturn(Optional.empty());

        // When / Then
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.achieveGoal(goalId, null));
        assertEquals("training.goal.not.found", e.getMessage());
    }

    // ---------- reads ----------

    @Test
    void shouldReturnActiveSortedByHorizonAndAchievedAsGiven() {
        // Given: repository returns active goals in arbitrary order
        AthleteGoal longG = new AthleteGoal(athlete, GoalHorizon.LONG, "Długi", LocalDate.of(2027, 3, 1));
        AthleteGoal shortG = new AthleteGoal(athlete, GoalHorizon.SHORT, "Krótki", LocalDate.of(2026, 9, 1));
        AthleteGoal mediumG = new AthleteGoal(athlete, GoalHorizon.MEDIUM, "Średni", LocalDate.of(2026, 12, 1));
        when(goalRepository.findByAthleteIdAndAchievedAtIsNull(athleteId))
            .thenReturn(List.of(longG, shortG, mediumG));
        when(goalRepository.findByAthleteIdAndAchievedAtIsNotNullOrderByAchievedAtDesc(athleteId))
            .thenReturn(List.of(achievedGoal()));

        // When
        GoalsDto dto = service.getMyGoals(athleteId);

        // Then: banner order is fixed short → medium → long
        assertEquals(List.of("SHORT", "MEDIUM", "LONG"),
            dto.active().stream().map(AthleteGoalDto::horizon).toList());
        assertEquals(1, dto.achieved().size());
    }

    @Test
    void shouldRejectReadWhenUserNotAthlete() {
        // Given
        UUID regularId = UUID.randomUUID();
        when(calendarService.requireAthlete(regularId))
            .thenThrow(new IllegalStateException("training.calendar.not.athlete"));

        // When / Then
        assertThrows(IllegalStateException.class, () -> service.getMyGoals(regularId));
    }

    // ---------- weight goals ----------

    @Test
    void shouldSnapshotTheCurrentTrendAsTheStartingWeight() {
        // Given: three readings averaging 70.00
        stubTrendReadings("70.0", "71.0", "69.0");
        when(goalRepository.findByAthleteIdAndAchievedAtIsNull(athleteId)).thenReturn(List.of());
        when(goalRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        AthleteGoalDto dto = service.createGoal(athleteId, weightGoalRequest(GoalHorizon.MEDIUM, "67.0"));

        // Then: the coach never types the start — it is measured, so the progress bar cannot lie
        assertEquals("WEIGHT", dto.kind());
        assertEquals(0, new BigDecimal("70.00").compareTo(requireNonNull(dto.startWeightKg())));
        assertEquals(0, new BigDecimal("67.00").compareTo(requireNonNull(dto.targetWeightKg())));
    }

    @Test
    void shouldRefuseAWeightGoalBeforeTheAthleteEverWeighedIn() {
        // Given: no readings at all — there is nothing to measure progress from
        stubTrendReadings();
        when(goalRepository.findByAthleteIdAndAchievedAtIsNull(athleteId)).thenReturn(List.of());

        // When / Then
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> service.createGoal(athleteId, weightGoalRequest(GoalHorizon.SHORT, "67.0")));
        assertEquals("training.goal.weight.no.data", e.getMessage());
    }

    @Test
    void shouldRefuseAWeightGoalAlreadyMetOnTheDayItIsSet() {
        // Given: the target equals the current trend — it would close on the next weigh-in
        stubTrendReadings("70.0");
        when(goalRepository.findByAthleteIdAndAchievedAtIsNull(athleteId)).thenReturn(List.of());

        // When / Then
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.createGoal(athleteId, weightGoalRequest(GoalHorizon.SHORT, "70.0")));
        assertEquals("training.goal.weight.already.met", e.getMessage());
    }

    @Test
    void shouldAllowATrainingGoalAndAWeightGoalOnTheSameHorizon() {
        // Given: the SHORT slot is taken by a training goal
        stubTrendReadings("70.0");
        when(goalRepository.findByAthleteIdAndAchievedAtIsNull(athleteId))
            .thenReturn(List.of(new AthleteGoal(athlete, GoalHorizon.SHORT, "Przejść 7a", LocalDate.of(2026, 9, 1))));
        when(goalRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        // When / Then: a weight goal occupies a different slot, so it goes through
        AthleteGoalDto dto = service.createGoal(athleteId, weightGoalRequest(GoalHorizon.SHORT, "67.0"));
        assertEquals("WEIGHT", dto.kind());
    }

    @Test
    void shouldRejectASecondActiveWeightGoalOnTheSameHorizon() {
        // Given
        when(goalRepository.findByAthleteIdAndAchievedAtIsNull(athleteId)).thenReturn(List.of(activeWeightGoal()));

        // When / Then
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> service.createGoal(athleteId, weightGoalRequest(GoalHorizon.SHORT, "66.0")));
        assertEquals("training.goal.active.exists", e.getMessage());
    }

    @Test
    void shouldCloseAWeightGoalReachedByAConfirmedTrend() {
        // Given: a loss goal down to 67 kg, and a confirmed trend that got there
        AthleteGoal goal = activeWeightGoal();
        when(goalRepository.findByAthleteIdAndKindAndAchievedAtIsNull(athleteId, GoalKind.WEIGHT))
            .thenReturn(List.of(goal));

        // When
        service.evaluateWeightGoals(athleteId, new ConfirmedTrend(new BigDecimal("66.80"), 3));

        // Then: closed by the machine, so the coach may still undo it
        assertTrue(goal.isAchieved());
        assertTrue(goal.isAchievedAutomatically());
    }

    @Test
    void shouldLeaveAWeightGoalOpenWhenTheTrendIsStillAboveTarget() {
        // Given
        AthleteGoal goal = activeWeightGoal();
        when(goalRepository.findByAthleteIdAndKindAndAchievedAtIsNull(athleteId, GoalKind.WEIGHT))
            .thenReturn(List.of(goal));

        // When
        service.evaluateWeightGoals(athleteId, new ConfirmedTrend(new BigDecimal("68.00"), 5));

        // Then
        assertFalse(goal.isAchieved());
    }

    @Test
    void shouldDoNothingWhenTheTrendIsNotConfirmed() {
        // Given / When: too few readings — the weight service hands over null
        service.evaluateWeightGoals(athleteId, null);

        // Then: not even a query, let alone a closure
        verify(goalRepository, never()).findByAthleteIdAndKindAndAchievedAtIsNull(any(), any());
    }

    @Test
    void shouldCloseAGainGoalWhenTheTrendRises() {
        // Given: target ABOVE the start — the comparison must flip
        AthleteGoal goal = AthleteGoal.weightGoal(athlete, GoalHorizon.LONG, "Nabrać masy",
            LocalDate.of(2027, 1, 1), new BigDecimal("75.0"), new BigDecimal("70.0"));
        when(goalRepository.findByAthleteIdAndKindAndAchievedAtIsNull(athleteId, GoalKind.WEIGHT))
            .thenReturn(List.of(goal));

        // When
        service.evaluateWeightGoals(athleteId, new ConfirmedTrend(new BigDecimal("75.20"), 4));

        // Then
        assertTrue(goal.isAchieved());
    }

    // ---------- reopen ----------

    @Test
    void shouldReopenAGoalThatClosedItself() {
        // Given: a mistyped weigh-in closed this goal
        UUID goalId = UUID.randomUUID();
        AthleteGoal goal = activeWeightGoal();
        goal.markAchievedAutomatically(Instant.parse("2026-07-01T10:00:00Z"));
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        when(goalRepository.findByAthleteIdAndAchievedAtIsNull(athleteId)).thenReturn(List.of());
        when(goalRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        AthleteGoalDto dto = service.reopenGoal(goalId);

        // Then
        assertNull(dto.achievedAt());
        assertFalse(dto.achievedAutomatically());
        assertFalse(goal.isAchieved());
    }

    @Test
    void shouldRefuseToReopenAGoalTheCoachClosedByHand() {
        // Given: a human decision — the trophy chest must stay trustworthy
        UUID goalId = UUID.randomUUID();
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(achievedGoal()));

        // When / Then
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.reopenGoal(goalId));
        assertEquals("training.goal.reopen.manual.only", e.getMessage());
    }

    @Test
    void shouldRefuseToReopenAGoalThatWasNeverAchieved() {
        // Given
        UUID goalId = UUID.randomUUID();
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(activeWeightGoal()));

        // When / Then
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.reopenGoal(goalId));
        assertEquals("training.goal.reopen.not.achieved", e.getMessage());
    }

    @Test
    void shouldRefuseToReopenWhenANewGoalAlreadyTookTheFreedSlot() {
        // Given: the coach set a replacement while the old one sat in the trophy chest
        UUID goalId = UUID.randomUUID();
        AthleteGoal goal = activeWeightGoal();
        goal.markAchievedAutomatically(Instant.parse("2026-07-01T10:00:00Z"));
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        when(goalRepository.findByAthleteIdAndAchievedAtIsNull(athleteId)).thenReturn(List.of(activeWeightGoal()));

        // When / Then: a clean 409 rather than a raw constraint violation from the index
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.reopenGoal(goalId));
        assertEquals("training.goal.active.exists", e.getMessage());
    }

    // ---------- helpers ----------

    /** A weight goal request; the service snapshots the start weight itself. */
    private static SaveGoalRequest weightGoalRequest(GoalHorizon horizon, String targetKg) {
        return new SaveGoalRequest(GoalKind.WEIGHT, horizon, "Zejść do wagi startowej",
            LocalDate.of(2026, 12, 1), new BigDecimal(targetKg));
    }

    /** An active loss goal: 70.0 kg → 67.0 kg on the SHORT horizon. */
    private AthleteGoal activeWeightGoal() {
        return AthleteGoal.weightGoal(athlete, GoalHorizon.SHORT, "Zejść do wagi startowej",
            LocalDate.of(2026, 12, 1), new BigDecimal("67.0"), new BigDecimal("70.0"));
    }

    /** Readings on consecutive days ending today, so they all land inside the trend window. */
    private void stubTrendReadings(String... weights) {
        List<AthleteWeight> readings = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Warsaw"));
        for (int i = 0; i < weights.length; i++) {
            readings.add(new AthleteWeight(athlete, today.minusDays(weights.length - 1L - i), new BigDecimal(weights[i])));
        }
        when(weightRepository.findRange(eq(athleteId), any(), any())).thenReturn(readings);
    }

    private AthleteGoal achievedGoal() {
        AthleteGoal goal = new AthleteGoal(athlete, GoalHorizon.SHORT, "Osiągnięty cel", LocalDate.of(2026, 6, 1));
        setField(goal, "achievedAt", Instant.parse("2026-07-01T10:00:00Z"));
        return goal;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
