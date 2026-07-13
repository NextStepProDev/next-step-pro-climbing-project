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
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeatRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationStatus;
import pl.nextsteppro.climbing.domain.reservation.SlotParticipantCount;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlist;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlistRepository;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistStatus;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.mail.WaitlistMailService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for EventWaitlistService - handles event waitlist join, leave,
 * offer confirmation, notification, and expiration logic.
 *
 * Test coverage:
 * - Join event waitlist (happy path and validation)
 * - Leave event waitlist
 * - Confirm event offer (race condition handling)
 * - Notify all waiting users
 * - Expire pending confirmations
 * - Get user's event waitlist
 * - Edge cases: inactive event, past event, already registered, already waiting, slot has spots
 */
@ExtendWith(MockitoExtension.class)
class EventWaitlistServiceTest {

    @Mock
    private EventWaitlistRepository eventWaitlistRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private TimeSlotRepository timeSlotRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservedSeatRepository reservedSeatRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WaitlistMailService waitlistMailService;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private MessageService msg;

    private EventWaitlistService eventWaitlistService;
    private User testUser;
    private Event testEvent;
    private UUID userId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        eventWaitlistService = new EventWaitlistService(
            eventWaitlistRepository,
            eventRepository,
            timeSlotRepository,
            reservationRepository,
            reservedSeatRepository,
            userRepository,
            waitlistMailService,
            activityLogService,
            msg
        );

        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        testUser = new User("test@example.com", "John", "Doe", "+48123456789", "johndoe");
        setFieldViaReflection(testUser, User.class, "id", userId);
        setFieldViaReflection(testUser, User.class, "createdAt", Instant.now());
        setFieldViaReflection(testUser, User.class, "updatedAt", Instant.now());

