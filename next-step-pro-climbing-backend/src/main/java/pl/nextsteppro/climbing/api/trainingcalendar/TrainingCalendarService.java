package pl.nextsteppro.climbing.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.domain.personaltraining.AthleteActivityCount;
import pl.nextsteppro.climbing.domain.personaltraining.AthleteLastActivity;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTraining;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCalendarRead;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCalendarReadRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingComment;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingDeletion;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingDeletionRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingKind;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationRpe;
import pl.nextsteppro.climbing.domain.reservation.ReservationRpeRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationStatus;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeat;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeatRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Core of the personal training calendar (TrainingPeaks-style): a shared plan between
 * an athlete (flagged user) and the coach (admin). Holds all business logic for both
 * viewers; {@link AdminTrainingCalendarService} is a thin wrapper adding activity logging.
 *
 * <p>No caching on purpose: every read is viewer-scoped (unread markers), so the existing
 * {@code condition="#userId == null"} cache pattern would never hit anyway.
 */
@Service
@Transactional
public class TrainingCalendarService {

    /** Range endpoint guard: a month view needs ~42 days; anything beyond 62 is a client bug. */
    static final int MAX_RANGE_DAYS = 62;

    // Slot times are stored as local Poland time while the container runs UTC —
    // "now" comparisons MUST use this zone (see CLAUDE.md gotcha).
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    // Deletion log only survives until read; prune anything older on the next write
    private static final Duration DELETION_LOG_RETENTION = Duration.ofDays(60);

    private final PersonalTrainingRepository trainingRepository;
    private final TrainingCommentRepository commentRepository;
    private final TrainingCalendarReadRepository readRepository;
    private final TrainingDeletionRepository deletionRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationRpeRepository reservationRpeRepository;
    private final ReservedSeatRepository reservedSeatRepository;
    private final UserRepository userRepository;
    private final AttachmentSupport attachments;
    private final MessageService msg;

    public TrainingCalendarService(PersonalTrainingRepository trainingRepository,
                                   TrainingCommentRepository commentRepository,
                                   TrainingCalendarReadRepository readRepository,
                                   TrainingDeletionRepository deletionRepository,
                                   ReservationRepository reservationRepository,
                                   ReservationRpeRepository reservationRpeRepository,
                                   ReservedSeatRepository reservedSeatRepository,
                                   UserRepository userRepository,
                                   AttachmentSupport attachments,
                                   MessageService msg) {
        this.trainingRepository = trainingRepository;
        this.commentRepository = commentRepository;
        this.readRepository = readRepository;
        this.deletionRepository = deletionRepository;
        this.reservationRepository = reservationRepository;
        this.reservationRpeRepository = reservationRpeRepository;
        this.reservedSeatRepository = reservedSeatRepository;
        this.userRepository = userRepository;
        this.attachments = attachments;
        this.msg = msg;
    }

    // ---------- athlete-facing (viewer = the athlete themself) ----------

    @Transactional(readOnly = true)
    public CalendarRangeDto getMyRange(UUID userId, LocalDate from, LocalDate to) {
        User athlete = requireAthlete(userId);
        return buildRange(athlete.getId(), userId, false, from, to);
    }

    public PersonalTrainingDto createMy(UUID userId, CreatePersonalTrainingRequest request) {
        User athlete = requireAthlete(userId);
        return toDtoWithAttachments(createTraining(athlete, false, request), false, nowWarsaw());
    }

    public PersonalTrainingDto updateMy(UUID userId, UUID trainingId, CreatePersonalTrainingRequest request) {
        requireAthlete(userId);
        PersonalTraining training = requireOwnTraining(trainingId, userId);
        applyUpdate(training, false, request);
        return toDtoWithAttachments(training, false, nowWarsaw());
    }

    public void deleteMy(UUID userId, UUID trainingId) {
        requireAthlete(userId);
        PersonalTraining training = requireOwnTraining(trainingId, userId);
        recordDeletionIfFuture(training, false);
        attachments.purgeTrainingAttachments(trainingId);
        trainingRepository.delete(training);
    }

