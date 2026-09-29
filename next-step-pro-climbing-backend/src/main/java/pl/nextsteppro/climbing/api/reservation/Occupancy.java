package pl.nextsteppro.climbing.api.reservation;

import pl.nextsteppro.climbing.domain.reservation.GuestReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.SlotParticipantCount;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * How many seats are taken, the one definition every capacity read uses: confirmed participants
 * plus guests. Guests are the half that used to go missing. Six capacity reads left them out, and
 * the calendar said "full" while the endpoint kept accepting people.
 *
 * <p>Not a Spring bean on purpose: each service builds one from the repositories it already has,
 * so their constructors (and the tests that call them) stay as they are.
 */
public final class Occupancy {

    private final ReservationRepository reservationRepository;
    private final GuestReservationRepository guestReservationRepository;

    public Occupancy(ReservationRepository reservationRepository,
                     GuestReservationRepository guestReservationRepository) {
        this.reservationRepository = reservationRepository;
        this.guestReservationRepository = guestReservationRepository;
    }

    /**
     * Seats taken on an event, counting only the given slots (callers pass all, or the bookable
     * ones). Every registered participant holds a reservation on every day, so the fullest day,
     * not the sum, is the headcount. Guests hang off the EVENT, not its slots, so they are added
     * once on top.
     */
    public int ofEvent(UUID eventId, List<TimeSlot> slots) {
        int guests = guestReservationRepository.sumParticipantsByEventId(eventId);
        if (slots.isEmpty()) return guests;
        List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
        return reservationRepository.countConfirmedByTimeSlotIds(slotIds).stream()
            .mapToInt(SlotParticipantCount::countAsInt)
            .max()
            .orElse(0) + guests;
    }

    /**
     * Seats taken per standalone slot, guests booked onto that slot included. A slot nobody holds
     * is absent from the map. The admin list and the public calendar both read this, so the slot
     * list cannot say "3/6" while the slot detail says "5/6".
     */
    public Map<UUID, Integer> perSlot(List<UUID> slotIds) {
        if (slotIds.isEmpty()) return Map.of();
        Map<UUID, Integer> counts = new HashMap<>(reservationRepository.countConfirmedByTimeSlotIds(slotIds).stream()
            .collect(Collectors.toMap(SlotParticipantCount::slotId, SlotParticipantCount::countAsInt)));
        for (SlotParticipantCount guests : guestReservationRepository.sumParticipantsByTimeSlotIds(slotIds)) {
            counts.merge(guests.slotId(), guests.countAsInt(), Integer::sum);
        }
        return counts;
    }
}
