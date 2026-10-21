package pl.nextsteppro.climbing.api.reschedule;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.domain.BookingTimeValidator;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventRepository;
import pl.nextsteppro.climbing.domain.reschedule.RescheduleRequest;
import pl.nextsteppro.climbing.domain.reschedule.RescheduleRequestRepository;
import pl.nextsteppro.climbing.domain.reschedule.RescheduleRequestStatus;
import pl.nextsteppro.climbing.domain.reschedule.RescheduleTerm;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationStatus;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.mail.AsyncMailEntities;
import pl.nextsteppro.climbing.infrastructure.mail.RescheduleMailService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * The client's half: ask to move a booked term, see what is waiting, take it back.
 *
 * <p>Nothing here touches the booking itself — it stands unchanged until the coach accepts, and
 * the coach's answer lives in {@code AdminRescheduleService}.
 */
@Service
@Transactional
public class RescheduleService {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final RescheduleRequestRepository requestRepository;
    private final ReservationRepository reservationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final OtherParticipants otherParticipants;
    private final RescheduleMailService mailService;
    private final ActivityLogService activityLogService;
    private final MessageService msg;

    public RescheduleService(RescheduleRequestRepository requestRepository,
                             ReservationRepository reservationRepository,
                             EventRepository eventRepository,
                             UserRepository userRepository,
                             OtherParticipants otherParticipants,
                             RescheduleMailService mailService,
                             ActivityLogService activityLogService,
                             MessageService msg) {
        this.requestRepository = requestRepository;
        this.reservationRepository = reservationRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.otherParticipants = otherParticipants;
        this.mailService = mailService;
        this.activityLogService = activityLogService;
        this.msg = msg;
    }

    public RescheduleResultDto create(UUID userId, CreateRescheduleRequest request) {
        if ((request.reservationId() == null) == (request.eventId() == null)) {
            throw new IllegalArgumentException(msg.get("reschedule.target.required"));
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("error.not.found.user")));

        RescheduleRequest created;
        String title;
        OtherParticipants.Others others;
        if (request.reservationId() != null) {
            TimeSlot slot = requireOwnSlot(request.reservationId(), userId);
            RescheduleTerm proposed = slotTerm(request);
            requireNew(RescheduleTerm.of(slot), proposed);
            if (requestRepository.findPendingForSlot(userId, slot.getId()).isPresent()) {
                throw new IllegalStateException(msg.get("reschedule.already.pending"));
            }
            created = RescheduleRequest.forSlot(user, slot, proposed);
            title = titleOf(slot);
            others = otherParticipants.onSlot(slot, userId);
        } else {
            Event event = requireOwnEvent(request.eventId(), userId);
            RescheduleTerm proposed = eventTerm(request, event);
            requireNew(RescheduleTerm.of(event), proposed);
            if (requestRepository.findPendingForEvent(userId, event.getId()).isPresent()) {
                throw new IllegalStateException(msg.get("reschedule.already.pending"));
            }
            created = RescheduleRequest.forEvent(user, event, proposed);
            title = event.getTitle();
            others = otherParticipants.onEvent(event, userId);
        }
        created.setComment(RescheduleRequest.sanitizeComment(request.comment()));
        created = requestRepository.save(created);

        activityLogService.logRescheduleRequested(user, created.getTimeSlot(), created.getEvent(), change(created));
        mailService.sendRequestToAdmins(AsyncMailEntities.loaded(user), title, created.oldTerm(), created.newTerm(),
            created.getComment(), others.names(), others.guests());

