package pl.nextsteppro.climbing.api.admin;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.BookingTimeValidator;
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
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeat;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeatRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequestRepository;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequestStatus;
import pl.nextsteppro.climbing.domain.auth.AuthTokenRepository;
import pl.nextsteppro.climbing.domain.auth.TokenType;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.domain.user.UserRole;

import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.mail.MailService;
import pl.nextsteppro.climbing.infrastructure.security.JwtAuthenticationFilter;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.api.reservation.EventWaitlistService;
import pl.nextsteppro.climbing.api.reservation.UserSeatReleaseService;
import pl.nextsteppro.climbing.api.reservation.WaitlistService;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlist;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlistRepository;
import pl.nextsteppro.climbing.domain.waitlist.Waitlist;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistRepository;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminService {

    // Schedules are stored as local Poland time while the container runs UTC — see CLAUDE.md.
    // Notification decisions must not flip around midnight, so they read "now" through this zone.
    private static final java.time.ZoneId WARSAW = java.time.ZoneId.of("Europe/Warsaw");

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
    private final UserSeatReleaseService userSeatReleaseService;
    private final ReservedSeatRepository reservedSeatRepository;
    private final TrainingRequestRepository trainingRequestRepository;
    private final pl.nextsteppro.climbing.api.trainingcalendar.TrainingCalendarService trainingCalendarService;

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
                       EventWaitlistService eventWaitlistService,
                       UserSeatReleaseService userSeatReleaseService,
                       ReservedSeatRepository reservedSeatRepository,
                       TrainingRequestRepository trainingRequestRepository,
                       pl.nextsteppro.climbing.api.trainingcalendar.TrainingCalendarService trainingCalendarService) {
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
        this.userSeatReleaseService = userSeatReleaseService;
        this.reservedSeatRepository = reservedSeatRepository;
        this.trainingRequestRepository = trainingRequestRepository;
        this.trainingCalendarService = trainingCalendarService;
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public TimeSlotAdminDto createTimeSlot(UUID adminId, CreateTimeSlotRequest request) {
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
            // An unavailable slot carries no seats — setUnavailable zeroes them, this keeps
            // the constructor from writing a capacity the DB CHECK would then reject.
            request.isUnavailable() ? 0 : request.maxParticipants()
        );
        if (request.title() != null && !request.title().isBlank()) {
            slot.setTitle(request.title());
        }
        if (event != null) {
            slot.setEvent(event);
        }
        slot.setAvailabilityWindow(request.isAvailabilityWindow());
        slot.setUnavailable(request.isUnavailable());

        slot = timeSlotRepository.save(slot);

        // Nobody is invited to an absence — the picker is hidden, and this makes the API agree.
        if (request.invitedUserIds() != null && !slot.isUnavailable()) {
            syncSlotInvites(slot, request.invitedUserIds());
        }

        if (request.trainingRequestId() != null) {
            linkTrainingRequest(request.trainingRequestId(), slot, null);
        }

        User admin = userRepository.findById(adminId).orElseThrow();
        activityLogService.logAdminSlotCreated(admin, slot);

        return toTimeSlotAdminDto(slot);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public SlotUpdateResultDto updateTimeSlot(UUID adminId, UUID slotId, UpdateTimeSlotRequest request) {
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
        if (request.isUnavailable() != null) {
            // Turning a slot into an absence drops its capacity to zero, so people already
            // holding a seat would silently lose it — refuse instead of cancelling behind
            // the admin's back (blocking the slot is the deliberate way to do that).
            if (request.isUnavailable()) {
                int confirmed = reservationRepository.countConfirmedByTimeSlotId(slotId)
                    + guestReservationRepository.sumParticipantsByTimeSlotId(slotId);
                if (confirmed > 0) {
                    throw new IllegalStateException(
                        msg.get("admin.slot.unavailable.has.reservations", String.valueOf(confirmed)));
                }
            }
            slot.setUnavailable(request.isUnavailable());
        }

        LocalTime start = slot.getStartTime();
        LocalTime end = slot.getEndTime();
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(msg.get("admin.slot.end.after.start"));
        }

        slot = timeSlotRepository.save(slot);

        // An absence keeps no held seats; anything the admin had invited is released here.
        if (slot.isUnavailable()) {
            syncSlotInvites(slot, List.of());
        } else if (request.invitedUserIds() != null) {
            syncSlotInvites(slot, request.invitedUserIds());
        }

        User admin = userRepository.findById(adminId).orElseThrow();
        activityLogService.logAdminSlotUpdated(admin, slot);

        if (request.maxParticipants() != null && request.maxParticipants() > oldMaxParticipants) {
            waitlistService.notifyAll(slotId);
            if (slot.belongsToEvent()) {
                eventWaitlistService.notifyAll(slot.getEvent().getId());
            }
        }

        // A slot that is over — before AND after the edit — must not mail its participants.
        boolean isNews = EditNotificationPolicy.slotEditIsNews(
            LocalDateTime.of(oldDate, oldEnd),
            LocalDateTime.of(slot.getDate(), slot.getEndTime()),
            LocalDateTime.now(WARSAW));
        boolean shouldNotify = isNews && !Boolean.FALSE.equals(request.sendNotifications());
        int notifiedCount = 0;
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
                // JOIN FETCH variant, as in blockTimeSlot: the mail is @Async and touches the user
                // on the mail thread, where a lazy proxy has no session left to initialise.
                List<Reservation> confirmed = reservationRepository.findConfirmedByTimeSlotIds(List.of(slot.getId()));
                String displayTitle = slot.getDisplayTitle();
                for (Reservation reservation : confirmed) {
                    // Same predicate MailService applies before sending — asking it here is what
                    // keeps the number reported to the admin equal to the number of mails sent.
                    if (!reservation.getUser().isEmailNotificationsEnabled()) continue;
                    mailService.sendAdminSlotModificationNotification(reservation.getUser(), slot, changes, displayTitle);
                    notifiedCount++;
                }
            }
        }

        // Registered bookings only — guests hold seats but have no account to write to, so for
        // "was there anyone to notify" they do not count. Fetched once and handed to the DTO
        // factory, which would otherwise run the same count again.
        int confirmedSeats = reservationRepository.countConfirmedByTimeSlotId(slotId);
        int guestSeats = guestReservationRepository.sumParticipantsByTimeSlotId(slotId);
        return new SlotUpdateResultDto(
            toTimeSlotAdminDto(slot, confirmedSeats + guestSeats), notifiedCount, confirmedSeats > 0);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void blockTimeSlot(UUID adminId, UUID slotId, @Nullable String reason) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        // Blocking an archived slot is tidying up, not a cancellation anyone needs to hear about.
        boolean slotIsOver = BookingTimeValidator.isPast(slot.getDate(), slot.getEndTime());
        // Multi-slot variant (has JOIN FETCH user), exactly as deleteTimeSlot does: the mail is
        // @Async and dereferences reservation.getUser() on the mail thread, where open-in-view=false
        // leaves no session to initialise a lazy proxy. The resulting LazyInitializationException is
        // swallowed by the async decorator, so the cancellation mails vanished without a trace.
        List<Reservation> confirmed = reservationRepository.findConfirmedByTimeSlotIds(List.of(slotId));
        for (Reservation reservation : confirmed) {
            reservation.getUser();     // force-load user proxy while still managed
            reservation.cancelByAdmin();
            reservationRepository.save(reservation);
            activityLogService.logCancelledByAdmin(reservation.getUser(), slot, reservation.getParticipants());
        }

        slot.block(reason);
        timeSlotRepository.save(slot);

        User admin = userRepository.findById(adminId).orElseThrow();
        activityLogService.logAdminSlotBlocked(admin, slot, reason);

        // Mail only after the DB writes succeed, mirroring deleteTimeSlot.
        if (!slotIsOver) {
            for (Reservation reservation : confirmed) {
                mailService.sendAdminCancellationNotification(reservation);
            }
        }
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void unblockTimeSlot(UUID adminId, UUID slotId) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        slot.unblock();
        timeSlotRepository.save(slot);

        User admin = userRepository.findById(adminId).orElseThrow();
        activityLogService.logAdminSlotUnblocked(admin, slot);

        // Blocking cancelled every reservation, so an unblocked slot is completely empty. Every
        // other seat-freeing operation offers those seats to the queue; without this the waitlist
        // sat idle on a wide-open slot until some unrelated cancellation happened to wake it.
        waitlistService.notifyAll(slotId);
        if (slot.belongsToEvent()) {
            eventWaitlistService.notifyAll(slot.getEvent().getId());
        }
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void deleteTimeSlot(UUID adminId, UUID slotId) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        // "Past" = slot fully ended (same definition as the archive query findPastOrdered):
        // a slot that already ended earlier *today* is archived, so deleting it must NOT notify.
        boolean isPast = BookingTimeValidator.isPast(slot.getDate(), slot.getEndTime());

        // Capture slot description before deletion (FK will be SET NULL after delete)
        var tf = DateTimeFormatter.ofPattern("HH:mm");
        var df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String slotDesc = slot.getDate().format(df) + " " + slot.getStartTime().format(tf) + "-" + slot.getEndTime().format(tf)
            + (slot.getDisplayTitle() != null ? " (" + slot.getDisplayTitle() + ")" : "");

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

        User admin = userRepository.findById(adminId).orElseThrow();
        activityLogService.logAdminSlotDeleted(admin, slotDesc);

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
        String displayTitle = slot.getDisplayTitle();
        int notified = 0;
        for (Reservation reservation : confirmed) {
            if (reservation.getUser().isEmailNotificationsEnabled()) {
                mailService.sendAdminSlotModificationNotification(reservation.getUser(), slot, changes, displayTitle);
                notified++;
            }
        }
        return notified;
    }

    @Transactional(readOnly = true)
    public List<TimeSlotAdminDto> getUpcomingSlots(LocalDate from) {
        // Container clock is UTC; slot dates/times are Warsaw local. A bare now() misclassifies
        // the whole current day between 00:00 and 02:00 Warsaw, and in summer keeps a slot that
        // ended at 22:00 looking "upcoming" until midnight UTC.
        LocalDate today = LocalDate.now(WARSAW);
        LocalTime now = LocalTime.now(WARSAW);
        LocalDate to = from.plusDays(90);
        List<TimeSlot> slots = timeSlotRepository.findByDateRangeOrdered(from, to).stream()
            .filter(slot -> !slot.belongsToEvent())
            .filter(slot -> slot.getDate().isAfter(today) || slot.getEndTime().isAfter(now))
            .toList();
        Map<UUID, Integer> countMap = buildCountMap(slots);
        return slots.stream()
            .map(slot -> toTimeSlotAdminDto(slot, countMap.getOrDefault(slot.getId(), 0)))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TimeSlotAdminDto> getPastSlots() {
        // Container clock is UTC; slot dates/times are Warsaw local. A bare now() misclassifies
        // the whole current day between 00:00 and 02:00 Warsaw, and in summer keeps a slot that
        // ended at 22:00 looking "upcoming" until midnight UTC.
        LocalDate today = LocalDate.now(WARSAW);
        LocalTime now = LocalTime.now(WARSAW);
        List<TimeSlot> slots = timeSlotRepository.findPastOrdered(today, now).stream()
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
            all.add(toWaitlistAdminEntryDto(w, null));
        }
        for (Waitlist w : entries) {
            all.add(toWaitlistAdminEntryDto(w, null));
        }

        return new SlotWaitlistDto(slotId, slot.getDate(), slot.getStartTime(), slot.getEndTime(), all);
    }

    private WaitlistAdminEntryDto toWaitlistAdminEntryDto(Waitlist w, @Nullable Instant newSince) {
        return new WaitlistAdminEntryDto(
            w.getId(),
            w.getUser().getId(),
            w.getUser().getFullName(),
            w.getUser().getEmail(),
            w.getUser().getPhone(),
            w.getPosition(),
            w.getStatus().name(),
            w.getConfirmationDeadline(),
            w.getCreatedAt(),
            newSince != null && w.getCreatedAt() != null && w.getCreatedAt().isAfter(newSince)
        );
    }

    /** Mirror of {@link #getSlotWaitlist} for events — people with an active offer (PENDING) first. */
    @Transactional(readOnly = true)
    public EventWaitlistAdminDto getEventWaitlist(UUID eventId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        List<EventWaitlist> pending = eventWaitlistRepository.findByEventIdAndStatusWithUser(eventId, WaitlistStatus.PENDING_CONFIRMATION);
        List<EventWaitlist> waiting = eventWaitlistRepository.findByEventIdAndStatusWithUser(eventId, WaitlistStatus.WAITING);

        List<WaitlistAdminEntryDto> all = new ArrayList<>();
        for (EventWaitlist w : pending) {
            all.add(toEventWaitlistAdminEntryDto(w, null));
        }
        for (EventWaitlist w : waiting) {
            all.add(toEventWaitlistAdminEntryDto(w, null));
        }

        return new EventWaitlistAdminDto(eventId, event.getTitle(), event.getStartDate(), event.getEndDate(), all);
    }

    private WaitlistAdminEntryDto toEventWaitlistAdminEntryDto(EventWaitlist w, @Nullable Instant newSince) {
        return new WaitlistAdminEntryDto(
            w.getId(),
            w.getUser().getId(),
            w.getUser().getFullName(),
            w.getUser().getEmail(),
            w.getUser().getPhone(),
            w.getPosition(),
            w.getStatus().name(),
            w.getConfirmationDeadline(),
            w.getCreatedAt(),
            newSince != null && w.getCreatedAt() != null && w.getCreatedAt().isAfter(newSince)
        );
    }

    /**
     * Global "Waitlists" view (Reservations tab): all upcoming slots/events someone is
     * currently waiting for. Entries for past dates are skipped (there is nothing left to
     * backfill, and WAITING entries on past slots can linger in the database).
     */
    @Transactional(readOnly = true)
    public AdminWaitlistsDto getAdminWaitlists(UUID adminId) {
        LocalDate today = LocalDate.now(WARSAW);
        Instant newSince = userRepository.findById(adminId).orElseThrow().getAdminReservationsSeenAt();

        Map<UUID, SlotWaitlistGroupDto> slotGroups = new LinkedHashMap<>();
        for (Waitlist w : waitlistRepository.findActiveForUpcomingSlots(today)) {
            TimeSlot slot = w.getTimeSlot();
            slotGroups.computeIfAbsent(slot.getId(), id -> new SlotWaitlistGroupDto(
                id, slot.getDate(), slot.getStartTime(), slot.getEndTime(), slot.getDisplayTitle(), new ArrayList<>()
            )).entries().add(toWaitlistAdminEntryDto(w, newSince));
        }

        Map<UUID, EventWaitlistAdminDto> eventGroups = new LinkedHashMap<>();
        for (EventWaitlist w : eventWaitlistRepository.findActiveForUpcomingEvents(today)) {
            Event event = w.getEvent();
            eventGroups.computeIfAbsent(event.getId(), id -> new EventWaitlistAdminDto(
                id, event.getTitle(), event.getStartDate(), event.getEndDate(), new ArrayList<>()
            )).entries().add(toEventWaitlistAdminEntryDto(w, newSince));
        }

        return new AdminWaitlistsDto(List.copyOf(slotGroups.values()), List.copyOf(eventGroups.values()));
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public EventAdminDto createEvent(UUID adminId, CreateEventRequest request) {
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

        if (request.invitedUserIds() != null) {
            syncEventInvites(event, request.invitedUserIds());
        }

        if (request.trainingRequestId() != null) {
            linkTrainingRequest(request.trainingRequestId(), null, event);
        }

        User admin = userRepository.findById(adminId).orElseThrow();
        activityLogService.logAdminEventCreated(admin, event);

        return toEventAdminDto(event);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public EventUpdateResultDto updateEvent(UUID adminId, UUID eventId, UpdateEventRequest request) {
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
            // Guests sit on the event, not its slots, so they are invisible to the per-slot counts.
            // Without them the floor lets the admin cut capacity below the people actually booked.
            int eventGuests = guestReservationRepository.sumParticipantsByEventId(eventId);
            if (!eventSlots.isEmpty() || eventGuests > 0) {
                List<UUID> slotIds = eventSlots.stream().map(TimeSlot::getId).toList();
                int maxConfirmed = (slotIds.isEmpty() ? 0
                    : reservationRepository.countConfirmedByTimeSlotIds(slotIds)
                        .stream().mapToInt(SlotParticipantCount::countAsInt).max().orElse(0))
                    + eventGuests;
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

        if (request.invitedUserIds() != null) {
            syncEventInvites(event, request.invitedUserIds());
        }

        User admin = userRepository.findById(adminId).orElseThrow();
        activityLogService.logAdminEventUpdated(admin, event);

        if (request.maxParticipants() != null && request.maxParticipants() > oldEventMaxParticipants) {
            eventWaitlistService.notifyAll(eventId);
            // People can queue on an individual day-slot of an event too. Every other seat-freeing
            // path here notifies both queues; this one used to notify only the event's.
            for (TimeSlot eventSlot : timeSlotRepository.findByEventId(eventId)) {
                waitlistService.notifyAll(eventSlot.getId());
            }
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
        // An event that is over — before AND after the edit — must not mail its participants.
        // Tidying up a past event's location (which the athlete stats group by) is bookkeeping,
        // not news; moving a finished event to a future date still is.
        boolean isNews = EditNotificationPolicy.eventEditIsNews(oldEndDate, event.getEndDate(), LocalDate.now(WARSAW));
        int notifiedCount = 0;
        if (!changes.isEmpty() && isNews) {
            List<TimeSlot> slots = timeSlotRepository.findByEventId(eventId);
            if (!slots.isEmpty()) {
                List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
                List<Reservation> confirmed = reservationRepository.findConfirmedByTimeSlotIds(slotIds);
                Map<UUID, User> notifiedUsers = new LinkedHashMap<>();
                for (Reservation reservation : confirmed) {
                    notifiedUsers.putIfAbsent(reservation.getUser().getId(), reservation.getUser());
                }
                for (User user : notifiedUsers.values()) {
                    // See the slot twin: counting only who MailService will actually write to.
                    if (!user.isEmailNotificationsEnabled()) continue;
                    mailService.sendAdminEventModificationNotification(user, event, changes);
                    notifiedCount++;
                }
            }
        }

        // Registered bookings across the event's day-slots — see the slot twin for why guests
        // are left out of this particular question.
        boolean hadParticipants = !reservationRepository.findConfirmedUserIdsByEventId(eventId).isEmpty();
        return new EventUpdateResultDto(toEventAdminDto(event), notifiedCount, hadParticipants);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void deleteEvent(UUID adminId, UUID eventId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        // An event ending TODAY is not over yet — cancelling it is exactly the mail people need.
        boolean isPast = BookingTimeValidator.dayHasPassed(event.getEndDate());

        // Capture event description before deletion (FK will be SET NULL after delete)
        var df2 = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String eventDesc = event.getTitle() + " (" + event.getStartDate().format(df2) + " – " + event.getEndDate().format(df2) + ")";

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

        User admin = userRepository.findById(adminId).orElseThrow();
        activityLogService.logAdminEventDeleted(admin, eventDesc);

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

    /**
     * Upcoming reservations for the panel, each row flagged {@code isNew} when created since
     * this admin's previous read (the reservations the badge was alerting about). The read
     * marker is NOT advanced here — the client resets it via {@link #markReservationsSeen}
     * once both this list and the waitlists view have loaded, so both can be flagged against
     * the same pre-visit marker without racing over it.
     */
    @Transactional(readOnly = true)
    public List<ReservationAdminDto> getAllUpcomingReservations(UUID adminId) {
        User admin = userRepository.findById(adminId).orElseThrow();
        Instant newSince = admin.getAdminReservationsSeenAt();
        LocalDate today = LocalDate.now(WARSAW);
        List<TimeSlot> slots = timeSlotRepository.findByDateRangeOrdered(today, today.plusYears(1));
        return buildReservationAdminDtos(slots, newSince);
    }

    @Transactional(readOnly = true)
    public List<ReservationAdminDto> getAllPastReservations() {
        List<TimeSlot> slots = timeSlotRepository.findPastOrdered(LocalDate.now(WARSAW), LocalTime.now(WARSAW));
        return buildReservationAdminDtos(slots, null);
    }

    @Transactional(readOnly = true)
    public List<ReservationAdminDto> getReservationsByDate(LocalDate date) {
        List<TimeSlot> slots = timeSlotRepository.findByDateSorted(date);
        return buildReservationAdminDtos(slots, null);
    }

    /**
     * @param newSince marker for the "new" flag: reservations created after it (and not
     *                 created by an admin) are flagged {@code isNew}; pass {@code null} to
     *                 flag nothing (past / by-date listings).
     */
    private List<ReservationAdminDto> buildReservationAdminDtos(List<TimeSlot> slots, @Nullable Instant newSince) {
        if (slots.isEmpty()) return List.of();

        List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
        List<Reservation> allReservations = reservationRepository.findConfirmedByTimeSlotIds(slotIds);

        Map<UUID, TimeSlot> slotMap = slots.stream()
            .collect(Collectors.toMap(TimeSlot::getId, s -> s));

        return allReservations.stream()
            .map(r -> {
                TimeSlot slot = slotMap.get(r.getTimeSlot().getId());
                Event event = slot.belongsToEvent() ? slot.getEvent() : null;
                boolean isNew = newSince != null
                    && !r.isCreatedByAdmin()
                    && r.getCreatedAt() != null
                    && r.getCreatedAt().isAfter(newSince);
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
                    event != null ? event.getEndDate() : null,
                    event != null ? event.getId() : null,
                    isNew
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
                u.isNewsletterSubscribed(),
                u.isAthlete()
            ))
            .toList();
    }

    public void makeAdmin(UUID adminId, UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setRole(UserRole.ADMIN);
        userRepository.save(user);
        jwtAuthenticationFilter.evictUser(userId);

        User admin = userRepository.findById(adminId).orElseThrow();
        activityLogService.logAdminUserMakeAdmin(admin, user.getFullName() + " (" + user.getEmail() + ")");
    }

    public void removeAdmin(UUID adminId, UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.isAdmin()) {
            throw new IllegalStateException(msg.get("admin.user.not.admin"));
        }

        user.setRole(UserRole.USER);
        userRepository.save(user);
        jwtAuthenticationFilter.evictUser(userId);

        User admin = userRepository.findById(adminId).orElseThrow();
        activityLogService.logAdminUserAdminRemoved(admin, user.getFullName() + " (" + user.getEmail() + ")");
    }

    /** Toggles the coach-designated athlete flag (personal training calendar access). Data is kept on un-flag. */
    public void setAthlete(UUID adminId, UUID userId, boolean isAthlete) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setAthlete(isAthlete);
        userRepository.save(user);

        User admin = userRepository.findById(adminId).orElseThrow();
        activityLogService.logAdminUserAthleteToggled(admin,
            user.getFullName() + " (" + user.getEmail() + ") -> " + (isAthlete ? "athlete" : "not athlete"));
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
        // One background campaign task (sequential, isolated executor) instead of fanning out
        // N async tasks onto the transactional mail queue — see MailService#sendBulk.
        mailService.sendBulk(recipients, request.subject(), request.body(), isNewsletter);

        return recipients.size();
    }

    /**
     * Forces the user to be logged out of all devices (e.g. compromised account).
     * Deletes all refresh tokens — access tokens (15 min) expire on their own.
     */
    public void forceLogout(UUID adminId, UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        authTokenRepository.deleteByUserIdAndTokenType(userId, TokenType.REFRESH_TOKEN);
        jwtAuthenticationFilter.evictUser(userId);

        User admin = userRepository.findById(adminId).orElseThrow();
        activityLogService.logAdminUserForceLogout(admin, user.getFullName() + " (" + user.getEmail() + ")");
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void deleteUser(UUID adminId, UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.isAdmin()) {
            throw new IllegalStateException(msg.get("admin.user.cannot.delete.admin"));
        }

        String userDesc = user.getFullName() + " (" + user.getEmail() + ")";

        authMailService.sendAccountDeletedByAdminNotification(user);
        // Shared with UserService.deleteAccount: cancelling the reservations is only half the job —
        // the freed seats have to be offered to whoever is queued for them, and the user has to come
        // off the queues they were waiting on. This path used to do neither.
        userSeatReleaseService.releaseSeatsAndNotifyWaitlists(userId);
        authTokenRepository.deleteAllByUserId(userId);
        userRepository.delete(user);
        jwtAuthenticationFilter.evictUser(userId);

        User admin = userRepository.findById(adminId).orElseThrow();
        activityLogService.logAdminUserDeleted(admin, userDesc);
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
            slot.isAvailabilityWindow(),
            slot.isUnavailable()
        );
    }

    /**
     * Occupancy per slot, guests included — same definition as {@code CalendarService.buildCountMap}
     * and as the single-slot {@code toTimeSlotAdminDto}. Counting only confirmed reservations here
     * made the slot LIST disagree with the slot DETAIL for any slot with walk-ins ("3/6" vs "5/6").
     */
    private Map<UUID, Integer> buildCountMap(List<TimeSlot> slots) {
        if (slots.isEmpty()) return Map.of();
        List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
        Map<UUID, Integer> counts = new HashMap<>(reservationRepository.countConfirmedByTimeSlotIds(slotIds).stream()
            .collect(Collectors.toMap(SlotParticipantCount::slotId, SlotParticipantCount::countAsInt)));
        for (SlotParticipantCount guests : guestReservationRepository.sumParticipantsByTimeSlotIds(slotIds)) {
            counts.merge(guests.slotId(), guests.countAsInt(), Integer::sum);
        }
        return counts;
    }

    // ---- Invitations (held seats) ----

    @Transactional(readOnly = true)
    public List<InvitedUserDto> getSlotInvites(UUID slotId) {
        return reservedSeatRepository.findBySlotIdWithUser(slotId).stream()
            .map(rs -> new InvitedUserDto(rs.getUser().getId(), rs.getUser().getFullName(), rs.getUser().getEmail(), rs.getNotifiedAt()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<InvitedUserDto> getEventInvites(UUID eventId) {
        return reservedSeatRepository.findByEventIdWithUser(eventId).stream()
            .map(rs -> new InvitedUserDto(rs.getUser().getId(), rs.getUser().getFullName(), rs.getUser().getEmail(), rs.getNotifiedAt()))
            .toList();
    }

    /**
     * Manual sending of invitation emails to people with a held seat in the slot.
     * Sends only to "pending" invitations (recipient has no confirmed reservation yet) —
     * whoever already booked got a regular confirmation. {@code onlyUnnotified} skips people
     * already invited (re-send after adding new invitees without spamming the rest).
     */
    public int notifySlotInvites(UUID slotId, boolean onlyUnnotified) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));
        String displayTitle = slot.getDisplayTitle();
        int sent = 0;
        for (ReservedSeat rs : reservedSeatRepository.findBySlotIdWithUser(slotId)) {
            if (onlyUnnotified && rs.getNotifiedAt() != null) continue;
            if (reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(
                    rs.getUser().getId(), slotId, ReservationStatus.CONFIRMED)) continue;
            mailService.sendSlotInvitationNotification(rs.getUser(), slot, displayTitle);
            rs.markNotified();
            sent++;
        }
        return sent;
    }

    /** Like {@link #notifySlotInvites}, but for event invitations. */
    public int notifyEventInvites(UUID eventId, boolean onlyUnnotified) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        // Hoisted out of the loop: this used to run a full JOIN FETCH over every confirmed
        // reservation of the event once per invitee — 20 invitees meant 20 identical queries on
        // one button press. Same projection syncEventInvites already uses.
        Set<UUID> confirmedUserIds = new HashSet<>(reservationRepository.findConfirmedUserIdsByEventId(eventId));
        int sent = 0;
        for (ReservedSeat rs : reservedSeatRepository.findByEventIdWithUser(eventId)) {
            if (onlyUnnotified && rs.getNotifiedAt() != null) continue;
            if (confirmedUserIds.contains(rs.getUser().getId())) continue;
            mailService.sendEventInvitationNotification(rs.getUser(), event);
            rs.markNotified();
            sent++;
        }
        return sent;
    }

    // ---- Admin panel notifications ----

    /**
     * Badge counters: pending training requests (inherent state — PENDING) plus new
     * reservations and waitlist joins since last "read" (per-admin marker
     * {@code adminReservationsSeenAt}, reset by entering the Reservations tab via
     * {@link #markReservationsSeen}).
     */
    @Transactional(readOnly = true)
    public AdminNotificationsDto getNotifications(UUID adminId) {
        User admin = userRepository.findById(adminId).orElseThrow();
        int pendingRequests = trainingRequestRepository.countByStatus(TrainingRequestStatus.PENDING);
        int newReservations = reservationRepository.countConfirmedCreatedAfter(admin.getAdminReservationsSeenAt());
        int newWaitlistEntries = waitlistRepository.countActiveCreatedAfter(admin.getAdminReservationsSeenAt())
            + eventWaitlistRepository.countActiveCreatedAfter(admin.getAdminReservationsSeenAt());
        long athleteActivity = trainingCalendarService.getTotalAthleteActivity(adminId);
        return new AdminNotificationsDto(pendingRequests, newReservations, newWaitlistEntries, athleteActivity);
    }

    public void markReservationsSeen(UUID adminId) {
        User admin = userRepository.findById(adminId).orElseThrow();
        admin.markAdminReservationsSeen();
    }

    /**
     * Links a freshly created slot/event with a training request (status ACCEPTED + link).
     * Deliberately tolerant of a missing request (it may have been withdrawn by the user
     * in the meantime) — creating the slot/event matters more than updating the request.
     */
    private void linkTrainingRequest(UUID trainingRequestId, @Nullable TimeSlot slot, @Nullable Event event) {
        trainingRequestRepository.findById(trainingRequestId).ifPresent(tr -> {
            tr.setCreatedSlot(slot);
            tr.setCreatedEvent(event);
            tr.resolve(TrainingRequestStatus.ACCEPTED);
        });
    }

    /** Sets exactly the given set of invitees for the slot (diff: adds/removes). */
    private void syncSlotInvites(TimeSlot slot, List<UUID> desiredUserIds) {
        Set<UUID> desired = new LinkedHashSet<>(desiredUserIds);
        int confirmed = reservationRepository.countConfirmedByTimeSlotId(slot.getId())
            + guestReservationRepository.sumParticipantsByTimeSlotId(slot.getId());
        // An invitee who already booked is counted in `confirmed` — only pending
        // invitations count towards the limit, otherwise a full slot with a used
        // invitation would reject every edit (double-counting the same person).
        Set<UUID> confirmedUserIds = new HashSet<>(reservationRepository.findConfirmedUserIdsByTimeSlotId(slot.getId()));
        long pendingDesired = desired.stream().filter(id -> !confirmedUserIds.contains(id)).count();
        if (pendingDesired + confirmed > slot.getMaxParticipants()) {
            throw new IllegalStateException(msg.get("admin.invites.too.many", String.valueOf(slot.getMaxParticipants())));
        }

        Set<UUID> existingUserIds = new HashSet<>();
        boolean removedAny = false;
        for (ReservedSeat rs : reservedSeatRepository.findBySlotIdWithUser(slot.getId())) {
            UUID uid = rs.getUser().getId();
            existingUserIds.add(uid);
            if (!desired.contains(uid)) {
                reservedSeatRepository.delete(rs);
                removedAny = true;
            }
        }
        for (UUID uid : desired) {
            if (!existingUserIds.contains(uid)) {
                User user = userRepository.findById(uid)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + uid));
                reservedSeatRepository.save(new ReservedSeat(slot, user));
            }
        }
        // Removing an invitation frees the seat for everyone — notify the waitlist.
        if (removedAny) {
            waitlistService.notifyAll(slot.getId());
            if (slot.belongsToEvent()) {
                eventWaitlistService.notifyAll(slot.getEvent().getId());
            }
        }
    }

    /** Sets exactly the given set of invitees for the event (diff: adds/removes). */
    private void syncEventInvites(Event event, List<UUID> desiredUserIds) {
        Set<UUID> desired = new LinkedHashSet<>(desiredUserIds);
        List<TimeSlot> eventSlots = timeSlotRepository.findByEventId(event.getId());
        // Event guests occupy seats the per-slot counts cannot see — they must count against the
        // invitation limit too, or invites can be handed out for seats walk-ins already took.
        int maxConfirmed = guestReservationRepository.sumParticipantsByEventId(event.getId());
        if (!eventSlots.isEmpty()) {
            List<UUID> slotIds = eventSlots.stream().map(TimeSlot::getId).toList();
            maxConfirmed += reservationRepository.countConfirmedByTimeSlotIds(slotIds)
                .stream().mapToInt(SlotParticipantCount::countAsInt).max().orElse(0);
        }
        // As in syncSlotInvites: an invitee with a confirmed event reservation is already
        // counted in maxConfirmed — only pending invitations count towards the limit.
        Set<UUID> confirmedUserIds = new HashSet<>(reservationRepository.findConfirmedUserIdsByEventId(event.getId()));
        long pendingDesired = desired.stream().filter(id -> !confirmedUserIds.contains(id)).count();
        if (pendingDesired + maxConfirmed > event.getMaxParticipants()) {
            throw new IllegalStateException(msg.get("admin.invites.too.many", String.valueOf(event.getMaxParticipants())));
        }

        Set<UUID> existingUserIds = new HashSet<>();
        boolean removedAny = false;
        for (ReservedSeat rs : reservedSeatRepository.findByEventIdWithUser(event.getId())) {
            UUID uid = rs.getUser().getId();
            existingUserIds.add(uid);
            if (!desired.contains(uid)) {
                reservedSeatRepository.delete(rs);
                removedAny = true;
            }
        }
        for (UUID uid : desired) {
            if (!existingUserIds.contains(uid)) {
                User user = userRepository.findById(uid)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + uid));
                reservedSeatRepository.save(new ReservedSeat(event, user));
            }
        }
        if (removedAny) {
            eventWaitlistService.notifyAll(event.getId());
        }
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
        // Over = the slot has ENDED. Keyed off the start time, a cancellation sent while the
        // session was still running went silent — to someone who may well be on their way to it.
        if (!BookingTimeValidator.isPast(slot.getDate(), slot.getEndTime())) {
            mailService.sendAdminCancellationNotification(reservation);
        }
        activityLogService.logCancelledByAdmin(user, slot, participants);
        waitlistService.notifyAll(slot.getId());
        if (slot.belongsToEvent()) {
            eventWaitlistService.notifyAll(slot.getEvent().getId());
        }
    }

    /**
     * Permanently (hard) deletes a single archived reservation — archive cleanup.
     * Unlike {@link #cancelReservationByAdmin} it does NOT notify the user or the waitlist
     * and does not log a cancellation — this is tidying up past entries, not cancelling.
     * Allowed only for past slots (the UI exposes the action only in the archive).
     */
    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void deleteReservationPermanently(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
        TimeSlot slot = reservation.getTimeSlot();
        if (!BookingTimeValidator.isPast(slot.getDate(), slot.getEndTime())) {
            throw new IllegalStateException("Only past reservations can be permanently deleted");
        }
        reservationRepository.delete(reservation);
    }

    /**
     * Permanently deletes all reservations of an archived event (group archive cleanup).
     * The event itself remains — only registrations on its slots are deleted. Allowed only
     * once the event has finished.
     */
    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void deletePastEventReservations(UUID eventId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        if (!BookingTimeValidator.dayHasPassed(event.getEndDate())) {
            throw new IllegalStateException("Only past events can have their reservations permanently deleted");
        }
        List<TimeSlot> slots = timeSlotRepository.findByEventId(eventId);
        if (slots.isEmpty()) return;
        List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
        reservationRepository.deleteByTimeSlotIds(slotIds);
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
        if (!BookingTimeValidator.dayHasPassed(event.getEndDate())) {
            mailService.sendAdminEventParticipantRemovedNotification(user, event);
        }
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
        if (!BookingTimeValidator.isPast(slot.getDate(), slot.getEndTime())) {
            mailService.sendAdminParticipantReductionNotification(user, slot, oldParticipants, newParticipants);
        }
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
        // Locked like the slot twin — a raise read against an unlocked capacity can overshoot.
        Event event = eventRepository.findByIdForUpdate(eventId)
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
        // Event guests are outside the per-slot counts — see createEventReservation.
        int currentMaxTotal = countMap.values().stream().mapToInt(Integer::intValue).max().orElse(0)
            + guestReservationRepository.sumParticipantsByEventId(eventId);
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
        if (!BookingTimeValidator.dayHasPassed(event.getEndDate())) {
            mailService.sendAdminEventParticipantReductionNotification(user, event, oldParticipants, newParticipants);
        }
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
            reservation.setCreatedByAdmin(true);
            reservation = reservationRepository.save(reservation);
        }

        String displayTitle = slot.getDisplayTitle();
        if (!BookingTimeValidator.isPast(slot.getDate(), slot.getEndTime())) {
            mailService.sendReservationConfirmation(reservation, displayTitle);
            mailService.sendAdminNotification(reservation, displayTitle);
        }
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

        List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();

        // Find existing confirmed reservations for this user on this event
        List<Reservation> existingUserReservations = reservationRepository.findConfirmedByTimeSlotIds(slotIds)
            .stream().filter(r -> r.getUser().getId().equals(user.getId())).toList();

        // Check capacity — use max across slots (event-level logic)
        int guestCount = guestReservationRepository.sumParticipantsByEventId(eventId);
        Map<UUID, Integer> countMap = reservationRepository.countConfirmedByTimeSlotIds(slotIds).stream()
            .collect(Collectors.toMap(SlotParticipantCount::slotId, SlotParticipantCount::countAsInt));
        int maxConfirmed = countMap.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int available = event.getMaxParticipants() - maxConfirmed - guestCount;
        if (request.participants() > available) {
            throw new IllegalStateException(msg.get("reservation.spots.available", available, request.participants()));
        }

        if (!existingUserReservations.isEmpty()) {
            // User already has a reservation — add participants to the existing one
            int currentUserParticipants = existingUserReservations.getFirst().getParticipants();
            int newTotal = currentUserParticipants + request.participants();
            String sanitizedComment = request.comment() != null
                ? Reservation.sanitizeComment(request.comment())
                : existingUserReservations.getFirst().getComment();
            for (Reservation reservation : existingUserReservations) {
                reservation.setParticipants(newTotal);
                if (request.comment() != null) reservation.setComment(sanitizedComment);
                reservationRepository.save(reservation);
            }
            if (!BookingTimeValidator.dayHasPassed(event.getEndDate())) {
                mailService.sendEventReservationUpdateConfirmation(user, event, currentUserParticipants, newTotal);
                mailService.sendEventAdminNotification(user, event, newTotal, sanitizedComment);
            }
            activityLogService.logEventReservationUpdated(user, event, newTotal);
        } else {
            // No existing reservation — create new reservations on all slots
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
                    reservation.setCreatedByAdmin(true);
                    reservationRepository.save(reservation);
                }
            }
            if (!BookingTimeValidator.dayHasPassed(event.getEndDate())) {
                mailService.sendEventReservationConfirmation(user, event, request.participants());
                mailService.sendEventAdminNotification(user, event, request.participants(), sanitizedComment);
            }
            activityLogService.logEventReservationCreated(user, event, request.participants());
        }
    }

    /* No @CacheEvict here: this is private, so proxy-based AOP never sees the call.
     * Its only caller (addRegisteredParticipantToEvent) evicts for it. */
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

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
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