    /**
     * Completion requires the training to have STARTED (Warsaw time): a session in progress
     * may be checked off (ended early) and retroactive logging is fine, but a future plan
     * cannot be marked done. No end-time restriction — athletes often log days later.
     */
    public PersonalTrainingDto complete(UUID userId, UUID trainingId, CompleteTrainingRequest request) {
        requireAthlete(userId);
        PersonalTraining training = requireOwnTraining(trainingId, userId);
        // Untimed ("all-day") training may be logged from the start of its day (00:00).
        if (trainingStart(training).isAfter(nowWarsaw())) {
            throw new IllegalStateException(msg.get("training.calendar.complete.future"));
        }
        // A task is ticked off, never rated: perceived effort is a question about a session, and an
        // answer here would land in the RPE averages, which read every rated entry there is. The
        // database enforces the same thing (V77), so this is the readable error before the 409.
        if (training.isTask()) {
            if (request.rpe() != null) {
                throw new IllegalArgumentException(msg.get("training.calendar.task.no.rpe"));
            }
        } else {
            // Defense in depth: @Min/@Max fire only via controller @Valid, and the kind-dependent
            // requirement cannot be expressed as a bean-validation annotation at all.
            if (request.rpe() == null) {
                throw new IllegalArgumentException(msg.get("training.calendar.rpe.required"));
            }
            if (request.rpe() < 1 || request.rpe() > 10) {
                throw new IllegalArgumentException(msg.get("training.calendar.rpe.invalid"));
            }
        }
        training.complete(
            PersonalTraining.sanitizeText(request.feedback(), PersonalTraining.MAX_FEEDBACK_LENGTH),
            request.rpe());
        return toDtoWithAttachments(training, false, nowWarsaw());
    }

    public PersonalTrainingDto uncomplete(UUID userId, UUID trainingId) {
        requireAthlete(userId);
        PersonalTraining training = requireOwnTraining(trainingId, userId);
        training.uncomplete();
        return toDtoWithAttachments(training, false, nowWarsaw());
    }

