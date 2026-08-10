package pl.nextsteppro.climbing.api.reservation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.domain.reservation.GuestReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationStatus;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeatRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.domain.waitlist.Waitlist;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistRepository;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistStatus;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.mail.WaitlistMailService;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WaitlistService.
 * Verifies: join, leave, confirmOffer (bypasses booking window), offerToNext, expiry.
 */
@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock private WaitlistRepository waitlistRepository;
    @Mock private TimeSlotRepository timeSlotRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private GuestReservationRepository guestReservationRepository;
    @Mock private ReservedSeatRepository reservedSeatRepository;
    @Mock private UserRepository userRepository;
    @Mock private WaitlistMailService waitlistMailService;
    @Mock private ActivityLogService activityLogService;
    @Mock private MessageService msg;

    private WaitlistService waitlistService;

    @BeforeEach
    void setUp() {
        waitlistService = new WaitlistService(
            waitlistRepository, timeSlotRepository, reservationRepository,
            guestReservationRepository, reservedSeatRepository, userRepository, waitlistMailService, activityLogService, msg);

        // Default message returns
        lenient().when(msg.get(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(msg.get(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ========== joinWaitlist ==========

    @Test
    void joinWaitlist_shouldJoinQueueWhenSlotIsFull() {
        // Given
        UUID slotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TimeSlot slot = buildFutureSlot(slotId);
        User user = buildUser(userId);

        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slotId, ReservationStatus.CONFIRMED)).thenReturn(false);
        when(waitlistRepository.existsByUserAndSlotAndStatuses(eq(userId), eq(slotId), any())).thenReturn(false);
        when(reservationRepository.countConfirmedByTimeSlotId(slotId)).thenReturn(5); // maxParticipants = 5
        when(waitlistRepository.countPendingConfirmationBySlotId(slotId)).thenReturn(0);
        when(waitlistRepository.findMaxPositionForSlot(slotId)).thenReturn(2);
        when(waitlistRepository.save(any(Waitlist.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(msg.get("waitlist.joined")).thenReturn("Joined waitlist");

        // When
        WaitlistResultDto result = waitlistService.joinWaitlist(slotId, userId);

        // Then
        assertTrue(result.success());
        ArgumentCaptor<Waitlist> captor = ArgumentCaptor.forClass(Waitlist.class);
        verify(waitlistRepository).save(captor.capture());
        assertEquals(3, captor.getValue().getPosition());
        assertEquals(WaitlistStatus.WAITING, captor.getValue().getStatus());
    }

    @Test
    void joinWaitlist_shouldFailWhenUserAlreadyHasReservation() {
        // Given
        UUID slotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TimeSlot slot = buildFutureSlot(slotId);

        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slotId, ReservationStatus.CONFIRMED)).thenReturn(true);
        lenient().when(msg.get("waitlist.already.reserved")).thenReturn("Already reserved");

        // When / Then
        assertThrows(IllegalStateException.class, () -> waitlistService.joinWaitlist(slotId, userId));
        verify(waitlistRepository, never()).save(any());
    }

    @Test
    void joinWaitlist_shouldFailWhenUserAlreadyOnWaitlist() {
        // Given
        UUID slotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TimeSlot slot = buildFutureSlot(slotId);

        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slotId, ReservationStatus.CONFIRMED)).thenReturn(false);
        when(waitlistRepository.existsByUserAndSlotAndStatuses(eq(userId), eq(slotId), any())).thenReturn(true);
        lenient().when(msg.get("waitlist.already.waiting")).thenReturn("Already waiting");

        // When / Then
        assertThrows(IllegalStateException.class, () -> waitlistService.joinWaitlist(slotId, userId));
        verify(waitlistRepository, never()).save(any());
    }

    @Test
    void joinWaitlist_shouldFailWhenSlotHasSpotsAvailable() {
        // Given
        UUID slotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TimeSlot slot = buildFutureSlot(slotId); // maxParticipants = 5

        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slotId, ReservationStatus.CONFIRMED)).thenReturn(false);
        when(waitlistRepository.existsByUserAndSlotAndStatuses(eq(userId), eq(slotId), any())).thenReturn(false);
        when(reservationRepository.countConfirmedByTimeSlotId(slotId)).thenReturn(3); // 3/5 — seats available
        when(waitlistRepository.countPendingConfirmationBySlotId(slotId)).thenReturn(0);
        lenient().when(msg.get("waitlist.slot.has.spots")).thenReturn("Slot has spots");

        // When / Then
        assertThrows(IllegalStateException.class, () -> waitlistService.joinWaitlist(slotId, userId));
        verify(waitlistRepository, never()).save(any());
    }

    @Test
    void joinWaitlist_shouldJoinQueueWhenFullOnlyByReservedSeats() {
        // Given — 3/5 confirmed, but the 2 remaining seats are invitation-held for other people.
        // For this (uninvited) viewer the slot is effectively full → the queue should be available.
        UUID slotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TimeSlot slot = buildFutureSlot(slotId); // maxParticipants = 5
        User user = buildUser(userId);

        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slotId, ReservationStatus.CONFIRMED)).thenReturn(false);
        when(waitlistRepository.existsByUserAndSlotAndStatuses(eq(userId), eq(slotId), any())).thenReturn(false);
        when(reservationRepository.countConfirmedByTimeSlotId(slotId)).thenReturn(3);
        when(waitlistRepository.countPendingConfirmationBySlotId(slotId)).thenReturn(0);
        when(reservedSeatRepository.countPendingBySlotIdExcludingUser(slotId, userId)).thenReturn(2);
        when(waitlistRepository.findMaxPositionForSlot(slotId)).thenReturn(0);
        when(waitlistRepository.save(any(Waitlist.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        WaitlistResultDto result = waitlistService.joinWaitlist(slotId, userId);

        // Then
        assertTrue(result.success());
        verify(waitlistRepository).save(any(Waitlist.class));
    }

    @Test
    void joinWaitlist_shouldFailWhenReservedSeatsLeaveARealSpot() {
        // Given — 3/5 confirmed, 1 invitation-held → still 1 genuinely free seat.
        // Joining the queue must be rejected while a normal booking is still possible.
        UUID slotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TimeSlot slot = buildFutureSlot(slotId); // maxParticipants = 5

        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slotId, ReservationStatus.CONFIRMED)).thenReturn(false);
        when(waitlistRepository.existsByUserAndSlotAndStatuses(eq(userId), eq(slotId), any())).thenReturn(false);
        when(reservationRepository.countConfirmedByTimeSlotId(slotId)).thenReturn(3);
        when(waitlistRepository.countPendingConfirmationBySlotId(slotId)).thenReturn(0);
        when(reservedSeatRepository.countPendingBySlotIdExcludingUser(slotId, userId)).thenReturn(1);
        lenient().when(msg.get("waitlist.slot.has.spots")).thenReturn("Slot has spots");

        // When / Then
        assertThrows(IllegalStateException.class, () -> waitlistService.joinWaitlist(slotId, userId));
        verify(waitlistRepository, never()).save(any());
    }

    @Test
    void joinWaitlist_shouldFailWhenSlotIsPast() {
        // Given
        UUID slotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TimeSlot slot = buildPastSlot(slotId);

        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        lenient().when(msg.get("reservation.slot.past")).thenReturn("Slot is past");

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> waitlistService.joinWaitlist(slotId, userId));
    }

    // ========== leaveWaitlist ==========

    @Test
    void leaveWaitlist_shouldRemoveEntrySuccessfully() {
        // Given
        UUID slotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Waitlist entry = buildWaitlistEntry(userId, buildFutureSlot(slotId), WaitlistStatus.WAITING);

        when(waitlistRepository.findByUserIdAndSlotId(userId, slotId)).thenReturn(Optional.of(entry));

        // When
        waitlistService.leaveWaitlist(slotId, userId);

        // Then
        verify(waitlistRepository).delete(entry);
        // WAITING — nie triggeruje offerToNext
        verify(waitlistRepository, never()).findWaitingBySlotIdOrdered(any());
    }

    @Test
    void leaveWaitlist_shouldTriggerOfferToNextWhenLeavingPendingConfirmation() {
        // Given
        UUID slotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TimeSlot slot = buildFutureSlot(slotId);
        Waitlist entry = buildWaitlistEntry(userId, slot, WaitlistStatus.PENDING_CONFIRMATION);

        when(waitlistRepository.findByUserIdAndSlotId(userId, slotId)).thenReturn(Optional.of(entry));
        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(reservationRepository.countConfirmedByTimeSlotId(slotId)).thenReturn(0);
        when(waitlistRepository.countPendingConfirmationBySlotId(slotId)).thenReturn(0);
        when(waitlistRepository.findWaitingBySlotIdOrdered(slotId)).thenReturn(List.of());

        // When
        waitlistService.leaveWaitlist(slotId, userId);

        // Then
        verify(waitlistRepository).delete(entry);
        verify(waitlistRepository).findWaitingBySlotIdOrdered(slotId);
    }

    @Test
    void leaveWaitlist_shouldThrowWhenEntryNotFound() {
        // Given
        UUID slotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(waitlistRepository.findByUserIdAndSlotId(userId, slotId)).thenReturn(Optional.empty());
        lenient().when(msg.get("waitlist.not.found")).thenReturn("Not found");

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> waitlistService.leaveWaitlist(slotId, userId));
    }

    // ========== confirmOffer ==========

    @Test
    void confirmOffer_shouldCreateReservationAndDeleteWaitlistEntry() {
        // Given
        UUID waitlistId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);
        TimeSlot slot = buildFutureSlot(UUID.randomUUID());
        Waitlist entry = buildPendingConfirmationEntry(waitlistId, user, slot, Instant.now().plusSeconds(3600));

        when(waitlistRepository.findById(waitlistId)).thenReturn(Optional.of(entry));
        when(timeSlotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));
        when(reservationRepository.countConfirmedByTimeSlotId(slot.getId())).thenReturn(0);
        when(reservationRepository.findByUserIdAndTimeSlotId(userId, slot.getId())).thenReturn(null);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(waitlistRepository.findBySlotIdAndStatusWithUser(eq(slot.getId()), eq(WaitlistStatus.PENDING_CONFIRMATION))).thenReturn(List.of());
        lenient().when(msg.get("reservation.confirmed")).thenReturn("Confirmed");

        // When
        ReservationResultDto result = waitlistService.confirmOffer(waitlistId, userId);

        // Then
        assertTrue(result.success());
        verify(reservationRepository).save(any(Reservation.class));
        verify(waitlistRepository).delete(entry);
        verify(waitlistMailService).sendWaitlistReservationConfirmed(user, slot);
        verify(waitlistMailService).sendWaitlistAdminNotification(user, slot);
    }

    @Test
    void confirmOffer_shouldFailWhenReservedSeatsFillRemainingSpots() {
        // Given — 4/5 confirmed + 1 seat invitation-held for someone else = full.
        // Confirming a queue offer must not go over the limit (protects the other person's invitation).
        UUID waitlistId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);
        TimeSlot slot = buildFutureSlot(UUID.randomUUID()); // maxParticipants = 5
        Waitlist entry = buildPendingConfirmationEntry(waitlistId, user, slot, Instant.now().plusSeconds(3600));

        when(waitlistRepository.findById(waitlistId)).thenReturn(Optional.of(entry));
        when(timeSlotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));
        when(reservationRepository.countConfirmedByTimeSlotId(slot.getId())).thenReturn(4);
        when(reservedSeatRepository.countPendingBySlotIdExcludingUser(slot.getId(), userId)).thenReturn(1);
        lenient().when(msg.get("waitlist.race.lost")).thenReturn("Race lost");

        // When / Then
        assertThrows(IllegalStateException.class, () -> waitlistService.confirmOffer(waitlistId, userId));
        assertEquals(WaitlistStatus.WAITING, entry.getStatus()); // back in the queue
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void confirmOffer_shouldRejectWhenSlotWasBlockedAfterTheOfferWentOut() {
        // Given — blockTimeSlot cancels the confirmed reservations but leaves PENDING offers alive,
        // so the seats it frees made the capacity check pass and handed this user a reservation on
        // a term that had just been called off.
        UUID waitlistId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);
        TimeSlot slot = buildFutureSlot(UUID.randomUUID());
        slot.block("instructor ill");
        Waitlist entry = buildPendingConfirmationEntry(waitlistId, user, slot, Instant.now().plusSeconds(3600));

        when(waitlistRepository.findById(waitlistId)).thenReturn(Optional.of(entry));
        when(timeSlotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));

        // When / Then
        assertThrows(IllegalStateException.class, () -> waitlistService.confirmOffer(waitlistId, userId));
        verify(reservationRepository, never()).save(any(Reservation.class));
        verify(waitlistRepository, never()).delete(any(Waitlist.class));
    }

    @Test
    void confirmOffer_shouldRejectWhenSlotWasMarkedUnavailableAfterTheOfferWentOut() {
        // Given — an absence is not a bookable slot, however many seats the arithmetic says are free.
        UUID waitlistId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);
        TimeSlot slot = buildFutureSlot(UUID.randomUUID());
        slot.setUnavailable(true);
        Waitlist entry = buildPendingConfirmationEntry(waitlistId, user, slot, Instant.now().plusSeconds(3600));

        when(waitlistRepository.findById(waitlistId)).thenReturn(Optional.of(entry));
        when(timeSlotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));
        lenient().when(msg.get("reservation.slot.unavailable")).thenReturn("Slot unavailable");

        // When / Then
        assertThrows(IllegalStateException.class, () -> waitlistService.confirmOffer(waitlistId, userId));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void confirmOffer_shouldBypassBookingWindowAndConfirmEvenIfClose() {
        // Given — slot starts in 2h (normally blocked by 12h booking window, but waitlist bypasses it)
        UUID waitlistId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);
        TimeSlot slot = buildSlotStartingIn(UUID.randomUUID(), 2);
        Waitlist entry = buildPendingConfirmationEntry(waitlistId, user, slot, Instant.now().plusSeconds(3600));

        when(waitlistRepository.findById(waitlistId)).thenReturn(Optional.of(entry));
        when(timeSlotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));
        when(reservationRepository.countConfirmedByTimeSlotId(slot.getId())).thenReturn(0);
        when(reservationRepository.findByUserIdAndTimeSlotId(userId, slot.getId())).thenReturn(null);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(waitlistRepository.findBySlotIdAndStatusWithUser(eq(slot.getId()), eq(WaitlistStatus.PENDING_CONFIRMATION))).thenReturn(List.of());
        lenient().when(msg.get("reservation.confirmed")).thenReturn("Confirmed");

        // When — should NOT throw despite slot being within 12h window
        ReservationResultDto result = waitlistService.confirmOffer(waitlistId, userId);

        // Then
        assertTrue(result.success());
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void confirmOffer_shouldFailWhenDeadlinePassed() {
        // Given
        UUID waitlistId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);
        TimeSlot slot = buildFutureSlot(UUID.randomUUID());
        // deadline in the past
        Waitlist entry = buildPendingConfirmationEntry(waitlistId, user, slot, Instant.now().minusSeconds(60));

        when(waitlistRepository.findById(waitlistId)).thenReturn(Optional.of(entry));
        lenient().when(msg.get("waitlist.offer.expired")).thenReturn("Expired");

        // When / Then
        assertThrows(IllegalStateException.class, () -> waitlistService.confirmOffer(waitlistId, userId));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void confirmOffer_shouldFailWhenEntryBelongsToAnotherUser() {
        // Given
        UUID waitlistId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();
        User anotherUser = buildUser(anotherUserId);
        TimeSlot slot = buildFutureSlot(UUID.randomUUID());
        Waitlist entry = buildPendingConfirmationEntry(waitlistId, anotherUser, slot, Instant.now().plusSeconds(3600));

        when(waitlistRepository.findById(waitlistId)).thenReturn(Optional.of(entry));

        // When / Then — userId != anotherUserId
        assertThrows(IllegalStateException.class, () -> waitlistService.confirmOffer(waitlistId, userId));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void confirmOffer_shouldFailWhenStatusIsNotPendingConfirmation() {
        // Given
        UUID waitlistId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);
        TimeSlot slot = buildFutureSlot(UUID.randomUUID());
        Waitlist entry = buildWaitlistEntry(userId, slot, WaitlistStatus.WAITING);
        setField(entry, "id", waitlistId);
        setField(entry, "user", user);

        when(waitlistRepository.findById(waitlistId)).thenReturn(Optional.of(entry));
        lenient().when(msg.get("waitlist.offer.not.pending")).thenReturn("Not pending");

        // When / Then
        assertThrows(IllegalStateException.class, () -> waitlistService.confirmOffer(waitlistId, userId));
    }

    // ========== notifyAll ==========

    @Test
    void notifyAll_shouldOfferSpotToAllWaitingPeopleSimultaneously() {
        // Given — race model: everyone notified at once
        UUID slotId = UUID.randomUUID();
        User user1 = buildUser(UUID.randomUUID());
        User user2 = buildUser(UUID.randomUUID());
        TimeSlot slot = buildFutureSlot(slotId);
        Waitlist entry1 = buildWaitlistEntry(user1.getId(), slot, WaitlistStatus.WAITING);
        Waitlist entry2 = buildWaitlistEntry(user2.getId(), slot, WaitlistStatus.WAITING);
        setField(entry1, "user", user1);
        setField(entry2, "user", user2);

        when(waitlistRepository.findWaitingBySlotIdOrdered(slotId)).thenReturn(List.of(entry1, entry2));
        when(waitlistRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        // When
        waitlistService.notifyAll(slotId);

        // Then — both entries set to PENDING_CONFIRMATION
        assertTrue(entry1.isPendingConfirmation());
        assertTrue(entry2.isPendingConfirmation());
        assertNotNull(entry1.getConfirmationDeadline());
        verify(waitlistMailService).sendWaitlistOfferNotification(eq(user1), eq(slot), any(Instant.class));
        verify(waitlistMailService).sendWaitlistOfferNotification(eq(user2), eq(slot), any(Instant.class));
    }

    @Test
    void notifyAll_shouldDoNothingWhenQueueIsEmpty() {
        // Given
        UUID slotId = UUID.randomUUID();
        when(waitlistRepository.findWaitingBySlotIdOrdered(slotId)).thenReturn(List.of());

        // When
        waitlistService.notifyAll(slotId);

        // Then
        verify(waitlistRepository, never()).saveAll(anyList());
        verify(waitlistMailService, never()).sendWaitlistOfferNotification(any(), any(), any());
    }

    // ========== expireAndNotify ==========

    @Test
    void expireAndNotify_shouldReturnExpiredEntriesToWaitingWithoutSendingMail() {
        // Given — nobody confirmed in 24h window; entries return to WAITING, no new mail
        UUID slotId = UUID.randomUUID();
        User user = buildUser(UUID.randomUUID());
        TimeSlot slot = buildFutureSlot(slotId);
        Waitlist expiredEntry = buildPendingConfirmationEntry(UUID.randomUUID(), user, slot, Instant.now().minusSeconds(60));

        when(waitlistRepository.findExpiredPendingConfirmations(any(Instant.class))).thenReturn(List.of(expiredEntry));
        when(waitlistRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        // When
        waitlistService.expireAndNotify();

        // Then — returned to WAITING, no spam mail
        assertEquals(WaitlistStatus.WAITING, expiredEntry.getStatus());
        verify(waitlistRepository).saveAll(anyList());
        verify(waitlistMailService, never()).sendWaitlistOfferNotification(any(), any(), any());
    }

    @Test
    void expireAndNotify_shouldDoNothingWhenNoExpiredEntries() {
        // Given
        when(waitlistRepository.findExpiredPendingConfirmations(any(Instant.class))).thenReturn(List.of());

        // When
        waitlistService.expireAndNotify();

        // Then
        verify(waitlistRepository, never()).saveAll(anyList());
    }

    // ========== Helpers ==========

    private TimeSlot buildFutureSlot(UUID slotId) {
        TimeSlot slot = new TimeSlot(null, LocalDate.now().plusDays(7), LocalTime.of(10, 0), LocalTime.of(11, 0), 5);
        setField(slot, "id", slotId);
        return slot;
    }

    private TimeSlot buildPastSlot(UUID slotId) {
        TimeSlot slot = new TimeSlot(null, LocalDate.now().minusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0), 5);
        setField(slot, "id", slotId);
        return slot;
    }

    /**
     * A slot that starts the given number of hours from now, on the same clock the service reads.
     *
     * <p>Two traps, both of which used to fire between 00:00 and 02:00 Warsaw — one CI run in ten:
     * the runner's JVM is UTC while {@code BookingTimeValidator} asks Europe/Warsaw, and a bare
     * {@code LocalTime.plusHours} wraps past midnight without moving the date, which turned
     * "starts in 2h" into "ended 22h ago" and failed the confirm-offer test on a past slot.
     */
    private TimeSlot buildSlotStartingIn(UUID slotId, int hoursFromNow) {
        LocalDateTime start = LocalDateTime.now(ZoneId.of("Europe/Warsaw"))
            .plusHours(hoursFromNow)
            .truncatedTo(ChronoUnit.MINUTES);
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = startTime.plusHours(1);
        // A slot lives inside a single day (date + start + end), so an end that wrapped would read
        // as end-before-start, i.e. already over. Keep the whole slot in the day it starts in.
        if (!endTime.isAfter(startTime)) {
            startTime = LocalTime.of(23, 0);
            endTime = LocalTime.of(23, 59);
        }
        TimeSlot slot = new TimeSlot(null, start.toLocalDate(), startTime, endTime, 5);
        setField(slot, "id", slotId);
        return slot;
    }

    private User buildUser(UUID userId) {
        User user = new User("Jan", "Kowalski", "jan@test.pl", "+48123456789", "hashedpw");
        setField(user, "id", userId);
        return user;
    }

    private Waitlist buildWaitlistEntry(UUID userId, TimeSlot slot, WaitlistStatus status) {
        User user = buildUser(userId);
        Waitlist entry = new Waitlist(user, slot, 1);
        setField(entry, "id", UUID.randomUUID());
        setField(entry, "status", status);
        setField(entry, "createdAt", Instant.now());
        return entry;
    }

    private Waitlist buildPendingConfirmationEntry(UUID waitlistId, User user, TimeSlot slot, Instant deadline) {
        Waitlist entry = new Waitlist(user, slot, 1);
        setField(entry, "id", waitlistId);
        setField(entry, "status", WaitlistStatus.PENDING_CONFIRMATION);
        setField(entry, "offeredAt", Instant.now());
        setField(entry, "confirmationDeadline", deadline);
        setField(entry, "createdAt", Instant.now());
        return entry;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    Field f = clazz.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            throw new RuntimeException("Field not found: " + fieldName);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot set field: " + fieldName, e);
        }
    }
}
