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
    private final UserRepository userRepository;
    private final WaitlistMailService waitlistMailService;
    private final ActivityLogService activityLogService;
    private final MessageService msg;

    public WaitlistService(WaitlistRepository waitlistRepository,
                           TimeSlotRepository timeSlotRepository,
                           ReservationRepository reservationRepository,
                           GuestReservationRepository guestReservationRepository,
                           UserRepository userRepository,
                           WaitlistMailService waitlistMailService,
                           ActivityLogService activityLogService,
                           MessageService msg) {
        this.waitlistRepository = waitlistRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.reservationRepository = reservationRepository;
        this.guestReservationRepository = guestReservationRepository;
        this.userRepository = userRepository;
        this.waitlistMailService = waitlistMailService;
        this.activityLogService = activityLogService;
        this.msg = msg;
    }

    public WaitlistResultDto joinWaitlist(UUID slotId, UUID userId) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        if (BookingTimeValidator.isPast(slot.getDate(), slot.getStartTime())) {
            throw new IllegalArgumentException(msg.get("reservation.slot.past"));
        }
        if (slot.isBlocked()) {
            throw new IllegalStateException("This time slot is blocked");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slotId, ReservationStatus.CONFIRMED)) {
            throw new IllegalStateException(msg.get("waitlist.already.reserved"));
        }

        boolean alreadyOnWaitlist = waitlistRepository.existsByUserAndSlotAndStatuses(
            userId, slotId, List.of(WaitlistStatus.WAITING, WaitlistStatus.PENDING_CONFIRMATION));
        if (alreadyOnWaitlist) {
            throw new IllegalStateException(msg.get("waitlist.already.waiting"));
        }

        // Slot musi być efektywnie pełny (uwzględniamy PENDING_CONFIRMATION jako zajęte)
        int confirmedCount = reservationRepository.countConfirmedByTimeSlotId(slotId)
            + guestReservationRepository.sumParticipantsByTimeSlotId(slotId);
        int pendingCount = waitlistRepository.countPendingConfirmationBySlotId(slotId);
        int effectiveSpotsLeft = slot.getMaxParticipants() - confirmedCount - pendingCount;
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

    public void leaveWaitlist(UUID slotId, UUID userId) {
        Waitlist entry = waitlistRepository.findByUserIdAndSlotId(userId, slotId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("waitlist.not.found")));

        boolean wasPending = entry.isPendingConfirmation();
        waitlistRepository.delete(entry);
        waitlistRepository.flush();

        // Jeśli odchodzi osoba z PENDING — sprawdź czy slot nadal efektywnie pełny
        // (mogła być ostatnią osobą "ścigającą się" po zwolnionym miejscu)
        if (wasPending) {
            TimeSlot slot = timeSlotRepository.findById(slotId).orElse(null);
            if (slot != null) {
                int confirmed = reservationRepository.countConfirmedByTimeSlotId(slotId)
                    + guestReservationRepository.sumParticipantsByTimeSlotId(slotId);
                int pending = waitlistRepository.countPendingConfirmationBySlotId(slotId);
                if (confirmed + pending < slot.getMaxParticipants()) {
                    // Miejsce jest wolne i nikt się już nie ściga — powiadamiamy oczekujących
                    notifyAll(slotId);
                }
            }
        }

        log.info("User {} left waitlist for slot {}", userId, slotId);
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
            throw new IllegalStateException("You can only confirm your own waitlist offer");
        }
        if (!entry.isPendingConfirmation()) {
            throw new IllegalStateException(msg.get("waitlist.offer.not.pending"));
        }
        if (entry.isDeadlinePassed()) {
            throw new IllegalStateException(msg.get("waitlist.offer.expired"));
        }

        UUID slotId = entry.getTimeSlot().getId();

        // Pesymistyczny lock na slot — zapobiega race condition przy równoczesnych potwierdzeniach
        TimeSlot slot = timeSlotRepository.findByIdForUpdate(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        int confirmedCount = reservationRepository.countConfirmedByTimeSlotId(slotId)
            + guestReservationRepository.sumParticipantsByTimeSlotId(slotId);
        if (confirmedCount >= slot.getMaxParticipants()) {
            // Ktoś inny był szybszy — resetujemy tego użytkownika do WAITING
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

        // Pozostałe PENDING osoby wracają do kolejki (wyścig zakończony dla tego miejsca)
        List<Waitlist> otherPending = waitlistRepository.findBySlotIdAndStatusWithUser(slotId, WaitlistStatus.PENDING_CONFIRMATION);
        for (Waitlist other : otherPending) {
            other.returnToWaiting();
        }
        waitlistRepository.saveAll(otherPending);

        waitlistMailService.sendWaitlistReservationConfirmed(user, slot);
        activityLogService.logReservationCreated(user, slot, 1);

        log.info("User {} confirmed waitlist offer for slot {} — reservation created, {} others returned to waiting",
            userId, slot.getId(), otherPending.size());
        return new ReservationResultDto(reservation.getId(), true, msg.get("reservation.confirmed"));
    }

    // Wywoływane po zwolnieniu miejsca — powiadamia WSZYSTKICH oczekujących jednocześnie
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

    // Wywoływane przez scheduler co 5 minut — expiruje przeterminowane oferty i re-powiadamia jeśli miejsce nadal wolne
    public void expireAndNotify() {
        List<Waitlist> expired = waitlistRepository.findExpiredPendingConfirmations(Instant.now());
        if (expired.isEmpty()) return;

        // Deadline minął i nikt nie potwierdził — wracamy do WAITING bez kolejnego powiadomienia.
        // Następny notifyAll zostanie wywołany dopiero gdy ktoś anuluje rezerwację.
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
