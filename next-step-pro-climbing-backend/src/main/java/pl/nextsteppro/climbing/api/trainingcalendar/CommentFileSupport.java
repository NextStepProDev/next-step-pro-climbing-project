package pl.nextsteppro.climbing.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingComment;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentFile;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentFileRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Mechanics of the files attached to thread messages: validation, storage, DTOs and every path that
 * deletes one. Carries no authorisation of its own — the callers ({@link TrainingCalendarService}
 * and the retention sweep) decide who may act, exactly as {@link AttachmentSupport} does for the
 * coach's materials. Keeping it guard-free is also what avoids a dependency cycle with the service
 * that owns the guards.
 */
@Component
public class CommentFileSupport {

    private static final Logger logger = LoggerFactory.getLogger(CommentFileSupport.class);

    /**
     * Deliberately NOT {@code training/}. That folder is swept by
     * {@link pl.nextsteppro.climbing.infrastructure.scheduler.TrainingAttachmentCleanupScheduler},
     * which decides what is abandoned by asking {@code TrainingAttachmentRepository} alone — so a
     * comment attachment dropped in there would be deleted as an orphan within six hours. Teaching
     * that sweep a second table would leave two different retention policies sharing one deleter,
     * where the first mistake destroys somebody's data. A separate folder removes the whole class.
     */
    static final String FOLDER = "commentfiles";

    /**
     * How long an attachment lives. A photo of a route or a written plan stays useful across a
     * season, which is the honest answer here — and a bounded one, so a folder of other people's
     * training photos cannot grow without limit for years.
     */
    static final Duration RETENTION = Duration.ofDays(365);

    /** Across the whole thread. Bounds disk growth over the retention window. */
    static final int MAX_PER_TRAINING = 20;

    /** Lower than the 10 MB for coach materials: these sit on disk for a year. */
    static final long MAX_BYTES = 5L * 1024 * 1024;

    private final TrainingCommentFileRepository fileRepository;
    private final TrainingCommentRepository commentRepository;
    private final FileStorageService fileStorageService;
    private final MessageService msg;

    public CommentFileSupport(TrainingCommentFileRepository fileRepository,
                       TrainingCommentRepository commentRepository,
                       FileStorageService fileStorageService,
                       MessageService msg) {
        this.fileRepository = fileRepository;
        this.commentRepository = commentRepository;
        this.fileStorageService = fileStorageService;
        this.msg = msg;
    }

    // ---------- writing ----------

    /**
     * Stores the uploads and hangs them off an already-persisted comment. Files land on disk inside
     * the caller's transaction; a rollback afterwards leaves them behind, which is precisely what
     * the orphan pass of the retention sweep exists to reconcile.
     */
    void attach(TrainingComment comment, List<MultipartFile> files) {
        if (files.isEmpty()) {
            throw new IllegalArgumentException(msg.get("training.comment.file.required"));
        }
        if (files.size() > TrainingCommentFile.MAX_PER_COMMENT) {
            throw new IllegalArgumentException(msg.get("training.comment.file.too.many"));
        }
        if (countInThread(comment) + files.size() > MAX_PER_TRAINING) {
            throw new IllegalStateException(msg.get("training.comment.file.training.limit"));
        }

        Instant expiresAt = Instant.now().plus(RETENTION);
        int position = 0;
        for (MultipartFile file : files) {
            FileStorageService.StoredFile stored = store(file);
            fileRepository.save(new TrainingCommentFile(
                comment,
                stored.filename(),
                TrainingCommentFile.sanitizeName(file.getOriginalFilename()),
                stored.mimeType(),
                stored.sizeBytes(),
                stored.width(),
                stored.height(),
                position++,
                expiresAt));
        }
    }

    /**
     * How much of the cap this conversation has already spent. Three branches because the target is
     * three real columns; the alternative — one query joining all three with {@code IS NULL} arms —
     * would read as if a message could hang on two things at once and would skip the partial
     * indexes. The switch is exhaustive by construction: the CHECK guarantees exactly one target.
     */
    private long countInThread(TrainingComment comment) {
        UUID trainingId = comment.trainingId();
        if (trainingId != null) return fileRepository.countForTraining(trainingId);
        UUID slotId = comment.timeSlotId();
        if (slotId != null) return fileRepository.countForSlotThread(slotId, comment.athleteId());
        return fileRepository.countForEventThread(
            Objects.requireNonNull(comment.eventId()), comment.athleteId());
    }

    private FileStorageService.StoredFile store(MultipartFile file) {
        try {
            return fileStorageService.storeAttachment(file, FOLDER, MAX_BYTES);
        } catch (IOException e) {
            throw new IllegalStateException(msg.get("training.attachment.upload.failed"));
        }
    }

    // ---------- reading ----------

    /** Batch map for a whole thread — a per-comment lookup would be N+1 on every training opened. */
    Map<UUID, List<TrainingCommentFileDto>> dtosForComments(Collection<UUID> commentIds,
                                                            UUID viewerId, boolean viewerIsAdmin) {
        Map<UUID, List<TrainingCommentFileDto>> byComment = new HashMap<>();
        if (commentIds.isEmpty()) {
            return byComment;
        }
        for (TrainingCommentFile f : fileRepository.findByCommentIdInOrderByPositionAsc(commentIds)) {
            byComment.computeIfAbsent(f.getComment().getId(), k -> new ArrayList<>())
                .add(toDto(f, canDelete(f.getComment(), viewerId, viewerIsAdmin)));
        }
        return byComment;
    }