    /**
     * Rate an attended reservation (idempotent upsert). The booking must be the athlete's own,
     * CONFIRMED, and already over (same Warsaw past-predicate the stats use). A foreign reservation
     * yields the same not-found error as a missing one — no id probing.
     */
    public void rateReservation(UUID userId, UUID reservationId, RateReservationRequest request) {
        requireAthlete(userId);
        Reservation reservation = reservationRepository.findById(reservationId)
            .filter(r -> r.getUser().getId().equals(userId))
            .orElseThrow(() -> new IllegalArgumentException(msg.get("training.reservation.not.found")));
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException(msg.get("training.reservation.rpe.not.attended"));
        }
        TimeSlot slot = reservation.getTimeSlot();
        // Event reservations carry no time slot — they never surface a "rate" chip (the overlay is
        // slot-only), but the endpoint is reachable by id, so guard against the NPE with a clear 409.
        if (slot == null) {
            throw new IllegalStateException(msg.get("training.reservation.rpe.not.attended"));
        }
        LocalDate today = LocalDate.now(WARSAW);
        LocalTime now = LocalTime.now(WARSAW);
        boolean past = slot.getDate().isBefore(today)
            || (slot.getDate().equals(today) && !slot.getEndTime().isAfter(now));
        if (!past) {
            throw new IllegalStateException(msg.get("training.reservation.rpe.future"));
        }
        String note = ReservationRpe.sanitizeNote(request.note());
        // Single statement rather than read-then-save: a double-submitted rating raced on the
        // reservation_id unique index. Overwriting is the intended semantics — this is an upsert.
        reservationRpeRepository.upsertRating(reservationId, request.rpe(), note, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<TrainingCommentDto> getMyComments(UUID userId, UUID trainingId) {
        requireAthlete(userId);
        requireOwnTraining(trainingId, userId);
        return toCommentDtos(commentRepository.findThread(trainingId), userId);
    }

    public TrainingCommentDto addMyComment(UUID userId, UUID trainingId, String body) {
        User athlete = requireAthlete(userId);
        PersonalTraining training = requireOwnTraining(trainingId, userId);
        return addComment(training, athlete, false, body, userId);
    }

    @Transactional(readOnly = true)
    public TrainingNotificationsDto getAthleteNotifications(UUID userId) {
        requireAthleteIgnoringConsent(userId);
        Instant seen = seenAt(userId, userId);
        long count = trainingRepository.countCoachChangesSince(userId, seen)
            + commentRepository.countCoachCommentsSince(userId, seen)
            + deletionRepository.countAdminDeletionsSince(userId, seen);
        return new TrainingNotificationsDto(count);
    }

    public void markAthleteSeen(UUID userId) {
        requireAthleteIgnoringConsent(userId);
        upsertSeen(userId, userId);
    }

    // ---------- coach-facing (viewer = admin); called via AdminTrainingCalendarService ----------

    @Transactional(readOnly = true)
    public List<AthleteSummaryDto> getAthleteSummaries(UUID adminId) {
        List<User> athletes = userRepository.findAllByAthleteTrueOrderByFirstNameAscLastNameAsc();
        Map<UUID, Long> counts = mergeCounts(
            trainingRepository.countNewAthleteTrainingsPerAthlete(adminId),
            trainingRepository.countNewCompletionsPerAthlete(adminId),
            commentRepository.countNewAthleteCommentsPerAthlete(adminId),
            deletionRepository.countNewAthleteDeletionsPerAthlete(adminId),
            reservationRepository.countNewReservationsPerAthlete(adminId));
        Map<UUID, Instant> lastActivity = mergeLastActivity(
            trainingRepository.findLastTrainingActivityPerAthlete(),
            commentRepository.findLastCommentActivityPerAthlete());

        return athletes.stream()
            .map(a -> new AthleteSummaryDto(
                a.getId(), a.getFirstName(), a.getLastName(), a.getNickname(),
                avatarUrl(a), counts.getOrDefault(a.getId(), 0L), lastActivity.get(a.getId())))
            // Athletes with unread activity first, then by most recent activity
            .sorted(java.util.Comparator
                .comparing((AthleteSummaryDto s) -> s.newCount() > 0 ? 0 : 1)
                .thenComparing(s -> s.lastActivityAt() != null ? s.lastActivityAt() : Instant.EPOCH,
                    java.util.Comparator.reverseOrder()))
            .toList();
    }

    /**
     * Global admin badge: total unread athlete activity across all currently flagged athletes.
     * Polled every 60s from the navbar, so it does no more work than the number it returns —
     * the flag filter lives in the queries, which also removes the roster read this used to do
     * just to discard rows afterwards.
     */
    @Transactional(readOnly = true)
    public long getTotalAthleteActivity(UUID adminId) {
        return mergeCounts(
            trainingRepository.countNewAthleteTrainingsPerAthlete(adminId),
            trainingRepository.countNewCompletionsPerAthlete(adminId),
            commentRepository.countNewAthleteCommentsPerAthlete(adminId),
            deletionRepository.countNewAthleteDeletionsPerAthlete(adminId),
            reservationRepository.countNewReservationsPerAthlete(adminId))
            .values().stream()
            .mapToLong(Long::longValue)
            .sum();
    }

    @Transactional(readOnly = true)
    public CalendarRangeDto getRangeForAthlete(UUID adminId, UUID athleteId, LocalDate from, LocalDate to) {
        User athlete = requireFlaggedAthlete(athleteId);
        return buildRange(athlete.getId(), adminId, true, from, to);
    }

    public PersonalTrainingDto createForAthlete(UUID athleteId, CreatePersonalTrainingRequest request) {
        User athlete = requireFlaggedAthlete(athleteId);
        return toDtoWithAttachments(createTraining(athlete, true, request), false, nowWarsaw());
    }

    public PersonalTrainingDto updateAsAdmin(UUID trainingId, CreatePersonalTrainingRequest request) {
        PersonalTraining training = requireTrainingOfFlaggedAthlete(trainingId);
        applyUpdate(training, true, request);
        return toDtoWithAttachments(training, false, nowWarsaw());
    }

    public void deleteAsAdmin(UUID trainingId) {
        PersonalTraining training = requireTrainingOfFlaggedAthlete(trainingId);
        recordDeletionIfFuture(training, true);
        attachments.purgeTrainingAttachments(trainingId);
        trainingRepository.delete(training);
    }

    /**
     * Deleting a FUTURE training alerts the other side (a vanished plan matters);
     * removing past entries is just tidying the journal — no alert.
     */
    private void recordDeletionIfFuture(PersonalTraining training, boolean byAdmin) {
        // Untimed ("all-day"): "future" until the day itself begins (00:00).
        if (!trainingStart(training).isAfter(nowWarsaw())) return;
        deletionRepository.pruneOldForAthlete(training.getAthlete().getId(),
            Instant.now().minus(DELETION_LOG_RETENTION));
        deletionRepository.save(new TrainingDeletion(training, byAdmin));
    }

    @Transactional(readOnly = true)
    public List<TrainingCommentDto> getCommentsAsAdmin(UUID adminId, UUID trainingId) {
        requireTrainingOfFlaggedAthlete(trainingId);
        return toCommentDtos(commentRepository.findThread(trainingId), adminId);
    }

    public TrainingCommentDto addCommentAsAdmin(UUID adminId, UUID trainingId, String body) {
        PersonalTraining training = requireTrainingOfFlaggedAthlete(trainingId);
        User admin = userRepository.findById(adminId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return addComment(training, admin, true, body, adminId);
    }

    public void markCoachSeen(UUID adminId, UUID athleteId) {
        requireFlaggedAthlete(athleteId);
        upsertSeen(adminId, athleteId);
    }

    // Package-private for AdminTrainingCalendarService (activity-log descriptions).
    PersonalTraining requireTraining(UUID trainingId) {
        return trainingRepository.findById(trainingId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("training.calendar.not.found")));
    }

    /**
     * Coach path, addressing a training by ITS id rather than by athlete: the athlete flag must
     * still be checked here, or the guard is only as good as the route taken. Un-flagging clears
     * the athlete's consent ({@link User#setAthlete}), so without this an ex-athlete's calendar —
     * feedback and RPE included — stayed writable through the by-id routes while the by-athlete
     * ones correctly refused.
     *
     * <p>Reads the flag off the already-loaded association instead of a second findById.
     */
    PersonalTraining requireTrainingOfFlaggedAthlete(UUID trainingId) {
        PersonalTraining training = requireTraining(trainingId);
        if (!training.getAthlete().isAthlete()) {
            throw new IllegalArgumentException(msg.get("training.calendar.athlete.not.found"));
        }
        return training;
    }

    // ---------- shared internals ----------

    private PersonalTraining createTraining(User athlete, boolean byAdmin, CreatePersonalTrainingRequest request) {
        TrainingKind kind = request.kind() != null ? request.kind() : TrainingKind.TRAINING;
        validateTimes(request, kind);
        validateTargetCalories(request, kind);
        attachments.validate(request.attachments());
        PersonalTraining training = new PersonalTraining(
            athlete, kind, request.date(), request.startTime(), request.endTime(),
            requireSanitizedTitle(request.title()),
            PersonalTraining.sanitizeText(request.description(), PersonalTraining.MAX_DESCRIPTION_LENGTH),
            kind == TrainingKind.TASK ? request.targetCalories() : null,
            byAdmin);
        trainingRepository.save(training);
        // On create, null attachments simply means "none"
        if (request.attachments() != null) {
            attachments.persistForTraining(training, request.attachments());
        }
        return training;
    }

    private void applyUpdate(PersonalTraining training, boolean byAdmin, CreatePersonalTrainingRequest request) {
        // The kind on the request is ignored: it is fixed at creation. Validate against the kind the
        // row actually has, so a task cannot be given hours by editing it.
        TrainingKind kind = training.getKind();
        validateTimes(request, kind);
        validateTargetCalories(request, kind);
        // Defense in depth (the UI already blocks dragging completed sessions): a completed training
        // must stay in the past. Moving it into the future would leave a COMPLETED entry dated ahead
        // of "now" and skew the date-keyed stats/heatmap. Uncomplete first to reschedule.
        if (training.isCompleted()
                && trainingEnd(request.date(), request.endTime()).isAfter(nowWarsaw())) {
            throw new IllegalStateException(msg.get("training.calendar.completed.future"));
        }
        attachments.validate(request.attachments());
        training.update(
            request.date(), request.startTime(), request.endTime(),
            requireSanitizedTitle(request.title()),
            PersonalTraining.sanitizeText(request.description(), PersonalTraining.MAX_DESCRIPTION_LENGTH),
            kind == TrainingKind.TASK ? request.targetCalories() : null,
            byAdmin);
        // null = leave attachments untouched (a move/drag PUT omits them); a list (incl. []) replaces
        if (request.attachments() != null) {
            attachments.replaceForTraining(training, request.attachments());
        }
    }

    public AttachmentUploadResponse uploadMyAttachment(UUID userId, MultipartFile file) {
        requireAthlete(userId);
        return attachments.upload(file);
    }

    public AttachmentUploadResponse uploadAttachmentAsAdmin(MultipartFile file) {
        return attachments.upload(file);
    }

    private TrainingCommentDto addComment(PersonalTraining training, User author, boolean authorIsAdmin,
                                          String body, UUID viewerId) {
        String sanitized = TrainingComment.sanitizeBody(body);
        if (sanitized == null) {
            throw new IllegalArgumentException(msg.get("training.calendar.comment.empty"));
        }
        TrainingComment comment = commentRepository.save(
            new TrainingComment(training, author, authorIsAdmin, sanitized));
        return toCommentDto(comment, viewerId);
    }

    private CalendarRangeDto buildRange(UUID athleteId, UUID viewerId, boolean viewerIsAdmin,
                                        LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException(msg.get("training.calendar.range.invalid"));
        }
        List<PersonalTraining> trainings =
            trainingRepository.findByAthleteIdAndTrainingDateBetweenOrderByTrainingDateAscStartTimeAsc(athleteId, from, to);

        Instant seen = seenAt(viewerId, athleteId);
        // Unread dot: new messages written by the OTHER side after the viewer's marker
        Set<UUID> withNewComments = new HashSet<>(
            commentRepository.findTrainingIdsWithNewComments(athleteId, !viewerIsAdmin, seen));

        LocalDateTime nowWarsaw = nowWarsaw();
        // Batch-load attachments for all trainings in range (no N+1), grouped by training id
        Map<UUID, List<TrainingAttachmentDto>> attachmentsByTraining =
            attachments.dtosForTrainings(trainings.stream().map(PersonalTraining::getId).toList());
        List<PersonalTrainingDto> trainingDtos = trainings.stream()
            .map(t -> toDto(t, hasUnread(t, viewerIsAdmin, seen, withNewComments), nowWarsaw,
                attachmentsByTraining.getOrDefault(t.getId(), List.of())))
            .toList();

        List<Reservation> confirmed = reservationRepository.findConfirmedByUserIdInRange(athleteId, from, to);
        // Batch-load RPE ratings for the overlaid bookings (no N+1)
        Map<UUID, ReservationRpe> rpeByReservation = new HashMap<>();
        List<UUID> reservationIds = confirmed.stream().map(Reservation::getId).toList();
        if (!reservationIds.isEmpty()) {
            for (ReservationRpe rr : reservationRpeRepository.findByReservationIdIn(reservationIds)) {
                rpeByReservation.put(rr.reservationId(), rr);
            }
        }
        List<ReservationOverlayDto> overlay = confirmed.stream()
            .map(r -> toOverlayDto(r, viewerIsAdmin && isNewForCoach(r, seen),
                rpeByReservation.get(r.getId()), nowWarsaw))
            .toList();

        List<InvitationOverlayDto> invitations = buildInvitationOverlay(athleteId, from, to);

        // "Deleted trainings" strip: unseen future-training deletions by the OTHER side.
        // Deliberately NOT limited to the viewed date range — the entry may belong to a
        // week the viewer is not looking at right now.
        List<TrainingDeletionDto> deletions = deletionRepository
            .findUnseen(athleteId, !viewerIsAdmin, seen).stream()
            .limit(10)
            .map(d -> new TrainingDeletionDto(
                d.getTrainingDate(), d.getStartTime(), d.getEndTime(),
                d.getTitle(), d.isDeletedByAdmin(), d.getCreatedAt()))
            .toList();

        return new CalendarRangeDto(trainingDtos, overlay, invitations, deletions);
    }

