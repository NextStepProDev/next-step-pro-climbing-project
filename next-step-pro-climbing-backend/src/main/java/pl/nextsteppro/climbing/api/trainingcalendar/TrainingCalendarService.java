package pl.nextsteppro.climbing.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;
import pl.nextsteppro.climbing.domain.personaltraining.AthleteActivityCount;
import pl.nextsteppro.climbing.domain.personaltraining.AthleteLastActivity;
import pl.nextsteppro.climbing.domain.personaltraining.AttachmentKind;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTraining;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingAttachment;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingAttachmentRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCalendarRead;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCalendarReadRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingComment;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingDeletion;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingDeletionRepository;
import pl.nextsteppro.climbing.infrastructure.media.VideoEmbedUrls;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeat;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeatRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    private static final Logger logger = LoggerFactory.getLogger(TrainingCalendarService.class);

    /** Range endpoint guard: a month view needs ~42 days; anything beyond 62 is a client bug. */
    static final int MAX_RANGE_DAYS = 62;

    // Slot times are stored as local Poland time while the container runs UTC —
    // "now" comparisons MUST use this zone (see CLAUDE.md gotcha).
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    // Deletion log only survives until read; prune anything older on the next write
    private static final Duration DELETION_LOG_RETENTION = Duration.ofDays(60);

    // Uploaded training materials live here (see FileController /training/{filename})
    static final String ATTACHMENT_FOLDER = "training";

    private final PersonalTrainingRepository trainingRepository;
    private final TrainingCommentRepository commentRepository;
    private final TrainingCalendarReadRepository readRepository;
    private final TrainingDeletionRepository deletionRepository;
    private final TrainingAttachmentRepository attachmentRepository;
    private final ReservationRepository reservationRepository;
    private final ReservedSeatRepository reservedSeatRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final MessageService msg;

    public TrainingCalendarService(PersonalTrainingRepository trainingRepository,
                                   TrainingCommentRepository commentRepository,
                                   TrainingCalendarReadRepository readRepository,
                                   TrainingDeletionRepository deletionRepository,
                                   TrainingAttachmentRepository attachmentRepository,
                                   ReservationRepository reservationRepository,
                                   ReservedSeatRepository reservedSeatRepository,
                                   UserRepository userRepository,
                                   FileStorageService fileStorageService,
                                   MessageService msg) {
        this.trainingRepository = trainingRepository;
        this.commentRepository = commentRepository;
        this.readRepository = readRepository;
        this.deletionRepository = deletionRepository;
        this.attachmentRepository = attachmentRepository;
        this.reservationRepository = reservationRepository;
        this.reservedSeatRepository = reservedSeatRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
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
        List<String> files = attachmentFilenames(trainingId);
        trainingRepository.delete(training);
        deleteFilesQuietly(files);
    }

    /**
     * Completion requires the training to have STARTED (Warsaw time): a session in progress
     * may be checked off (ended early) and retroactive logging is fine, but a future plan
     * cannot be marked done. No end-time restriction — athletes often log days later.
     */
    public PersonalTrainingDto complete(UUID userId, UUID trainingId, CompleteTrainingRequest request) {
        requireAthlete(userId);
        PersonalTraining training = requireOwnTraining(trainingId, userId);
        if (LocalDateTime.of(training.getTrainingDate(), training.getStartTime()).isAfter(nowWarsaw())) {
            throw new IllegalStateException(msg.get("training.calendar.complete.future"));
        }
        // Defense in depth: @Min/@Max fire only via controller @Valid; without this a bad
        // value would surface as an ugly 500 from the DB CHECK constraint
        if (request.rpe() != null && (request.rpe() < 1 || request.rpe() > 10)) {
            throw new IllegalArgumentException(msg.get("training.calendar.rpe.invalid"));
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
        requireAthlete(userId);
        Instant seen = seenAt(userId, userId);
        long count = trainingRepository.countCoachChangesSince(userId, seen)
            + commentRepository.countCoachCommentsSince(userId, seen)
            + deletionRepository.countAdminDeletionsSince(userId, seen);
        return new TrainingNotificationsDto(count);
    }

    public void markAthleteSeen(UUID userId) {
        requireAthlete(userId);
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

    /** Global admin badge: total unread athlete activity across all currently flagged athletes. */
    @Transactional(readOnly = true)
    public long getTotalAthleteActivity(UUID adminId) {
        Set<UUID> flagged = new HashSet<>();
        userRepository.findAllByAthleteTrueOrderByFirstNameAscLastNameAsc()
            .forEach(u -> flagged.add(u.getId()));
        if (flagged.isEmpty()) return 0;
        Map<UUID, Long> counts = mergeCounts(
            trainingRepository.countNewAthleteTrainingsPerAthlete(adminId),
            trainingRepository.countNewCompletionsPerAthlete(adminId),
            commentRepository.countNewAthleteCommentsPerAthlete(adminId),
            deletionRepository.countNewAthleteDeletionsPerAthlete(adminId),
            reservationRepository.countNewReservationsPerAthlete(adminId));
        return counts.entrySet().stream()
            .filter(e -> flagged.contains(e.getKey()))
            .mapToLong(Map.Entry::getValue)
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
        PersonalTraining training = requireTraining(trainingId);
        applyUpdate(training, true, request);
        return toDtoWithAttachments(training, false, nowWarsaw());
    }

    public void deleteAsAdmin(UUID trainingId) {
        PersonalTraining training = requireTraining(trainingId);
        recordDeletionIfFuture(training, true);
        List<String> files = attachmentFilenames(trainingId);
        trainingRepository.delete(training);
        deleteFilesQuietly(files);
    }

    /**
     * Deleting a FUTURE training alerts the other side (a vanished plan matters);
     * removing past entries is just tidying the journal — no alert.
     */
    private void recordDeletionIfFuture(PersonalTraining training, boolean byAdmin) {
        LocalDateTime start = LocalDateTime.of(training.getTrainingDate(), training.getStartTime());
        if (!start.isAfter(nowWarsaw())) return;
        deletionRepository.pruneOldForAthlete(training.getAthlete().getId(),
            Instant.now().minus(DELETION_LOG_RETENTION));
        deletionRepository.save(new TrainingDeletion(training, byAdmin));
    }

    @Transactional(readOnly = true)
    public List<TrainingCommentDto> getCommentsAsAdmin(UUID adminId, UUID trainingId) {
        requireTraining(trainingId);
        return toCommentDtos(commentRepository.findThread(trainingId), adminId);
    }

    public TrainingCommentDto addCommentAsAdmin(UUID adminId, UUID trainingId, String body) {
        PersonalTraining training = requireTraining(trainingId);
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

    // ---------- shared internals ----------

    private PersonalTraining createTraining(User athlete, boolean byAdmin, CreatePersonalTrainingRequest request) {
        validateTimes(request);
        validateAttachments(request.attachments());
        PersonalTraining training = new PersonalTraining(
            athlete, request.date(), request.startTime(), request.endTime(),
            requireSanitizedTitle(request.title()),
            PersonalTraining.sanitizeText(request.description(), PersonalTraining.MAX_DESCRIPTION_LENGTH),
            byAdmin);
        trainingRepository.save(training);
        // On create, null attachments simply means "none"
        if (request.attachments() != null) {
            persistAttachments(training, request.attachments());
        }
        return training;
    }

    private void applyUpdate(PersonalTraining training, boolean byAdmin, CreatePersonalTrainingRequest request) {
        validateTimes(request);
        validateAttachments(request.attachments());
        training.update(
            request.date(), request.startTime(), request.endTime(),
            requireSanitizedTitle(request.title()),
            PersonalTraining.sanitizeText(request.description(), PersonalTraining.MAX_DESCRIPTION_LENGTH),
            byAdmin);
        // null = leave attachments untouched (a move/drag PUT omits them); a list (incl. []) replaces
        if (request.attachments() != null) {
            // Uploaded files no longer referenced after the replace must be removed from disk
            Set<String> keptFiles = request.attachments().stream()
                .filter(AttachmentRequest::isFile).map(AttachmentRequest::filename).collect(java.util.stream.Collectors.toSet());
            List<String> orphaned = attachmentRepository.findByTrainingIdOrderByPositionAsc(training.getId()).stream()
                .filter(a -> a.getKind() == AttachmentKind.FILE)
                .map(TrainingAttachment::getFilename)
                .filter(f -> f != null && !keptFiles.contains(f))
                .toList();
            attachmentRepository.deleteByTrainingId(training.getId());
            persistAttachments(training, request.attachments());
            deleteFilesQuietly(orphaned);
        }
    }

    private void persistAttachments(PersonalTraining training, List<AttachmentRequest> requests) {
        int position = 0;
        for (AttachmentRequest req : requests) {
            String label = TrainingAttachment.sanitizeLabel(req.label());
            TrainingAttachment attachment = req.isFile()
                ? TrainingAttachment.file(training, req.filename(), sanitizeName(req.originalName()),
                    req.mimeType(), req.sizeBytes(), label, position++)
                : TrainingAttachment.link(training, req.url().trim(), label, position++);
            attachmentRepository.save(attachment);
        }
    }

    private void validateAttachments(@Nullable List<AttachmentRequest> requests) {
        if (requests == null) return;
        if (requests.size() > TrainingAttachment.MAX_PER_TRAINING) {
            throw new IllegalArgumentException(msg.get("training.attachment.too.many"));
        }
        for (AttachmentRequest req : requests) {
            if (req.isFile()) {
                validateFileAttachment(req);
            } else {
                validateLinkUrl(req.url());
            }
        }
    }

    private void validateLinkUrl(@Nullable String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException(msg.get("training.attachment.url.invalid"));
        }
        try {
            java.net.URI uri = java.net.URI.create(rawUrl.trim());
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException(msg.get("training.attachment.url.invalid"));
            }
        } catch (IllegalArgumentException e) {
            // URI.create throws IllegalArgumentException on malformed input — normalise the message
            throw new IllegalArgumentException(msg.get("training.attachment.url.invalid"));
        }
    }

    /** A FILE attachment must reference a file already uploaded to the training folder. */
    private void validateFileAttachment(AttachmentRequest req) {
        String filename = req.filename();
        boolean ok;
        try {
            // exists() also enforces the strict UUID.ext format (rejects path traversal)
            ok = filename != null && fileStorageService.exists(filename, ATTACHMENT_FOLDER);
        } catch (IllegalArgumentException e) {
            ok = false;
        }
        if (!ok) {
            throw new IllegalArgumentException(msg.get("training.attachment.file.invalid"));
        }
    }

    /**
     * Stores an uploaded document (PDF/image) in the training folder and returns its metadata.
     * The file is linked to a training only when the training is saved with a FILE attachment
     * referencing this filename.
     */
    public AttachmentUploadResponse uploadMyAttachment(UUID userId, MultipartFile file) {
        requireAthlete(userId);
        return storeAttachment(file);
    }

    public AttachmentUploadResponse uploadAttachmentAsAdmin(MultipartFile file) {
        return storeAttachment(file);
    }

    private AttachmentUploadResponse storeAttachment(MultipartFile file) {
        try {
            String filename = fileStorageService.storeDocument(file, ATTACHMENT_FOLDER);
            return new AttachmentUploadResponse(
                filename,
                sanitizeName(file.getOriginalFilename()),
                file.getContentType(),
                file.getSize(),
                "/api/files/" + ATTACHMENT_FOLDER + "/" + filename);
        } catch (IOException e) {
            throw new IllegalStateException(msg.get("training.attachment.upload.failed"));
        }
    }

    @Nullable
    private static String sanitizeName(@Nullable String name) {
        if (name == null || name.isBlank()) return null;
        String escaped = HtmlUtils.htmlEscape(name.trim(), StandardCharsets.UTF_8.name());
        return escaped.length() > 255 ? escaped.substring(0, 255) : escaped;
    }

    /** Best-effort disk cleanup — never fails the surrounding business transaction. */
    private void deleteFilesQuietly(List<String> filenames) {
        for (String filename : filenames) {
            try {
                fileStorageService.delete(filename, ATTACHMENT_FOLDER);
            } catch (Exception e) {
                logger.warn("Failed to delete orphaned training attachment file {}", filename, e);
            }
        }
    }

    /** File attachments of a training, for disk cleanup before the DB CASCADE removes the rows. */
    private List<String> attachmentFilenames(UUID trainingId) {
        return attachmentRepository.findByTrainingIdOrderByPositionAsc(trainingId).stream()
            .filter(a -> a.getKind() == AttachmentKind.FILE)
            .map(TrainingAttachment::getFilename)
            .filter(Objects::nonNull)
            .toList();
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
        Map<UUID, List<TrainingAttachmentDto>> attachmentsByTraining = new HashMap<>();
        List<UUID> trainingIds = trainings.stream().map(PersonalTraining::getId).toList();
        if (!trainingIds.isEmpty()) {
            for (TrainingAttachment a : attachmentRepository.findByTrainingIdInOrderByPositionAsc(trainingIds)) {
                attachmentsByTraining
                    .computeIfAbsent(a.trainingId(), k -> new ArrayList<>())
                    .add(toAttachmentDto(a));
            }
        }
        List<PersonalTrainingDto> trainingDtos = trainings.stream()
            .map(t -> toDto(t, hasUnread(t, viewerIsAdmin, seen, withNewComments), nowWarsaw,
                attachmentsByTraining.getOrDefault(t.getId(), List.of())))
            .toList();

        List<ReservationOverlayDto> overlay = reservationRepository
            .findConfirmedByUserIdInRange(athleteId, from, to).stream()
            .map(r -> toOverlayDto(r, viewerIsAdmin && isNewForCoach(r, seen)))
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

    private void validateTimes(CreatePersonalTrainingRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException(msg.get("admin.slot.end.after.start"));
        }
    }

    private String requireSanitizedTitle(String title) {
        String sanitized = PersonalTraining.sanitizeText(title, PersonalTraining.MAX_TITLE_LENGTH);
        if (sanitized == null) {
            throw new IllegalArgumentException(msg.get("training.calendar.title.empty"));
        }
        return sanitized;
    }

    // Package-private: AthleteGoalService reuses the exact same athlete-flag guards
    User requireAthlete(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!user.isAthlete()) {
            throw new IllegalStateException(msg.get("training.calendar.not.athlete"));
        }
        return user;
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
        List<TrainingAttachmentDto> attachments = attachmentRepository
            .findByTrainingIdOrderByPositionAsc(t.getId()).stream()
            .map(TrainingCalendarService::toAttachmentDto)
            .toList();
        return toDto(t, hasUnreadActivity, nowWarsaw, attachments);
    }

    static PersonalTrainingDto toDto(PersonalTraining t, boolean hasUnreadActivity, LocalDateTime nowWarsaw,
                                     List<TrainingAttachmentDto> attachments) {
        return new PersonalTrainingDto(
            t.getId(),
            t.getTrainingDate(),
            t.getStartTime(),
            t.getEndTime(),
            t.getTitle(),
            t.getDescription(),
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

    static TrainingAttachmentDto toAttachmentDto(TrainingAttachment a) {
        if (a.getKind() == AttachmentKind.FILE) {
            String serveUrl = "/api/files/" + ATTACHMENT_FOLDER + "/" + a.getFilename();
            return new TrainingAttachmentDto(a.getId(), "FILE", serveUrl, a.getLabel(),
                null, a.getFilename(), a.getOriginalName(), a.getMimeType(), a.getSizeBytes());
        }
        return new TrainingAttachmentDto(a.getId(), "LINK", a.getUrl(), a.getLabel(),
            a.getUrl() != null ? VideoEmbedUrls.toEmbedUrlOrNull(a.getUrl()) : null, null, null, null, null);
    }

    /** MISSED is derived, never stored: planned training whose end already passed (Warsaw time). */
    static String deriveStatus(PersonalTraining t, LocalDateTime nowWarsaw) {
        if (t.isCompleted()) return "COMPLETED";
        LocalDateTime end = LocalDateTime.of(t.getTrainingDate(), t.getEndTime());
        return end.isBefore(nowWarsaw) ? "MISSED" : "PLANNED";
    }

    /** "New" is a coach-side concept: the athlete booked after the coach's last visit.
     * Bookings an admin made by hand are the coach's own action — never new. */
    private static boolean isNewForCoach(Reservation r, Instant seen) {
        return !r.isCreatedByAdmin() && r.getCreatedAt().isAfter(seen);
    }

    private static ReservationOverlayDto toOverlayDto(Reservation r, boolean isNew) {
        TimeSlot slot = r.getTimeSlot();
        return new ReservationOverlayDto(
            r.getId(), slot.getId(), slot.getDate(), slot.getStartTime(), slot.getEndTime(), slot.getDisplayTitle(),
            isNew);
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
