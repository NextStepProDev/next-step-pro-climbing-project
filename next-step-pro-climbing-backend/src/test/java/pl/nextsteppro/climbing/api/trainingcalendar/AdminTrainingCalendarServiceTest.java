package pl.nextsteppro.climbing.api.trainingcalendar;

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
    @Mock private ActivityLogService activityLogService;
    @Mock private UserRepository userRepository;

    private AdminTrainingCalendarService service;

    private UUID adminId;
    private User admin;
    private UUID athleteId;
    private User athlete;

    @BeforeEach
    void setUp() {
        service = new AdminTrainingCalendarService(core, statsService, activityLogService, userRepository);

        adminId = UUID.randomUUID();
        admin = new User("coach@example.com", "Trener", "Główny", "+48111111111", "coach");
        setField(admin, "id", adminId);
        athleteId = UUID.randomUUID();
        athlete = new User("athlete@example.com", "Anna", "Wspinaczka", "+48123456789", "anna");
        athlete.setAthlete(true);
        setField(athlete, "id", athleteId);

        lenient().when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        lenient().when(userRepository.findById(athleteId)).thenReturn(Optional.of(athlete));
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
        verify(activityLogService).logAdminTrainingCreated(eq(admin), contains("Anna Wspinaczka"));
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
        verify(activityLogService).logAdminTrainingDeleted(eq(admin), contains("Kampus"));
    }

    @Test
    void shouldNotLogActivityWhenCoachComments() {
        // Given
        UUID trainingId = UUID.randomUUID();
        when(core.addCommentAsAdmin(adminId, trainingId, "Dobra robota"))
            .thenReturn(new TrainingCommentDto(UUID.randomUUID(), "Dobra robota", true,
                "Trener Główny", null, Instant.now(), true));

        // When
        service.addComment(adminId, trainingId, "Dobra robota");

        // Then
        verifyNoInteractions(activityLogService);
    }

    private PersonalTrainingDto dtoFor(CreatePersonalTrainingRequest request) {
        return new PersonalTrainingDto(
            UUID.randomUUID(), request.date(), request.startTime(), request.endTime(),
            request.title(), request.description(), true, "PLANNED",
            null, null, null, false, Instant.now());
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