        return new RescheduleResultDto(created.getId(), msg.get("reschedule.created"));
    }

    @Transactional(readOnly = true)
    public List<MyRescheduleRequestDto> getMyPending(UUID userId) {
        return requestRepository.findPendingByUserId(userId).stream()
            .map(r -> {
                RescheduleTerm proposed = r.newTerm();
                TimeSlot slot = r.getTimeSlot();
                Event event = r.getEvent();
                return new MyRescheduleRequestDto(
                    r.getId(),
                    slot != null ? slot.getId() : null,
                    event != null ? event.getId() : null,
                    proposed.date(),
                    proposed.endDate(),
                    proposed.startTime(),
                    proposed.endTime(),
                    r.getComment(),
                    r.getCreatedAt());
            })
            .toList();
    }

    public void withdraw(UUID userId, UUID requestId) {
        // Locked like the coach's answer, so withdrawing cannot interleave with an accept.
        RescheduleRequest r = requestRepository.findByIdForUpdate(requestId)
            .filter(found -> found.getUser().getId().equals(userId))
            // Somebody else's id answers exactly like a missing one — nothing to enumerate.
            .orElseThrow(() -> new IllegalArgumentException(msg.get("reschedule.not.found")));
        if (!r.isPending()) {
            throw new IllegalStateException(msg.get("reschedule.not.pending"));
        }
        r.resolve(RescheduleRequestStatus.WITHDRAWN, null);
    }

    /** "old → new", dates only — what the activity log carries instead of a name. */
    public static String change(RescheduleRequest r) {
        return r.oldTerm().label() + " → " + r.newTerm().label();
    }

    public static String titleOf(TimeSlot slot) {
        String title = slot.getDisplayTitle();
        return title != null ? title : "Next Step Pro Climbing";
    }

    // ---- Guards ----

    private TimeSlot requireOwnSlot(UUID reservationId, UUID userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("reservation.not.found")));
        if (!reservation.getUser().getId().equals(userId)) {
            throw new IllegalStateException(msg.get("reschedule.not.yours"));
        }
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException(msg.get("reschedule.not.confirmed"));
        }
        TimeSlot slot = reservation.getTimeSlot();
        // One day of a course is the event's day-slot; the course moves as a whole or not at all.
        if (slot.belongsToEvent()) {
            throw new IllegalStateException(msg.get("reschedule.slot.belongs.to.event"));
        }
        if (BookingTimeValidator.isPast(slot.getDate(), slot.getEndTime())) {
            throw new IllegalStateException(msg.get("reschedule.term.over"));
        }
        return slot;
    }

    private Event requireOwnEvent(UUID eventId, UUID userId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("error.not.found.event")));
        if (!reservationRepository.findConfirmedUserIdsByEventId(eventId).contains(userId)) {
            throw new IllegalStateException(msg.get("reschedule.not.yours"));
        }
        if (BookingTimeValidator.dayHasPassed(event.getEndDate())) {
            throw new IllegalStateException(msg.get("reschedule.term.over"));
        }
        return event;
    }

    private RescheduleTerm slotTerm(CreateRescheduleRequest request) {
        LocalTime start = request.newStartTime();
        LocalTime end = request.newEndTime();
        if (start == null || end == null) {
            throw new IllegalArgumentException(msg.get("reschedule.times.required"));
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(msg.get("admin.slot.end.after.start"));
        }
        if (BookingTimeValidator.isPast(request.newDate(), start)) {
            throw new IllegalArgumentException(msg.get("reschedule.past"));
        }
        return new RescheduleTerm(request.newDate(), null, start, end);
    }

    /**
     * The shape {@code events} has: an end day always, hours both-or-neither — and the same
     * "has hours" as the event already has. A request may move the hours, not turn an all-day
     * course into a timed one: the edit path accepting it treats a null hour as "leave as is".
     */
    private RescheduleTerm eventTerm(CreateRescheduleRequest request, Event event) {
        LocalDate endDate = request.newEndDate() != null ? request.newEndDate() : request.newDate();
        if (endDate.isBefore(request.newDate())) {
            throw new IllegalArgumentException(msg.get("validation.event.date.range"));
        }
        @Nullable LocalTime start = request.newStartTime();
        @Nullable LocalTime end = request.newEndTime();
        if ((start == null) != (end == null)) {
            throw new IllegalArgumentException(msg.get("reschedule.times.both"));
        }
        boolean eventHasHours = event.getStartTime() != null && event.getEndTime() != null;
        if (eventHasHours && start == null) {
            throw new IllegalArgumentException(msg.get("reschedule.times.required"));
        }
        if (!eventHasHours) {
            start = null;
            end = null;
        }
        if (start != null && end != null && endDate.equals(request.newDate()) && !end.isAfter(start)) {
            throw new IllegalArgumentException(msg.get("validation.event.time.range"));
        }
        boolean past = start != null
            ? BookingTimeValidator.isPast(request.newDate(), start)
            : request.newDate().isBefore(LocalDate.now(WARSAW));
        if (past) {
            throw new IllegalArgumentException(msg.get("reschedule.past"));
        }
        return new RescheduleTerm(request.newDate(), endDate, start, end);
    }

    private void requireNew(RescheduleTerm current, RescheduleTerm proposed) {
        if (current.sameAs(proposed)) {
            throw new IllegalArgumentException(msg.get("reschedule.same.term"));
        }
    }
}
