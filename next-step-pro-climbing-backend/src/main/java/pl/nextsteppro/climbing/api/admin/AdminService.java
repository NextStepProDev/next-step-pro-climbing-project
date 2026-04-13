package pl.nextsteppro.climbing.api.admin;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.course.Course;
import pl.nextsteppro.climbing.domain.course.CourseRepository;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventRepository;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.SlotParticipantCount;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;
import pl.nextsteppro.climbing.domain.auth.AuthTokenRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.domain.user.UserRole;

import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.mail.MailService;
import pl.nextsteppro.climbing.infrastructure.security.JwtAuthenticationFilter;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.domain.waitlist.Waitlist;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistRepository;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminService {

    private final TimeSlotRepository timeSlotRepository;
    private final EventRepository eventRepository;
    private final CourseRepository courseRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final MailService mailService;
    private final ActivityLogService activityLogService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final MessageService msg;
    private final WaitlistRepository waitlistRepository;
    private final pl.nextsteppro.climbing.infrastructure.mail.AuthMailService authMailService;

    public AdminService(TimeSlotRepository timeSlotRepository,
                       EventRepository eventRepository,
                       CourseRepository courseRepository,
                       ReservationRepository reservationRepository,
                       UserRepository userRepository,
                       AuthTokenRepository authTokenRepository,
                       MailService mailService,
                       ActivityLogService activityLogService,
                       JwtAuthenticationFilter jwtAuthenticationFilter,
                       MessageService msg,
                       WaitlistRepository waitlistRepository,
                       pl.nextsteppro.climbing.infrastructure.mail.AuthMailService authMailService) {
        this.timeSlotRepository = timeSlotRepository;
        this.eventRepository = eventRepository;
        this.courseRepository = courseRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.mailService = mailService;
        this.activityLogService = activityLogService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.msg = msg;
        this.waitlistRepository = waitlistRepository;
        this.authMailService = authMailService;
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public TimeSlotAdminDto createTimeSlot(CreateTimeSlotRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException(msg.get("admin.slot.end.after.start"));
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
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public TimeSlotAdminDto updateTimeSlot(UUID slotId, UpdateTimeSlotRequest request) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        LocalTime oldStart = slot.getStartTime();
        LocalTime oldEnd = slot.getEndTime();
        String oldTitle = slot.getTitle();

        if (request.startTime() != null) slot.setStartTime(request.startTime());
        if (request.endTime() != null) slot.setEndTime(request.endTime());
        if (request.maxParticipants() != null) {
            int confirmed = reservationRepository.countConfirmedByTimeSlotId(slotId);
            if (request.maxParticipants() < confirmed) {
                throw new IllegalStateException(msg.get("admin.slot.capacity.too.low", String.valueOf(confirmed)));
            }
            slot.setMaxParticipants(request.maxParticipants());
        }
        if (request.title() != null) slot.setTitle(request.title().isBlank() ? null : request.title());

        LocalTime start = slot.getStartTime();
        LocalTime end = slot.getEndTime();
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(msg.get("admin.slot.end.after.start"));
        }

        slot = timeSlotRepository.save(slot);

        var tf = DateTimeFormatter.ofPattern("HH:mm");
        List<MailService.FieldChange> changes = new ArrayList<>();
        if (!oldStart.equals(slot.getStartTime()) || !oldEnd.equals(slot.getEndTime())) {
            changes.add(new MailService.FieldChange(
                "email.change.time",
                oldStart.format(tf) + " – " + oldEnd.format(tf),
                slot.getStartTime().format(tf) + " – " + slot.getEndTime().format(tf)
            ));
        }
        if (!Objects.equals(oldTitle, slot.getTitle())) {
            changes.add(new MailService.FieldChange(
                "email.change.title",
                oldTitle != null ? oldTitle : "–",
                slot.getTitle() != null ? slot.getTitle() : "–"
            ));
        }
        if (!changes.isEmpty()) {
            List<Reservation> confirmed = reservationRepository.findConfirmedByTimeSlotId(slot.getId());
            for (Reservation reservation : confirmed) {
                mailService.sendAdminSlotModificationNotification(reservation.getUser(), slot, changes);
            }
        }

        return toTimeSlotAdminDto(slot);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
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
            activityLogService.logCancelledByAdmin(reservation.getUser(), slot, reservation.getParticipants());
        }

        slot.block(reason);
        timeSlotRepository.save(slot);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
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
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void deleteTimeSlot(UUID slotId) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        List<Reservation> confirmed = reservationRepository.findConfirmedByTimeSlotId(slotId);
        for (Reservation reservation : confirmed) {
            reservation.cancelByAdmin();
            reservationRepository.save(reservation);
            mailService.sendAdminCancellationNotification(reservation);
            activityLogService.logCancelledByAdmin(reservation.getUser(), slot, reservation.getParticipants());
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
                r.getId(),
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

    @Transactional(readOnly = true)
    public SlotWaitlistDto getSlotWaitlist(UUID slotId) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        List<Waitlist> entries = waitlistRepository.findBySlotIdAndStatusWithUser(slotId, WaitlistStatus.WAITING);
        List<Waitlist> pending = waitlistRepository.findBySlotIdAndStatusWithUser(slotId, WaitlistStatus.PENDING_CONFIRMATION);

        List<WaitlistAdminEntryDto> all = new java.util.ArrayList<>();
        for (Waitlist w : pending) {
            all.add(toWaitlistAdminEntryDto(w));
        }
        for (Waitlist w : entries) {
            all.add(toWaitlistAdminEntryDto(w));
        }

        return new SlotWaitlistDto(slotId, slot.getDate(), slot.getStartTime(), slot.getEndTime(), all);
    }

    private WaitlistAdminEntryDto toWaitlistAdminEntryDto(Waitlist w) {
        return new WaitlistAdminEntryDto(
            w.getId(),
            w.getUser().getId(),
            w.getUser().getFullName(),
            w.getUser().getEmail(),
            w.getUser().getPhone(),
            w.getPosition(),
            w.getStatus().name(),
            w.getConfirmationDeadline(),
            w.getCreatedAt()
        );
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
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

        if (request.courseId() != null) {
            Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));
            event.setCourse(course);
            event.setTitle(course.getTitle());
        }

        event = eventRepository.save(event);

        return toEventAdminDto(event);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public EventAdminDto updateEvent(UUID eventId, UpdateEventRequest request) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        String oldTitle = event.getTitle();
        String oldLocation = event.getLocation();
        LocalDate oldStartDate = event.getStartDate();
        LocalDate oldEndDate = event.getEndDate();
        LocalTime oldStartTime = event.getStartTime();
        LocalTime oldEndTime = event.getEndTime();

        if (request.title() != null) event.setTitle(request.title());
        if (request.description() != null) event.setDescription(request.description());
        if (request.location() != null) event.setLocation(request.location());
        if (request.eventType() != null) event.setEventType(EventType.valueOf(request.eventType()));
        if (request.startDate() != null) event.setStartDate(request.startDate());
        if (request.endDate() != null) event.setEndDate(request.endDate());
        if (request.maxParticipants() != null) {
            List<TimeSlot> eventSlots = timeSlotRepository.findByEventId(eventId);
            if (!eventSlots.isEmpty()) {
                List<UUID> slotIds = eventSlots.stream().map(TimeSlot::getId).toList();
                int maxConfirmed = reservationRepository.countConfirmedByTimeSlotIds(slotIds)
                    .stream().mapToInt(SlotParticipantCount::countAsInt).max().orElse(0);
                if (request.maxParticipants() < maxConfirmed) {
                    throw new IllegalStateException(msg.get("admin.slot.capacity.too.low", String.valueOf(maxConfirmed)));
                }
            }
            event.setMaxParticipants(request.maxParticipants());
        }
        if (request.active() != null) event.setActive(request.active());
        if (request.startTime() != null) event.setStartTime(request.startTime());
        if (request.endTime() != null) event.setEndTime(request.endTime());
        if (request.courseId() != null) {
            Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));
            event.setCourse(course);
            event.setTitle(course.getTitle());
        } else if (Boolean.TRUE.equals(request.removeCourse())) {
            event.setCourse(null);
        }

        event = eventRepository.save(event);

        var df = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        var tf = DateTimeFormatter.ofPattern("HH:mm");
        List<MailService.FieldChange> changes = new ArrayList<>();
        if (!event.getTitle().equals(oldTitle)) {
            changes.add(new MailService.FieldChange("email.change.title", oldTitle, event.getTitle()));
        }
        if (!Objects.equals(oldLocation, event.getLocation())) {
            changes.add(new MailService.FieldChange("email.change.location",
                oldLocation != null ? oldLocation : "–",
                event.getLocation() != null ? event.getLocation() : "–"));
        }
        if (!oldStartDate.equals(event.getStartDate()) || !oldEndDate.equals(event.getEndDate())) {
            changes.add(new MailService.FieldChange("email.change.dates",
                oldStartDate.format(df) + " – " + oldEndDate.format(df),
                event.getStartDate().format(df) + " – " + event.getEndDate().format(df)));
        }
        if (!Objects.equals(oldStartTime, event.getStartTime()) || !Objects.equals(oldEndTime, event.getEndTime())) {
            String oldTime = (oldStartTime != null && oldEndTime != null)
                ? oldStartTime.format(tf) + " – " + oldEndTime.format(tf) : "–";
            String newTime = (event.getStartTime() != null && event.getEndTime() != null)
                ? event.getStartTime().format(tf) + " – " + event.getEndTime().format(tf) : "–";
            changes.add(new MailService.FieldChange("email.change.time", oldTime, newTime));
        }
        if (!changes.isEmpty()) {
            List<TimeSlot> slots = timeSlotRepository.findByEventId(eventId);
            if (!slots.isEmpty()) {
                List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
                List<Reservation> confirmed = reservationRepository.findConfirmedByTimeSlotIds(slotIds);
                Map<UUID, User> notifiedUsers = new LinkedHashMap<>();
                for (Reservation reservation : confirmed) {
                    notifiedUsers.putIfAbsent(reservation.getUser().getId(), reservation.getUser());
                }
                for (User user : notifiedUsers.values()) {
                    mailService.sendAdminEventModificationNotification(user, event, changes);
                }
            }
        }

        return toEventAdminDto(event);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
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
                activityLogService.logCancelledByAdmin(reservation.getUser(), reservation.getTimeSlot(), reservation.getParticipants());
            }

            for (User user : notifiedUsers.values()) {
                mailService.sendAdminEventCancellationNotification(user, event);
            }

            // Delete all reservations and time slots before deleting the event
            reservationRepository.deleteByTimeSlotIds(slotIds);
            timeSlotRepository.deleteAll(slots);
        }

        eventRepository.deleteById(eventId);
    }

    @Transactional(readOnly = true)
    public List<EventAdminDto> getAllEvents() {
        List<Event> events = eventRepository.findAllByOrderByStartDateAsc();
        if (events.isEmpty()) return List.of();

        List<UUID> eventIds = events.stream().map(Event::getId).toList();
        List<TimeSlot> allSlots = timeSlotRepository.findByEventIdIn(eventIds);
        Map<UUID, Integer> participantsMap = buildEventParticipantsMap(allSlots);

        return events.stream()
            .map(e -> toEventAdminDto(e, participantsMap.getOrDefault(e.getId(), 0)))
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

        int currentParticipants = slotDtos.stream()
            .mapToInt(TimeSlotAdminDto::currentParticipants)
            .reduce(0, Math::max);

        return new EventDetailAdminDto(
            event.getId(),
            event.getTitle(),
            event.getDescription(),
            event.getLocation(),
            event.getEventType().name(),
            event.getStartDate(),
            event.getEndDate(),
            event.getMaxParticipants(),
            currentParticipants,
            event.isActive(),
            event.getStartTime(),
            event.getEndTime(),
            event.belongsToCourse() ? event.getCourse().getId() : null,
            event.belongsToCourse() ? event.getCourse().getTitle() : null,
            slotDtos
        );
    }

    @Transactional(readOnly = true)
    public List<ReservationAdminDto> getAllUpcomingReservations() {
        List<TimeSlot> slots = timeSlotRepository.findByDateRangeOrdered(LocalDate.now(), LocalDate.now().plusYears(1));
        return buildReservationAdminDtos(slots);
    }

    @Transactional(readOnly = true)
    public List<ReservationAdminDto> getAllPastReservations() {
        List<TimeSlot> slots = timeSlotRepository.findPastOrdered(LocalDate.now(), LocalTime.now());
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
        jwtAuthenticationFilter.evictUser(userId);
    }

    public void removeAdmin(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.isAdmin()) {
            throw new IllegalStateException(msg.get("admin.user.not.admin"));
        }

        user.setRole(UserRole.USER);
        userRepository.save(user);
        jwtAuthenticationFilter.evictUser(userId);
    }

    public int sendMailToUsers(SendMailRequest request) {
        List<User> users;
        if (request.recipientType() == RecipientType.ALL) {
            users = userRepository.findAll();
        } else {
            if (request.userIds() == null || request.userIds().isEmpty()) {
                throw new IllegalArgumentException("User IDs required for SELECTED recipient type");
            }
            users = userRepository.findAllById(request.userIds());
        }

        List<User> recipients = users.stream()
            .filter(User::isEmailVerified)
            .toList();

        for (User recipient : recipients) {
            mailService.sendCustomAdminMail(recipient.getEmail(), request.subject(), request.body());
        }

        return recipients.size();
    }

    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.isAdmin()) {
            throw new IllegalStateException(msg.get("admin.user.cannot.delete.admin"));
        }

        authMailService.sendAccountDeletedByAdminNotification(user);
        reservationRepository.cancelConfirmedByUserId(userId);
        authTokenRepository.deleteAllByUserId(userId);
        userRepository.delete(user);
        jwtAuthenticationFilter.evictUser(userId);
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
            .collect(Collectors.toMap(SlotParticipantCount::slotId, SlotParticipantCount::countAsInt));
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
                r.getId(),
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

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void cancelReservationByAdmin(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
        if (!reservation.isConfirmed()) {
            throw new IllegalStateException("Reservation is not confirmed");
        }
        User user = reservation.getUser();
        TimeSlot slot = reservation.getTimeSlot();
        int participants = reservation.getParticipants();
        reservation.cancelByAdmin();
        reservationRepository.save(reservation);
        mailService.sendAdminCancellationNotification(reservation);
        activityLogService.logCancelledByAdmin(user, slot, participants);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void cancelEventParticipantByAdmin(UUID eventId, UUID userId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        List<TimeSlot> slots = timeSlotRepository.findByEventId(eventId);
        if (slots.isEmpty()) return;
        List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
        List<Reservation> userReservations = reservationRepository.findConfirmedByTimeSlotIds(slotIds)
            .stream().filter(r -> r.getUser().getId().equals(userId)).toList();
        if (userReservations.isEmpty()) return;
        User user = userReservations.getFirst().getUser();
        for (Reservation reservation : userReservations) {
            int participants = reservation.getParticipants();
            reservation.cancelByAdmin();
            reservationRepository.save(reservation);
            activityLogService.logCancelledByAdmin(user, reservation.getTimeSlot(), participants);
        }
        mailService.sendAdminEventParticipantRemovedNotification(user, event);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void updateReservationParticipants(UUID reservationId, int newParticipants) {
        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
        if (!reservation.isConfirmed()) {
            throw new IllegalStateException("Reservation is not confirmed");
        }
        TimeSlot slot = reservation.getTimeSlot();
        int oldParticipants = reservation.getParticipants();
        int currentTotal = reservationRepository.countConfirmedByTimeSlotId(slot.getId());
        int available = slot.getMaxParticipants() - currentTotal + oldParticipants;
        if (newParticipants > available) {
            throw new IllegalStateException(msg.get("admin.slot.capacity.too.low", String.valueOf(available)));
        }
        reservation.setParticipants(newParticipants);
        reservationRepository.save(reservation);
        mailService.sendAdminParticipantReductionNotification(reservation.getUser(), slot, oldParticipants, newParticipants);
    }

    private EventAdminDto toEventAdminDto(Event event) {
        return toEventAdminDto(event, 0);
    }

    private EventAdminDto toEventAdminDto(Event event, int currentParticipants) {
        return new EventAdminDto(
            event.getId(),
            event.getTitle(),
            event.getDescription(),
            event.getLocation(),
            event.getEventType().name(),
            event.getStartDate(),
            event.getEndDate(),
            event.getMaxParticipants(),
            currentParticipants,
            event.isActive(),
            event.getStartTime(),
            event.getEndTime(),
            event.belongsToCourse() ? event.getCourse().getId() : null,
            event.belongsToCourse() ? event.getCourse().getTitle() : null
        );
    }

    private Map<UUID, Integer> buildEventParticipantsMap(List<TimeSlot> slots) {
        if (slots.isEmpty()) return Map.of();
        Map<UUID, Integer> countMap = buildCountMap(slots);
        Map<UUID, Integer> participantsMap = new java.util.HashMap<>();
        for (TimeSlot slot : slots) {
            if (slot.belongsToEvent()) {
                UUID eventId = slot.getEvent().getId();
                int confirmed = countMap.getOrDefault(slot.getId(), 0);
                participantsMap.merge(eventId, confirmed, Math::max);
            }
        }
        return participantsMap;
    }
}
