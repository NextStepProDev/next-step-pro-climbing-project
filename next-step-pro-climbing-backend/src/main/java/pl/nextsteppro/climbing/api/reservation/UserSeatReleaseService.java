package pl.nextsteppro.climbing.api.reservation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;

import java.util.List;
import java.util.UUID;

/**
 * Frees every seat a departing user held and hands those seats to whoever is queued for them.
 *
 * <p>Extracted because both deletion paths need it and only one of them had it: a user deleting
 * their own account released their seats to the waitlists, while an admin deleting the same user
 * cancelled the reservations and told nobody — the queue sat idle on seats that were already free.
 *
 * <p><b>The order below is the point of this class and must not be rearranged:</b>
 * <ol>
 *   <li>Collect the affected slot/event ids as <em>projections</em>. Loading {@code Reservation}
 *       entities here conflicts with the Hibernate session that is about to delete their owner.</li>
 *   <li>Cancel the confirmed reservations with a bulk UPDATE, so the seats are free in the DB.</li>
 *   <li>Only now notify the waitlists — {@code notifyAll} recounts free seats with a fresh
 *       aggregate query, so it must run <em>after</em> the cancellation, never before.</li>
 *   <li>Drop the user from the queues they were themselves waiting on; if they were holding a
 *       PENDING offer, that seat returns to the pool for the next waiter.</li>
 * </ol>
 *
 * <p>Callers remain responsible for their own concerns (password check, admin guard, avatar file,
 * mail, activity log, token cleanup, cache eviction) and for actually deleting the user.
 */
@Service
@Transactional
public class UserSeatReleaseService {

    private final ReservationRepository reservationRepository;
    private final WaitlistService waitlistService;
    private final EventWaitlistService eventWaitlistService;

    public UserSeatReleaseService(ReservationRepository reservationRepository,
                                  WaitlistService waitlistService,
                                  EventWaitlistService eventWaitlistService) {
        this.reservationRepository = reservationRepository;
        this.waitlistService = waitlistService;
        this.eventWaitlistService = eventWaitlistService;
    }

    /**
     * @return how many confirmed reservations were cancelled (for the admin notification mail)
     */
    public int releaseSeatsAndNotifyWaitlists(UUID userId) {
        // 1. Projections only — no entities into the session.
        List<UUID> affectedSlotIds = reservationRepository.findConfirmedSlotIdsByUserId(userId);
        List<UUID> affectedEventIds = reservationRepository.findConfirmedEventIdsByUserId(userId);
        int cancelledReservations = affectedSlotIds.size();

        // 2. Free the seats in the database.
        reservationRepository.cancelConfirmedByUserId(userId);

        // 3. Now the queues can see them.
        for (UUID slotId : affectedSlotIds) {
            waitlistService.notifyAll(slotId);
        }
        for (UUID eventId : affectedEventIds) {
            eventWaitlistService.notifyAll(eventId);
        }

        // 4. Take the user off the queues they were waiting on.
        waitlistService.removeUserFromAllWaitlists(userId);
        eventWaitlistService.removeUserFromAllWaitlists(userId);

        return cancelledReservations;
    }
}
