package pl.nextsteppro.climbing.api.admin.reschedule;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.api.admin.AdminService;
import pl.nextsteppro.climbing.api.reschedule.OtherParticipants;
import pl.nextsteppro.climbing.api.reschedule.RescheduleService;
import pl.nextsteppro.climbing.domain.BookingTimeValidator;
import pl.nextsteppro.climbing.domain.StoredText;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.reschedule.RescheduleRequest;
import pl.nextsteppro.climbing.domain.reschedule.RescheduleRequestRepository;
import pl.nextsteppro.climbing.domain.reschedule.RescheduleRequestStatus;
import pl.nextsteppro.climbing.domain.reschedule.RescheduleTerm;
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
import java.util.Objects;
import java.util.UUID;

/**
 * The coach's answer to a client's request to move a booked term.
 *
 * <p>Accepting does not move anything itself: it hands the new term to the same
 * {@code AdminService} path an edit in the panel takes, so invitations, the event's day-slots,
 * the calendar cache and everyone else's "term changed" mail all behave exactly as they would if
 * the coach had typed the new hours by hand.
 */
@Service
@Transactional
public class AdminRescheduleService {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final int RECENT_LIMIT = 15;

    private final RescheduleRequestRepository requestRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final AdminService adminService;
    private final OtherParticipants otherParticipants;
    private final RescheduleMailService mailService;
    private final ActivityLogService activityLogService;
    private final MessageService msg;

