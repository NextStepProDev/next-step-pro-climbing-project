package pl.nextsteppro.climbing.api.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.domain.BookingTimeValidator;
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
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class WaitlistService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);
    private static final long CONFIRMATION_WINDOW_HOURS = 24;

    private final WaitlistRepository waitlistRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final WaitlistMailService waitlistMailService;
    private final ActivityLogService activityLogService;
    private final MessageService msg;

    public WaitlistService(WaitlistRepository waitlistRepository,
                           TimeSlotRepository timeSlotRepository,
                           ReservationRepository reservationRepository,
                           UserRepository userRepository,
                           WaitlistMailService waitlistMailService,
                           ActivityLogService activityLogService,
                           MessageService msg) {
        this.waitlistRepository = waitlistRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.reservationRepository = reservationRepository;
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
        int confirmedCount = reservationRepository.countConfirmedByTimeSlotId(slotId);
        int pendingCount = waitlistRepository.countPendingConfirmationBySlotId(slotId);
        int effectiveSpotsLeft = slot.getMaxParticipants() - confirmedCount - pendingCount;
        if (effectiveSpotsLeft > 0) {
            throw new IllegalStateException(msg.get("waitlist.slot.has.spots"));
        }

        int position = waitlistRepository.findMaxPositionForSlot(slotId) + 1;
        Waitlist entry = new Waitlist(user, slot, position);
        waitlistRepository.save(entry);

        log.info("User {} joined waitlist for slot {} at position {}", userId, slotId, position);
        return new WaitlistResultDto(true, msg.get("waitlist.joined"));
    }

    public void leaveWaitlist(UUID slotId, UUID userId) {
        Waitlist entry = waitlistRepository.findByUserIdAndSlotId(userId, slotId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("waitlist.not.found")));

        boolean wasPending = entry.isPendingConfirmation();
        waitlistRepository.delete(entry);

        // Jeśli opuszcza PENDING — slot "odmraża się", oferujemy następnemu w kolejce
        if (wasPending) {
            offerToNext(slotId);
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

        User user = entry.getUser();
        TimeSlot slot = entry.getTimeSlot();

        // Tworzymy rezerwację z pominięciem booking window (użytkownik był już na waitliście)
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

        waitlistMailService.sendWaitlistReservationConfirmed(user, slot);
        activityLogService.logReservationCreated(user, slot, 1);

        log.info("User {} confirmed waitlist offer for slot {} — reservation created", userId, slot.getId());
        return new ReservationResultDto(reservation.getId(), true, msg.get("reservation.confirmed"));
    }

    // Wywoływane po anulowaniu rezerwacji — oferuje miejsce pierwszej osobie z kolejki
    public void offerToNext(UUID slotId) {
        List<Waitlist> waiting = waitlistRepository.findWaitingBySlotIdOrdered(slotId);
        if (waiting.isEmpty()) {
            log.debug("No waitlist entries for slot {}, slot is freely available", slotId);
            return;
        }

        Waitlist next = waiting.getFirst();
        TimeSlot slot = next.getTimeSlot();

        // Jeśli slot już w przeszłości lub mniej niż BOOKING_WINDOW_HOURS — skracamy deadline
        Instant slotInstant = LocalDateTime.of(slot.getDate(), slot.getStartTime())
            .toInstant(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
        Instant maxDeadline = Instant.now().plus(CONFIRMATION_WINDOW_HOURS, ChronoUnit.HOURS);
        Instant deadline = slotInstant.isBefore(maxDeadline) ? slotInstant : maxDeadline;

        // Jeśli slot już minął — nie ma sensu oferować
        if (deadline.isBefore(Instant.now())) {
            log.info("Slot {} is in the past, skipping waitlist offer", slotId);
            next.expire();
            waitlistRepository.save(next);
            // Spróbuj następnego (rekurencja przez scheduler, nie tu — żeby nie blokować transakcji)
            return;
        }

        next.offerSpot(deadline);
        waitlistRepository.save(next);

        waitlistMailService.sendWaitlistOfferNotification(next.getUser(), slot, deadline);
        log.info("Offered slot {} to user {} (waitlist position {}), deadline: {}",
            slotId, next.getUser().getId(), next.getPosition(), deadline);
    }

    // Wywoływane przez scheduler co 5 minut — expiruje przeterminowane oferty i promuje następnego
    public void expireAndPromoteNext() {
        List<Waitlist> expired = waitlistRepository.findExpiredPendingConfirmations(Instant.now());
        for (Waitlist entry : expired) {
            UUID slotId = entry.getTimeSlot().getId();
            entry.expire();
            waitlistRepository.save(entry);
            log.info("Expired waitlist offer for user {} on slot {}", entry.getUser().getId(), slotId);
            offerToNext(slotId);
        }
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
