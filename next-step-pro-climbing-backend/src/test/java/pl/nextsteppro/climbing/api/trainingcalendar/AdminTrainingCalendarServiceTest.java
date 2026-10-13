package pl.nextsteppro.climbing.api.trainingcalendar;

import pl.nextsteppro.climbing.domain.personaltraining.TrainingKind;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTraining;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AdminTrainingCalendarService — the thin coach wrapper.
 * Verifies: activity logging on coach mutations, no logging on comments.
 */
@ExtendWith(MockitoExtension.class)
class AdminTrainingCalendarServiceTest {

    @Mock private TrainingCalendarService core;
    @Mock private TrainingStatsService statsService;
    @Mock private AthleteGoalService goalService;
    @Mock private AttachmentSupport attachments;
    @Mock private ActivityLogService activityLogService;
    @Mock private UserRepository userRepository;

    private AdminTrainingCalendarService service;

    private UUID adminId;
    private User admin;
    private UUID athleteId;
    private User athlete;

    @BeforeEach
    void setUp() {
        service = new AdminTrainingCalendarService(core, statsService, goalService, attachments, activityLogService, userRepository);

        adminId = UUID.randomUUID();
        admin = new User("coach@example.com", "Trener", "Główny", "+48111111111", "coach");
        setField(admin, "id", adminId);
        athleteId = UUID.randomUUID();
        athlete = new User("athlete@example.com", "Anna", "Wspinaczka", "+48123456789", "anna");
        athlete.setAthlete(true);
        // Athlete-side endpoints sit behind the GDPR art. 9 consent gate (V76)
        athlete.grantTrainingConsent();
        setField(athlete, "id", athleteId);

        lenient().when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        lenient().when(userRepository.findById(athleteId)).thenReturn(Optional.of(athlete));
        lenient().when(userRepository.getReferenceById(athleteId)).thenReturn(athlete);
    }

    @Test
    void shouldLogActivityWhenCoachCreatesTraining() {
        // Given
        CreatePersonalTrainingRequest request = new CreatePersonalTrainingRequest(
            LocalDate.of(2026, 7, 20), LocalTime.of(18, 0), LocalTime.of(19, 30), "Trening siłowy", null);
        PersonalTrainingDto dto = dtoFor(request);
        when(core.createForAthlete(athleteId, request)).thenReturn(dto);

        // When
        service.createForAthlete(adminId, athleteId, request);

        // Then
        // The athlete is the entry's subject, and nothing that names them goes into the text —
        // that copy would outlive the erasure of their account
        verify(activityLogService).logAdminTrainingCreated(eq(admin), eq(athlete),
            eq("2026-07-20 Trening siłowy"));
    }

    @Test
    void shouldLogActivityWithTrainingContextWhenCoachDeletes() {
        // Given
        UUID trainingId = UUID.randomUUID();
        PersonalTraining training = new PersonalTraining(
            athlete, LocalDate.of(2026, 7, 20), LocalTime.of(18, 0), LocalTime.of(19, 30),
            "Kampus", null, true);
        when(core.requireTraining(trainingId)).thenReturn(training);

        // When
        service.delete(adminId, trainingId);

        // Then
        verify(core).deleteAsAdmin(trainingId);
        verify(activityLogService).logAdminTrainingDeleted(eq(admin), eq(athlete), contains("Kampus"));
    }

    @Test
    void shouldNotLogActivityWhenCoachComments() {
        // Given
        UUID trainingId = UUID.randomUUID();
        when(core.addCommentAsAdmin(adminId, trainingId, "Dobra robota"))
            .thenReturn(new TrainingCommentDto(UUID.randomUUID(), "Dobra robota", true,
                "Trener Główny", null, Instant.now(), null, true, java.util.List.of()));

        // When
        service.addComment(adminId, trainingId, "Dobra robota");

        // Then
        verifyNoInteractions(activityLogService);
    }

    @Test
    void shouldLogActivityWhenCoachCreatesGoal() {
        // Given
        SaveGoalRequest request = new SaveGoalRequest(
            null, pl.nextsteppro.climbing.domain.athletegoal.GoalHorizon.SHORT, "Przejść 7a",
            LocalDate.of(2026, 9, 15), null);
        AthleteGoalDto dto = new AthleteGoalDto(
            UUID.randomUUID(), "GENERAL", "SHORT", "Przejść 7a", LocalDate.of(2026, 9, 15),
            null, null, false, null, null, Instant.now());
        when(goalService.createGoal(athleteId, request)).thenReturn(dto);

        // When
        service.createGoal(adminId, athleteId, request);

        // Then
        verify(activityLogService).logAdminGoalCreated(eq(admin), eq(athlete), contains("Przejść 7a"));
    }

    @Test
    void shouldLogActivityWhenCoachAchievesGoal() {
        // Given
        UUID goalId = UUID.randomUUID();
        var goal = new pl.nextsteppro.climbing.domain.athletegoal.AthleteGoal(
            athlete, pl.nextsteppro.climbing.domain.athletegoal.GoalHorizon.LONG, "7c przed 30-tką",
            LocalDate.of(2027, 3, 1));
        AthleteGoalDto dto = new AthleteGoalDto(
            goalId, "GENERAL", "LONG", "7c przed 30-tką", LocalDate.of(2027, 3, 1),
            null, null, false, Instant.now(), Instant.now(), Instant.now());
        when(goalService.requireGoal(goalId)).thenReturn(goal);
        when(goalService.achieveGoal(eq(goalId), any())).thenReturn(dto);

        // When
        service.achieveGoal(adminId, goalId, new AchieveGoalRequest(null));

        // Then
        verify(activityLogService).logAdminGoalAchieved(eq(admin), eq(athlete), contains("7c przed 30-tką"));
    }

    @Test
    void shouldLogActivityWithGoalContextWhenCoachDeletesGoal() {
        // Given
        UUID goalId = UUID.randomUUID();
        var goal = new pl.nextsteppro.climbing.domain.athletegoal.AthleteGoal(
            athlete, pl.nextsteppro.climbing.domain.athletegoal.GoalHorizon.MEDIUM, "Zawody PP",
            LocalDate.of(2026, 12, 20));
        when(goalService.requireGoal(goalId)).thenReturn(goal);

        // When
        service.deleteGoal(adminId, goalId);

        // Then
        verify(goalService).deleteGoal(goalId);
        verify(activityLogService).logAdminGoalDeleted(eq(admin), eq(athlete), contains("Zawody PP"));
    }

    private PersonalTrainingDto dtoFor(CreatePersonalTrainingRequest request) {
        return new PersonalTrainingDto(
            UUID.randomUUID(), TrainingKind.TRAINING, request.date(), request.startTime(), request.endTime(),
            request.title(), request.description(), null, true, "PLANNED",
            null, null, null, false, Instant.now(), java.util.List.of(), 0L);
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