    public AdminRescheduleService(RescheduleRequestRepository requestRepository,
                                  ReservationRepository reservationRepository,
                                  UserRepository userRepository,
                                  AdminService adminService,
                                  OtherParticipants otherParticipants,
                                  RescheduleMailService mailService,
                                  ActivityLogService activityLogService,
                                  MessageService msg) {
        this.requestRepository = requestRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.adminService = adminService;
        this.otherParticipants = otherParticipants;
        this.mailService = mailService;
        this.activityLogService = activityLogService;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public AdminRescheduleListDto list() {
        List<AdminRescheduleRequestDto> pending = requestRepository.findAllPendingWithDetails().stream()
            .map(r -> toDto(r, true))
            .toList();
        List<AdminRescheduleRequestDto> recent = requestRepository
            .findRecentResolvedWithDetails(PageRequest.of(0, RECENT_LIMIT)).stream()
            .map(r -> toDto(r, false))
            .toList();
        return new AdminRescheduleListDto(pending, recent);
    }

    public AcceptRescheduleResultDto accept(UUID adminId, UUID requestId) {
        RescheduleRequest r = requirePending(requestId);
        User requester = r.getUser();
        TimeSlot slot = r.getTimeSlot();
        Event event = r.getEvent();

        if (!stillBooked(r)) {
            throw new IllegalStateException(msg.get("reschedule.requester.gone"));
        }
        // The coach moved the term after the client asked. "From Tuesday to Thursday" now describes
        // a Tuesday that is not there; moving again on top of that would be a second, silent move.
        if (!r.termStillMatches()) {
            throw new IllegalStateException(msg.get("reschedule.stale"));
        }
        RescheduleTerm oldTerm = r.oldTerm();
        RescheduleTerm newTerm = r.newTerm();
        if (termIsOver(oldTerm)) {
            throw new IllegalStateException(msg.get("reschedule.term.over"));
        }
        if (startsInPast(newTerm)) {
            throw new IllegalStateException(msg.get("reschedule.past"));
        }

        // Everything the mail and the log need is read NOW: the event path re-dates its day-slots
        // with bulk deletes that clear the persistence context, detaching what is loaded here.
        String title = slot != null ? RescheduleService.titleOf(slot) : Objects.requireNonNull(event).getTitle();
        @Nullable String location = event != null ? event.getLocation() : null;
        User mailUser = AsyncMailEntities.loaded(requester);
        User admin = userRepository.findById(adminId).orElseThrow();
        String change = RescheduleService.change(r);
        AdminRescheduleRequestDto before = toDto(r, true);

        r.resolve(RescheduleRequestStatus.ACCEPTED, null);
        activityLogService.logAdminRescheduleAccepted(admin, requester, slot, event, change);

        int notifiedOthers;
        if (slot != null) {
            notifiedOthers = adminService.rescheduleSlot(adminId, slot.getId(),
                newTerm.date(), Objects.requireNonNull(newTerm.startTime()), Objects.requireNonNull(newTerm.endTime()),
                requester.getId());
        } else {
            Event target = Objects.requireNonNull(event);
            notifiedOthers = adminService.rescheduleEvent(adminId, target.getId(),
                newTerm.date(), Objects.requireNonNull(newTerm.endDate()), newTerm.startTime(), newTerm.endTime(),
                requester.getId());
        }

        // Last, so a move refused above sends nothing.
        mailService.sendAccepted(mailUser, title, location, oldTerm, newTerm);

        return new AcceptRescheduleResultDto(withStatus(before, RescheduleRequestStatus.ACCEPTED, null), notifiedOthers);
    }

    public AdminRescheduleRequestDto reject(UUID adminId, UUID requestId, RejectRescheduleRequest request) {
        RescheduleRequest r = requirePending(requestId);
        String note = StoredText.trimAndCap(request.adminNote(), RescheduleRequest.MAX_ADMIN_NOTE_LENGTH);
        r.resolve(RescheduleRequestStatus.REJECTED, note);

        User admin = userRepository.findById(adminId).orElseThrow();
        activityLogService.logAdminRescheduleRejected(admin, r.getUser(), r.getTimeSlot(), r.getEvent(),
            RescheduleService.change(r));

        if (request.notifyUser()) {
            TimeSlot slot = r.getTimeSlot();
            String title = slot != null ? RescheduleService.titleOf(slot) : Objects.requireNonNull(r.getEvent()).getTitle();
            mailService.sendRejected(AsyncMailEntities.loaded(r.getUser()), title, r.oldTerm(), r.newTerm(), note);
        }
        return toDto(r, false);
    }

    private RescheduleRequest requirePending(UUID requestId) {
        RescheduleRequest r = requestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("reschedule.not.found")));
        if (!r.isPending()) {
            throw new IllegalStateException(msg.get("reschedule.not.pending"));
        }
        return r;
    }

    private boolean stillBooked(RescheduleRequest r) {
        UUID userId = r.getUser().getId();
        TimeSlot slot = r.getTimeSlot();
        if (slot != null) {
            return reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slot.getId(), ReservationStatus.CONFIRMED);
        }
        return reservationRepository.findConfirmedUserIdsByEventId(Objects.requireNonNull(r.getEvent()).getId()).contains(userId);
    }

    private static boolean termIsOver(RescheduleTerm term) {
        if (term.endDate() != null) {
            return BookingTimeValidator.dayHasPassed(term.endDate());
        }
        return BookingTimeValidator.isPast(term.date(), Objects.requireNonNull(term.endTime()));
    }

    private static boolean startsInPast(RescheduleTerm term) {
        LocalTime start = term.startTime();
        return start != null
            ? BookingTimeValidator.isPast(term.date(), start)
            : term.date().isBefore(LocalDate.now(WARSAW));
    }

    private AdminRescheduleRequestDto toDto(RescheduleRequest r, boolean withOthers) {
        User u = r.getUser();
        TimeSlot slot = r.getTimeSlot();
        Event event = r.getEvent();
        OtherParticipants.Others others = !withOthers
            ? new OtherParticipants.Others(List.of(), 0)
            : slot != null ? otherParticipants.onSlot(slot, u.getId()) : otherParticipants.onEvent(Objects.requireNonNull(event), u.getId());
        RescheduleTerm oldTerm = r.oldTerm();
        RescheduleTerm newTerm = r.newTerm();
        return new AdminRescheduleRequestDto(
            r.getId(),
            u.getId(),
            u.getFullName(),
            u.getEmail(),
            u.getPhone(),
            slot != null ? "SLOT" : "EVENT",
            slot != null ? slot.getId() : null,
            event != null ? event.getId() : null,
            slot != null ? RescheduleService.titleOf(slot) : Objects.requireNonNull(event).getTitle(),
            oldTerm.date(), oldTerm.endDate(), oldTerm.startTime(), oldTerm.endTime(),
            newTerm.date(), newTerm.endDate(), newTerm.startTime(), newTerm.endTime(),
            r.getComment(),
            r.getStatus().name(),
            r.getAdminNote(),
            r.getCreatedAt(),
            r.getResolvedAt(),
            others.names(),
            others.guests(),
            r.isPending() && !r.termStillMatches()
        );
    }

    private static AdminRescheduleRequestDto withStatus(AdminRescheduleRequestDto d, RescheduleRequestStatus status,
                                                        @Nullable String note) {
        return new AdminRescheduleRequestDto(d.id(), d.userId(), d.userFullName(), d.userEmail(), d.userPhone(),
            d.targetType(), d.timeSlotId(), d.eventId(), d.title(),
            d.oldDate(), d.oldEndDate(), d.oldStartTime(), d.oldEndTime(),
            d.newDate(), d.newEndDate(), d.newStartTime(), d.newEndTime(),
            d.comment(), status.name(), note, d.createdAt(), java.time.Instant.now(),
            d.otherParticipants(), d.otherGuests(), false);
    }
}
