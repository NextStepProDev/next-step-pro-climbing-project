package pl.nextsteppro.climbing.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.nextsteppro.climbing.api.trainingcalendar.CommentFileRetentionService;

/**
 * Deletes the attachments people send in a training thread once they are a year old, and reconciles
 * the folder against the rows.
 *
 * <p>Both passes live on {@link CommentFileRetentionService} rather than here, and this class only
 * calls them. A {@code @Scheduled} method invoking {@code @Transactional} methods on its own class
 * would bypass the Spring proxy and run them without a transaction — silently, and invisibly to
 * tests that call those methods directly.
 *
 * <p>Deliberately not merged with {@link TrainingAttachmentCleanupScheduler}: that one sweeps
 * abandoned coach-material uploads out of a different folder on a different policy, and one deleter
 * serving two retention rules is how the wrong files get removed.
 */
@Component
public class CommentFileRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(CommentFileRetentionScheduler.class);

    private final CommentFileRetentionService retention;

    public CommentFileRetentionScheduler(CommentFileRetentionService retention) {
        this.retention = retention;
    }

    /** Daily at 03:35 — an idle hour, and offset from the other jobs so they do not pile up. */
    @Scheduled(cron = "0 35 3 * * *")
    public void sweep() {
        int expired = retention.deleteExpired();
        int orphans = retention.deleteOrphans();
        if (expired > 0 || orphans > 0) {
            log.info("Comment attachment sweep: {} expired, {} orphaned file(s) removed", expired, orphans);
        } else {
            log.debug("No comment attachments to sweep");
        }
    }
}