    /**
     * Pending invitations (held seats the athlete has NOT booked yet) inside the range.
     * Rendered as a loud call-to-action, never like a reservation — a held seat that
     * looks "done" is exactly how people forget to book. Reuses the same repository
     * queries as the "Invitations" section in My Reservations, so the entries vanish
     * on their own once the athlete books or the admin withdraws the invite.
     */
    private List<InvitationOverlayDto> buildInvitationOverlay(UUID athleteId, LocalDate from, LocalDate to) {
        List<InvitationOverlayDto> invitations = new ArrayList<>();
        LocalDate today = LocalDate.now(WARSAW);
        LocalTime nowTime = LocalTime.now(WARSAW);

        for (ReservedSeat rs : reservedSeatRepository.findUpcomingPendingSlotInvitesByUserId(athleteId, today, nowTime)) {
            TimeSlot slot = Objects.requireNonNull(rs.getTimeSlot());
            if (slot.getDate().isBefore(from) || slot.getDate().isAfter(to)) continue;
            invitations.add(new InvitationOverlayDto(
                slot.getId(), null, slot.getDate(),
                slot.getStartTime(), slot.getEndTime(), slot.getDisplayTitle()));
        }

        for (ReservedSeat rs : reservedSeatRepository.findUpcomingPendingEventInvitesByUserId(athleteId, today)) {
            Event event = Objects.requireNonNull(rs.getEvent());
            // Multi-day events: one entry per visible day so every affected day shouts "book me"
            LocalDate first = event.getStartDate().isBefore(from) ? from : event.getStartDate();
            LocalDate last = event.getEndDate().isAfter(to) ? to : event.getEndDate();
            for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
                invitations.add(new InvitationOverlayDto(
                    null, event.getId(), day,
                    event.getStartTime(), event.getEndTime(), event.getTitle()));
            }
        }
        return invitations;
    }

    private static boolean hasUnread(PersonalTraining t, boolean viewerIsAdmin, Instant seen, Set<UUID> withNewComments) {
        if (withNewComments.contains(t.getId())) return true;
        if (viewerIsAdmin) {
            boolean newTraining = !t.isCreatedByAdmin() && t.getCreatedAt().isAfter(seen);
            boolean editedByAthlete = !t.isLastModifiedByAdmin() && t.getUpdatedAt().isAfter(seen);
            boolean newCompletion = t.getCompletedAt() != null && t.getCompletedAt().isAfter(seen);
            return newTraining || editedByAthlete || newCompletion;
        }
        boolean newFromCoach = t.isCreatedByAdmin() && t.getCreatedAt().isAfter(seen);
        boolean editedByCoach = t.isLastModifiedByAdmin() && t.getUpdatedAt().isAfter(seen);
        return newFromCoach || editedByCoach;
    }

    private void validateTimes(CreatePersonalTrainingRequest request, TrainingKind kind) {
        boolean hasStart = request.startTime() != null;
        boolean hasEnd = request.endTime() != null;
        // A commitment held across a whole day has no hour, and an hour would place it on the week
        // view's grid at a position that claims something it does not have.
        if (kind == TrainingKind.TASK && (hasStart || hasEnd)) {
            throw new IllegalArgumentException(msg.get("training.calendar.task.untimed"));
        }
        // Untimed ("all-day"): both null is allowed.
        if (!hasStart && !hasEnd) {
            return;
        }
        // Never exactly one — a training is either fully timed or fully untimed.
        if (hasStart != hasEnd) {
            throw new IllegalArgumentException(msg.get("training.calendar.time.partial"));
        }
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException(msg.get("admin.slot.end.after.start"));
        }
    }

    /**
     * A calorie ceiling belongs to a task. Silently dropping it from a training would hide a
     * frontend bug; the bounds catch a slipped digit the way the weight range does.
     */
    private void validateTargetCalories(CreatePersonalTrainingRequest request, TrainingKind kind) {
        Integer target = request.targetCalories();
        if (target == null) {
            return;
        }
        if (kind != TrainingKind.TASK) {
            throw new IllegalArgumentException(msg.get("training.calendar.calories.task.only"));
        }
        if (target < PersonalTraining.MIN_TARGET_CALORIES || target > PersonalTraining.MAX_TARGET_CALORIES) {
            throw new IllegalArgumentException(msg.get("training.calendar.calories.range"));
        }
    }

    private String requireSanitizedTitle(String title) {
        String sanitized = PersonalTraining.sanitizeText(title, PersonalTraining.MAX_TITLE_LENGTH);
        if (sanitized == null) {
            throw new IllegalArgumentException(msg.get("training.calendar.title.empty"));
        }
        return sanitized;
    }

    /**
     * The athlete-side gate: coach-set flag AND the athlete's own explicit consent to
     * training-calendar data processing (GDPR art. 9(2)(a) — see V76). Consent is checked here
     * rather than per endpoint so that anything added later is gated by default; the two
     * notification methods deliberately opt out via {@link #requireAthleteIgnoringConsent}.
     * Package-private: AthleteGoalService/AthleteWeightService reuse the exact same guards.
     */
    User requireAthlete(UUID userId) {
        User user = requireAthleteIgnoringConsent(userId);
        if (!user.hasTrainingConsent()) {
            throw new IllegalStateException(msg.get("training.calendar.consent.required"));
        }
        return user;
    }

    /**
     * Flag only, no consent check. For the unread-badge endpoints: they return a bare counter
     * (no calendar content), and they are polled from every page — gating them would spam the
     * athlete's browser with 409s while the consent screen is still on their table.
     */
    User requireAthleteIgnoringConsent(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!user.isAthlete()) {
            throw new IllegalStateException(msg.get("training.calendar.not.athlete"));
        }
        return user;
    }

    /** Records the athlete's explicit consent (idempotent — re-accepting keeps the first timestamp). */
    public void grantConsent(UUID userId) {
        requireAthleteIgnoringConsent(userId).grantTrainingConsent();
    }

    User requireFlaggedAthlete(UUID athleteId) {
        return userRepository.findById(athleteId)
            .filter(User::isAthlete)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("training.calendar.athlete.not.found")));
    }

    private PersonalTraining requireOwnTraining(UUID trainingId, UUID userId) {
        PersonalTraining training = requireTraining(trainingId);
        if (!training.getAthlete().getId().equals(userId)) {
            // Same message as not-found: don't reveal other users' training ids
            throw new IllegalArgumentException(msg.get("training.calendar.not.found"));
        }
        return training;
    }

    private Instant seenAt(UUID viewerId, UUID athleteId) {
        return readRepository.findByUserIdAndAthleteId(viewerId, athleteId)
            .map(TrainingCalendarRead::getSeenAt)
            // Never opened: count everything (new calendars start empty anyway)
            .orElse(Instant.EPOCH);
    }

    private void upsertSeen(UUID viewerId, UUID athleteId) {
        readRepository.upsertSeen(viewerId, athleteId, Instant.now());
    }

    private List<TrainingCommentDto> toCommentDtos(List<TrainingComment> comments, UUID viewerId) {
        return comments.stream().map(c -> toCommentDto(c, viewerId)).toList();
    }

    private static TrainingCommentDto toCommentDto(TrainingComment c, UUID viewerId) {
        return new TrainingCommentDto(
            c.getId(),
            c.getBody(),
            c.isAuthorIsAdmin(),
            c.getAuthor().getFullName(),
            avatarUrl(c.getAuthor()),
            c.getCreatedAt(),
            c.getAuthor().getId().equals(viewerId)
        );
    }

    /** Single-training DTO with its attachments loaded (create/update/complete paths). */
    private PersonalTrainingDto toDtoWithAttachments(PersonalTraining t, boolean hasUnreadActivity, LocalDateTime nowWarsaw) {
        return toDto(t, hasUnreadActivity, nowWarsaw, attachments.dtosForTraining(t.getId()));
    }

    static PersonalTrainingDto toDto(PersonalTraining t, boolean hasUnreadActivity, LocalDateTime nowWarsaw,
                                     List<TrainingAttachmentDto> attachments) {
        return new PersonalTrainingDto(
            t.getId(),
            t.getKind(),
            t.getTrainingDate(),
            t.getStartTime(),
            t.getEndTime(),
            t.getTitle(),
            t.getDescription(),
            t.getTargetCalories(),
            t.isCreatedByAdmin(),
            deriveStatus(t, nowWarsaw),
            t.getCompletedAt(),
            t.getFeedback(),
            t.getRpe(),
            hasUnreadActivity,
            t.getCreatedAt(),
            attachments
        );
    }

    /** MISSED is derived, never stored: planned training whose end already passed (Warsaw time). */
    static String deriveStatus(PersonalTraining t, LocalDateTime nowWarsaw) {
        if (t.isCompleted()) return "COMPLETED";
        return trainingEnd(t.getTrainingDate(), t.getEndTime()).isBefore(nowWarsaw) ? "MISSED" : "PLANNED";
    }

    /** Effective start: the training's time, or the start of its day when untimed ("all-day"). */
    private static LocalDateTime trainingStart(PersonalTraining t) {
        LocalTime start = t.getStartTime();
        return start != null ? LocalDateTime.of(t.getTrainingDate(), start) : t.getTrainingDate().atStartOfDay();
    }

    /** Effective end: the training's time, or the end of its day (23:59:59.999...) when untimed. */
    static LocalDateTime trainingEnd(LocalDate date, @Nullable LocalTime endTime) {
        return LocalDateTime.of(date, endTime != null ? endTime : LocalTime.MAX);
    }

    /** "New" is a coach-side concept: the athlete booked after the coach's last visit.
     * Bookings an admin made by hand are the coach's own action — never new. */
    private static boolean isNewForCoach(Reservation r, Instant seen) {
        return !r.isCreatedByAdmin() && r.getCreatedAt().isAfter(seen);
    }

    private static ReservationOverlayDto toOverlayDto(Reservation r, boolean isNew,
                                                      @Nullable ReservationRpe rpe, LocalDateTime nowWarsaw) {
        TimeSlot slot = r.getTimeSlot();
        // Ratable once the booking is over (same past-predicate as the stats/rate guard)
        boolean past = slot.getDate().isBefore(nowWarsaw.toLocalDate())
            || (slot.getDate().equals(nowWarsaw.toLocalDate()) && !slot.getEndTime().isAfter(nowWarsaw.toLocalTime()));
        return new ReservationOverlayDto(
            r.getId(), slot.getId(), slot.getDate(), slot.getStartTime(), slot.getEndTime(), slot.getDisplayTitle(),
            isNew,
            rpe != null ? rpe.getRpe() : null,
            rpe != null ? rpe.getNote() : null,
            past);
    }

    @Nullable
    private static String avatarUrl(User user) {
        return user.getAvatarFilename() != null
            ? "/api/files/avatars/" + user.getAvatarFilename()
            : null;
    }

    private static LocalDateTime nowWarsaw() {
        return LocalDateTime.now(WARSAW);
    }

    @SafeVarargs
    private static Map<UUID, Long> mergeCounts(List<AthleteActivityCount>... lists) {
        Map<UUID, Long> merged = new HashMap<>();
        for (List<AthleteActivityCount> list : lists) {
            for (AthleteActivityCount count : list) {
                merged.merge(count.athleteId(), count.count(), Long::sum);
            }
        }
        return merged;
    }

    private static Map<UUID, Instant> mergeLastActivity(List<AthleteLastActivity> a, List<AthleteLastActivity> b) {
        Map<UUID, Instant> merged = new HashMap<>();
        for (List<AthleteLastActivity> list : List.of(a, b)) {
            for (AthleteLastActivity activity : list) {
                merged.merge(activity.athleteId(), activity.lastActivityAt(),
                    (x, y) -> x.isAfter(y) ? x : y);
            }
        }
        return merged;
    }
}
