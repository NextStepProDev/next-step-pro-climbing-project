package pl.nextsteppro.climbing.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.trainingcalendar.AttachmentSupport;

import java.time.Duration;

/**
 * Removes abandoned training-material uploads. The upload is two-phase (file stored first, linked
 * to a training/template only on save), so cancelling a form or a failed save leaves a file on disk
 * that nothing references. This sweep deletes such files once they are older than the grace window
 * (so an upload in progress, not yet attached, is never touched). Files that ARE referenced are
 * left alone — normal deletes/edits already clean those up (reference-counted).
 */
@Component
public class TrainingAttachmentCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrainingAttachmentCleanupScheduler.class);

    // A file uploaded but not attached within this window is considered abandoned. Kept short (and
    // paired with the 6-hourly schedule below) so an upload flood cannot sit on disk for a full day.
    private static final Duration GRACE = Duration.ofHours(6);

    private final AttachmentSupport attachmentSupport;

    public TrainingAttachmentCleanupScheduler(AttachmentSupport attachmentSupport) {
        this.attachmentSupport = attachmentSupport;
    }

    /** Every 6 hours at :20 (exact hour is not important for cleanup; pairs with the 6h grace). */
    @Scheduled(cron = "0 20 */6 * * *")
    @Transactional
    public void sweepAbandonedUploads() {
        int deleted = attachmentSupport.sweepOrphanUploads(GRACE);
        if (deleted > 0) {
            log.info("Swept {} abandoned training-material upload(s)", deleted);
        } else {
            log.debug("No abandoned training-material uploads to sweep");
        }
    }

    /**
     * Daily at 3:45 — the retention promise: a file attached to a training goes a year after it was
     * attached. Ten minutes after the comment-file sweep at 3:35 rather than alongside it, so the
     * two deletions are told apart in the log instead of arriving in the same second.
     *
     * <p>Separate from the orphan sweep above even though both end in a delete: that one answers
     * "nobody ever saved this", this one "its year is up", and the day one of those windows is
     * reconsidered the other must not move with it.
     */
    @Scheduled(cron = "0 45 3 * * *")
    @Transactional
    public void sweepExpiredMaterials() {
        int deleted = attachmentSupport.deleteExpired();
        if (deleted > 0) {
            log.info("Removed {} expired training material(s)", deleted);
        } else {
            log.debug("No training materials past their retention window");
        }
    }
}
