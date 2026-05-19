package pl.nextsteppro.climbing.api.activitylog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import pl.nextsteppro.climbing.domain.activitylog.ActivityActionType;
import pl.nextsteppro.climbing.domain.activitylog.ActivityLog;
import pl.nextsteppro.climbing.domain.activitylog.ActivityLogRepository;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for ActivityLogService - handles audit logging for all
 * reservation and admin actions in the system.
 *
 * Test coverage:
 * - Reservation log entries (created, cancelled, reactivated, updated)
 * - Event reservation log entries
 * - Admin action log entries (slot CRUD, event CRUD, user management)
 * - Log retrieval with pagination
 * - DTO mapping with slot and event details
 * - Edge cases: null slot, null event, null participants, null description
 */
@ExtendWith(MockitoExtension.class)
class ActivityLogServiceTest {

    @Mock
    private ActivityLogRepository activityLogRepository;

    private ActivityLogService activityLogService;
    private User testUser;
    private User adminUser;
    private TimeSlot testSlot;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        activityLogService = new ActivityLogService(activityLogRepository);

        testUser = new User("user@example.com", "John", "Doe", "+48123456789", "johndoe");
        setFieldViaReflection(testUser, User.class, "id", UUID.randomUUID());
        setFieldViaReflection(testUser, User.class, "createdAt", Instant.now());
        setFieldViaReflection(testUser, User.class, "updatedAt", Instant.now());

        adminUser = new User("admin@example.com", "Admin", "User", "+48987654321", "adminuser");
        setFieldViaReflection(adminUser, User.class, "id", UUID.randomUUID());
        setFieldViaReflection(adminUser, User.class, "createdAt", Instant.now());
        setFieldViaReflection(adminUser, User.class, "updatedAt", Instant.now());

        testSlot = new TimeSlot(
            LocalDate.of(2026, 6, 15),
            LocalTime.of(10, 0),
            LocalTime.of(12, 0),
            8
        );
        testSlot.setTitle("Climbing Session");
        setFieldViaReflection(testSlot, TimeSlot.class, "id", UUID.randomUUID());
        setFieldViaReflection(testSlot, TimeSlot.class, "createdAt", Instant.now());
        setFieldViaReflection(testSlot, TimeSlot.class, "updatedAt", Instant.now());

