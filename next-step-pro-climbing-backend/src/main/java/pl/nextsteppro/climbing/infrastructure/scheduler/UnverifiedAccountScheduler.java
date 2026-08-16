package pl.nextsteppro.climbing.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.nextsteppro.climbing.api.auth.UnverifiedAccountRetentionService;

/**
 * Removes accounts that were registered and never confirmed, warning their owner a day earlier.
 *
 * <p>Both passes live on {@link UnverifiedAccountRetentionService} and this class only calls them:
 * a {@code @Scheduled} method invoking {@code @Transactional} methods on its own class would bypass
 * the Spring proxy and run them without a transaction — silently, and invisibly to tests that call
 * those methods directly.
 *
 * <p>The reminder runs first on purpose. Both passes read the same clock reading only in spirit —
 * each takes its own {@code Instant.now()} — but the bands do not overlap ({@code [6d, 7d)} versus
 * {@code >= 7d}), so no account can be warned and deleted by the same run whichever order they run
 * in; going reminder-first simply keeps the reading order the same as the story.
 */
@Component
public class UnverifiedAccountScheduler {

    private static final Logger log = LoggerFactory.getLogger(UnverifiedAccountScheduler.class);

    private final UnverifiedAccountRetentionService retention;

    public UnverifiedAccountScheduler(UnverifiedAccountRetentionService retention) {
        this.retention = retention;
    }

    /** Daily at 04:15 — an idle hour, offset from the other jobs so they do not pile up. */
    @Scheduled(cron = "0 15 4 * * *")
    public void sweep() {
        int reminded = retention.sendReminders();
        int deleted = retention.deleteExpired();
        if (reminded > 0 || deleted > 0) {
            log.info("Unverified account sweep: {} reminder(s) sent, {} account(s) deleted", reminded, deleted);
        } else {
            log.debug("No unverified accounts to sweep");
        }
    }
}
