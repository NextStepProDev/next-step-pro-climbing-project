package pl.nextsteppro.climbing.api.reservation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the ordering invariant shared by both account-deletion paths. These assertions used to
 * live in UserServiceTest; they moved here with the logic so the admin-side deletion is covered
 * by them too — it previously skipped this sequence entirely.
 */
@ExtendWith(MockitoExtension.class)
class UserSeatReleaseServiceTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private WaitlistService waitlistService;
    @Mock
    private EventWaitlistService eventWaitlistService;

    private UserSeatReleaseService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new UserSeatReleaseService(reservationRepository, waitlistService, eventWaitlistService);
        userId = UUID.randomUUID();
    }

    @Test
    void shouldCancelReservationsBeforeNotifyingWaitlistsWhenSeatsAreReleased() {
        UUID slotId = UUID.randomUUID();
        when(reservationRepository.findConfirmedSlotIdsByUserId(userId)).thenReturn(List.of(slotId));

        service.releaseSeatsAndNotifyWaitlists(userId);

        // notifyAll recounts free seats with a fresh aggregate query, so cancelling must come first —
        // reversed, the queue is told about seats the database still reports as taken.
        var inOrder = inOrder(reservationRepository, waitlistService);
        inOrder.verify(reservationRepository).cancelConfirmedByUserId(userId);
        inOrder.verify(waitlistService).notifyAll(slotId);
    }

    @Test
    void shouldCollectAffectedIdsBeforeCancellingWhenSeatsAreReleased() {
        when(reservationRepository.findConfirmedSlotIdsByUserId(userId)).thenReturn(List.of(UUID.randomUUID()));

        service.releaseSeatsAndNotifyWaitlists(userId);

        // The bulk UPDATE flips the rows to CANCELLED, so the projections have to be read first
        // or there is nothing left to identify the affected slots by.
        var inOrder = inOrder(reservationRepository);
        inOrder.verify(reservationRepository).findConfirmedSlotIdsByUserId(userId);
        inOrder.verify(reservationRepository).cancelConfirmedByUserId(userId);
    }

    @Test
    void shouldNotifyBothQueuesAndRemoveUserFromThemWhenSeatsAreReleased() {
        UUID slotId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(reservationRepository.findConfirmedSlotIdsByUserId(userId)).thenReturn(List.of(slotId));
        when(reservationRepository.findConfirmedEventIdsByUserId(userId)).thenReturn(List.of(eventId));

        service.releaseSeatsAndNotifyWaitlists(userId);

        verify(waitlistService).notifyAll(slotId);
        verify(eventWaitlistService).notifyAll(eventId);
        verify(waitlistService).removeUserFromAllWaitlists(userId);
        verify(eventWaitlistService).removeUserFromAllWaitlists(userId);
    }

    @Test
    void shouldReturnCancelledCountWhenSeatsAreReleased() {
        when(reservationRepository.findConfirmedSlotIdsByUserId(userId))
            .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        assertEquals(3, service.releaseSeatsAndNotifyWaitlists(userId));
    }

    @Test
    void shouldOfferNothingWhenUserHeldNoReservations() {
        // Mockito returns empty lists by default — the user had nothing booked.
        assertEquals(0, service.releaseSeatsAndNotifyWaitlists(userId));

        verify(waitlistService, never()).notifyAll(any());
        verify(eventWaitlistService, never()).notifyAll(any());
        // They may still have been waiting on someone else's slot, so this always runs.
        verify(waitlistService).removeUserFromAllWaitlists(userId);
        verify(eventWaitlistService).removeUserFromAllWaitlists(userId);
    }
}