        testEvent = new Event("Summer Course", EventType.COURSE,
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 5), 20);
        setFieldViaReflection(testEvent, Event.class, "id", UUID.randomUUID());
        setFieldViaReflection(testEvent, Event.class, "createdAt", Instant.now());
        setFieldViaReflection(testEvent, Event.class, "updatedAt", Instant.now());
    }

    // ========== RESERVATION LOGGING TESTS ==========

    @Test
    void shouldLogReservationCreated() {
        // When
        activityLogService.logReservationCreated(testUser, testSlot, 2);

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        ActivityLog log = captor.getValue();
        assertEquals(testUser, log.getUser());
        assertEquals(ActivityActionType.RESERVATION_CREATED, log.getActionType());
        assertEquals(testSlot, log.getTimeSlot());
        assertNull(log.getEvent());
        assertEquals(2, log.getParticipants());
        assertNull(log.getDescription());
    }

    @Test
    void shouldLogReservationReactivated() {
        // When
        activityLogService.logReservationReactivated(testUser, testSlot, 1);

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        ActivityLog log = captor.getValue();
        assertEquals(ActivityActionType.RESERVATION_REACTIVATED, log.getActionType());
        assertEquals(testSlot, log.getTimeSlot());
        assertEquals(1, log.getParticipants());
    }

    @Test
    void shouldLogReservationCancelled() {
        // When
        activityLogService.logReservationCancelled(testUser, testSlot, 3);

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        ActivityLog log = captor.getValue();
        assertEquals(ActivityActionType.RESERVATION_CANCELLED, log.getActionType());
        assertEquals(3, log.getParticipants());
    }

    @Test
    void shouldLogReservationUpdated() {
        // When
        activityLogService.logReservationUpdated(testUser, testSlot, 4);

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        assertEquals(ActivityActionType.RESERVATION_UPDATED, captor.getValue().getActionType());
        assertEquals(4, captor.getValue().getParticipants());
    }

    // ========== EVENT RESERVATION LOGGING TESTS ==========

    @Test
    void shouldLogEventReservationCreated() {
        // When
        activityLogService.logEventReservationCreated(testUser, testEvent, 1);

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        ActivityLog log = captor.getValue();
        assertEquals(ActivityActionType.EVENT_RESERVATION_CREATED, log.getActionType());
        assertNull(log.getTimeSlot());
        assertEquals(testEvent, log.getEvent());
        assertEquals(1, log.getParticipants());
    }

    @Test
    void shouldLogEventReservationCancelled() {
        // When
        activityLogService.logEventReservationCancelled(testUser, testEvent);

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        ActivityLog log = captor.getValue();
        assertEquals(ActivityActionType.EVENT_RESERVATION_CANCELLED, log.getActionType());
        assertEquals(testEvent, log.getEvent());
        assertNull(log.getParticipants());
    }

    @Test
    void shouldLogEventReservationUpdated() {
        // When
        activityLogService.logEventReservationUpdated(testUser, testEvent, 5);

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        assertEquals(ActivityActionType.EVENT_RESERVATION_UPDATED, captor.getValue().getActionType());
        assertEquals(5, captor.getValue().getParticipants());
    }

    // ========== ADMIN CANCELLATION LOGGING TESTS ==========

    @Test
    void shouldLogCancelledByAdmin() {
        // When
        activityLogService.logCancelledByAdmin(testUser, testSlot, 2);

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        ActivityLog log = captor.getValue();
        assertEquals(ActivityActionType.RESERVATION_CANCELLED_BY_ADMIN, log.getActionType());
        assertEquals(testUser, log.getUser());
        assertEquals(testSlot, log.getTimeSlot());
        assertEquals(2, log.getParticipants());
    }

    // ========== ADMIN SLOT LOGGING TESTS ==========

    @Test
    void shouldLogAdminSlotCreated() {
        // When
        activityLogService.logAdminSlotCreated(adminUser, testSlot);

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        ActivityLog log = captor.getValue();
        assertEquals(adminUser, log.getUser());
        assertEquals(ActivityActionType.ADMIN_SLOT_CREATED, log.getActionType());
        assertEquals(testSlot, log.getTimeSlot());
        assertNull(log.getParticipants());
    }

    @Test
    void shouldLogAdminSlotUpdated() {
        // When
        activityLogService.logAdminSlotUpdated(adminUser, testSlot);

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        assertEquals(ActivityActionType.ADMIN_SLOT_UPDATED, captor.getValue().getActionType());
    }

    @Test
    void shouldLogAdminSlotDeleted() {
        // When
        activityLogService.logAdminSlotDeleted(adminUser, "2026-06-15 10:00-12:00 (Climbing)");

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        ActivityLog log = captor.getValue();
        assertEquals(ActivityActionType.ADMIN_SLOT_DELETED, log.getActionType());
        assertNull(log.getTimeSlot());
        assertEquals("2026-06-15 10:00-12:00 (Climbing)", log.getDescription());
    }

    @Test
    void shouldLogAdminSlotBlockedWithReason() {
        // When
        activityLogService.logAdminSlotBlocked(adminUser, testSlot, "Maintenance");

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        ActivityLog log = captor.getValue();
        assertEquals(ActivityActionType.ADMIN_SLOT_BLOCKED, log.getActionType());
        assertEquals(testSlot, log.getTimeSlot());
        assertEquals("Maintenance", log.getDescription());
    }

    @Test
    void shouldLogAdminSlotBlockedWithoutReason() {
        // When
        activityLogService.logAdminSlotBlocked(adminUser, testSlot, null);

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        ActivityLog log = captor.getValue();
        assertEquals(ActivityActionType.ADMIN_SLOT_BLOCKED, log.getActionType());
        assertNull(log.getDescription());
    }

    @Test
    void shouldLogAdminSlotUnblocked() {
        // When
        activityLogService.logAdminSlotUnblocked(adminUser, testSlot);

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        assertEquals(ActivityActionType.ADMIN_SLOT_UNBLOCKED, captor.getValue().getActionType());
    }

    // ========== ADMIN EVENT LOGGING TESTS ==========

    @Test
    void shouldLogAdminEventCreated() {
        // When
        activityLogService.logAdminEventCreated(adminUser, testEvent);

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        ActivityLog log = captor.getValue();
        assertEquals(ActivityActionType.ADMIN_EVENT_CREATED, log.getActionType());
        assertEquals(testEvent, log.getEvent());
        assertNull(log.getTimeSlot());
    }

    @Test
    void shouldLogAdminEventUpdated() {
        // When
        activityLogService.logAdminEventUpdated(adminUser, testEvent);

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        assertEquals(ActivityActionType.ADMIN_EVENT_UPDATED, captor.getValue().getActionType());
    }

    @Test
    void shouldLogAdminEventDeleted() {
        // When
        activityLogService.logAdminEventDeleted(adminUser, "Summer Course (2026-07-01 - 2026-07-05)");

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        ActivityLog log = captor.getValue();
        assertEquals(ActivityActionType.ADMIN_EVENT_DELETED, log.getActionType());
        assertNull(log.getEvent());
        assertEquals("Summer Course (2026-07-01 - 2026-07-05)", log.getDescription());
    }

    // ========== ADMIN USER MANAGEMENT LOGGING TESTS ==========

    @Test
    void shouldLogAdminUserMakeAdmin() {
        // When
        activityLogService.logAdminUserMakeAdmin(adminUser, "John Doe (user@example.com)");

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        ActivityLog log = captor.getValue();
        assertEquals(ActivityActionType.ADMIN_USER_MAKE_ADMIN, log.getActionType());
        assertEquals("John Doe (user@example.com)", log.getDescription());
    }

    @Test
    void shouldLogAdminUserAdminRemoved() {
        // When
        activityLogService.logAdminUserAdminRemoved(adminUser, "John Doe (user@example.com)");

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        assertEquals(ActivityActionType.ADMIN_USER_ADMIN_REMOVED, captor.getValue().getActionType());
    }

    @Test
    void shouldLogAdminUserDeleted() {
        // When
        activityLogService.logAdminUserDeleted(adminUser, "John Doe (user@example.com)");

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        assertEquals(ActivityActionType.ADMIN_USER_DELETED, captor.getValue().getActionType());
    }

    // ========== GET RECENT LOGS TESTS ==========

    @Test
    void shouldGetRecentLogsWithSlotDetails() {
        // Given
        ActivityLog log = new ActivityLog(testUser, ActivityActionType.RESERVATION_CREATED);
        log.setTimeSlot(testSlot);
        log.setParticipants(2);
        setFieldViaReflection(log, ActivityLog.class, "id", UUID.randomUUID());

        when(activityLogRepository.findRecentWithDetails(PageRequest.of(0, 20)))
            .thenReturn(List.of(log));

        // When
        List<ActivityLogDto> result = activityLogService.getRecentLogs(0, 20);

        // Then
        assertEquals(1, result.size());
        ActivityLogDto dto = result.getFirst();
        assertEquals("John Doe", dto.userFullName());
        assertEquals("user@example.com", dto.userEmail());
        assertEquals("RESERVATION_CREATED", dto.actionType());
        assertEquals(LocalDate.of(2026, 6, 15), dto.slotDate());
        assertEquals(LocalTime.of(10, 0), dto.slotStartTime());
        assertEquals(LocalTime.of(12, 0), dto.slotEndTime());
        assertEquals("Climbing Session", dto.slotTitle());
        assertNull(dto.eventTitle());
        assertEquals(2, dto.participants());
        assertNull(dto.description());
    }

    @Test
    void shouldGetRecentLogsWithEventDetails() {
        // Given
        ActivityLog log = new ActivityLog(testUser, ActivityActionType.EVENT_RESERVATION_CREATED);
        log.setEvent(testEvent);
        log.setParticipants(1);
        setFieldViaReflection(log, ActivityLog.class, "id", UUID.randomUUID());

        when(activityLogRepository.findRecentWithDetails(PageRequest.of(0, 20)))
            .thenReturn(List.of(log));

        // When
        List<ActivityLogDto> result = activityLogService.getRecentLogs(0, 20);

        // Then
        assertEquals(1, result.size());
        ActivityLogDto dto = result.getFirst();
        assertNull(dto.slotDate());
        assertNull(dto.slotStartTime());
        assertNull(dto.slotEndTime());
        assertNull(dto.slotTitle());
        assertEquals("Summer Course", dto.eventTitle());
        assertEquals(LocalDate.of(2026, 7, 1), dto.eventStartDate());
        assertEquals(LocalDate.of(2026, 7, 5), dto.eventEndDate());
    }

    @Test
    void shouldGetRecentLogsWithDescriptionOnly() {
        // Given
        ActivityLog log = new ActivityLog(adminUser, ActivityActionType.ADMIN_SLOT_DELETED);
        log.setDescription("2026-06-15 10:00-12:00");
        setFieldViaReflection(log, ActivityLog.class, "id", UUID.randomUUID());

        when(activityLogRepository.findRecentWithDetails(PageRequest.of(0, 10)))
            .thenReturn(List.of(log));

        // When
        List<ActivityLogDto> result = activityLogService.getRecentLogs(0, 10);

        // Then
        assertEquals(1, result.size());
        ActivityLogDto dto = result.getFirst();
        assertNull(dto.slotDate());
        assertNull(dto.eventTitle());
        assertNull(dto.participants());
        assertEquals("2026-06-15 10:00-12:00", dto.description());
    }

    @Test
    void shouldReturnEmptyListWhenNoLogs() {
        // Given
        when(activityLogRepository.findRecentWithDetails(PageRequest.of(0, 20)))
            .thenReturn(List.of());

        // When
        List<ActivityLogDto> result = activityLogService.getRecentLogs(0, 20);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldPassCorrectPaginationToRepository() {
        // Given
        when(activityLogRepository.findRecentWithDetails(PageRequest.of(2, 15)))
            .thenReturn(List.of());

        // When
        activityLogService.getRecentLogs(2, 15);

        // Then
        verify(activityLogRepository).findRecentWithDetails(PageRequest.of(2, 15));
    }

    // ========== VERIFY EACH ACTION TYPE SAVES CORRECTLY ==========

    @Test
    void shouldSaveLogWithAllFieldsPopulated() {
        // Given — a reservation with all fields
        // When
        activityLogService.logReservationCreated(testUser, testSlot, 3);

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        ActivityLog log = captor.getValue();
        assertNotNull(log.getUser());
        assertNotNull(log.getActionType());
        assertNotNull(log.getTimeSlot());
        assertNull(log.getEvent());
        assertNotNull(log.getParticipants());
        assertNull(log.getDescription());
        assertNotNull(log.getCreatedAt());
    }

    @Test
    void shouldSaveLogWithMinimalFields() {
        // Given — an admin slot unblock with no participants, no description
        // When
        activityLogService.logAdminSlotUnblocked(adminUser, testSlot);

        // Then
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());

        ActivityLog log = captor.getValue();
        assertNotNull(log.getUser());
        assertEquals(ActivityActionType.ADMIN_SLOT_UNBLOCKED, log.getActionType());
        assertNotNull(log.getTimeSlot());
        assertNull(log.getEvent());
        assertNull(log.getParticipants());
        assertNull(log.getDescription());
    }

    // ========== HELPER METHODS ==========

    private void setFieldViaReflection(Object target, Class<?> clazz, String fieldName, Object value) {
        try {
            var field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
