package pl.nextsteppro.climbing.api.reschedule;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.reservation.GuestReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;
import pl.nextsteppro.climbing.domain.user.User;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who ELSE is booked on a term — the people an accepted request would move along with the
 * requester. The coach decided (2026-10-21) that accepting moves the whole term, on the condition
 * that this is never a surprise: the admin mail and the request card both read it from here.
 */
@Component
public class OtherParticipants {

    public record Others(List<String> names, int guests) {
        public boolean any() {
            return !names.isEmpty() || guests > 0;
        }
    }

    private final ReservationRepository reservationRepository;
    private final GuestReservationRepository guestReservationRepository;
    private final TimeSlotRepository timeSlotRepository;

    public OtherParticipants(ReservationRepository reservationRepository,
                             GuestReservationRepository guestReservationRepository,
                             TimeSlotRepository timeSlotRepository) {
        this.reservationRepository = reservationRepository;
        this.guestReservationRepository = guestReservationRepository;
        this.timeSlotRepository = timeSlotRepository;
    }

    @Transactional(readOnly = true)
    public Others onSlot(TimeSlot slot, UUID requesterId) {
        return new Others(
            namesExcept(reservationRepository.findConfirmedByTimeSlotIds(List.of(slot.getId())), requesterId),
            guestReservationRepository.sumParticipantsByTimeSlotId(slot.getId()));
    }

    /** An event booking is one row per day; each person is named once. */
    @Transactional(readOnly = true)
    public Others onEvent(Event event, UUID requesterId) {
        List<UUID> slotIds = timeSlotRepository.findByEventId(event.getId()).stream().map(TimeSlot::getId).toList();
        List<String> names = slotIds.isEmpty()
            ? List.of()
            : namesExcept(reservationRepository.findConfirmedByTimeSlotIds(slotIds), requesterId);
        return new Others(names, guestReservationRepository.sumParticipantsByEventId(event.getId()));
    }

    private static List<String> namesExcept(List<Reservation> confirmed, UUID requesterId) {
        Map<UUID, String> byUser = new LinkedHashMap<>();
        for (Reservation r : confirmed) {
            User u = r.getUser();
            if (u.getId().equals(requesterId)) continue;
            byUser.putIfAbsent(u.getId(), u.getFullName());
        }
        return List.copyOf(byUser.values());
    }
}
