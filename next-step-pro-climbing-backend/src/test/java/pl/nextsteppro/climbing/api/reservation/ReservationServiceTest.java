package pl.nextsteppro.climbing.api.reservation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventRepository;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reservation.GuestReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationStatus;
import pl.nextsteppro.climbing.domain.reservation.SlotParticipantCount;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.mail.MailService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for ReservationService - handles slot and event reservations with validation.
 *
 * Test coverage:
 * - Slot reservation with capacity validation
 * - Booking window checks
 * - Multi-user conflict prevention (overbooking)
 * - Cancellation flow with time window validation
 * - Event reservations with multi-slot handling
 * - Reactivation of cancelled reservations
 * - Edge cases: full slots, past slots, blocked slots, duplicate bookings
 *
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private GuestReservationRepository guestReservationRepository;
    @Mock
    private TimeSlotRepository timeSlotRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private MailService mailService;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private MessageService msg;
    @Mock
    private WaitlistService waitlistService;
    @Mock
    private EventWaitlistService eventWaitlistService;

    private ReservationService reservationService;
    private User testUser;
    private TimeSlot testSlot;
    private Event testEvent;
    private UUID userId;
    private UUID slotId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(
            reservationRepository,
            guestReservationRepository,
            timeSlotRepository,
            userRepository,
            eventRepository,
            mailService,
            activityLogService,
            msg,
            waitlistService,
            eventWaitlistService
        );

        userId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        testUser = new User("test@example.com", "John", "Doe", "+48123456789", "johndoe");
        setEntityIdViaReflection(testUser, userId);

        // Create a slot 48 hours in the future (within booking window)
        LocalDate futureDate = LocalDate.now().plusDays(2);
        testSlot = new TimeSlot(futureDate, LocalTime.of(10, 0), LocalTime.of(11, 0), 10);
        setEntityIdViaReflection(testSlot, slotId);

        testEvent = new Event("Test Course", EventType.COURSE,
                              LocalDate.now().plusDays(5), LocalDate.now().plusDays(7), 20);
        testEvent.setDescription("Description");
        setEntityIdViaReflection(testEvent, eventId);
    }

    // ========== SLOT RESERVATION CREATION TESTS ==========

    @Test
    void shouldCreateReservationSuccessfully() {
        // Given
        when(timeSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(testSlot));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slotId, ReservationStatus.CONFIRMED))
            .thenReturn(false);
        when(reservationRepository.countConfirmedByTimeSlotId(slotId)).thenReturn(5);
        when(reservationRepository.findByUserIdAndTimeSlotId(userId, slotId)).thenReturn(null);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            setEntityIdViaReflection(r, UUID.randomUUID());
            return r;
        });
        when(msg.get("reservation.confirmed")).thenReturn("Reservation confirmed");

        // When
        ReservationResultDto result = reservationService.createReservation(slotId, userId, "Test comment", 2);

        // Then
        assertNotNull(result);
        assertTrue(result.success());
        assertEquals("Reservation confirmed", result.message());

        verify(reservationRepository).save(any(Reservation.class));
        verify(mailService).sendReservationConfirmation(any(Reservation.class));
        verify(mailService).sendAdminNotification(any(Reservation.class));
        verify(activityLogService).logReservationCreated(testUser, testSlot, 2);
    }

    @Test
    void shouldThrowExceptionWhenSlotNotFound() {
        // Given
        when(timeSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(
            IllegalArgumentException.class,
            () -> reservationService.createReservation(slotId, userId, null, 1)
        );
    }

    @Test
    void shouldThrowExceptionWhenUserNotFound() {
        // Given
        when(timeSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(testSlot));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(
            IllegalArgumentException.class,
            () -> reservationService.createReservation(slotId, userId, null, 1)
        );
    }

    @Test
    void shouldThrowExceptionWhenSlotIsPast() {
        // Given
        LocalDate pastDate = LocalDate.now().minusDays(1);
        TimeSlot pastSlot = new TimeSlot(pastDate, LocalTime.of(10, 0), LocalTime.of(11, 0), 10);
        setEntityIdViaReflection(pastSlot, slotId);

        when(timeSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(pastSlot));
        when(msg.get("reservation.slot.past")).thenReturn("Cannot book past slot");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> reservationService.createReservation(slotId, userId, null, 1)
        );
        assertEquals("Cannot book past slot", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenBookingWindowTooShort() {
        // Given
        LocalDateTime targetDateTime = LocalDateTime.now().plusHours(6); // Less than 12 hours
        LocalDate targetDate = targetDateTime.toLocalDate();
        LocalTime targetTime = targetDateTime.toLocalTime();
        TimeSlot soonSlot = new TimeSlot(targetDate, targetTime, targetTime.plusHours(1), 10);
        setEntityIdViaReflection(soonSlot, slotId);

        when(timeSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(soonSlot));
        when(msg.get("reservation.booking.window")).thenReturn("Booking window too short");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> reservationService.createReservation(slotId, userId, null, 1)
        );
        assertEquals("Booking window too short", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenSlotIsBlocked() {
        // Given
        testSlot.block("Maintenance");

        when(timeSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(testSlot));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> reservationService.createReservation(slotId, userId, null, 1)
        );
        assertEquals("This time slot is blocked", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenUserAlreadyHasReservation() {
        // Given
        when(timeSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(testSlot));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slotId, ReservationStatus.CONFIRMED))
            .thenReturn(true);
        when(msg.get("reservation.already.exists")).thenReturn("Already reserved");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> reservationService.createReservation(slotId, userId, null, 1)
        );
        assertEquals("Already reserved", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenSlotIsFull() {
        // Given
        when(timeSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(testSlot));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slotId, ReservationStatus.CONFIRMED))
            .thenReturn(false);
        when(reservationRepository.countConfirmedByTimeSlotId(slotId)).thenReturn(10); // Slot max is 10
        when(msg.get("reservation.no.spots")).thenReturn("No spots available");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> reservationService.createReservation(slotId, userId, null, 1)
        );
        assertEquals("No spots available", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenNotEnoughSpotsForParticipants() {
        // Given
        when(timeSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(testSlot));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slotId, ReservationStatus.CONFIRMED))
            .thenReturn(false);
        when(reservationRepository.countConfirmedByTimeSlotId(slotId)).thenReturn(8); // 2 spots left
        when(msg.get(eq("reservation.spots.available"), anyInt(), anyInt()))
            .thenReturn("Only 2 spots available, you requested 3");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> reservationService.createReservation(slotId, userId, null, 3)
        );
        assertEquals("Only 2 spots available, you requested 3", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenParticipantsLessThanOne() {
        // Given
        when(msg.get("reservation.min.participants")).thenReturn("Minimum 1 participant");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> reservationService.createReservation(slotId, userId, null, 0)
        );
        assertEquals("Minimum 1 participant", exception.getMessage());
    }

    @Test
    void shouldReactivateCancelledReservation() {
        // Given
        Reservation existingCancelled = new Reservation(testUser, testSlot);
        existingCancelled.cancel();

        when(timeSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(testSlot));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slotId, ReservationStatus.CONFIRMED))
            .thenReturn(false);
        when(reservationRepository.countConfirmedByTimeSlotId(slotId)).thenReturn(5);
        when(reservationRepository.findByUserIdAndTimeSlotId(userId, slotId)).thenReturn(existingCancelled);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(msg.get("reservation.confirmed")).thenReturn("Reservation confirmed");

        // When
        reservationService.createReservation(slotId, userId, "Reactivate", 2);

        // Then
        assertTrue(existingCancelled.isConfirmed());
        assertEquals(2, existingCancelled.getParticipants());
        assertEquals("Reactivate", existingCancelled.getComment());

        verify(reservationRepository).save(existingCancelled);
        verify(activityLogService).logReservationReactivated(testUser, testSlot, 2);
    }

    @Test
    void shouldSanitizeCommentWhenCreatingReservation() {
        // Given
        String longComment = "A".repeat(600); // Over 500 limit
        when(timeSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(testSlot));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slotId, ReservationStatus.CONFIRMED))
            .thenReturn(false);
        when(reservationRepository.countConfirmedByTimeSlotId(slotId)).thenReturn(5);
        when(reservationRepository.findByUserIdAndTimeSlotId(userId, slotId)).thenReturn(null);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            setEntityIdViaReflection(r, UUID.randomUUID());
            return r;
        });
        when(msg.get("reservation.confirmed")).thenReturn("Reservation confirmed");

        // When
        reservationService.createReservation(slotId, userId, longComment, 1);

        // Then
        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(captor.capture());

        Reservation saved = captor.getValue();
        assertNotNull(saved.getComment());
        assertTrue(saved.getComment().length() <= 500);
    }

    // ========== CANCELLATION TESTS ==========

    @Test
    void shouldCancelReservationSuccessfully() {
        // Given
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = new Reservation(testUser, testSlot);
        setEntityIdViaReflection(reservation, reservationId);

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        // When
        reservationService.cancelReservation(reservationId, userId);

        // Then
        assertTrue(reservation.isCancelled());

        verify(reservationRepository).save(reservation);
        verify(mailService).sendCancellationConfirmation(reservation);
        verify(mailService).sendUserCancellationAdminNotification(reservation);
        verify(activityLogService).logReservationCancelled(testUser, testSlot, reservation.getParticipants());
    }

    @Test
    void shouldThrowExceptionWhenReservationNotFoundForCancellation() {
        // Given
        UUID reservationId = UUID.randomUUID();
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(
            IllegalArgumentException.class,
            () -> reservationService.cancelReservation(reservationId, userId)
        );
    }

    @Test
    void shouldThrowExceptionWhenCancellingOtherUsersReservation() {
        // Given
        UUID reservationId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Reservation reservation = new Reservation(testUser, testSlot);
        setEntityIdViaReflection(reservation, reservationId);

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> reservationService.cancelReservation(reservationId, otherUserId)
        );
        assertEquals("You can only cancel your own reservations", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenReservationAlreadyCancelled() {
        // Given
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = new Reservation(testUser, testSlot);
        setEntityIdViaReflection(reservation, reservationId);
        reservation.cancel();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> reservationService.cancelReservation(reservationId, userId)
        );
        assertEquals("This reservation is already cancelled", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenCancellationWindowTooShort() {
        // Given
        UUID reservationId = UUID.randomUUID();
        LocalDateTime targetDateTime = LocalDateTime.now().plusHours(6); // Less than 12 hours
        LocalDate targetDate = targetDateTime.toLocalDate();
        LocalTime targetTime = targetDateTime.toLocalTime();
        TimeSlot soonSlot = new TimeSlot(targetDate, targetTime, targetTime.plusHours(1), 10);

        Reservation reservation = new Reservation(testUser, soonSlot);
        setEntityIdViaReflection(reservation, reservationId);

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(msg.get("reservation.cancel.window")).thenReturn("Cancellation window too short");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> reservationService.cancelReservation(reservationId, userId)
        );
        assertEquals("Cancellation window too short", exception.getMessage());
    }

    // ========== EVENT RESERVATION TESTS ==========

    @Test
    void shouldCreateEventReservationSuccessfully() {
        // Given
        List<TimeSlot> eventSlots = List.of(
            createEventSlot(eventId, LocalDate.now().plusDays(5)),
            createEventSlot(eventId, LocalDate.now().plusDays(6)),
            createEventSlot(eventId, LocalDate.now().plusDays(7))
        );

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(testEvent));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(eventSlots);
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(eq(userId), any(UUID.class), eq(ReservationStatus.CONFIRMED)))
            .thenReturn(false);
        when(reservationRepository.countConfirmedByTimeSlotIds(anyList()))
            .thenReturn(List.of());
        when(reservationRepository.findByUserIdAndTimeSlotId(eq(userId), any(UUID.class))).thenReturn(null);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            setEntityIdViaReflection(r, UUID.randomUUID());
            return r;
        });
        when(msg.get("reservation.event.confirmed")).thenReturn("Event reservation confirmed");

        // When
        EventReservationResultDto result = reservationService.createEventReservation(eventId, userId, "Event comment", 2);

        // Then
        assertNotNull(result);
        assertTrue(result.success());
        assertEquals(3, result.slotsReserved());

        verify(reservationRepository, times(3)).save(any(Reservation.class));
        verify(mailService).sendEventReservationConfirmation(testUser, testEvent, 2);
        verify(mailService).sendEventAdminNotification(testUser, testEvent, 2);
        verify(activityLogService).logEventReservationCreated(testUser, testEvent, 2);
    }

    @Test
    void shouldThrowExceptionWhenEventNotFound() {
        // Given
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());
        when(msg.get("reservation.event.not.found")).thenReturn("Event not found");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> reservationService.createEventReservation(eventId, userId, null, 1)
        );
        assertEquals("Event not found", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenEventIsInactive() {
        // Given
        testEvent.setActive(false);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(testEvent));
        when(msg.get("reservation.event.inactive")).thenReturn("Event is inactive");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> reservationService.createEventReservation(eventId, userId, null, 1)
        );
        assertEquals("Event is inactive", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenEventBookingWindowTooShort() {
        // Given
        LocalDateTime targetDateTime = LocalDateTime.now().plusHours(6); // Less than 12 hours
        Event soonEvent = new Event("Soon Event", EventType.TRAINING,
                                     targetDateTime.toLocalDate(), LocalDate.now().plusDays(2), 20);
        soonEvent.setDescription("Desc");
        soonEvent.setStartTime(targetDateTime.toLocalTime());

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(soonEvent));
        when(msg.get("reservation.event.booking.window")).thenReturn("Event booking window too short");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> reservationService.createEventReservation(eventId, userId, null, 1)
        );
        assertEquals("Event booking window too short", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenUserAlreadyRegisteredForEvent() {
        // Given
        List<TimeSlot> eventSlots = List.of(createEventSlot(eventId, LocalDate.now().plusDays(5)));

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(testEvent));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(eventSlots);
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(eq(userId), any(UUID.class), eq(ReservationStatus.CONFIRMED)))
            .thenReturn(true);
        when(msg.get("reservation.event.already.registered")).thenReturn("Already registered for event");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> reservationService.createEventReservation(eventId, userId, null, 1)
        );
        assertEquals("Already registered for event", exception.getMessage());
    }

    @Test
    void shouldCreateDefaultSlotsWhenEventHasNoSlots() {
        // Given - Event has no slots, so service will create default slots automatically
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(testEvent));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of());
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(eq(userId), any(UUID.class), eq(ReservationStatus.CONFIRMED)))
            .thenReturn(false);
        when(reservationRepository.countConfirmedByTimeSlotIds(anyList())).thenReturn(List.of());
        when(reservationRepository.findByUserIdAndTimeSlotId(eq(userId), any(UUID.class))).thenReturn(null);
        when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> {
            TimeSlot slot = inv.getArgument(0);
            setEntityIdViaReflection(slot, UUID.randomUUID());
            return slot;
        });
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            setEntityIdViaReflection(r, UUID.randomUUID());
            return r;
        });
        when(msg.get("reservation.event.confirmed")).thenReturn("Event reservation confirmed");

        // When
        EventReservationResultDto result = reservationService.createEventReservation(eventId, userId, null, 1);

        // Then
        assertNotNull(result);
        assertTrue(result.success());
    }

    @Test
    void shouldThrowExceptionWhenEventIsFull() {
        // Given
        List<TimeSlot> eventSlots = List.of(createEventSlot(eventId, LocalDate.now().plusDays(5)));
        UUID slotId = eventSlots.get(0).getId();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(testEvent));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(eventSlots);
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(eq(userId), any(UUID.class), eq(ReservationStatus.CONFIRMED)))
            .thenReturn(false);

        // Create mock projection - only stub methods that are actually called
        SlotParticipantCount mockProjection = mock(SlotParticipantCount.class);
        when(mockProjection.slotId()).thenReturn(slotId);
        when(mockProjection.countAsInt()).thenReturn(20); // Event max is 20

        when(reservationRepository.countConfirmedByTimeSlotIds(anyList()))
            .thenReturn(List.of(mockProjection));
        when(msg.get("reservation.event.no.spots")).thenReturn("Event is full");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> reservationService.createEventReservation(eventId, userId, null, 1)
        );
        assertEquals("Event is full", exception.getMessage());
    }

    // ========== EVENT CANCELLATION TESTS ==========

    @Test
    void shouldCancelEventReservationSuccessfully() {
        // Given
        TimeSlot eventSlot = createEventSlot(eventId, LocalDate.now().plusDays(5));
        Reservation reservation = new Reservation(testUser, eventSlot);

        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of(eventSlot));
        when(reservationRepository.findByUserIdAndTimeSlotId(userId, eventSlot.getId())).thenReturn(reservation);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(testEvent));

        // When
        reservationService.cancelEventReservation(eventId, userId);

        // Then
        assertTrue(reservation.isCancelled());

        verify(reservationRepository).save(reservation);
        verify(mailService).sendEventCancellationConfirmation(testUser, testEvent);
        verify(mailService).sendUserEventCancellationAdminNotification(testUser, testEvent);
        verify(activityLogService).logEventReservationCancelled(testUser, testEvent);
    }

    @Test
    void shouldThrowExceptionWhenNoSlotsForEventCancellation() {
        // Given
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of());
        when(msg.get("reservation.event.no.slots")).thenReturn("No slots for event");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> reservationService.cancelEventReservation(eventId, userId)
        );
        assertEquals("No slots for event", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenEventCancellationWindowTooShort() {
        // Given
        LocalDateTime targetDateTime = LocalDateTime.now().plusHours(6); // Less than 12 hours
        LocalDate targetDate = targetDateTime.toLocalDate();
        LocalTime targetTime = targetDateTime.toLocalTime();
        TimeSlot soonEventSlot = new TimeSlot(testEvent, targetDate, targetTime, targetTime.plusHours(1), 20);

        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of(soonEventSlot));
        when(msg.get("reservation.cancel.window")).thenReturn("Cancellation window too short");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> reservationService.cancelEventReservation(eventId, userId)
        );
        assertEquals("Cancellation window too short", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenNoReservationsFoundForEventCancellation() {
        // Given
        TimeSlot eventSlot = createEventSlot(eventId, LocalDate.now().plusDays(5));

        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of(eventSlot));
        when(reservationRepository.findByUserIdAndTimeSlotId(userId, eventSlot.getId())).thenReturn(null);
        when(msg.get("reservation.event.not.found.cancel")).thenReturn("No reservations to cancel");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> reservationService.cancelEventReservation(eventId, userId)
        );
        assertEquals("No reservations to cancel", exception.getMessage());
    }

    // ========== USER RESERVATIONS RETRIEVAL TESTS ==========

    @Test
    void shouldGetUserReservations() {
        // Given
        Reservation reservation = new Reservation(testUser, testSlot);
        setEntityIdViaReflection(reservation, UUID.randomUUID());

        when(reservationRepository.findByUserId(userId)).thenReturn(List.of(reservation));

        // When
        List<UserReservationDto> result = reservationService.getUserReservations(userId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void shouldGetUpcomingReservationsSeparatedByStandaloneAndEvent() {
        // Given
        Reservation standaloneReservation = new Reservation(testUser, testSlot);
        setEntityIdViaReflection(standaloneReservation, UUID.randomUUID());

        TimeSlot eventSlot = createEventSlot(eventId, LocalDate.now().plusDays(5));
        Reservation eventReservation = new Reservation(testUser, eventSlot);
        setEntityIdViaReflection(eventReservation, UUID.randomUUID());

        when(reservationRepository.findUpcomingByUserIdIncludingAdminCancelled(eq(userId), any(LocalDate.class), any(LocalTime.class)))
            .thenReturn(List.of(standaloneReservation, eventReservation));

        // When
        MyReservationsDto result = reservationService.getUserUpcomingReservations(userId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.slots().size());
        assertEquals(1, result.events().size());
    }

    @Test
    void shouldGetPastReservationsSeparatedByStandaloneAndEvent() {
        // Given
        LocalDate pastDate = LocalDate.now().minusDays(5);
        TimeSlot pastSlot = new TimeSlot(pastDate, LocalTime.of(10, 0), LocalTime.of(11, 0), 10);
        Reservation pastReservation = new Reservation(testUser, pastSlot);
        setEntityIdViaReflection(pastReservation, UUID.randomUUID());

        when(reservationRepository.findPastByUserId(eq(userId), any(LocalDate.class), any(LocalTime.class)))
            .thenReturn(List.of(pastReservation));

        // When
        MyReservationsDto result = reservationService.getUserPastReservations(userId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.slots().size());
    }

    // ========== HELPER METHODS ==========

    private TimeSlot createEventSlot(UUID eventId, LocalDate date) {
        TimeSlot slot = new TimeSlot(testEvent, date, LocalTime.of(10, 0), LocalTime.of(12, 0), 20);
        setEntityIdViaReflection(slot, UUID.randomUUID());
        return slot;
    }

    private void setEntityIdViaReflection(Object entity, UUID id) {
        try {
            var idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);

            var createdAtField = entity.getClass().getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(entity, Instant.now());

            var updatedAtField = entity.getClass().getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(entity, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException("Failed to set entity ID", e);
        }
    }
}