    static TrainingCommentFileDto toDto(TrainingCommentFile f, boolean canDelete) {
        return new TrainingCommentFileDto(
            f.getId(),
            "/api/training-calendar/comment-files/" + f.getId(),
            f.getMimeType(),
            f.getOriginalName(),
            f.getSizeBytes(),
            f.getWidth() == null ? null : (int) f.getWidth(),
            f.getHeight() == null ? null : (int) f.getHeight(),
            f.getExpiresAt(),
            canDelete);
    }

    /**
     * A client may withdraw what they sent; they may not remove what the coach sent them. The coach
     * may remove anything in a thread they are responsible for.
     */
    static boolean canDelete(TrainingComment comment, UUID viewerId, boolean viewerIsAdmin) {
        return viewerIsAdmin || comment.getAuthor().getId().equals(viewerId);
    }

    // ---------- deleting ----------

    /**
     * The one deletion path, shared by the manual button and the retention sweep — split in two,
     * expiry and a click would sooner or later disagree about what a message is left holding.
     *
     * <p>A message whose only content was the file goes with it: {@code body} is nullable now, so
     * nothing else would stop an empty bubble from sitting in the thread forever.
     */
    void deleteFile(TrainingCommentFile file) {
        TrainingComment comment = file.getComment();
        String filename = file.getFilename();

        fileRepository.delete(file);
        fileRepository.flush(); // count below must not see the row we just removed

        if (comment.getBody() == null && fileRepository.countByCommentId(comment.getId()) == 0) {
            commentRepository.delete(comment);
        }
        unlink(filename);
    }

    /**
     * Unlink the files of a training that is about to be deleted. Must run BEFORE the delete: the
     * comment rows vanish through the DB cascade without Hibernate ever loading them, so no entity
     * callback can reach the files. Same reason {@code purgeTrainingAttachments} exists.
     */
    void purgeForTraining(UUID trainingId) {
        unlinkAll(fileRepository.findFilenamesForTraining(trainingId));
    }

    /**
     * Same, for a slot or an event about to be deleted. Public because the callers are in
     * {@code AdminService}, which owns both deletions.
     *
     * <p>New with V97 and easy to miss: until threads could hang on a booked session, deleting one
     * destroyed no conversation and so needed no unlink. Now it does, and the rows still vanish
     * through the DB cascade without Hibernate loading them — so the files have to be named here or
     * they are simply left behind.
     */
    public void purgeForSlot(UUID slotId) {
        unlinkAll(fileRepository.findFilenamesForSlot(slotId));
    }

    /** Twin of {@link #purgeForSlot} — see there. */
    public void purgeForEvent(UUID eventId) {
        unlinkAll(fileRepository.findFilenamesForEvent(eventId));
    }

    /**
     * Same, for an account about to be deleted. Covers both trainings owned and messages written.
     *
     * <p>Public because both deletion paths need it — {@code UserService.deleteAccount} and
     * {@code AdminService.deleteUser}. One method called twice, never a second implementation:
     * these two paths have already drifted apart once over seat release (see
     * {@code UserSeatReleaseService}). The orphan sweep would eventually catch whatever is missed,
     * but "eventually" is the wrong answer to an erasure request.
     */
    public void purgeForUser(UUID userId) {
        unlinkAll(fileRepository.findFilenamesForUser(userId));
    }

    // ---------- retention ----------

    List<TrainingCommentFile> findExpired(Instant now) {
        return fileRepository.findExpiredBefore(now);
    }

    /**
     * Files in the folder that no row claims and that are older than the grace window. This is the
     * pass that makes "gone after a year" auditable: every explicit unlink sits in a transaction
     * that can still roll back, and {@code LocalFileStorageService.delete} logs its failures rather
     * than raising them. Without it one silent failure is a permanent leak of somebody's data;
     * with it the file survives at most one more night.
     *
     * <p>The grace window protects an upload that landed on disk between reading the known names
     * and listing the folder — deleting a file somebody just sent is far worse than sweeping it a
     * day later.
     */
    int sweepOrphans(Duration grace) {
        Set<String> known = fileRepository.findAllFilenames();
        Instant cutoff = Instant.now().minus(grace);
        int deleted = 0;
        for (String filename : fileStorageService.listFilenames(FOLDER)) {
            try {
                if (known.contains(filename)) continue;
                long modified = fileStorageService.getLastModifiedMillis(filename, FOLDER);
                if (modified < 0 || Instant.ofEpochMilli(modified).isAfter(cutoff)) continue;
                fileStorageService.delete(filename, FOLDER);
                deleted++;
            } catch (Exception e) {
                // Malformed name or IO error — skip this file, keep sweeping
                logger.warn("Skipping orphan sweep of comment file {}: {}", filename, e.getMessage());
            }
        }
        return deleted;
    }

    // ---------- storage ----------

    TrainingCommentFile requireFile(UUID fileId) {
        return fileRepository.findByIdWithComment(fileId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("training.comment.file.not.found")));
    }

    boolean exists(String filename) {
        try {
            return fileStorageService.exists(filename, FOLDER);
        } catch (IllegalArgumentException e) {
            return false; // malformed name (path-traversal guard) → treat as missing
        }
    }

    java.io.InputStream open(String filename) throws IOException {
        return fileStorageService.getInputStream(filename, FOLDER);
    }

    long sizeOf(String filename) {
        return fileStorageService.getFileSize(filename, FOLDER);
    }

    private void unlinkAll(List<String> filenames) {
        for (String filename : filenames) {
            unlink(filename);
        }
    }

    private void unlink(@Nullable String filename) {
        if (filename == null) return;
        try {
            fileStorageService.delete(filename, FOLDER);
        } catch (Exception e) {
            // Never fatal: the orphan sweep reconciles whatever a failure leaves behind.
            logger.warn("Failed to delete comment file {}", filename, e);
        }
    }
}
