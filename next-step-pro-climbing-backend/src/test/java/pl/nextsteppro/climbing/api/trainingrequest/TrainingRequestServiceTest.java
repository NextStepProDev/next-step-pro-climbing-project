package pl.nextsteppro.climbing.api.trainingrequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.nextsteppro.climbing.domain.course.CourseRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequest;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequestRepository;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequestStatus;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.mail.MailService;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TrainingRequestService.
 * Verifies: create (validation, window bounds, pending limit), cancel (ownership, status guard).
 */
@ExtendWith(MockitoExtension.class)
class TrainingRequestServiceTest {

    @Mock private TrainingRequestRepository trainingRequestRepository;
    @Mock private TimeSlotRepository timeSlotRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private UserRepository userRepository;
    @Mock private MailService mailService;
    @Mock private MessageService msg;

    private TrainingRequestService trainingRequestService;

    @BeforeEach
    void setUp() {
        trainingRequestService = new TrainingRequestService(
            trainingRequestRepository, timeSlotRepository, courseRepository,
            userRepository, mailService, msg);

        lenient().when(msg.get(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(msg.get(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ========== create ==========

    @Test
    void shouldCreateRequestAndNotifyAdminWhenValid() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(trainingRequestRepository.countByUserIdAndStatus(userId, TrainingRequestStatus.PENDING)).thenReturn(0);
        when(trainingRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateTrainingRequestRequest request = new CreateTrainingRequestRequest(
            LocalDate.now().plusDays(7), LocalTime.of(10, 0), LocalTime.of(12, 0),
            2, "Poproszę o trening na wytrzymałość", null, null);

        // When
        TrainingRequestResultDto result = trainingRequestService.create(userId, request);

        // Then
        assertNotNull(result);
        ArgumentCaptor<TrainingRequest> captor = ArgumentCaptor.forClass(TrainingRequest.class);
        verify(trainingRequestRepository).save(captor.capture());
        TrainingRequest saved = captor.getValue();
        assertEquals(TrainingRequestStatus.PENDING, saved.getStatus());
        assertEquals(2, saved.getParticipants());
        verify(mailService).sendTrainingRequestAdminNotification(
            eq(user), eq(request.requestedDate()), eq(request.startTime()), eq(request.endTime()),
            eq(2), any(), isNull(), eq(false));
    }

    @Test
    void shouldRejectRequestWhenDateInPast() {
        // Given
        UUID userId = UUID.randomUUID();
        CreateTrainingRequestRequest request = new CreateTrainingRequestRequest(
            LocalDate.now().minusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0),
            1, null, null, null);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> trainingRequestService.create(userId, request));
        verify(trainingRequestRepository, never()).save(any());
    }

    @Test
    void shouldRejectRequestWhenEndNotAfterStart() {
        UUID userId = UUID.randomUUID();
        CreateTrainingRequestRequest request = new CreateTrainingRequestRequest(
            LocalDate.now().plusDays(7), LocalTime.of(12, 0), LocalTime.of(10, 0),
            1, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> trainingRequestService.create(userId, request));
        verify(trainingRequestRepository, never()).save(any());
    }

    @Test
    void shouldRejectRequestWhenPendingLimitReached() {
        // Given
        UUID userId = UUID.randomUUID();
        when(trainingRequestRepository.countByUserIdAndStatus(userId, TrainingRequestStatus.PENDING))
            .thenReturn(TrainingRequestService.MAX_PENDING_PER_USER);

        CreateTrainingRequestRequest request = new CreateTrainingRequestRequest(
            LocalDate.now().plusDays(7), LocalTime.of(10, 0), LocalTime.of(12, 0),
            1, null, null, null);

        // When / Then
        assertThrows(IllegalStateException.class, () -> trainingRequestService.create(userId, request));
        verify(trainingRequestRepository, never()).save(any());
    }

    @Test
    void shouldLinkWindowWhenProposalWithinAvailabilityWindow() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID windowId = UUID.randomUUID();
        User user = buildUser(userId);
        LocalDate date = LocalDate.now().plusDays(7);
        TimeSlot window = buildWindow(windowId, date, LocalTime.of(9, 0), LocalTime.of(15, 0));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(trainingRequestRepository.countByUserIdAndStatus(userId, TrainingRequestStatus.PENDING)).thenReturn(0);
        when(timeSlotRepository.findById(windowId)).thenReturn(Optional.of(window));
        when(trainingRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateTrainingRequestRequest request = new CreateTrainingRequestRequest(
            date, LocalTime.of(10, 0), LocalTime.of(12, 0), 1, null, null, windowId);

        // When
        trainingRequestService.create(userId, request);

        // Then
        ArgumentCaptor<TrainingRequest> captor = ArgumentCaptor.forClass(TrainingRequest.class);
        verify(trainingRequestRepository).save(captor.capture());
        assertSame(window, captor.getValue().getWindowSlot());
        verify(mailService).sendTrainingRequestAdminNotification(
            eq(user), any(), any(), any(), anyInt(), any(), isNull(), eq(true));
    }

    @Test
    void shouldRejectProposalOutsideWindowHours() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID windowId = UUID.randomUUID();
        LocalDate date = LocalDate.now().plusDays(7);
        TimeSlot window = buildWindow(windowId, date, LocalTime.of(9, 0), LocalTime.of(11, 0));

        when(trainingRequestRepository.countByUserIdAndStatus(userId, TrainingRequestStatus.PENDING)).thenReturn(0);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(timeSlotRepository.findById(windowId)).thenReturn(Optional.of(window));

        CreateTrainingRequestRequest request = new CreateTrainingRequestRequest(
            date, LocalTime.of(10, 0), LocalTime.of(12, 0), 1, null, null, windowId);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> trainingRequestService.create(userId, request));
        verify(trainingRequestRepository, never()).save(any());
    }

    @Test
    void shouldRejectProposalWhenSlotIsNotAvailabilityWindow() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        LocalDate date = LocalDate.now().plusDays(7);
        TimeSlot regularSlot = buildWindow(slotId, date, LocalTime.of(9, 0), LocalTime.of(15, 0));
        regularSlot.setAvailabilityWindow(false);

        when(trainingRequestRepository.countByUserIdAndStatus(userId, TrainingRequestStatus.PENDING)).thenReturn(0);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(regularSlot));

