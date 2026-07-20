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

    // A file uploaded but not attached within this window is considered abandoned
    private static final Duration GRACE = Duration.ofHours(24);

    private final AttachmentSupport attachmentSupport;

    public TrainingAttachmentCleanupScheduler(AttachmentSupport attachmentSupport) {
        this.attachmentSupport = attachmentSupport;
    }

    /** Daily at 03:20 (Warsaw = container UTC + offset; exact hour is not important for cleanup). */
    @Scheduled(cron = "0 20 3 * * *")
    @Transactional
    public void sweepAbandonedUploads() {
        int deleted = attachmentSupport.sweepOrphanUploads(GRACE);
        if (deleted > 0) {
            log.info("Swept {} abandoned training-material upload(s)", deleted);
        } else {
            log.debug("No abandoned training-material uploads to sweep");
        }
    }
}
