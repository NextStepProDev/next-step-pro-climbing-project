package pl.nextsteppro.climbing.api.admin;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.api.reservation.EventWaitlistService;
import pl.nextsteppro.climbing.api.reservation.WaitlistService;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventRepository;
import pl.nextsteppro.climbing.domain.reservation.GuestReservation;
import pl.nextsteppro.climbing.domain.reservation.GuestReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationStatus;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.util.List;
import java.util.UUID;

/**
 * Turns a guest entry into a registered participant once that person has an account: the account
 * gets an ordinary reservation for the same number of seats, and the guest's charge and payments
 * move to it ({@link GuestMoneyHandover}) before the guest row goes.
 *
 * <p>Decisions (owner, 2026-11-21):
 * <ul>
 *   <li>A guest for several people becomes ONE reservation for that many seats.</li>
 *   <li>An account already booked on the session is refused, and so is one already charged for it
 *       (a leftover of a cancelled reservation). Merging two bookings or two charges silently would
 *       hide a decision the admin has to make.</li>
 *   <li>No e-mail. This is bookkeeping: the person already has the seat and has known about it.</li>
 * </ul>
 *
 * <p>No capacity check, deliberately: the guest's seats are released and the same number taken in
 * one transaction, so occupancy does not change. Re-running the booking checks would refuse a full
 * session for no reason — the seats being "taken" are the guest's own.
 *
 * <p>⚠️ Order inside each method matters: every refusal, then reservations, then money, then the
 * guest delete. A guest's charge and payments go with its row (ON DELETE CASCADE), so the delete must
 * see them already moved. The money updates run with clearAutomatically and detach everything loaded
 * before them — moving them ahead of the bookings was tried and lost reservation rows written against
 * the detached slots — which is why the money-side refusal is a separate read up front.
 */
@Service
@Transactional
public class GuestAssignmentService {

    private final TimeSlotRepository timeSlotRepository;
    private final EventRepository eventRepository;
    private final ReservationRepository reservationRepository;
    private final GuestReservationRepository guestReservationRepository;
    private final UserRepository userRepository;
    private final WaitlistService waitlistService;
    private final EventWaitlistService eventWaitlistService;
    private final ActivityLogService activityLogService;
    private final GuestMoneyHandover moneyHandover;
    private final MessageService msg;

    public GuestAssignmentService(TimeSlotRepository timeSlotRepository,
                                  EventRepository eventRepository,
                                  ReservationRepository reservationRepository,
                                  GuestReservationRepository guestReservationRepository,
                                  UserRepository userRepository,
                                  WaitlistService waitlistService,
                                  EventWaitlistService eventWaitlistService,
                                  ActivityLogService activityLogService,
                                  GuestMoneyHandover moneyHandover,
                                  MessageService msg) {
        this.timeSlotRepository = timeSlotRepository;
        this.eventRepository = eventRepository;
        this.reservationRepository = reservationRepository;
        this.guestReservationRepository = guestReservationRepository;
        this.userRepository = userRepository;
        this.waitlistService = waitlistService;
        this.eventWaitlistService = eventWaitlistService;
        this.activityLogService = activityLogService;
        this.moneyHandover = moneyHandover;
        this.msg = msg;
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void assignSlotGuest(UUID adminId, UUID slotId, UUID guestId, UUID userId) {
        // The same lock booking takes; a second click waits here and then finds the guest gone.
        TimeSlot slot = timeSlotRepository.findByIdForUpdate(slotId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("error.not.found.slot")));
        if (slot.belongsToEvent()) {
            throw new IllegalStateException(msg.get("reservation.slot.belongs.to.event"));
        }
        if (slot.isBlocked()) {
            throw new IllegalStateException(msg.get("admin.slot.blocked"));
        }
        GuestReservation guest = requireGuest(guestId);
        if (guest.getTimeSlot() == null || !guest.getTimeSlot().getId().equals(slotId)) {
            throw new IllegalArgumentException(msg.get("error.guest.not.on.target"));
        }
        User user = requireAssignableUser(userId);
        User admin = requireAdmin(adminId);
        if (reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slotId, ReservationStatus.CONFIRMED)) {
            throw new IllegalStateException(msg.get("admin.guest.assign.already.booked"));
        }

