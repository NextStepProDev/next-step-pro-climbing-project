package pl.nextsteppro.climbing.api.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.domain.BookingTimeValidator;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class WaitlistService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);
    private static final long CONFIRMATION_WINDOW_HOURS = 24;
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final WaitlistRepository waitlistRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final ReservationRepository reservationRepository;
    private final GuestReservationRepository guestReservationRepository;
    private final ReservedSeatRepository reservedSeatRepository;
    private final UserRepository userRepository;
    private final WaitlistMailService waitlistMailService;
    private final ActivityLogService activityLogService;
    private final MessageService msg;

    public WaitlistService(WaitlistRepository waitlistRepository,
                           TimeSlotRepository timeSlotRepository,
                           ReservationRepository reservationRepository,
                           GuestReservationRepository guestReservationRepository,
                           ReservedSeatRepository reservedSeatRepository,
                           UserRepository userRepository,
                           WaitlistMailService waitlistMailService,
                           ActivityLogService activityLogService,
                           MessageService msg) {
        this.waitlistRepository = waitlistRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.reservationRepository = reservationRepository;
        this.guestReservationRepository = guestReservationRepository;
        this.reservedSeatRepository = reservedSeatRepository;
        this.userRepository = userRepository;
        this.waitlistMailService = waitlistMailService;
        this.activityLogService = activityLogService;
        this.msg = msg;
    }

    public WaitlistResultDto joinWaitlist(UUID slotId, UUID userId) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("error.not.found.slot")));

        if (BookingTimeValidator.isPast(slot.getDate(), slot.getStartTime())) {
            throw new IllegalArgumentException(msg.get("reservation.slot.past"));
        }
        if (slot.isBlocked()) {
            throw new IllegalStateException(msg.get("admin.slot.blocked"));
        }

        // No queue for an absence — the seat it would wait for does not exist.
        if (slot.isUnavailable()) {
            throw new IllegalStateException(msg.get("reservation.slot.unavailable"));
        }
        // An availability window is not self-serviceable — you cannot join its queue either.
        if (slot.isAvailabilityWindow()) {
            throw new IllegalStateException(msg.get("reservation.slot.availability.window"));
        }
        // ⚠️ Same reason as the absence above, and it was missing: a slot with NO SEATS is full by
        // arithmetic (0 of 0), so the "is it full?" test below waved it through and somebody could
        // queue — with a confirmation email — for a seat that cannot ever exist. That shape is a
        // session run for somebody else, which is why it never frees up. Reuses the absence's
        // wording on purpose: to a client both mean "there is nothing to book here".
        if (slot.getMaxParticipants() == 0) {
            throw new IllegalStateException(msg.get("reservation.slot.unavailable"));
        }
        // An event's day-slot is queued for through the event — createReservation refuses booking it
        // directly, and confirming an offer here would hand out exactly that: one day of a course.
        if (slot.belongsToEvent()) {
            throw new IllegalStateException(msg.get("reservation.slot.belongs.to.event"));
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("error.not.found.user")));

        if (reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slotId, ReservationStatus.CONFIRMED)) {
            throw new IllegalStateException(msg.get("waitlist.already.reserved"));
        }

        boolean alreadyOnWaitlist = waitlistRepository.existsByUserAndSlotAndStatuses(
            userId, slotId, List.of(WaitlistStatus.WAITING, WaitlistStatus.PENDING_CONFIRMATION));
        if (alreadyOnWaitlist) {
            throw new IllegalStateException(msg.get("waitlist.already.waiting"));
        }

        // The slot must be effectively full (PENDING_CONFIRMATION counts as taken).
        // Seats held by invitation for OTHER people also consume availability — without this,
        // a slot full only due to a pending invitation would look like it has a free seat and
        // the queue join would be rejected (one's own invitation does not block, so we subtract
        // only other people's).
        int confirmedCount = reservationRepository.countConfirmedByTimeSlotId(slotId)
            + guestReservationRepository.sumParticipantsByTimeSlotId(slotId);
        int pendingCount = waitlistRepository.countPendingConfirmationBySlotId(slotId);
        int reservedForOthers = reservedSeatRepository.countPendingBySlotIdExcludingUser(slotId, userId);
        int effectiveSpotsLeft = slot.getMaxParticipants() - confirmedCount - pendingCount - reservedForOthers;
        if (effectiveSpotsLeft > 0) {
            throw new IllegalStateException(msg.get("waitlist.slot.has.spots"));
        }

        int position = waitlistRepository.findMaxPositionForSlot(slotId) + 1;
        Waitlist entry = new Waitlist(user, slot, position);
        waitlistRepository.save(entry);

        waitlistMailService.sendWaitlistJoinedConfirmation(user, slot);

        log.info("User {} joined waitlist for slot {} at position {}", userId, slotId, position);
        return new WaitlistResultDto(true, msg.get("waitlist.joined"));
    }

    /**
     * Someone who now holds a seat is no longer waiting for one. Called from every path that books
     * a slot: without it a client who booked directly, or whom the admin added by hand, stayed in
     * the queue — listed under "my waitlists" for a session they already had, and offered their own
     * seat at the next cancellation.
     */
    public void forgetEntryOnceBooked(UUID userId, UUID slotId) {
        waitlistRepository.findByUserIdAndSlotId(userId, slotId).ifPresent(waitlistRepository::delete);
    }

    // Declining an open offer gives the seat it held back — the cached anonymous calendar must see it.
    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void leaveWaitlist(UUID slotId, UUID userId) {
        Waitlist entry = waitlistRepository.findByUserIdAndSlotId(userId, slotId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("waitlist.not.found")));

        removeEntryAndMaybeNotify(entry);
        log.info("User {} left waitlist for slot {}", userId, slotId);
    }

    // Removes ALL of the user's active waitlist entries (on account deletion).
    // If the user had an active offer (PENDING_CONFIRMATION), the freed seat is
    // re-offered to the remaining waiters — same logic as leaveWaitlist.
    public void removeUserFromAllWaitlists(UUID userId) {
        List<Waitlist> entries = waitlistRepository.findActiveByUserId(userId);
        for (Waitlist entry : entries) {
            removeEntryAndMaybeNotify(entry);
        }
        if (!entries.isEmpty()) {
            log.info("Removed user {} from {} waitlist(s) during account deletion", userId, entries.size());
        }
    }

    private void removeEntryAndMaybeNotify(Waitlist entry) {
        UUID slotId = entry.getTimeSlot().getId();
        boolean wasPending = entry.isPendingConfirmation();
        waitlistRepository.delete(entry);
        waitlistRepository.flush();

        // If someone with PENDING leaves — check whether the slot is still effectively full
        // (they may have been the last person "racing" for the freed seat)
        if (wasPending) {
            TimeSlot slot = timeSlotRepository.findById(slotId).orElse(null);
            if (slot != null) {
                int confirmed = reservationRepository.countConfirmedByTimeSlotId(slotId)
                    + guestReservationRepository.sumParticipantsByTimeSlotId(slotId);
                int pending = waitlistRepository.countPendingConfirmationBySlotId(slotId);
                int reserved = reservedSeatRepository.countPendingBySlotId(slotId);
                if (confirmed + pending + reserved < slot.getMaxParticipants()) {
                    // The seat is free (and not held by invitation) — notify the waiters
                    notifyAll(slotId);
                }
            }
        }
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public ReservationResultDto confirmOffer(UUID waitlistId, UUID userId) {
        Waitlist entry = waitlistRepository.findById(waitlistId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("waitlist.not.found")));

        if (!entry.getUser().getId().equals(userId)) {
            throw new IllegalStateException(msg.get("error.waitlist.offer.not.yours"));
        }
        if (!entry.isPendingConfirmation()) {
            throw new IllegalStateException(msg.get("waitlist.offer.not.pending"));
        }
        if (entry.isDeadlinePassed()) {
            throw new IllegalStateException(msg.get("waitlist.offer.expired"));
        }

        UUID slotId = entry.getTimeSlot().getId();

        // Pessimistic lock on the slot — prevents race conditions on concurrent confirmations
        TimeSlot slot = timeSlotRepository.findByIdForUpdate(slotId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("error.not.found.slot")));

        // An offer outlives the slot state it was made under. blockTimeSlot cancels the confirmed
        // reservations but leaves PENDING_CONFIRMATION offers alive, so the seats it frees make the
        // capacity check below pass — the queued user would be handed a reservation on a cancelled
        // term. joinWaitlist guards all three of these; confirming has to as well.
        if (slot.isBlocked()) {
            // Same literal as joinWaitlist and createReservation — no message key exists for this one.
            throw new IllegalStateException(msg.get("admin.slot.blocked"));
        }
        if (slot.isUnavailable()) {
            throw new IllegalStateException(msg.get("reservation.slot.unavailable"));
        }
        if (BookingTimeValidator.isPast(slot.getDate(), slot.getEndTime())) {
            throw new IllegalStateException(msg.get("reservation.slot.past"));
        }
        // An entry queued on a day-slot before joinWaitlist refused it must not book one day either.
        if (slot.belongsToEvent()) {
            throw new IllegalStateException(msg.get("reservation.slot.belongs.to.event"));
        }

        // Already booked by another route (the admin added them while the offer was out). The capacity
        // check below counts their own seat as taken, so this used to answer "someone else was faster,
        // you are back on the waitlist" — to the person holding the seat, who then stayed queued.
        Reservation booked = reservationRepository.findByUserIdAndTimeSlotId(userId, slotId);
        if (booked != null && booked.isConfirmed()) {
            waitlistRepository.delete(entry);
            return new ReservationResultDto(booked.getId(), true, msg.get("reservation.confirmed"));
        }

        int confirmedCount = reservationRepository.countConfirmedByTimeSlotId(slotId)
            + guestReservationRepository.sumParticipantsByTimeSlotId(slotId);
        // Seats held for other invitees still block — otherwise two queued people offered at
        // once (e.g. when the admin removed one of several invitations) could confirm past the
        // limit. The confirmer's own invitation does not block (they may take their held seat).
        int reservedForOthers = reservedSeatRepository.countPendingBySlotIdExcludingUser(slotId, userId);
        if (confirmedCount + reservedForOthers >= slot.getMaxParticipants()) {
            // Someone else was faster — reset this user back to WAITING
            entry.returnToWaiting();
            waitlistRepository.save(entry);
            throw new IllegalStateException(msg.get("waitlist.race.lost"));
        }

        User user = entry.getUser();

        Reservation existing = reservationRepository.findByUserIdAndTimeSlotId(userId, slot.getId());
        Reservation reservation;
        if (existing != null && existing.isCancelled()) {
            existing.confirm();
            existing.setParticipants(1);
            reservation = existing;
        } else {
            reservation = new Reservation(user, slot);
        }
        reservationRepository.save(reservation);

        waitlistRepository.delete(entry);

        // Remaining PENDING people go back to the queue once the race is over — that is, once no seat
        // is left. While one is (two seats freed, one confirmed), their offers stay open: the seat
        // belongs to the queue during the offer (WaitlistHold), and returning them to WAITING here
        // would hand that second seat to the first walk-in instead, with nobody re-notified.
        List<Waitlist> otherPending = waitlistRepository.findBySlotIdAndStatusWithUser(slotId, WaitlistStatus.PENDING_CONFIRMATION);
        int taken = confirmedCount + reservation.getParticipants() + reservedSeatRepository.countPendingBySlotId(slotId);
        if (taken >= slot.getMaxParticipants()) {
            for (Waitlist other : otherPending) {
                other.returnToWaiting();
            }
            waitlistRepository.saveAll(otherPending);
        } else {
            otherPending = List.of();
        }

        waitlistMailService.sendWaitlistReservationConfirmed(user, slot);
        waitlistMailService.sendWaitlistAdminNotification(user, slot);
        activityLogService.logReservationCreated(user, slot, 1);

        log.info("User {} confirmed waitlist offer for slot {} — reservation created, {} others returned to waiting",
            userId, slot.getId(), otherPending.size());
        return new ReservationResultDto(reservation.getId(), true, msg.get("reservation.confirmed"));
    }

    // Called after a seat is freed — notifies ALL waiters at once
    public void notifyAll(UUID slotId) {
        log.info("WaitlistService.notifyAll called for slot {}", slotId);
        List<Waitlist> waiting = waitlistRepository.findWaitingBySlotIdOrdered(slotId);
        if (waiting.isEmpty()) {
            log.info("No waitlist entries for slot {}, slot is freely available", slotId);
            return;
        }

        TimeSlot slot = waiting.getFirst().getTimeSlot();
        Instant slotInstant = LocalDateTime.of(slot.getDate(), slot.getStartTime())
            .atZone(WARSAW).toInstant();
        Instant maxDeadline = Instant.now().plus(CONFIRMATION_WINDOW_HOURS, ChronoUnit.HOURS);
        Instant deadline = slotInstant.isBefore(maxDeadline) ? slotInstant : maxDeadline;

        if (deadline.isBefore(Instant.now())) {
            log.info("Slot {} is in the past, skipping waitlist notification", slotId);
            for (Waitlist entry : waiting) {
                entry.expire();
            }
            waitlistRepository.saveAll(waiting);
            return;
        }

        for (Waitlist entry : waiting) {
            entry.offerSpot(deadline);
        }
        waitlistRepository.saveAll(waiting);

        for (Waitlist entry : waiting) {
            waitlistMailService.sendWaitlistOfferNotification(entry.getUser(), slot, deadline);
        }

        log.info("Notified {} waitlist users for slot {}, deadline: {}", waiting.size(), slotId, deadline);
    }

    // Called by the scheduler every 5 minutes — expires overdue offers and re-notifies if the seat is still free
    public void expireAndNotify() {
        List<Waitlist> expired = waitlistRepository.findExpiredPendingConfirmations(Instant.now());
        if (expired.isEmpty()) return;

        // The deadline passed and nobody confirmed — back to WAITING without another notification.
        // The next notifyAll fires only when someone cancels a reservation.
        for (Waitlist entry : expired) {
            entry.returnToWaiting();
        }
        waitlistRepository.saveAll(expired);
        log.info("Returned {} expired waitlist entries to WAITING", expired.size());
    }

    @Transactional(readOnly = true)
    public List<WaitlistEntryDto> getUserWaitlist(UUID userId) {
        return waitlistRepository.findActiveByUserId(userId).stream()
            .map(this::toEntryDto)
            .toList();
    }

    private WaitlistEntryDto toEntryDto(Waitlist entry) {
        TimeSlot slot = entry.getTimeSlot();
        int position = waitlistRepository.countWaitingAtOrBeforePosition(slot.getId(), entry.getPosition());
        return new WaitlistEntryDto(
            entry.getId(),
            slot.getId(),
            slot.getDate(),
            slot.getStartTime(),
            slot.getEndTime(),
            slot.getDisplayTitle(),
            entry.getStatus(),
            entry.getConfirmationDeadline(),
            Math.max(1, position)
        );
    }
}
