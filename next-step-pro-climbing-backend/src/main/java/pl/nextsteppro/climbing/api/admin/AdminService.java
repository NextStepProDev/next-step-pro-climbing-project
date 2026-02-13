package pl.nextsteppro.climbing.api.admin;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventRepository;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;
import pl.nextsteppro.climbing.domain.auth.AuthTokenRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.domain.user.UserRole;

import pl.nextsteppro.climbing.infrastructure.mail.MailService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminService {

    private final TimeSlotRepository timeSlotRepository;
    private final EventRepository eventRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final MailService mailService;

    public AdminService(TimeSlotRepository timeSlotRepository,
                       EventRepository eventRepository,
                       ReservationRepository reservationRepository,
                       UserRepository userRepository,
                       AuthTokenRepository authTokenRepository,
                       MailService mailService) {
        this.timeSlotRepository = timeSlotRepository;
        this.eventRepository = eventRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.mailService = mailService;
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public TimeSlotAdminDto createTimeSlot(CreateTimeSlotRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("Godzina zakończenia musi być późniejsza niż godzina rozpoczęcia");
        }

        Event event = null;
        if (request.eventId() != null) {
            event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        }

        TimeSlot slot = new TimeSlot(
            request.date(),
            request.startTime(),
            request.endTime(),
            request.maxParticipants()
        );
        if (request.title() != null && !request.title().isBlank()) {
            slot.setTitle(request.title());
        }
        if (event != null) {
            slot.setEvent(event);
        }

        slot = timeSlotRepository.save(slot);
        return toTimeSlotAdminDto(slot);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public TimeSlotAdminDto updateTimeSlot(UUID slotId, UpdateTimeSlotRequest request) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        if (request.startTime() != null) slot.setStartTime(request.startTime());
        if (request.endTime() != null) slot.setEndTime(request.endTime());
        if (request.maxParticipants() != null) slot.setMaxParticipants(request.maxParticipants());
        if (request.title() != null) slot.setTitle(request.title().isBlank() ? null : request.title());

        LocalTime start = slot.getStartTime();
        LocalTime end = slot.getEndTime();
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Godzina zakończenia musi być późniejsza niż godzina rozpoczęcia");
        }

        slot = timeSlotRepository.save(slot);
        return toTimeSlotAdminDto(slot);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void blockTimeSlot(UUID slotId, @Nullable String reason) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        List<Reservation> confirmed = reservationRepository.findConfirmedByTimeSlotId(slotId);
        for (Reservation reservation : confirmed) {
            reservation.cancelByAdmin();
            reservationRepository.save(reservation);
            mailService.sendAdminCancellationNotification(reservation);
        }

        slot.block(reason);
        timeSlotRepository.save(slot);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void unblockTimeSlot(UUID slotId) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        slot.unblock();
        timeSlotRepository.save(slot);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void deleteTimeSlot(UUID slotId) {
        List<Reservation> confirmed = reservationRepository.findConfirmedByTimeSlotId(slotId);
        for (Reservation reservation : confirmed) {
            reservation.cancelByAdmin();
            reservationRepository.save(reservation);
            mailService.sendAdminCancellationNotification(reservation);
        }

        timeSlotRepository.deleteById(slotId);
    }

    @Transactional(readOnly = true)
    public SlotParticipantsDto getSlotParticipants(UUID slotId) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        List<Reservation> reservations = reservationRepository.findConfirmedByTimeSlotId(slotId);

        List<ParticipantDto> participants = reservations.stream()
            .map(r -> new ParticipantDto(
                r.getUser().getId(),
                r.getUser().getFullName(),
                r.getUser().getEmail(),
                r.getUser().getPhone(),
                r.getComment(),
                r.getParticipants(),
                r.getCreatedAt()
            ))
            .toList();

        return new SlotParticipantsDto(
            slotId,
            slot.getDate(),
            slot.getStartTime(),
            slot.getEndTime(),
            slot.getMaxParticipants(),
            participants
        );
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public EventAdminDto createEvent(CreateEventRequest request) {
        Event event = new Event(
            request.title(),
            EventType.valueOf(request.eventType()),
            request.startDate(),
            request.endDate(),
            request.maxParticipants()
        );
        event.setDescription(request.description());
        event.setLocation(request.location());
        event.setStartTime(request.startTime());
        event.setEndTime(request.endTime());

        event = eventRepository.save(event);

        return toEventAdminDto(event);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public EventAdminDto updateEvent(UUID eventId, UpdateEventRequest request) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        if (request.title() != null) event.setTitle(request.title());
        if (request.description() != null) event.setDescription(request.description());
        if (request.location() != null) event.setLocation(request.location());
        if (request.eventType() != null) event.setEventType(EventType.valueOf(request.eventType()));
        if (request.startDate() != null) event.setStartDate(request.startDate());
        if (request.endDate() != null) event.setEndDate(request.endDate());
        if (request.maxParticipants() != null) event.setMaxParticipants(request.maxParticipants());
        if (request.active() != null) event.setActive(request.active());
        if (request.startTime() != null) event.setStartTime(request.startTime());
        if (request.endTime() != null) event.setEndTime(request.endTime());

        event = eventRepository.save(event);
        return toEventAdminDto(event);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void deleteEvent(UUID eventId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        List<TimeSlot> slots = timeSlotRepository.findByEventId(eventId);
        if (!slots.isEmpty()) {
            List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
            List<Reservation> confirmed = reservationRepository.findConfirmedByTimeSlotIds(slotIds);

            Map<UUID, User> notifiedUsers = new LinkedHashMap<>();
            for (Reservation reservation : confirmed) {
                reservation.cancelByAdmin();
                reservationRepository.save(reservation);
                notifiedUsers.putIfAbsent(reservation.getUser().getId(), reservation.getUser());
            }

            for (User user : notifiedUsers.values()) {
                mailService.sendAdminEventCancellationNotification(user, event);
            }

            // Delete all reservations and time slots before deleting the event
            for (UUID slotId : slotIds) {
                reservationRepository.deleteAll(reservationRepository.findByTimeSlotId(slotId));
            }
            timeSlotRepository.deleteAll(slots);
        }

        eventRepository.deleteById(eventId);
    }

    @Transactional(readOnly = true)
    public List<EventAdminDto> getAllEvents() {
        return eventRepository.findAllByOrderByStartDateAsc().stream()
            .map(this::toEventAdminDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public EventDetailAdminDto getEventDetails(UUID eventId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        List<TimeSlot> slots = timeSlotRepository.findByEventId(eventId);
        Map<UUID, Integer> countMap = buildCountMap(slots);
        List<TimeSlotAdminDto> slotDtos = slots.stream()
            .map(slot -> toTimeSlotAdminDto(slot, countMap.getOrDefault(slot.getId(), 0)))
            .toList();

        return new EventDetailAdminDto(
            event.getId(),
            event.getTitle(),
            event.getDescription(),
            event.getLocation(),
            event.getEventType().name(),
            event.getStartDate(),
            event.getEndDate(),
            event.getMaxParticipants(),
            event.isActive(),
            event.getStartTime(),
            event.getEndTime(),
            slotDtos
        );
    }

    @Transactional(readOnly = true)
    public List<ReservationAdminDto> getAllUpcomingReservations() {
        List<TimeSlot> slots = timeSlotRepository.findByDateRangeOrdered(LocalDate.now(), LocalDate.now().plusYears(1));
        return buildReservationAdminDtos(slots);
    }

    @Transactional(readOnly = true)
    public List<ReservationAdminDto> getReservationsByDate(LocalDate date) {
        List<TimeSlot> slots = timeSlotRepository.findByDateSorted(date);
        return buildReservationAdminDtos(slots);
    }

    private List<ReservationAdminDto> buildReservationAdminDtos(List<TimeSlot> slots) {
        if (slots.isEmpty()) return List.of();

        List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
        List<Reservation> allReservations = reservationRepository.findConfirmedByTimeSlotIds(slotIds);

        Map<UUID, TimeSlot> slotMap = slots.stream()
            .collect(Collectors.toMap(TimeSlot::getId, s -> s));

        return allReservations.stream()
            .map(r -> {
                TimeSlot slot = slotMap.get(r.getTimeSlot().getId());
                Event event = slot.belongsToEvent() ? slot.getEvent() : null;
                return new ReservationAdminDto(
                    r.getId(),
                    r.getUser().getFullName(),
                    r.getUser().getEmail(),
                    r.getUser().getPhone(),
                    slot.getDate(),
                    slot.getStartTime(),
                    slot.getEndTime(),
                    slot.getDisplayTitle(),
                    r.getComment(),
                    r.getParticipants(),
                    event != null ? event.getStartDate() : null,
                    event != null ? event.getEndDate() : null
                );
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<UserAdminDto> getAllUsers() {
        return userRepository.findAll().stream()
            .map(u -> new UserAdminDto(
                u.getId(),
                u.getFirstName(),
                u.getLastName(),
                u.getEmail(),
                u.getPhone(),
                u.getRole().name(),
                u.getCreatedAt()
            ))
            .toList();
    }

    public void makeAdmin(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setRole(UserRole.ADMIN);
        userRepository.save(user);
    }

    public void removeAdmin(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.isAdmin()) {
            throw new IllegalStateException("Użytkownik nie jest administratorem");
        }

        user.setRole(UserRole.USER);
        userRepository.save(user);
    }

    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.isAdmin()) {
            throw new IllegalStateException("Nie można usunąć konta administratora");
        }

        for (Reservation reservation : reservationRepository.findByUserId(userId)) {
            if (reservation.isConfirmed()) {
                reservation.cancel();
                reservationRepository.save(reservation);
            }
        }

        authTokenRepository.deleteByUserId(userId);
        userRepository.delete(user);
    }

    private TimeSlotAdminDto toTimeSlotAdminDto(TimeSlot slot) {
        int confirmedCount = reservationRepository.countConfirmedByTimeSlotId(slot.getId());
        return toTimeSlotAdminDto(slot, confirmedCount);
    }

    private TimeSlotAdminDto toTimeSlotAdminDto(TimeSlot slot, int confirmedCount) {
        return new TimeSlotAdminDto(
            slot.getId(),
            slot.getDate(),
            slot.getStartTime(),
            slot.getEndTime(),
            slot.getMaxParticipants(),
            confirmedCount,
            slot.isBlocked(),
            slot.getBlockReason(),
            slot.getDisplayTitle(),
            slot.belongsToEvent() ? slot.getEvent().getId() : null
        );
    }

    private Map<UUID, Integer> buildCountMap(List<TimeSlot> slots) {
        if (slots.isEmpty()) return Map.of();
        List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
        return reservationRepository.countConfirmedByTimeSlotIds(slotIds).stream()
            .collect(Collectors.toMap(
                row -> (UUID) row[0],
                row -> ((Number) row[1]).intValue()
            ));
    }

    @Transactional(readOnly = true)
    public EventParticipantsDto getEventParticipants(UUID eventId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        List<TimeSlot> slots = timeSlotRepository.findByEventId(eventId);
        if (slots.isEmpty()) {
            return new EventParticipantsDto(eventId, event.getMaxParticipants(), List.of());
        }

        List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
        List<Reservation> allReservations = reservationRepository.findConfirmedByTimeSlotIds(slotIds);

        // Deduplicate by user - keep the earliest reservation per user
        Map<UUID, Reservation> uniqueByUser = new LinkedHashMap<>();
        for (Reservation r : allReservations) {
            uniqueByUser.putIfAbsent(r.getUser().getId(), r);
        }

        List<ParticipantDto> participants = uniqueByUser.values().stream()
            .map(r -> new ParticipantDto(
                r.getUser().getId(),
                r.getUser().getFullName(),
                r.getUser().getEmail(),
                r.getUser().getPhone(),
                r.getComment(),
                r.getParticipants(),
                r.getCreatedAt()
            ))
            .toList();

        return new EventParticipantsDto(eventId, event.getMaxParticipants(), participants);
    }

    private EventAdminDto toEventAdminDto(Event event) {
        return new EventAdminDto(
            event.getId(),
            event.getTitle(),
            event.getDescription(),
            event.getLocation(),
            event.getEventType().name(),
            event.getStartDate(),
            event.getEndDate(),
            event.getMaxParticipants(),
            event.isActive(),
            event.getStartTime(),
            event.getEndTime()
        );
    }
}
