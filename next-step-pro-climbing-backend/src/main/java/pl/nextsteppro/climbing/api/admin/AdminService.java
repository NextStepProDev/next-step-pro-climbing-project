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
import pl.nextsteppro.climbing.domain.reservation.GuestReservation;
import pl.nextsteppro.climbing.domain.reservation.GuestReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationStatus;
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
import pl.nextsteppro.climbing.api.reservation.EventWaitlistService;
import pl.nextsteppro.climbing.api.reservation.WaitlistService;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlistRepository;
import pl.nextsteppro.climbing.domain.waitlist.Waitlist;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistRepository;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final GuestReservationRepository guestReservationRepository;
    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final MailService mailService;
    private final ActivityLogService activityLogService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final MessageService msg;
    private final WaitlistRepository waitlistRepository;
    private final EventWaitlistRepository eventWaitlistRepository;
    private final pl.nextsteppro.climbing.infrastructure.mail.AuthMailService authMailService;
    private final WaitlistService waitlistService;
    private final EventWaitlistService eventWaitlistService;

    public AdminService(TimeSlotRepository timeSlotRepository,
                       EventRepository eventRepository,
                       CourseRepository courseRepository,
                       ReservationRepository reservationRepository,
                       GuestReservationRepository guestReservationRepository,
                       UserRepository userRepository,
                       AuthTokenRepository authTokenRepository,
                       MailService mailService,
                       ActivityLogService activityLogService,
                       JwtAuthenticationFilter jwtAuthenticationFilter,
                       MessageService msg,
                       WaitlistRepository waitlistRepository,
                       EventWaitlistRepository eventWaitlistRepository,
                       pl.nextsteppro.climbing.infrastructure.mail.AuthMailService authMailService,
                       WaitlistService waitlistService,
                       EventWaitlistService eventWaitlistService) {
        this.timeSlotRepository = timeSlotRepository;
        this.eventRepository = eventRepository;
        this.courseRepository = courseRepository;
        this.reservationRepository = reservationRepository;
        this.guestReservationRepository = guestReservationRepository;
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.mailService = mailService;
        this.activityLogService = activityLogService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.msg = msg;
        this.waitlistRepository = waitlistRepository;
        this.eventWaitlistRepository = eventWaitlistRepository;
        this.authMailService = authMailService;
        this.waitlistService = waitlistService;
        this.eventWaitlistService = eventWaitlistService;
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
        slot.setAvailabilityWindow(request.isAvailabilityWindow());

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

        LocalDate oldDate = slot.getDate();
        LocalTime oldStart = slot.getStartTime();
        LocalTime oldEnd = slot.getEndTime();
        String oldTitle = slot.getTitle();

        if (request.date() != null) slot.setDate(request.date());
        if (request.startTime() != null) slot.setStartTime(request.startTime());
        if (request.endTime() != null) slot.setEndTime(request.endTime());
        int oldMaxParticipants = slot.getMaxParticipants();
        if (request.maxParticipants() != null) {
            int confirmed = reservationRepository.countConfirmedByTimeSlotId(slotId)
                + guestReservationRepository.sumParticipantsByTimeSlotId(slotId);
            if (request.maxParticipants() < confirmed) {
                throw new IllegalStateException(msg.get("admin.slot.capacity.too.low", String.valueOf(confirmed)));
            }
            slot.setMaxParticipants(request.maxParticipants());
        }
        if (request.title() != null) slot.setTitle(request.title().isBlank() ? null : request.title());
        if (request.isAvailabilityWindow() != null) slot.setAvailabilityWindow(request.isAvailabilityWindow());

        LocalTime start = slot.getStartTime();
        LocalTime end = slot.getEndTime();
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(msg.get("admin.slot.end.after.start"));
        }

        slot = timeSlotRepository.save(slot);

        if (request.maxParticipants() != null && request.maxParticipants() > oldMaxParticipants) {
            waitlistService.notifyAll(slotId);
            if (slot.belongsToEvent()) {
                eventWaitlistService.notifyAll(slot.getEvent().getId());
            }
        }

        boolean shouldNotify = !Boolean.FALSE.equals(request.sendNotifications());
        if (shouldNotify) {
            var tf = DateTimeFormatter.ofPattern("HH:mm");
            var df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            List<MailService.FieldChange> changes = new ArrayList<>();
            if (!oldDate.equals(slot.getDate())) {
                changes.add(new MailService.FieldChange(
                    "email.change.date",
                    oldDate.format(df),
                    slot.getDate().format(df)
                ));
            }
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

        boolean isPast = slot.getDate().isBefore(LocalDate.now());

        // Use multi-slot variant (has JOIN FETCH user) to avoid LazyInitializationException after cache clear
        List<Reservation> confirmed = reservationRepository.findConfirmedByTimeSlotIds(List.of(slotId));
        for (Reservation reservation : confirmed) {
            reservation.getUser();     // force-load user proxy while still managed
            reservation.getTimeSlot(); // force-load timeSlot proxy while still managed
            activityLogService.logCancelledByAdmin(reservation.getUser(), slot, reservation.getParticipants());
        }

        // Delete in FK-safe order; clearAutomatically clears L1 cache after each JPQL delete
        waitlistRepository.deleteByTimeSlotId(slotId);           // clears cache
        reservationRepository.deleteByTimeSlotIds(List.of(slotId)); // clears cache
        timeSlotRepository.deleteById(slotId);

        // Send emails only after successful DB deletion
        if (!isPast) {
            for (Reservation reservation : confirmed) {
                mailService.sendAdminCancellationNotification(reservation);
            }
        }
    }

    public int notifySlotParticipants(UUID slotId, @Nullable NotifySlotParticipantsRequest request) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        if (slot.isAvailabilityWindow()) return 0;

        List<MailService.FieldChange> changes = new ArrayList<>();
        if (request != null) {
            var tf = DateTimeFormatter.ofPattern("HH:mm");
            var df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            if (request.previousDate() != null && !request.previousDate().equals(slot.getDate())) {
                changes.add(new MailService.FieldChange(
                    "email.change.date",
                    request.previousDate().format(df),
                    slot.getDate().format(df)
                ));
            }
            boolean timeChanged = (request.previousStartTime() != null && !request.previousStartTime().equals(slot.getStartTime()))
                || (request.previousEndTime() != null && !request.previousEndTime().equals(slot.getEndTime()));
            if (timeChanged) {
                String oldTime = (request.previousStartTime() != null ? request.previousStartTime().format(tf) : "?")
                    + " – " + (request.previousEndTime() != null ? request.previousEndTime().format(tf) : "?");
                String newTime = slot.getStartTime().format(tf) + " – " + slot.getEndTime().format(tf);
                changes.add(new MailService.FieldChange("email.change.time", oldTime, newTime));
            }
        }
        if (changes.isEmpty()) {
            changes.add(new MailService.FieldChange("email.change.general", "–", "–"));
        }

        List<Reservation> confirmed = reservationRepository.findConfirmedByTimeSlotId(slotId);
        int notified = 0;
        for (Reservation reservation : confirmed) {
            if (reservation.getUser().isEmailNotificationsEnabled()) {
                mailService.sendAdminSlotModificationNotification(reservation.getUser(), slot, changes);
                notified++;
            }
        }
        return notified;
    }

    @Transactional(readOnly = true)
    public List<TimeSlotAdminDto> getUpcomingSlots(LocalDate from) {
        LocalDate to = from.plusDays(90);
        List<TimeSlot> slots = timeSlotRepository.findByDateRangeOrdered(from, to).stream()
            .filter(slot -> !slot.belongsToEvent())
            .toList();
        Map<UUID, Integer> countMap = buildCountMap(slots);
        return slots.stream()
            .map(slot -> toTimeSlotAdminDto(slot, countMap.getOrDefault(slot.getId(), 0)))
            .toList();
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

        List<GuestParticipantDto> guestParticipants = guestReservationRepository.findByTimeSlotId(slotId).stream()
            .map(g -> new GuestParticipantDto(g.getId(), g.getNote(), g.getParticipants(), g.getCreatedAt()))
            .toList();

        return new SlotParticipantsDto(
            slotId,
            slot.getDate(),
            slot.getStartTime(),
            slot.getEndTime(),
            slot.getMaxParticipants(),
            participants,
            guestParticipants
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
        int oldEventMaxParticipants = event.getMaxParticipants();
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

        if (request.maxParticipants() != null && request.maxParticipants() > oldEventMaxParticipants) {
            eventWaitlistService.notifyAll(eventId);
        }

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

        boolean isPast = !event.getEndDate().isAfter(LocalDate.now());

        List<TimeSlot> slots = timeSlotRepository.findByEventId(eventId);
        List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();

        // Collect notification targets and log activity BEFORE any deletes
        Map<UUID, User> notifiedUsers = new LinkedHashMap<>();
        if (!slotIds.isEmpty()) {
            List<Reservation> confirmed = reservationRepository.findConfirmedByTimeSlotIds(slotIds);
            for (Reservation reservation : confirmed) {
                User user = reservation.getUser(); // force-load lazy proxy while still managed
                if (!isPast) {
                    notifiedUsers.putIfAbsent(user.getId(), user);
                }
                activityLogService.logCancelledByAdmin(user, reservation.getTimeSlot(), reservation.getParticipants());
            }
        }

        // Delete in FK-safe order; clearAutomatically = true clears L1 cache after each JPQL delete,
        // preventing stale managed-entity conflicts when Hibernate removes the TimeSlot/Event entities.
        if (!slotIds.isEmpty()) {
            waitlistRepository.deleteByTimeSlotIdIn(slotIds);   // clears cache
            reservationRepository.deleteByTimeSlotIds(slotIds); // clears cache
            timeSlotRepository.deleteAllByIdInBatch(slotIds);
        }
        eventWaitlistRepository.deleteByEventId(eventId);       // clears cache
        eventRepository.deleteById(eventId);

        // Send emails only after all DB operations succeed
        if (!isPast) {
            for (User user : notifiedUsers.values()) {
                mailService.sendAdminEventCancellationNotification(user, event);
            }
        }
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
                u.getCreatedAt(),
                u.isNewsletterSubscribed()
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
        } else if (request.recipientType() == RecipientType.NEWSLETTER) {
            users = userRepository.findAllByNewsletterSubscribedTrue();
        } else {
            if (request.userIds() == null || request.userIds().isEmpty()) {
                throw new IllegalArgumentException("User IDs required for SELECTED recipient type");
            }
            users = userRepository.findAllById(request.userIds());
        }

        List<User> recipients = users.stream()
            .filter(User::isEmailVerified)
            .toList();

        boolean isNewsletter = request.recipientType() == RecipientType.NEWSLETTER;
        for (User recipient : recipients) {
            if (isNewsletter) {
                mailService.sendNewsletterMail(recipient.getEmail(), request.subject(), request.body(), recipient.getPreferredLanguage());
            } else {
                mailService.sendCustomAdminMail(recipient.getEmail(), request.subject(), request.body());
            }
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
        int confirmedCount = reservationRepository.countConfirmedByTimeSlotId(slot.getId())
            + guestReservationRepository.sumParticipantsByTimeSlotId(slot.getId());
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
            slot.belongsToEvent() ? slot.getEvent().getId() : null,
            slot.isAvailabilityWindow()
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

        List<ParticipantDto> participants;
        if (slots.isEmpty()) {
            participants = List.of();
        } else {
            List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
            List<Reservation> allReservations = reservationRepository.findConfirmedByTimeSlotIds(slotIds);

            // Deduplicate by user - keep the earliest reservation per user
            Map<UUID, Reservation> uniqueByUser = new LinkedHashMap<>();
            for (Reservation r : allReservations) {
                uniqueByUser.putIfAbsent(r.getUser().getId(), r);
            }

            participants = uniqueByUser.values().stream()
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
        }

        List<GuestParticipantDto> guestParticipants = guestReservationRepository.findByEventId(eventId).stream()
            .map(g -> new GuestParticipantDto(g.getId(), g.getNote(), g.getParticipants(), g.getCreatedAt()))
            .toList();

        return new EventParticipantsDto(eventId, event.getMaxParticipants(), participants, guestParticipants);
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
        waitlistService.notifyAll(slot.getId());
        if (slot.belongsToEvent()) {
            eventWaitlistService.notifyAll(slot.getEvent().getId());
        }
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
        eventWaitlistService.notifyAll(eventId);
        for (Reservation reservation : userReservations) {
            waitlistService.notifyAll(reservation.getTimeSlot().getId());
        }
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
        int currentTotal = reservationRepository.countConfirmedByTimeSlotId(slot.getId())
            + guestReservationRepository.sumParticipantsByTimeSlotId(slot.getId());
        int available = slot.getMaxParticipants() - currentTotal + oldParticipants;
        if (newParticipants > available) {
            throw new IllegalStateException(msg.get("admin.slot.capacity.too.low", String.valueOf(available)));
        }
        reservation.setParticipants(newParticipants);
        reservationRepository.save(reservation);
        User user = userRepository.findById(reservation.getUser().getId())
            .orElseThrow(() -> new IllegalStateException("User not found"));
        mailService.sendAdminParticipantReductionNotification(user, slot, oldParticipants, newParticipants);
        if (newParticipants < oldParticipants) {
            waitlistService.notifyAll(slot.getId());
            if (slot.belongsToEvent()) {
                eventWaitlistService.notifyAll(slot.getEvent().getId());
            }
        }
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void updateEventReservationParticipants(UUID eventId, UUID userId, int newParticipants) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        List<TimeSlot> slots = timeSlotRepository.findByEventId(eventId);
        if (slots.isEmpty()) throw new IllegalStateException("Event has no slots");

        List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
        List<Reservation> userReservations = reservationRepository.findConfirmedByTimeSlotIds(slotIds)
            .stream().filter(r -> r.getUser().getId().equals(userId)).toList();
        if (userReservations.isEmpty()) throw new IllegalStateException("No confirmed reservation found for this user");

        int oldParticipants = userReservations.getFirst().getParticipants();
        Map<UUID, Integer> countMap = reservationRepository.countConfirmedByTimeSlotIds(slotIds).stream()
            .collect(java.util.stream.Collectors.toMap(SlotParticipantCount::slotId, SlotParticipantCount::countAsInt));
        int currentMaxTotal = countMap.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int available = event.getMaxParticipants() - currentMaxTotal + oldParticipants;
        if (newParticipants > available) {
            throw new IllegalStateException(msg.get("admin.slot.capacity.too.low", String.valueOf(available)));
        }

        User user = userRepository.findById(userReservations.getFirst().getUser().getId())
            .orElseThrow(() -> new IllegalStateException("User not found"));
        for (Reservation reservation : userReservations) {
            reservation.setParticipants(newParticipants);
            reservationRepository.save(reservation);
        }
        mailService.sendAdminEventParticipantReductionNotification(user, event, oldParticipants, newParticipants);
        activityLogService.logEventReservationUpdated(user, event, newParticipants);
        if (newParticipants < oldParticipants) {
            eventWaitlistService.notifyAll(eventId);
            for (TimeSlot slot : slots) {
                waitlistService.notifyAll(slot.getId());
            }
        }
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

    // ==================== Admin Add/Remove Participants ====================

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void addRegisteredParticipantToSlot(UUID slotId, AddRegisteredParticipantRequest request) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        if (slot.isBlocked()) {
            throw new IllegalStateException(msg.get("admin.slot.blocked"));
        }

        User user = userRepository.findById(request.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(user.getId(), slotId, ReservationStatus.CONFIRMED)) {
            throw new IllegalStateException(msg.get("reservation.already.exists"));
        }

        int regularCount = reservationRepository.countConfirmedByTimeSlotId(slotId);
        int guestCount = guestReservationRepository.sumParticipantsByTimeSlotId(slotId);
        int available = slot.getMaxParticipants() - regularCount - guestCount;
        if (request.participants() > available) {
            throw new IllegalStateException(msg.get("reservation.spots.available", available, request.participants()));
        }

        Reservation existing = reservationRepository.findByUserIdAndTimeSlotId(user.getId(), slotId);
        String sanitizedComment = Reservation.sanitizeComment(request.comment());
        Reservation reservation;
        if (existing != null && existing.isCancelled()) {
            existing.confirm();
            existing.setParticipants(request.participants());
            existing.setComment(sanitizedComment);
            reservation = reservationRepository.save(existing);
        } else {
            reservation = new Reservation(user, slot);
            reservation.setParticipants(request.participants());
            reservation.setComment(sanitizedComment);
            reservation = reservationRepository.save(reservation);
        }

        boolean slotInPast = LocalDateTime.now().isAfter(slot.getDate().atTime(slot.getStartTime()));
        if (!slotInPast) {
            mailService.sendReservationConfirmation(reservation);
        }
        mailService.sendAdminNotification(reservation);
        activityLogService.logReservationCreated(user, slot, request.participants());
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public GuestParticipantDto addGuestParticipantToSlot(UUID slotId, AddGuestParticipantRequest request) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        if (slot.isBlocked()) {
            throw new IllegalStateException(msg.get("admin.slot.blocked"));
        }

        int regularCount = reservationRepository.countConfirmedByTimeSlotId(slotId);
        int guestCount = guestReservationRepository.sumParticipantsByTimeSlotId(slotId);
        int available = slot.getMaxParticipants() - regularCount - guestCount;
        if (request.participants() > available) {
            throw new IllegalStateException(msg.get("reservation.spots.available", available, request.participants()));
        }

        GuestReservation guest = new GuestReservation(slot, request.note().strip(), request.participants());
        guest = guestReservationRepository.save(guest);
        return new GuestParticipantDto(guest.getId(), guest.getNote(), guest.getParticipants(), guest.getCreatedAt());
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void deleteGuestParticipantFromSlot(UUID slotId, UUID guestId) {
        GuestReservation guest = guestReservationRepository.findById(guestId)
            .orElseThrow(() -> new IllegalArgumentException("Guest reservation not found"));
        if (guest.getTimeSlot() == null || !guest.getTimeSlot().getId().equals(slotId)) {
            throw new IllegalArgumentException("Guest reservation does not belong to this slot");
        }
        guestReservationRepository.delete(guest);
        waitlistService.notifyAll(slotId);
        timeSlotRepository.findById(slotId).ifPresent(slot -> {
            if (slot.belongsToEvent()) {
                eventWaitlistService.notifyAll(slot.getEvent().getId());
            }
        });
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void addRegisteredParticipantToEvent(UUID eventId, AddRegisteredParticipantRequest request) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        User user = userRepository.findById(request.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<TimeSlot> slots = timeSlotRepository.findByEventId(eventId);
        if (slots.isEmpty()) {
            slots = createDefaultSlotsForEvent(event);
        }

        // Check user is not already registered on any slot of this event
        List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
        boolean alreadyRegistered = reservationRepository.findConfirmedByTimeSlotIds(slotIds).stream()
            .anyMatch(r -> r.getUser().getId().equals(user.getId()));
        if (alreadyRegistered) {
            throw new IllegalStateException(msg.get("reservation.already.exists"));
        }

        // Check capacity — use max across slots (event-level logic)
        int guestCount = guestReservationRepository.sumParticipantsByEventId(eventId);
        Map<UUID, Integer> countMap = reservationRepository.countConfirmedByTimeSlotIds(slotIds).stream()
            .collect(Collectors.toMap(SlotParticipantCount::slotId, SlotParticipantCount::countAsInt));
        int maxConfirmed = countMap.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int available = event.getMaxParticipants() - maxConfirmed - guestCount;
        if (request.participants() > available) {
            throw new IllegalStateException(msg.get("reservation.spots.available", available, request.participants()));
        }

        String sanitizedComment = Reservation.sanitizeComment(request.comment());
        for (TimeSlot slot : slots) {
            Reservation existing = reservationRepository.findByUserIdAndTimeSlotId(user.getId(), slot.getId());
            if (existing != null && existing.isCancelled()) {
                existing.confirm();
                existing.setParticipants(request.participants());
                existing.setComment(sanitizedComment);
                reservationRepository.save(existing);
            } else if (existing == null) {
                Reservation reservation = new Reservation(user, slot);
                reservation.setParticipants(request.participants());
                reservation.setComment(sanitizedComment);
                reservationRepository.save(reservation);
            }
        }

        boolean eventInPast = LocalDate.now().isAfter(event.getEndDate());
        if (!eventInPast) {
            mailService.sendEventReservationConfirmation(user, event, request.participants());
        }
        mailService.sendEventAdminNotification(user, event, request.participants());
        activityLogService.logEventReservationCreated(user, event, request.participants());
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
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

    public GuestParticipantDto addGuestParticipantToEvent(UUID eventId, AddGuestParticipantRequest request) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        int guestCount = guestReservationRepository.sumParticipantsByEventId(eventId);
        List<TimeSlot> slots = timeSlotRepository.findByEventId(eventId);
        int maxConfirmed = 0;
        if (!slots.isEmpty()) {
            List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
            maxConfirmed = reservationRepository.countConfirmedByTimeSlotIds(slotIds).stream()
                .mapToInt(SlotParticipantCount::countAsInt).max().orElse(0);
        }
        int available = event.getMaxParticipants() - maxConfirmed - guestCount;
        if (request.participants() > available) {
            throw new IllegalStateException(msg.get("reservation.spots.available", available, request.participants()));
        }

        GuestReservation guest = new GuestReservation(event, request.note().strip(), request.participants());
        guest = guestReservationRepository.save(guest);
        return new GuestParticipantDto(guest.getId(), guest.getNote(), guest.getParticipants(), guest.getCreatedAt());
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void deleteGuestParticipantFromEvent(UUID eventId, UUID guestId) {
        GuestReservation guest = guestReservationRepository.findById(guestId)
            .orElseThrow(() -> new IllegalArgumentException("Guest reservation not found"));
        if (guest.getEvent() == null || !guest.getEvent().getId().equals(eventId)) {
            throw new IllegalArgumentException("Guest reservation does not belong to this event");
        }
        guestReservationRepository.delete(guest);
        eventWaitlistService.notifyAll(eventId);
        timeSlotRepository.findByEventId(eventId).forEach(slot -> waitlistService.notifyAll(slot.getId()));
    }
}
