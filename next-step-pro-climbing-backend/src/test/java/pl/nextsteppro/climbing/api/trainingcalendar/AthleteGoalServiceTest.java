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
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    @Mock private TrainingCalendarService calendarService;
    @Mock private MessageService msg;

    private AthleteGoalService service;

    private UUID athleteId;
    private User athlete;

    @BeforeEach
    void setUp() {
        service = new AthleteGoalService(goalRepository, calendarService, msg);

        athleteId = UUID.randomUUID();
        athlete = new User("athlete@example.com", "Anna", "Wspinaczka", "+48123456789", "anna");
        athlete.setAthlete(true);
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
        SaveGoalRequest request = new SaveGoalRequest(GoalHorizon.SHORT, "Przejść 7a na wędkę", LocalDate.of(2026, 9, 15));

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
        SaveGoalRequest request = new SaveGoalRequest(GoalHorizon.SHORT, "Nowy cel", LocalDate.of(2026, 9, 1));

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
        SaveGoalRequest request = new SaveGoalRequest(GoalHorizon.SHORT, "Nowy cel", LocalDate.of(2026, 9, 1));

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
        SaveGoalRequest request = new SaveGoalRequest(GoalHorizon.LONG, "Cel", LocalDate.of(2027, 3, 1));

        // When / Then
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> service.createGoal(athleteId, request));
        assertEquals("training.goal.active.exists", e.getMessage());
    }

    @Test
    void shouldRejectCreateWhenContentBlank() {
        // Given
        when(goalRepository.findByAthleteIdAndAchievedAtIsNull(athleteId)).thenReturn(List.of());
        SaveGoalRequest request = new SaveGoalRequest(GoalHorizon.SHORT, "   ", LocalDate.of(2026, 9, 1));

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
        SaveGoalRequest request = new SaveGoalRequest(
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
        SaveGoalRequest request = new SaveGoalRequest(GoalHorizon.LONG, "Nowa treść", LocalDate.of(2026, 11, 15));

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
        SaveGoalRequest request = new SaveGoalRequest(GoalHorizon.SHORT, "Zmiana", LocalDate.of(2026, 9, 1));

        // When / Then
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> service.updateGoal(goalId, request));
        assertEquals("training.goal.achieved.immutable", e.getMessage());
    }

    @Test
    void shouldRejectDeleteWhenGoalAchieved() {
        // Given
        UUID goalId = UUID.randomUUID();
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(achievedGoal()));

        // When / Then
        assertThrows(IllegalStateException.class, () -> service.deleteGoal(goalId));
        verify(goalRepository, never()).delete(any(AthleteGoal.class));
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

        // Then: the trophy stays in the table forever — never deleted
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

    // ---------- helpers ----------

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