        moneyHandover.requireHandoverPossible(guestId, userId);

        bookOnSlot(user, slot, guest.getParticipants());
        waitlistService.forgetEntryOnceBooked(userId, slotId);

        moneyHandover.handOver(guestId, userId);
        guestReservationRepository.deleteById(guestId);

        activityLogService.logAdminGuestAssignedToAccount(admin, user, slot, null);
        // Occupancy is unchanged, unless the account also held an invitation — that seat is free now.
        waitlistService.notifyAll(slotId);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void assignEventGuest(UUID adminId, UUID eventId, UUID guestId, UUID userId) {
        Event event = eventRepository.findByIdForUpdate(eventId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("error.not.found.event")));
        GuestReservation guest = requireGuest(guestId);
        if (!belongsToEvent(guest, eventId)) {
            throw new IllegalArgumentException(msg.get("error.guest.not.on.target"));
        }
        User user = requireAssignableUser(userId);
        User admin = requireAdmin(adminId);

        List<TimeSlot> slots = timeSlotRepository.findByEventId(eventId);
        if (slots.isEmpty()) {
            // Nobody registered has signed up yet: the per-day slots are created by the first booking.
            slots = TimeSlot.forEventDays(event).stream().map(timeSlotRepository::save).toList();
        }
        List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
        boolean alreadyBooked = reservationRepository.findConfirmedByTimeSlotIds(slotIds).stream()
            .anyMatch(r -> r.getUser().getId().equals(userId));
        if (alreadyBooked) {
            throw new IllegalStateException(msg.get("admin.guest.assign.already.booked"));
        }

        moneyHandover.requireHandoverPossible(guestId, userId);

        // Every day, as both signup paths do — an event booking is one row per day-slot.
        for (TimeSlot slot : slots) {
            bookOnSlot(user, slot, guest.getParticipants());
        }
        eventWaitlistService.forgetEntryOnceBooked(userId, eventId);

        moneyHandover.handOver(guestId, userId);
        guestReservationRepository.deleteById(guestId);

        activityLogService.logAdminGuestAssignedToAccount(admin, user, null, event);
        eventWaitlistService.notifyAll(eventId);
    }

    /**
     * A guest is timeSlot XOR event, and an event has both shapes: the event panel attaches guests
     * to the event, a day view attaches them to that day's slot. Both are this event's guest.
     */
    private static boolean belongsToEvent(GuestReservation guest, UUID eventId) {
        if (guest.getEvent() != null) return guest.getEvent().getId().equals(eventId);
        TimeSlot slot = guest.getTimeSlot();
        return slot != null && slot.belongsToEvent() && slot.getEvent().getId().equals(eventId);
    }

    /** Reuses a cancelled row for the pair (user, slot) — the unique key allows only one. */
    private void bookOnSlot(User user, TimeSlot slot, int participants) {
        Reservation existing = reservationRepository.findByUserIdAndTimeSlotId(user.getId(), slot.getId());
        if (existing != null) {
            existing.confirm();
            existing.setParticipants(participants);
            reservationRepository.save(existing);
            return;
        }
        Reservation reservation = new Reservation(user, slot);
        reservation.setParticipants(participants);
        // Keeps the "new reservations" dot dark: it is meant for what clients do, not the admin.
        reservation.setCreatedByAdmin(true);
        reservationRepository.save(reservation);
    }

    private GuestReservation requireGuest(UUID guestId) {
        return guestReservationRepository.findById(guestId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("error.not.found.guest")));
    }

    private User requireAssignableUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("error.not.found.user")));
        // The same rule as adding a participant: an unconfirmed account is deleted after 7 days,
        // and the guest's money would then end up under an anonymous erased payer.
        if (!user.isEmailVerified()) {
            throw new IllegalArgumentException(msg.get("admin.user.unverified", user.getFullName()));
        }
        return user;
    }

    private User requireAdmin(UUID adminId) {
        return userRepository.findById(adminId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("error.not.found.user")));
    }
}
