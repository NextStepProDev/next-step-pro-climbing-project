package pl.nextsteppro.climbing.api.reservation;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationStatus;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.mail.MailService;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@Transactional
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final MailService mailService;
    private final ActivityLogService activityLogService;

    public ReservationService(ReservationRepository reservationRepository,
                             TimeSlotRepository timeSlotRepository,
                             UserRepository userRepository,
                             EventRepository eventRepository,
                             MailService mailService,
                             ActivityLogService activityLogService) {
        this.reservationRepository = reservationRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.mailService = mailService;
        this.activityLogService = activityLogService;
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public ReservationResultDto createReservation(UUID slotId, UUID userId, @Nullable String comment, int participants) {
        if (participants < 1) {
            throw new IllegalArgumentException("Liczba miejsc musi wynosić co najmniej 1");
        }

        TimeSlot slot = timeSlotRepository.findByIdForUpdate(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        LocalDateTime slotDateTime = LocalDateTime.of(slot.getDate(), slot.getStartTime());
        if (slotDateTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Nie można zarezerwować terminu, który już minął");
        }
        if (slotDateTime.isBefore(LocalDateTime.now().plusHours(12))) {
            throw new IllegalStateException("Rezerwacja online jest możliwa do 12 godzin przed terminem. Skontaktuj się z instruktorem telefonicznie, aby sprawdzić dostępność.");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (slot.isBlocked()) {
            throw new IllegalStateException("This time slot is blocked");
        }

        if (reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slotId, ReservationStatus.CONFIRMED)) {
            throw new IllegalStateException("Masz już rezerwację na ten termin");
        }

        int currentCount = reservationRepository.countConfirmedByTimeSlotId(slotId);
        int spotsLeft = slot.getMaxParticipants() - currentCount;
        if (spotsLeft <= 0) {
            throw new IllegalStateException("Brak wolnych miejsc.");
        }
        if (participants > spotsLeft) {
            throw new IllegalStateException("Dostępnych miejsc: " + spotsLeft + ". Nie można zarezerwować " + participants + ".");
        }

        Reservation existing = reservationRepository.findByUserIdAndTimeSlotId(userId, slotId);
        boolean isReactivation = existing != null && existing.isCancelled();
        Reservation reservation;
        if (isReactivation) {
            existing.confirm();
            existing.setParticipants(participants);
            if (comment != null && !comment.isBlank()) {
                existing.setComment(comment.length() > 500 ? comment.substring(0, 500) : comment);
            } else {
                existing.setComment(null);
            }
            reservation = existing;
        } else {
            reservation = new Reservation(user, slot);
            reservation.setParticipants(participants);
            if (comment != null && !comment.isBlank()) {
                reservation.setComment(comment.length() > 500 ? comment.substring(0, 500) : comment);
            }
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
            "Rezerwacja potwierdzona!"
        );
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
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
        LocalDateTime slotDateTime = LocalDateTime.of(slot.getDate(), slot.getStartTime());
        if (slotDateTime.isBefore(LocalDateTime.now().plusHours(12))) {
            throw new IllegalStateException("Anulowanie online jest możliwe do 12 godzin przed terminem. Skontaktuj się z instruktorem telefonicznie.");
        }

        reservation.cancel();
        reservationRepository.save(reservation);

        mailService.sendCancellationConfirmation(reservation);
        mailService.sendUserCancellationAdminNotification(reservation);

        activityLogService.logReservationCancelled(reservation.getUser(), slot, reservation.getParticipants());
    }

    @Transactional(readOnly = true)
    public List<UserReservationDto> getUserReservations(UUID userId) {
        return reservationRepository.findByUserId(userId).stream()
            .filter(Reservation::isConfirmed)
            .map(this::toUserReservationDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public MyReservationsDto getUserUpcomingReservations(UUID userId) {
        List<Reservation> allReservations = reservationRepository.findUpcomingByUserIdIncludingAdminCancelled(userId, LocalDate.now(), LocalTime.now());

        List<UserReservationDto> standaloneSlots = new ArrayList<>();
        Map<UUID, List<Reservation>> eventReservations = new LinkedHashMap<>();

        for (Reservation r : allReservations) {
            TimeSlot slot = r.getTimeSlot();
            if (slot.belongsToEvent()) {
                eventReservations.computeIfAbsent(slot.getEvent().getId(), k -> new ArrayList<>()).add(r);
            } else {
                standaloneSlots.add(toUserReservationDto(r));
            }
        }

        List<UserEventReservationDto> eventDtos = eventReservations.entrySet().stream()
            .map(entry -> {
                List<Reservation> reservations = entry.getValue();
                Reservation first = reservations.getFirst();
                Event event = first.getTimeSlot().getEvent();
                return new UserEventReservationDto(
                    event.getId(),
                    event.getTitle(),
                    event.getEventType().name(),
                    event.getStartDate(),
                    event.getEndDate(),
                    first.getComment(),
                    first.getParticipants(),
                    reservations.size(),
                    first.getCreatedAt()
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
                standaloneSlots.add(toUserReservationDto(r));
            }
        }

        List<UserEventReservationDto> eventDtos = eventReservations.entrySet().stream()
            .map(entry -> {
                List<Reservation> reservations = entry.getValue();
                Reservation first = reservations.getFirst();
                Event event = first.getTimeSlot().getEvent();
                return new UserEventReservationDto(
                    event.getId(),
                    event.getTitle(),
                    event.getEventType().name(),
                    event.getStartDate(),
                    event.getEndDate(),
                    first.getComment(),
                    first.getParticipants(),
                    reservations.size(),
                    first.getCreatedAt()
                );
            })
            .toList();

        return new MyReservationsDto(standaloneSlots, eventDtos);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public EventReservationResultDto createEventReservation(UUID eventId, UUID userId, @Nullable String comment, int participants) {
        if (participants < 1) {
            throw new IllegalArgumentException("Liczba miejsc musi wynosić co najmniej 1");
        }

        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Wydarzenie nie zostało znalezione"));

        if (!event.isActive()) {
            throw new IllegalStateException("To wydarzenie nie jest aktywne");
        }

        LocalDateTime eventStart = LocalDateTime.of(
            event.getStartDate(),
            event.getStartTime() != null ? event.getStartTime() : LocalTime.of(0, 0)
        );
        if (eventStart.isBefore(LocalDateTime.now().plusHours(12))) {
            throw new IllegalStateException("Rezerwacja online jest możliwa do 12 godzin przed wydarzeniem. Skontaktuj się z instruktorem telefonicznie, aby sprawdzić dostępność.");
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
            throw new IllegalStateException("Brak aktywnych terminów dla tego wydarzenia");
        }

        boolean alreadyRegistered = activeSlots.stream()
            .anyMatch(slot -> reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slot.getId(), ReservationStatus.CONFIRMED));
        if (alreadyRegistered) {
            throw new IllegalStateException("Masz już rezerwację na to wydarzenie");
        }

        int currentParticipants = activeSlots.stream()
            .mapToInt(slot -> reservationRepository.countConfirmedByTimeSlotId(slot.getId()))
            .max()
            .orElse(0);

        int spotsLeft = event.getMaxParticipants() - currentParticipants;
        if (spotsLeft <= 0) {
            throw new IllegalStateException("Brak wolnych miejsc na to wydarzenie");
        }
        if (participants > spotsLeft) {
            throw new IllegalStateException("Dostępnych miejsc: " + spotsLeft + ". Nie można zarezerwować " + participants + ".");
        }

        String sanitizedComment = null;
        if (comment != null && !comment.isBlank()) {
            sanitizedComment = comment.length() > 500 ? comment.substring(0, 500) : comment;
        }

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
                if (sanitizedComment != null) {
                    reservation.setComment(sanitizedComment);
                }
            }
            reservationRepository.save(reservation);
            slotsReserved++;
        }

        mailService.sendEventReservationConfirmation(user, event);
        mailService.sendEventAdminNotification(user, event, participants);

        activityLogService.logEventReservationCreated(user, event, participants);

        return new EventReservationResultDto(eventId, true, "Zapisano na wydarzenie!", slotsReserved);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void cancelEventReservation(UUID eventId, UUID userId) {
        List<TimeSlot> slots = timeSlotRepository.findByEventId(eventId);
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("Wydarzenie nie ma przypisanych terminów");
        }

        LocalDateTime earliestSlotDateTime = slots.stream()
            .map(s -> LocalDateTime.of(s.getDate(), s.getStartTime()))
            .min(LocalDateTime::compareTo)
            .orElseThrow();
        if (earliestSlotDateTime.isBefore(LocalDateTime.now().plusHours(12))) {
            throw new IllegalStateException("Anulowanie online jest możliwe do 12 godzin przed terminem. Skontaktuj się z instruktorem telefonicznie.");
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
            throw new IllegalStateException("Nie znaleziono rezerwacji na to wydarzenie");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        mailService.sendEventCancellationConfirmation(user, event);
        mailService.sendUserEventCancellationAdminNotification(user, event);

        activityLogService.logEventReservationCancelled(user, event);
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

    private UserReservationDto toUserReservationDto(Reservation reservation) {
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
            reservation.getCreatedAt()
        );
    }
}
