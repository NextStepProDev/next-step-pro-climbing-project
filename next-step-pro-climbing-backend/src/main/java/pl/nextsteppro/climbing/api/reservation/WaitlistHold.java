package pl.nextsteppro.climbing.api.reservation;

import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlistRepository;
import pl.nextsteppro.climbing.domain.waitlist.OpenOfferCount;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * While a waitlist offer is open, the seat it offers belongs to the queue (owner's decision,
 * 2026-10-20). Before this, the offer went out to every waiter while the seat stayed bookable for
 * anybody: a walk-in could take it ahead of people who had queued for days, and the slot detail
 * (which counted offers) said "full" while the week view (which did not) showed a free seat.
 *
 * <p>An offer holds at most the seats that are actually free: it goes to ALL waiters at once, so
 * five open offers for one freed seat hold one seat, not five. People holding an offer are never
 * blocked by their own — they are the ones the seat is for.
 *
 * <p>Not a bean, for the same reason as {@link Occupancy}.
 */
public final class WaitlistHold {

    private final WaitlistRepository waitlistRepository;
    private final EventWaitlistRepository eventWaitlistRepository;

    public WaitlistHold(WaitlistRepository waitlistRepository, EventWaitlistRepository eventWaitlistRepository) {
        this.waitlistRepository = waitlistRepository;
        this.eventWaitlistRepository = eventWaitlistRepository;
    }

    /** Seats the queue holds, out of {@code freeSeats}, while {@code openOffers} of its offers are out. */
    public static int held(int openOffers, int freeSeats) {
        return Math.max(0, Math.min(openOffers, freeSeats));
    }

    /** Open offers on this slot that are not the viewer's own. */
    public int openOffersOnSlot(UUID slotId, @Nullable UUID viewerId) {
        return openOffersOnSlots(List.of(slotId)).getOrDefault(slotId, 0)
            - (slotsWithOwnOffer(viewerId, List.of(slotId)).isEmpty() ? 0 : 1);
    }

    /** Event twin of {@link #openOffersOnSlot}. */
    public int openOffersOnEvent(UUID eventId, @Nullable UUID viewerId) {
        return openOffersOnEvents(List.of(eventId)).getOrDefault(eventId, 0)
            - (eventsWithOwnOffer(viewerId, List.of(eventId)).isEmpty() ? 0 : 1);
    }

    public Map<UUID, Integer> openOffersOnSlots(Collection<UUID> slotIds) {
        if (slotIds.isEmpty()) return Map.of();
        return toMap(waitlistRepository.countOpenOffersBySlotIds(slotIds, Instant.now()));
    }

    public Map<UUID, Integer> openOffersOnEvents(Collection<UUID> eventIds) {
        if (eventIds.isEmpty()) return Map.of();
        return toMap(eventWaitlistRepository.countOpenOffersByEventIds(eventIds, Instant.now()));
    }

    public Set<UUID> slotsWithOwnOffer(@Nullable UUID viewerId, Collection<UUID> slotIds) {
        if (viewerId == null || slotIds.isEmpty()) return Set.of();
        return new HashSet<>(waitlistRepository.findSlotIdsWithOpenOfferFor(viewerId, slotIds, Instant.now()));
    }

    public Set<UUID> eventsWithOwnOffer(@Nullable UUID viewerId, Collection<UUID> eventIds) {
        if (viewerId == null || eventIds.isEmpty()) return Set.of();
        return new HashSet<>(eventWaitlistRepository.findEventIdsWithOpenOfferFor(viewerId, eventIds, Instant.now()));
    }

    private static Map<UUID, Integer> toMap(List<OpenOfferCount> counts) {
        return counts.stream().collect(Collectors.toMap(OpenOfferCount::targetId, OpenOfferCount::countAsInt));
    }
}
