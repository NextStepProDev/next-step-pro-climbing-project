package pl.nextsteppro.climbing.api.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationStatus;
import pl.nextsteppro.climbing.domain.reservation.SlotParticipantCount;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlist;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlistRepository;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistStatus;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.mail.WaitlistMailService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class EventWaitlistService {

    private static final Logger log = LoggerFactory.getLogger(EventWaitlistService.class);
    private static final long CONFIRMATION_WINDOW_HOURS = 24;

    private final EventWaitlistRepository eventWaitlistRepository;
    private final EventRepository eventRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final WaitlistMailService waitlistMailService;
    private final ActivityLogService activityLogService;
    private final MessageService msg;

    public EventWaitlistService(EventWaitlistRepository eventWaitlistRepository,
                                EventRepository eventRepository,
                                TimeSlotRepository timeSlotRepository,
                                ReservationRepository reservationRepository,
                                UserRepository userRepository,
                                WaitlistMailService waitlistMailService,
                                ActivityLogService activityLogService,
                                MessageService msg) {
        this.eventWaitlistRepository = eventWaitlistRepository;
        this.eventRepository = eventRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.waitlistMailService = waitlistMailService;
        this.activityLogService = activityLogService;
        this.msg = msg;
    }

    public WaitlistResultDto joinEventWaitlist(UUID eventId, UUID userId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("reservation.event.not.found")));

        if (!event.isActive()) {
            throw new IllegalStateException(msg.get("reservation.event.inactive"));
        }

        LocalTime eventStartTime = event.getStartTime() != null ? event.getStartTime() : LocalTime.of(0, 0);
        if (LocalDateTime.of(event.getStartDate(), eventStartTime).isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(msg.get("reservation.slot.past"));
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<TimeSlot> slots = timeSlotRepository.findByEventId(eventId);
        boolean alreadyRegistered = slots.stream()
            .anyMatch(slot -> reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slot.getId(), ReservationStatus.CONFIRMED));
        if (alreadyRegistered) {
            throw new IllegalStateException(msg.get("waitlist.already.reserved"));
        }

        boolean alreadyOnWaitlist = eventWaitlistRepository.existsByUserAndEventAndStatuses(
            userId, eventId, List.of(WaitlistStatus.WAITING, WaitlistStatus.PENDING_CONFIRMATION));
        if (alreadyOnWaitlist) {
            throw new IllegalStateException(msg.get("waitlist.already.waiting"));
        }

        // Wydarzenie musi być efektywnie pełne
        int currentParticipants = computeCurrentParticipants(event, slots);
        int pendingCount = eventWaitlistRepository.countPendingConfirmationByEventId(eventId);
        if (currentParticipants + pendingCount < event.getMaxParticipants()) {
            throw new IllegalStateException(msg.get("waitlist.slot.has.spots"));
        }

        int position = eventWaitlistRepository.findMaxPositionForEvent(eventId) + 1;
        EventWaitlist entry = new EventWaitlist(user, event, position);
        eventWaitlistRepository.save(entry);

        log.info("User {} joined event waitlist for event {} at position {}", userId, eventId, position);
        return new WaitlistResultDto(true, msg.get("waitlist.joined"));
    }

    public void leaveEventWaitlist(UUID eventId, UUID userId) {
        EventWaitlist entry = eventWaitlistRepository.findByUserIdAndEventId(userId, eventId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("waitlist.not.found")));

        boolean wasPending = entry.isPendingConfirmation();
        eventWaitlistRepository.delete(entry);
        eventWaitlistRepository.flush();

        if (wasPending) {
            Event event = eventRepository.findById(eventId).orElse(null);
            if (event != null) {
                List<TimeSlot> slots = timeSlotRepository.findByEventId(eventId);
                int confirmed = computeCurrentParticipants(event, slots);
                int pending = eventWaitlistRepository.countPendingConfirmationByEventId(eventId);
                if (confirmed + pending < event.getMaxParticipants()) {
                    notifyAll(eventId);
                }
            }
        }

        log.info("User {} left event waitlist for event {}", userId, eventId);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public EventReservationResultDto confirmEventOffer(UUID waitlistId, UUID userId) {
        EventWaitlist entry = eventWaitlistRepository.findById(waitlistId)
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

        Event event = entry.getEvent();
        UUID eventId = event.getId();
        User user = entry.getUser();

        List<TimeSlot> slots = timeSlotRepository.findByEventId(eventId);
        int currentParticipants = computeCurrentParticipants(event, slots);
        if (currentParticipants >= event.getMaxParticipants()) {
            // Ktoś inny był szybszy — resetujemy tego usera do WAITING
            entry.returnToWaiting();
            eventWaitlistRepository.save(entry);
            throw new IllegalStateException(msg.get("waitlist.race.lost"));
        }

        // Tworzymy rezerwacje na wszystkie aktywne sloty (z pominięciem booking window)
        List<TimeSlot> activeSlots = slots.stream()
            .filter(slot -> !slot.isBlocked())
            .filter(slot -> !LocalDateTime.of(slot.getDate(), slot.getStartTime()).isBefore(LocalDateTime.now()))
            .toList();

        int slotsReserved = 0;
        for (TimeSlot slot : activeSlots) {
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
            slotsReserved++;
        }

        eventWaitlistRepository.delete(entry);

        // Pozostałe PENDING osoby wracają do kolejki
        List<EventWaitlist> otherPending = eventWaitlistRepository.findByEventIdAndStatusWithUser(eventId, WaitlistStatus.PENDING_CONFIRMATION);
        for (EventWaitlist other : otherPending) {
            other.returnToWaiting();
        }
        eventWaitlistRepository.saveAll(otherPending);

        waitlistMailService.sendEventWaitlistReservationConfirmed(user, event);
        activityLogService.logEventReservationCreated(user, event, 1);

        log.info("User {} confirmed event waitlist offer for event {} — {} slots reserved, {} others returned to waiting",
            userId, eventId, slotsReserved, otherPending.size());
        return new EventReservationResultDto(eventId, true, msg.get("reservation.event.confirmed"), slotsReserved);
    }

    public void notifyAll(UUID eventId) {
        List<EventWaitlist> waiting = eventWaitlistRepository.findWaitingByEventIdOrdered(eventId);
        if (waiting.isEmpty()) {
            log.debug("No event waitlist entries for event {}", eventId);
            return;
        }

        Event event = waiting.getFirst().getEvent();
        LocalTime startTime = event.getStartTime() != null ? event.getStartTime() : LocalTime.of(0, 0);
        Instant eventInstant = LocalDateTime.of(event.getStartDate(), startTime)
            .toInstant(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
        Instant maxDeadline = Instant.now().plus(CONFIRMATION_WINDOW_HOURS, ChronoUnit.HOURS);
        Instant deadline = eventInstant.isBefore(maxDeadline) ? eventInstant : maxDeadline;

        if (deadline.isBefore(Instant.now())) {
            log.info("Event {} is in the past, skipping event waitlist notification", eventId);
            for (EventWaitlist entry : waiting) {
                entry.expire();
            }
            eventWaitlistRepository.saveAll(waiting);
            return;
        }

        for (EventWaitlist entry : waiting) {
            entry.offerSpot(deadline);
        }
        eventWaitlistRepository.saveAll(waiting);

        for (EventWaitlist entry : waiting) {
            waitlistMailService.sendEventWaitlistOfferNotification(entry.getUser(), event, deadline);
        }

        log.info("Notified {} event waitlist users for event {}, deadline: {}", waiting.size(), eventId, deadline);
    }

    public void expireAndNotify() {
        List<EventWaitlist> expired = eventWaitlistRepository.findExpiredPendingConfirmations(Instant.now());
        if (expired.isEmpty()) return;

        Map<UUID, List<EventWaitlist>> byEvent = expired.stream()
            .collect(Collectors.groupingBy(w -> w.getEvent().getId()));

        for (EventWaitlist entry : expired) {
            entry.expire();
        }
        eventWaitlistRepository.saveAll(expired);

        for (UUID eventId : byEvent.keySet()) {
            int pending = eventWaitlistRepository.countPendingConfirmationByEventId(eventId);
            if (pending > 0) continue;

            Event event = eventRepository.findById(eventId).orElse(null);
            if (event == null) continue;

            List<TimeSlot> slots = timeSlotRepository.findByEventId(eventId);
            int confirmed = computeCurrentParticipants(event, slots);
            if (confirmed < event.getMaxParticipants()) {
                notifyAll(eventId);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<EventWaitlistEntryDto> getUserEventWaitlist(UUID userId) {
        return eventWaitlistRepository.findActiveByUserId(userId).stream()
            .map(this::toEntryDto)
            .toList();
    }

    private EventWaitlistEntryDto toEntryDto(EventWaitlist entry) {
        Event event = entry.getEvent();
        int position = eventWaitlistRepository.countWaitingAtOrBeforePosition(event.getId(), entry.getPosition());
        return new EventWaitlistEntryDto(
            entry.getId(),
            event.getId(),
            event.getTitle(),
            event.getStartDate(),
            event.getEndDate(),
            entry.getStatus(),
            entry.getConfirmationDeadline(),
            Math.max(1, position)
        );
    }

    private int computeCurrentParticipants(Event event, List<TimeSlot> slots) {
        if (slots.isEmpty()) return 0;
        List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
        return reservationRepository.countConfirmedByTimeSlotIds(slotIds).stream()
            .mapToInt(SlotParticipantCount::countAsInt)
            .max()
            .orElse(0);
    }
}
