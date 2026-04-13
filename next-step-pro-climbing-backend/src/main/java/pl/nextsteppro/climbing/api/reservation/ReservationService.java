package pl.nextsteppro.climbing.api.reservation;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.BookingTimeValidator;
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
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.mail.MailService;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final MailService mailService;
    private final ActivityLogService activityLogService;
    private final MessageService msg;
    private final WaitlistService waitlistService;
    private final EventWaitlistService eventWaitlistService;

    public ReservationService(ReservationRepository reservationRepository,
                             TimeSlotRepository timeSlotRepository,
                             UserRepository userRepository,
                             EventRepository eventRepository,
                             MailService mailService,
                             ActivityLogService activityLogService,
                             MessageService msg,
                             WaitlistService waitlistService,
                             EventWaitlistService eventWaitlistService) {
        this.reservationRepository = reservationRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.mailService = mailService;
        this.activityLogService = activityLogService;
        this.msg = msg;
        this.waitlistService = waitlistService;
        this.eventWaitlistService = eventWaitlistService;
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public ReservationResultDto createReservation(UUID slotId, UUID userId, @Nullable String comment, int participants) {
        if (participants < 1) {
            throw new IllegalArgumentException(msg.get("reservation.min.participants"));
        }

        TimeSlot slot = timeSlotRepository.findByIdForUpdate(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        if (BookingTimeValidator.isPast(slot.getDate(), slot.getStartTime())) {
            throw new IllegalArgumentException(msg.get("reservation.slot.past"));
        }
        if (!BookingTimeValidator.isWithinBookingWindow(slot.getDate(), slot.getStartTime())) {
            throw new IllegalStateException(msg.get("reservation.booking.window"));
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (slot.isBlocked()) {
            throw new IllegalStateException("This time slot is blocked");
        }

        if (reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slotId, ReservationStatus.CONFIRMED)) {
            throw new IllegalStateException(msg.get("reservation.already.exists"));
        }

        int currentCount = reservationRepository.countConfirmedByTimeSlotId(slotId);
        int spotsLeft = slot.getMaxParticipants() - currentCount;
        if (spotsLeft <= 0) {
            throw new IllegalStateException(msg.get("reservation.no.spots"));
        }
        if (participants > spotsLeft) {
            throw new IllegalStateException(msg.get("reservation.spots.available", spotsLeft, participants));
        }

        Reservation existing = reservationRepository.findByUserIdAndTimeSlotId(userId, slotId);
        boolean isReactivation = existing != null && existing.isCancelled();
        String sanitizedComment = Reservation.sanitizeComment(comment);
        Reservation reservation;
        if (isReactivation) {
            existing.confirm();
            existing.setParticipants(participants);
            existing.setComment(sanitizedComment);
            reservation = existing;
        } else {
            reservation = new Reservation(user, slot);
            reservation.setParticipants(participants);
            reservation.setComment(sanitizedComment);
        }
        reservation = reservationRepository.save(reservation);

        mailService.sendReservationConfirmation(reservation);
        mailService.sendAdminNotification(reservation);

        if (isReactivation) {
            activityLogService.logReservationReactivated(user, slot, participants);
        } else {
            activityLogService.logReservationCreated(user, slot, participants);
        }

        return new ReservationResultDto(
            reservation.getId(),
            true,
            msg.get("reservation.confirmed")
        );
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void cancelReservation(UUID reservationId, UUID userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));

        if (!reservation.getUser().getId().equals(userId)) {
            throw new IllegalStateException("You can only cancel your own reservations");
        }

        if (reservation.isCancelled()) {
            throw new IllegalStateException("This reservation is already cancelled");
        }

        TimeSlot slot = reservation.getTimeSlot();
        if (!BookingTimeValidator.isWithinBookingWindow(slot.getDate(), slot.getStartTime())) {
            throw new IllegalStateException(msg.get("reservation.cancel.window"));
        }

        reservation.cancel();
        reservationRepository.save(reservation);

        mailService.sendCancellationConfirmation(reservation);
        mailService.sendUserCancellationAdminNotification(reservation);

        activityLogService.logReservationCancelled(reservation.getUser(), slot, reservation.getParticipants());

        // Jeśli ktoś czeka na liście oczekujących, powiadamiamy wszystkich jednocześnie
        waitlistService.notifyAll(slot.getId());
    }

    @Transactional(readOnly = true)
    public List<UserReservationDto> getUserReservations(UUID userId) {
        return reservationRepository.findByUserId(userId).stream()
            .filter(Reservation::isConfirmed)
            .map(r -> toUserReservationDto(r, 0))
            .toList();
    }

    @Transactional(readOnly = true)
    public MyReservationsDto getUserUpcomingReservations(UUID userId) {
        List<Reservation> allReservations = reservationRepository.findUpcomingByUserIdIncludingAdminCancelled(userId, LocalDate.now(), LocalTime.now());

        List<Reservation> standaloneReservations = new ArrayList<>();
        Map<UUID, List<Reservation>> eventReservations = new LinkedHashMap<>();

        for (Reservation r : allReservations) {
            TimeSlot slot = r.getTimeSlot();
            if (slot.belongsToEvent()) {
                eventReservations.computeIfAbsent(slot.getEvent().getId(), k -> new ArrayList<>()).add(r);
            } else {
                standaloneReservations.add(r);
            }
        }

        // Batch query confirmed counts for standalone slots
        Map<UUID, Integer> standaloneConfirmedCounts = Map.of();
        if (!standaloneReservations.isEmpty()) {
            List<UUID> slotIds = standaloneReservations.stream().map(r -> r.getTimeSlot().getId()).toList();
            standaloneConfirmedCounts = reservationRepository.countConfirmedByTimeSlotIds(slotIds).stream()
                .collect(Collectors.toMap(SlotParticipantCount::slotId, SlotParticipantCount::countAsInt));
        }

        final Map<UUID, Integer> standaloneCountsFinal = standaloneConfirmedCounts;
        List<UserReservationDto> standaloneSlots = standaloneReservations.stream()
            .map(r -> {
                int confirmed = standaloneCountsFinal.getOrDefault(r.getTimeSlot().getId(), 0);
                int spotsAvailable = r.getTimeSlot().getMaxParticipants() - confirmed + r.getParticipants();
                return toUserReservationDto(r, spotsAvailable);
            })
            .toList();

        // Batch query confirmed counts for all event slots
        Map<UUID, Integer> eventSlotConfirmedCounts = Map.of();
        if (!eventReservations.isEmpty()) {
            List<UUID> allEventSlotIds = eventReservations.values().stream()
                .flatMap(List::stream).map(r -> r.getTimeSlot().getId()).toList();
            eventSlotConfirmedCounts = reservationRepository.countConfirmedByTimeSlotIds(allEventSlotIds).stream()
                .collect(Collectors.toMap(SlotParticipantCount::slotId, SlotParticipantCount::countAsInt));
        }

        final Map<UUID, Integer> eventSlotCountsFinal = eventSlotConfirmedCounts;
        List<UserEventReservationDto> eventDtos = eventReservations.entrySet().stream()
            .map(entry -> {
                List<Reservation> reservations = entry.getValue();
                Reservation first = reservations.getFirst();
                Event event = first.getTimeSlot().getEvent();
                int maxConfirmedCount = reservations.stream()
                    .mapToInt(r -> eventSlotCountsFinal.getOrDefault(r.getTimeSlot().getId(), 0))
                    .max().orElse(0);
                int spotsAvailable = event.getMaxParticipants() - maxConfirmedCount + first.getParticipants();
                boolean cancelledByAdmin = reservations.stream()
                    .allMatch(r -> r.getStatus() == ReservationStatus.CANCELLED_BY_ADMIN);
                return new UserEventReservationDto(
                    event.getId(),
                    event.getTitle(),
                    event.getEventType().name(),
                    event.getStartDate(),
                    event.getEndDate(),
                    first.getComment(),
                    first.getParticipants(),
                    reservations.size(),
                    spotsAvailable,
                    first.getCreatedAt(),
                    event.getCourse() != null ? event.getCourse().getId() : null,
                    cancelledByAdmin
                );
            })
            .toList();

        return new MyReservationsDto(standaloneSlots, eventDtos);
    }

    @Transactional(readOnly = true)
    public MyReservationsDto getUserPastReservations(UUID userId) {
        List<Reservation> allReservations = reservationRepository.findPastByUserId(userId, LocalDate.now(), LocalTime.now());

        List<UserReservationDto> standaloneSlots = new ArrayList<>();
        Map<UUID, List<Reservation>> eventReservations = new LinkedHashMap<>();

        for (Reservation r : allReservations) {
            TimeSlot slot = r.getTimeSlot();
            if (slot.belongsToEvent()) {
                eventReservations.computeIfAbsent(slot.getEvent().getId(), k -> new ArrayList<>()).add(r);
            } else {
                standaloneSlots.add(toUserReservationDto(r, 0));
            }
        }

        List<UserEventReservationDto> eventDtos = eventReservations.entrySet().stream()
            .map(entry -> {
                List<Reservation> reservations = entry.getValue();
                Reservation first = reservations.getFirst();
                Event event = first.getTimeSlot().getEvent();
                boolean cancelledByAdmin = reservations.stream()
                    .allMatch(r -> r.getStatus() == ReservationStatus.CANCELLED_BY_ADMIN);
                return new UserEventReservationDto(
                    event.getId(),
                    event.getTitle(),
                    event.getEventType().name(),
                    event.getStartDate(),
                    event.getEndDate(),
                    first.getComment(),
                    first.getParticipants(),
                    reservations.size(),
                    0,
                    first.getCreatedAt(),
                    event.getCourse() != null ? event.getCourse().getId() : null,
                    cancelledByAdmin
                );
            })
            .toList();

        return new MyReservationsDto(standaloneSlots, eventDtos);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public EventReservationResultDto createEventReservation(UUID eventId, UUID userId, @Nullable String comment, int participants) {
        if (participants < 1) {
            throw new IllegalArgumentException(msg.get("reservation.min.participants"));
        }

        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("reservation.event.not.found")));

        if (!event.isActive()) {
            throw new IllegalStateException(msg.get("reservation.event.inactive"));
        }

        LocalTime eventStartTime = event.getStartTime() != null ? event.getStartTime() : LocalTime.of(0, 0);
        if (!BookingTimeValidator.isWithinBookingWindow(event.getStartDate(), eventStartTime)) {
            throw new IllegalStateException(msg.get("reservation.event.booking.window"));
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<TimeSlot> allSlots = timeSlotRepository.findByEventId(eventId);

        if (allSlots.isEmpty()) {
            allSlots = createDefaultSlotsForEvent(event);
        }

        List<TimeSlot> activeSlots = allSlots.stream()
            .filter(slot -> !slot.isBlocked())
            .filter(slot -> !LocalDateTime.of(slot.getDate(), slot.getStartTime()).isBefore(LocalDateTime.now()))
            .toList();

        if (activeSlots.isEmpty()) {
            throw new IllegalStateException(msg.get("reservation.event.no.active.slots"));
        }

        boolean alreadyRegistered = activeSlots.stream()
            .anyMatch(slot -> reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slot.getId(), ReservationStatus.CONFIRMED));
        if (alreadyRegistered) {
            throw new IllegalStateException(msg.get("reservation.event.already.registered"));
        }

        List<UUID> activeSlotIds = activeSlots.stream().map(TimeSlot::getId).toList();
        Map<UUID, Integer> countMap = reservationRepository.countConfirmedByTimeSlotIds(activeSlotIds).stream()
            .collect(Collectors.toMap(
                SlotParticipantCount::slotId,
                SlotParticipantCount::countAsInt
            ));
        int currentParticipants = countMap.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        int spotsLeft = event.getMaxParticipants() - currentParticipants;
        if (spotsLeft <= 0) {
            throw new IllegalStateException(msg.get("reservation.event.no.spots"));
        }
        if (participants > spotsLeft) {
            throw new IllegalStateException(msg.get("reservation.event.spots.available", spotsLeft, participants));
        }

        String sanitizedComment = Reservation.sanitizeComment(comment);

        int slotsReserved = 0;
        for (TimeSlot slot : activeSlots) {
            Reservation existing = reservationRepository.findByUserIdAndTimeSlotId(userId, slot.getId());
            Reservation reservation;
            if (existing != null && existing.isCancelled()) {
                existing.confirm();
                existing.setParticipants(participants);
                existing.setComment(sanitizedComment);
                reservation = existing;
            } else {
                reservation = new Reservation(user, slot);
                reservation.setParticipants(participants);
                reservation.setComment(sanitizedComment);
            }
            reservationRepository.save(reservation);
            slotsReserved++;
        }

        mailService.sendEventReservationConfirmation(user, event, participants);
        mailService.sendEventAdminNotification(user, event, participants);

        activityLogService.logEventReservationCreated(user, event, participants);

        return new EventReservationResultDto(eventId, true, msg.get("reservation.event.confirmed"), slotsReserved);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void cancelEventReservation(UUID eventId, UUID userId) {
        List<TimeSlot> slots = timeSlotRepository.findByEventId(eventId);
        if (slots.isEmpty()) {
            throw new IllegalArgumentException(msg.get("reservation.event.no.slots"));
        }

        TimeSlot earliestSlot = slots.stream()
            .min((a, b) -> LocalDateTime.of(a.getDate(), a.getStartTime())
                .compareTo(LocalDateTime.of(b.getDate(), b.getStartTime())))
            .orElseThrow();
        if (!BookingTimeValidator.isWithinBookingWindow(earliestSlot.getDate(), earliestSlot.getStartTime())) {
            throw new IllegalStateException(msg.get("reservation.cancel.window"));
        }

        List<UUID> cancelledSlotIds = new ArrayList<>();
        for (TimeSlot slot : slots) {
            Reservation reservation = reservationRepository.findByUserIdAndTimeSlotId(userId, slot.getId());
            if (reservation != null && reservation.isConfirmed()) {
                reservation.cancel();
                reservationRepository.save(reservation);
                cancelledSlotIds.add(slot.getId());
            }
        }

        if (cancelledSlotIds.isEmpty()) {
            throw new IllegalStateException(msg.get("reservation.event.not.found.cancel"));
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        mailService.sendEventCancellationConfirmation(user, event);
        mailService.sendUserEventCancellationAdminNotification(user, event);

        activityLogService.logEventReservationCancelled(user, event);

        // Jeśli ktoś czeka na liście oczekujących, powiadamiamy wszystkich jednocześnie
        eventWaitlistService.notifyAll(eventId);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public ReservationResultDto updateSlotParticipants(UUID reservationId, UUID userId, int participants) {
        if (participants < 1) {
            throw new IllegalArgumentException(msg.get("reservation.min.participants"));
        }

        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("reservation.not.found")));

        if (!reservation.getUser().getId().equals(userId)) {
            throw new IllegalStateException("You can only update your own reservations");
        }

        if (!reservation.isConfirmed()) {
            throw new IllegalStateException(msg.get("reservation.not.confirmed"));
        }

        TimeSlot slot = timeSlotRepository.findByIdForUpdate(reservation.getTimeSlot().getId())
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        if (!BookingTimeValidator.isWithinBookingWindow(slot.getDate(), slot.getStartTime())) {
            throw new IllegalStateException(msg.get("reservation.cancel.window"));
        }

        int totalParticipants = reservationRepository.countConfirmedByTimeSlotId(slot.getId());
        int availableForThisReservation = slot.getMaxParticipants() - totalParticipants + reservation.getParticipants();
        if (participants > availableForThisReservation) {
            throw new IllegalStateException(msg.get("reservation.spots.available", availableForThisReservation, participants));
        }

        int oldParticipants = reservation.getParticipants();
        reservation.setParticipants(participants);
        reservationRepository.save(reservation);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        mailService.sendReservationUpdateConfirmation(user, slot, oldParticipants, participants);
        activityLogService.logReservationUpdated(user, slot, participants);

        return new ReservationResultDto(reservation.getId(), true, msg.get("reservation.updated"));
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public EventReservationResultDto updateEventParticipants(UUID eventId, UUID userId, int participants) {
        if (participants < 1) {
            throw new IllegalArgumentException(msg.get("reservation.min.participants"));
        }

        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("reservation.event.not.found")));

        List<TimeSlot> slots = timeSlotRepository.findByEventId(eventId);
        if (slots.isEmpty()) {
            throw new IllegalStateException(msg.get("reservation.event.no.slots"));
        }

        TimeSlot earliestSlot = slots.stream()
            .min((a, b) -> LocalDateTime.of(a.getDate(), a.getStartTime())
                .compareTo(LocalDateTime.of(b.getDate(), b.getStartTime())))
            .orElseThrow();
        if (!BookingTimeValidator.isWithinBookingWindow(earliestSlot.getDate(), earliestSlot.getStartTime())) {
            throw new IllegalStateException(msg.get("reservation.cancel.window"));
        }

        List<Reservation> userReservations = slots.stream()
            .map(slot -> reservationRepository.findByUserIdAndTimeSlotId(userId, slot.getId()))
            .filter(r -> r != null && r.isConfirmed())
            .toList();

        if (userReservations.isEmpty()) {
            throw new IllegalStateException(msg.get("reservation.event.not.registered"));
        }

        List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
        Map<UUID, Integer> countMap = reservationRepository.countConfirmedByTimeSlotIds(slotIds).stream()
            .collect(Collectors.toMap(
                SlotParticipantCount::slotId,
                SlotParticipantCount::countAsInt
            ));

        int currentUserParticipants = userReservations.getFirst().getParticipants();
        int currentMaxTotal = countMap.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int availableForThisGroup = event.getMaxParticipants() - currentMaxTotal + currentUserParticipants;
        if (participants > availableForThisGroup) {
            throw new IllegalStateException(msg.get("reservation.event.spots.available", availableForThisGroup, participants));
        }

        for (Reservation reservation : userReservations) {
            reservation.setParticipants(participants);
            reservationRepository.save(reservation);
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        mailService.sendEventReservationUpdateConfirmation(user, event, currentUserParticipants, participants);
        activityLogService.logEventReservationUpdated(user, event, participants);

        return new EventReservationResultDto(eventId, true, msg.get("reservation.updated"), userReservations.size());
    }

    private List<TimeSlot> createDefaultSlotsForEvent(Event event) {
        List<TimeSlot> slots = new ArrayList<>();
        LocalTime slotStart = event.getStartTime() != null ? event.getStartTime() : LocalTime.of(0, 0);
        LocalTime slotEnd = event.getEndTime() != null ? event.getEndTime() : LocalTime.of(23, 59);
        LocalDate date = event.getStartDate();
        while (!date.isAfter(event.getEndDate())) {
            TimeSlot slot = new TimeSlot(event, date, slotStart, slotEnd, event.getMaxParticipants());
            slots.add(timeSlotRepository.save(slot));
            date = date.plusDays(1);
        }
        return slots;
    }

    private UserReservationDto toUserReservationDto(Reservation reservation, int spotsAvailable) {
        TimeSlot slot = reservation.getTimeSlot();
        return new UserReservationDto(
            reservation.getId(),
            slot.getId(),
            slot.getDate(),
            slot.getStartTime(),
            slot.getEndTime(),
            reservation.getStatus().name(),
            slot.getDisplayTitle(),
            reservation.getComment(),
            reservation.getParticipants(),
            spotsAvailable,
            reservation.getCreatedAt()
        );
    }
}