        CreateTrainingRequestRequest request = new CreateTrainingRequestRequest(
            date, LocalTime.of(10, 0), LocalTime.of(12, 0), 1, null, null, slotId);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> trainingRequestService.create(userId, request));
    }

    @Test
    void shouldPreserveDiacriticsAndEscapeHtmlInComment() {
        // Given: polskie znaki muszą przetrwać (UTF-8 escape), a HTML ma być unieszkodliwiony
        String comment = "Poproszę trening siłowy <script>alert(1)</script>";

        // When
        String sanitized = TrainingRequest.sanitizeComment(comment);

        // Then
        assertNotNull(sanitized);
        assertTrue(sanitized.contains("Poproszę trening siłowy"));
        assertFalse(sanitized.contains("<script>"));
        assertTrue(sanitized.contains("&lt;script&gt;"));
    }

    // ========== cancel ==========

    @Test
    void shouldCancelOwnPendingRequest() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        TrainingRequest tr = buildRequest(requestId, userId);
        when(trainingRequestRepository.findById(requestId)).thenReturn(Optional.of(tr));

        // When
        trainingRequestService.cancel(requestId, userId);

        // Then
        verify(trainingRequestRepository).delete(tr);
    }

    @Test
    void shouldRejectCancelWhenNotOwner() {
        // Given
        UUID requestId = UUID.randomUUID();
        TrainingRequest tr = buildRequest(requestId, UUID.randomUUID());
        when(trainingRequestRepository.findById(requestId)).thenReturn(Optional.of(tr));

        // When / Then
        assertThrows(IllegalStateException.class, () -> trainingRequestService.cancel(requestId, UUID.randomUUID()));
        verify(trainingRequestRepository, never()).delete(any(TrainingRequest.class));
    }

    @Test
    void shouldRejectCancelWhenAlreadyResolved() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        TrainingRequest tr = buildRequest(requestId, userId);
        tr.resolve(TrainingRequestStatus.ACCEPTED);
        when(trainingRequestRepository.findById(requestId)).thenReturn(Optional.of(tr));

        // When / Then
        assertThrows(IllegalStateException.class, () -> trainingRequestService.cancel(requestId, userId));
        verify(trainingRequestRepository, never()).delete(any(TrainingRequest.class));
    }

    // ========== helpers ==========

    private User buildUser(UUID userId) {
        User user = new User("Jan", "Kowalski", "jan@test.pl", "+48123456789", "hashedpw");
        setField(user, "id", userId);
        return user;
    }

    private TimeSlot buildWindow(UUID slotId, LocalDate date, LocalTime start, LocalTime end) {
        TimeSlot slot = new TimeSlot(date, start, end, 1);
        slot.setAvailabilityWindow(true);
        setField(slot, "id", slotId);
        return slot;
    }

    private TrainingRequest buildRequest(UUID requestId, UUID ownerId) {
        TrainingRequest tr = new TrainingRequest(
            buildUser(ownerId), LocalDate.now().plusDays(7), LocalTime.of(10, 0), LocalTime.of(12, 0), 1);
        setField(tr, "id", requestId);
        return tr;
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