        testEvent = new Event("Climbing Course", EventType.COURSE,
            LocalDate.now().plusDays(7), LocalDate.now().plusDays(8), 10);
        testEvent.setStartTime(LocalTime.of(10, 0));
        testEvent.setEndTime(LocalTime.of(18, 0));
        setFieldViaReflection(testEvent, Event.class, "id", eventId);
        setFieldViaReflection(testEvent, Event.class, "createdAt", Instant.now());
        setFieldViaReflection(testEvent, Event.class, "updatedAt", Instant.now());
    }

    // ========== JOIN EVENT WAITLIST TESTS ==========

    @Test
    void shouldJoinEventWaitlistSuccessfully() {
        // Given
        TimeSlot slot = createSlot(testEvent);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(testEvent));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of(slot));
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slot.getId(), ReservationStatus.CONFIRMED))
            .thenReturn(false);
        when(eventWaitlistRepository.existsByUserAndEventAndStatuses(eq(userId), eq(eventId), anyList()))
            .thenReturn(false);
        when(reservationRepository.countConfirmedByTimeSlotIds(anyList()))
            .thenReturn(List.of(new SlotParticipantCount(slot.getId(), 10L)));
        when(eventWaitlistRepository.countPendingConfirmationByEventId(eventId)).thenReturn(0);
        when(eventWaitlistRepository.findMaxPositionForEvent(eventId)).thenReturn(0);
        when(msg.get("waitlist.joined")).thenReturn("Successfully joined waitlist");

        // When
        WaitlistResultDto result = eventWaitlistService.joinEventWaitlist(eventId, userId);

        // Then
        assertTrue(result.success());
        assertEquals("Successfully joined waitlist", result.message());
        verify(eventWaitlistRepository).save(any(EventWaitlist.class));
        verify(waitlistMailService).sendEventWaitlistJoinedConfirmation(testUser, testEvent);
    }

    @Test
    void shouldAssignCorrectPositionWhenJoiningWaitlist() {
        // Given
        TimeSlot slot = createSlot(testEvent);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(testEvent));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of(slot));
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slot.getId(), ReservationStatus.CONFIRMED))
            .thenReturn(false);
        when(eventWaitlistRepository.existsByUserAndEventAndStatuses(eq(userId), eq(eventId), anyList()))
            .thenReturn(false);
        when(reservationRepository.countConfirmedByTimeSlotIds(anyList()))
            .thenReturn(List.of(new SlotParticipantCount(slot.getId(), 10L)));
        when(eventWaitlistRepository.countPendingConfirmationByEventId(eventId)).thenReturn(0);
        when(eventWaitlistRepository.findMaxPositionForEvent(eventId)).thenReturn(3);
        when(msg.get("waitlist.joined")).thenReturn("Joined");

        // When
        eventWaitlistService.joinEventWaitlist(eventId, userId);

        // Then
        ArgumentCaptor<EventWaitlist> captor = ArgumentCaptor.forClass(EventWaitlist.class);
        verify(eventWaitlistRepository).save(captor.capture());
        assertEquals(4, captor.getValue().getPosition());
    }

    @Test
    void shouldThrowExceptionWhenEventNotFound() {
        // Given
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());
        when(msg.get("reservation.event.not.found")).thenReturn("Event not found");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> eventWaitlistService.joinEventWaitlist(eventId, userId)
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
            () -> eventWaitlistService.joinEventWaitlist(eventId, userId)
        );
        assertEquals("Event is inactive", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenEventIsInThePast() {
        // Given
        Event pastEvent = new Event("Past Course", EventType.COURSE,
            LocalDate.now().minusDays(2), LocalDate.now().minusDays(1), 10);
        pastEvent.setStartTime(LocalTime.of(10, 0));
        setFieldViaReflection(pastEvent, Event.class, "id", eventId);
        setFieldViaReflection(pastEvent, Event.class, "createdAt", Instant.now());
        setFieldViaReflection(pastEvent, Event.class, "updatedAt", Instant.now());

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(pastEvent));
        when(msg.get("reservation.slot.past")).thenReturn("Event is in the past");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> eventWaitlistService.joinEventWaitlist(eventId, userId)
        );
        assertEquals("Event is in the past", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenAlreadyRegistered() {
        // Given
        TimeSlot slot = createSlot(testEvent);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(testEvent));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of(slot));
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slot.getId(), ReservationStatus.CONFIRMED))
            .thenReturn(true);
        when(msg.get("waitlist.already.reserved")).thenReturn("Already registered");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> eventWaitlistService.joinEventWaitlist(eventId, userId)
        );
        assertEquals("Already registered", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenAlreadyOnWaitlist() {
        // Given
        TimeSlot slot = createSlot(testEvent);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(testEvent));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of(slot));
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slot.getId(), ReservationStatus.CONFIRMED))
            .thenReturn(false);
        when(eventWaitlistRepository.existsByUserAndEventAndStatuses(eq(userId), eq(eventId), anyList()))
            .thenReturn(true);
        when(msg.get("waitlist.already.waiting")).thenReturn("Already on waitlist");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> eventWaitlistService.joinEventWaitlist(eventId, userId)
        );
        assertEquals("Already on waitlist", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenEventHasAvailableSpots() {
        // Given
        TimeSlot slot = createSlot(testEvent);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(testEvent));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of(slot));
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slot.getId(), ReservationStatus.CONFIRMED))
            .thenReturn(false);
        when(eventWaitlistRepository.existsByUserAndEventAndStatuses(eq(userId), eq(eventId), anyList()))
            .thenReturn(false);
        when(reservationRepository.countConfirmedByTimeSlotIds(anyList()))
            .thenReturn(List.of(new SlotParticipantCount(slot.getId(), 5L))); // Only 5 out of 10
        when(eventWaitlistRepository.countPendingConfirmationByEventId(eventId)).thenReturn(0);
        when(msg.get("waitlist.slot.has.spots")).thenReturn("Event has available spots");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> eventWaitlistService.joinEventWaitlist(eventId, userId)
        );
        assertEquals("Event has available spots", exception.getMessage());
    }

    @Test
    void shouldJoinWaitlistWhenEventFullOnlyByReservedSeats() {
        // Given — 8/10 confirmed + 2 seats invitation-held for others = effectively full.
        // An uninvited viewer has no way to book normally → the queue must be available.
        TimeSlot slot = createSlot(testEvent);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(testEvent));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of(slot));
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slot.getId(), ReservationStatus.CONFIRMED))
            .thenReturn(false);
        when(eventWaitlistRepository.existsByUserAndEventAndStatuses(eq(userId), eq(eventId), anyList()))
            .thenReturn(false);
        when(reservationRepository.countConfirmedByTimeSlotIds(anyList()))
            .thenReturn(List.of(new SlotParticipantCount(slot.getId(), 8L)));
        when(eventWaitlistRepository.countPendingConfirmationByEventId(eventId)).thenReturn(0);
        when(reservedSeatRepository.countPendingByEventIdExcludingUser(eventId, userId)).thenReturn(2);
        when(eventWaitlistRepository.findMaxPositionForEvent(eventId)).thenReturn(0);
        when(msg.get("waitlist.joined")).thenReturn("Successfully joined waitlist");

        // When
        WaitlistResultDto result = eventWaitlistService.joinEventWaitlist(eventId, userId);

        // Then
        assertTrue(result.success());
        verify(eventWaitlistRepository).save(any(EventWaitlist.class));
    }

    @Test
    void shouldFailConfirmOfferWhenReservedSeatsFillRemainingSpots() {
        // Given — 9/10 confirmed + 1 seat invitation-held for someone else = full.
        // Confirming a queue offer must not go over the limit.
        TimeSlot slot = createSlot(testEvent);
        UUID waitlistId = UUID.randomUUID();
        EventWaitlist entry = new EventWaitlist(testUser, testEvent, 1);
        setFieldViaReflection(entry, EventWaitlist.class, "id", waitlistId);
        entry.offerSpot(Instant.now().plusSeconds(3600)); // → PENDING_CONFIRMATION
        when(eventWaitlistRepository.findById(waitlistId)).thenReturn(Optional.of(entry));
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of(slot));
        when(reservationRepository.countConfirmedByTimeSlotIds(anyList()))
            .thenReturn(List.of(new SlotParticipantCount(slot.getId(), 9L)));
        when(reservedSeatRepository.countPendingByEventIdExcludingUser(eventId, userId)).thenReturn(1);
        when(msg.get("waitlist.race.lost")).thenReturn("Someone was faster");

        // When & Then
        assertThrows(IllegalStateException.class,
            () -> eventWaitlistService.confirmEventOffer(waitlistId, userId));
        assertEquals(WaitlistStatus.WAITING, entry.getStatus());
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    // ========== LEAVE EVENT WAITLIST TESTS ==========

    @Test
    void shouldLeaveEventWaitlistSuccessfully() {
        // Given
        EventWaitlist entry = new EventWaitlist(testUser, testEvent, 1);
        setFieldViaReflection(entry, EventWaitlist.class, "id", UUID.randomUUID());

        when(eventWaitlistRepository.findByUserIdAndEventId(userId, eventId))
            .thenReturn(Optional.of(entry));

        // When
        eventWaitlistService.leaveEventWaitlist(eventId, userId);

        // Then
        verify(eventWaitlistRepository).delete(entry);
        verify(eventWaitlistRepository).flush();
    }

    @Test
    void shouldNotifyNextWhenPendingUserLeaves() {
        // Given
        EventWaitlist pendingEntry = new EventWaitlist(testUser, testEvent, 1);
        pendingEntry.offerSpot(Instant.now().plusSeconds(3600));
        setFieldViaReflection(pendingEntry, EventWaitlist.class, "id", UUID.randomUUID());

        when(eventWaitlistRepository.findByUserIdAndEventId(userId, eventId))
            .thenReturn(Optional.of(pendingEntry));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(testEvent));

        TimeSlot slot = createSlot(testEvent);
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of(slot));
        when(reservationRepository.countConfirmedByTimeSlotIds(anyList()))
            .thenReturn(List.of(new SlotParticipantCount(slot.getId(), 5L)));
        when(eventWaitlistRepository.countPendingConfirmationByEventId(eventId)).thenReturn(0);
        when(eventWaitlistRepository.findWaitingByEventIdOrdered(eventId)).thenReturn(List.of());

        // When
        eventWaitlistService.leaveEventWaitlist(eventId, userId);

        // Then
        verify(eventWaitlistRepository).delete(pendingEntry);
    }

    @Test
    void shouldThrowExceptionWhenLeavingNonExistentWaitlist() {
        // Given
        when(eventWaitlistRepository.findByUserIdAndEventId(userId, eventId))
            .thenReturn(Optional.empty());
        when(msg.get("waitlist.not.found")).thenReturn("Waitlist entry not found");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> eventWaitlistService.leaveEventWaitlist(eventId, userId)
        );
        assertEquals("Waitlist entry not found", exception.getMessage());
    }

    // ========== CONFIRM EVENT OFFER TESTS ==========

    @Test
    void shouldConfirmEventOfferSuccessfully() {
        // Given
        UUID waitlistId = UUID.randomUUID();
        EventWaitlist entry = new EventWaitlist(testUser, testEvent, 1);
        entry.offerSpot(Instant.now().plusSeconds(3600));
        setFieldViaReflection(entry, EventWaitlist.class, "id", waitlistId);

        TimeSlot slot = createSlot(testEvent);

        when(eventWaitlistRepository.findById(waitlistId)).thenReturn(Optional.of(entry));
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of(slot));
        when(reservationRepository.countConfirmedByTimeSlotIds(anyList()))
            .thenReturn(List.of(new SlotParticipantCount(slot.getId(), 9L))); // 9/10 spots taken
        when(reservationRepository.findByUserIdAndTimeSlotId(userId, slot.getId())).thenReturn(null);
        when(eventWaitlistRepository.findByEventIdAndStatusWithUser(eventId, WaitlistStatus.PENDING_CONFIRMATION))
            .thenReturn(List.of());
        when(msg.get("reservation.event.confirmed")).thenReturn("Event reservation confirmed");

        // When
        EventReservationResultDto result = eventWaitlistService.confirmEventOffer(waitlistId, userId);

        // Then
        assertTrue(result.success());
        assertEquals(eventId, result.eventId());
        assertEquals("Event reservation confirmed", result.message());
        assertEquals(1, result.slotsReserved());

        verify(reservationRepository).save(any(Reservation.class));
        verify(eventWaitlistRepository).delete(entry);
        verify(waitlistMailService).sendEventWaitlistReservationConfirmed(testUser, testEvent);
        verify(waitlistMailService).sendEventWaitlistAdminNotification(testUser, testEvent);
        verify(activityLogService).logEventReservationCreated(testUser, testEvent, 1);
    }

    @Test
    void shouldThrowExceptionWhenConfirmingOtherUsersOffer() {
        // Given
        UUID waitlistId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        EventWaitlist entry = new EventWaitlist(testUser, testEvent, 1);
        entry.offerSpot(Instant.now().plusSeconds(3600));
        setFieldViaReflection(entry, EventWaitlist.class, "id", waitlistId);

        when(eventWaitlistRepository.findById(waitlistId)).thenReturn(Optional.of(entry));

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> eventWaitlistService.confirmEventOffer(waitlistId, otherUserId)
        );
        assertEquals("You can only confirm your own waitlist offer", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenOfferIsNotPending() {
        // Given
        UUID waitlistId = UUID.randomUUID();
        EventWaitlist entry = new EventWaitlist(testUser, testEvent, 1);
        // Not calling offerSpot(), so status is WAITING
        setFieldViaReflection(entry, EventWaitlist.class, "id", waitlistId);

        when(eventWaitlistRepository.findById(waitlistId)).thenReturn(Optional.of(entry));
        when(msg.get("waitlist.offer.not.pending")).thenReturn("Offer is not pending");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> eventWaitlistService.confirmEventOffer(waitlistId, userId)
        );
        assertEquals("Offer is not pending", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenOfferDeadlineHasPassed() {
        // Given
        UUID waitlistId = UUID.randomUUID();
        EventWaitlist entry = new EventWaitlist(testUser, testEvent, 1);
        entry.offerSpot(Instant.now().minusSeconds(3600)); // Expired
        setFieldViaReflection(entry, EventWaitlist.class, "id", waitlistId);

        when(eventWaitlistRepository.findById(waitlistId)).thenReturn(Optional.of(entry));
        when(msg.get("waitlist.offer.expired")).thenReturn("Offer has expired");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> eventWaitlistService.confirmEventOffer(waitlistId, userId)
        );
        assertEquals("Offer has expired", exception.getMessage());
    }

    @Test
    void shouldHandleRaceConditionWhenEventIsFull() {
        // Given
        UUID waitlistId = UUID.randomUUID();
        EventWaitlist entry = new EventWaitlist(testUser, testEvent, 1);
        entry.offerSpot(Instant.now().plusSeconds(3600));
        setFieldViaReflection(entry, EventWaitlist.class, "id", waitlistId);

        TimeSlot slot = createSlot(testEvent);

        when(eventWaitlistRepository.findById(waitlistId)).thenReturn(Optional.of(entry));
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of(slot));
        when(reservationRepository.countConfirmedByTimeSlotIds(anyList()))
            .thenReturn(List.of(new SlotParticipantCount(slot.getId(), 10L))); // Full!
        when(msg.get("waitlist.race.lost")).thenReturn("Someone was faster");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> eventWaitlistService.confirmEventOffer(waitlistId, userId)
        );
        assertEquals("Someone was faster", exception.getMessage());

        // Verify entry returned to WAITING
        verify(eventWaitlistRepository).save(entry);
        assertTrue(entry.isWaiting());
    }

    @Test
    void shouldReactivateCancelledReservationOnConfirm() {
        // Given
        UUID waitlistId = UUID.randomUUID();
        EventWaitlist entry = new EventWaitlist(testUser, testEvent, 1);
        entry.offerSpot(Instant.now().plusSeconds(3600));
        setFieldViaReflection(entry, EventWaitlist.class, "id", waitlistId);

        TimeSlot slot = createSlot(testEvent);
        Reservation cancelledReservation = new Reservation(testUser, slot);
        cancelledReservation.cancel();

        when(eventWaitlistRepository.findById(waitlistId)).thenReturn(Optional.of(entry));
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of(slot));
        when(reservationRepository.countConfirmedByTimeSlotIds(anyList()))
            .thenReturn(List.of(new SlotParticipantCount(slot.getId(), 9L)));
        when(reservationRepository.findByUserIdAndTimeSlotId(userId, slot.getId())).thenReturn(cancelledReservation);
        when(eventWaitlistRepository.findByEventIdAndStatusWithUser(eventId, WaitlistStatus.PENDING_CONFIRMATION))
            .thenReturn(List.of());
        when(msg.get("reservation.event.confirmed")).thenReturn("Confirmed");

        // When
        eventWaitlistService.confirmEventOffer(waitlistId, userId);

        // Then
        assertTrue(cancelledReservation.isConfirmed());
        verify(reservationRepository).save(cancelledReservation);
    }

    @Test
    void shouldReturnOtherPendingUsersToWaitingOnConfirm() {
        // Given
        UUID waitlistId = UUID.randomUUID();
        EventWaitlist entry = new EventWaitlist(testUser, testEvent, 1);
        entry.offerSpot(Instant.now().plusSeconds(3600));
        setFieldViaReflection(entry, EventWaitlist.class, "id", waitlistId);

        User otherUser = new User("other@example.com", "Jane", "Smith", "+48987654321", "janesmith");
        setFieldViaReflection(otherUser, User.class, "id", UUID.randomUUID());
        setFieldViaReflection(otherUser, User.class, "createdAt", Instant.now());
        setFieldViaReflection(otherUser, User.class, "updatedAt", Instant.now());

        EventWaitlist otherEntry = new EventWaitlist(otherUser, testEvent, 2);
        otherEntry.offerSpot(Instant.now().plusSeconds(3600));

        TimeSlot slot = createSlot(testEvent);

        when(eventWaitlistRepository.findById(waitlistId)).thenReturn(Optional.of(entry));
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of(slot));
        when(reservationRepository.countConfirmedByTimeSlotIds(anyList()))
            .thenReturn(List.of(new SlotParticipantCount(slot.getId(), 9L)));
        when(reservationRepository.findByUserIdAndTimeSlotId(userId, slot.getId())).thenReturn(null);
        when(eventWaitlistRepository.findByEventIdAndStatusWithUser(eventId, WaitlistStatus.PENDING_CONFIRMATION))
            .thenReturn(List.of(otherEntry));
        when(msg.get("reservation.event.confirmed")).thenReturn("Confirmed");

        // When
        eventWaitlistService.confirmEventOffer(waitlistId, userId);

        // Then
        assertTrue(otherEntry.isWaiting());
        verify(eventWaitlistRepository).saveAll(List.of(otherEntry));
    }

    // ========== NOTIFY ALL TESTS ==========

    @Test
    void shouldNotifyAllWaitingUsersWithCorrectDeadline() {
        // Given
        EventWaitlist entry1 = new EventWaitlist(testUser, testEvent, 1);
        setFieldViaReflection(entry1, EventWaitlist.class, "id", UUID.randomUUID());

        when(eventWaitlistRepository.findWaitingByEventIdOrdered(eventId)).thenReturn(List.of(entry1));

        // When
        eventWaitlistService.notifyAll(eventId);

        // Then
        assertTrue(entry1.isPendingConfirmation());
        assertNotNull(entry1.getConfirmationDeadline());
        verify(eventWaitlistRepository).saveAll(List.of(entry1));
        verify(waitlistMailService).sendEventWaitlistOfferNotification(eq(testUser), eq(testEvent), any(Instant.class));
    }

    @Test
    void shouldDoNothingWhenNoWaitingEntries() {
        // Given
        when(eventWaitlistRepository.findWaitingByEventIdOrdered(eventId)).thenReturn(List.of());

        // When
        eventWaitlistService.notifyAll(eventId);

        // Then
        verify(eventWaitlistRepository, never()).saveAll(anyList());
        verify(waitlistMailService, never()).sendEventWaitlistOfferNotification(any(), any(), any());
    }

    @Test
    void shouldExpireWaitingEntriesWhenEventIsInPast() {
        // Given
        Event pastEvent = new Event("Past Course", EventType.COURSE,
            LocalDate.now().minusDays(2), LocalDate.now().minusDays(1), 10);
        pastEvent.setStartTime(LocalTime.of(10, 0));
        UUID pastEventId = UUID.randomUUID();
        setFieldViaReflection(pastEvent, Event.class, "id", pastEventId);
        setFieldViaReflection(pastEvent, Event.class, "createdAt", Instant.now());
        setFieldViaReflection(pastEvent, Event.class, "updatedAt", Instant.now());

        EventWaitlist entry = new EventWaitlist(testUser, pastEvent, 1);
        setFieldViaReflection(entry, EventWaitlist.class, "id", UUID.randomUUID());

        when(eventWaitlistRepository.findWaitingByEventIdOrdered(pastEventId)).thenReturn(List.of(entry));

        // When
        eventWaitlistService.notifyAll(pastEventId);

        // Then
        assertEquals(WaitlistStatus.EXPIRED, entry.getStatus());
        verify(eventWaitlistRepository).saveAll(List.of(entry));
        verify(waitlistMailService, never()).sendEventWaitlistOfferNotification(any(), any(), any());
    }

    // ========== EXPIRE AND NOTIFY TESTS ==========

    @Test
    void shouldReturnExpiredEntriesToWaiting() {
        // Given
        EventWaitlist expired1 = new EventWaitlist(testUser, testEvent, 1);
        expired1.offerSpot(Instant.now().minusSeconds(3600));
        setFieldViaReflection(expired1, EventWaitlist.class, "id", UUID.randomUUID());

        when(eventWaitlistRepository.findExpiredPendingConfirmations(any(Instant.class)))
            .thenReturn(List.of(expired1));

        // When
        eventWaitlistService.expireAndNotify();

        // Then
        assertTrue(expired1.isWaiting());
        assertNull(expired1.getOfferedAt());
        assertNull(expired1.getConfirmationDeadline());
        verify(eventWaitlistRepository).saveAll(List.of(expired1));
    }

    @Test
    void shouldDoNothingWhenNoExpiredEntries() {
        // Given
        when(eventWaitlistRepository.findExpiredPendingConfirmations(any(Instant.class)))
            .thenReturn(List.of());

        // When
        eventWaitlistService.expireAndNotify();

        // Then
        verify(eventWaitlistRepository, never()).saveAll(anyList());
    }

    // ========== GET USER EVENT WAITLIST TESTS ==========

    @Test
    void shouldGetUserEventWaitlistEntries() {
        // Given
        EventWaitlist entry = new EventWaitlist(testUser, testEvent, 1);
        setFieldViaReflection(entry, EventWaitlist.class, "id", UUID.randomUUID());

        when(eventWaitlistRepository.findActiveByUserId(userId)).thenReturn(List.of(entry));
        when(eventWaitlistRepository.countWaitingAtOrBeforePosition(eventId, 1)).thenReturn(1);

        // When
        List<EventWaitlistEntryDto> result = eventWaitlistService.getUserEventWaitlist(userId);

        // Then
        assertEquals(1, result.size());
        EventWaitlistEntryDto dto = result.getFirst();
        assertEquals(eventId, dto.eventId());
        assertEquals("Climbing Course", dto.eventTitle());
        assertEquals(WaitlistStatus.WAITING, dto.status());
        assertEquals(1, dto.position());
    }

    @Test
    void shouldReturnEmptyListWhenNoWaitlistEntries() {
        // Given
        when(eventWaitlistRepository.findActiveByUserId(userId)).thenReturn(List.of());

        // When
        List<EventWaitlistEntryDto> result = eventWaitlistService.getUserEventWaitlist(userId);

        // Then
        assertTrue(result.isEmpty());
    }

    // ========== HELPER METHODS ==========

    private TimeSlot createSlot(Event event) {
        TimeSlot slot = new TimeSlot(
            event,
            event.getStartDate(),
            LocalTime.of(10, 0),
            LocalTime.of(18, 0),
            event.getMaxParticipants()
        );
        UUID slotId = UUID.randomUUID();
        setFieldViaReflection(slot, TimeSlot.class, "id", slotId);
        setFieldViaReflection(slot, TimeSlot.class, "createdAt", Instant.now());
        setFieldViaReflection(slot, TimeSlot.class, "updatedAt", Instant.now());
        return slot;
    }

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
