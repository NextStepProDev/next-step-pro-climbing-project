package pl.nextsteppro.climbing.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.nextsteppro.climbing.api.admin.settlement.AdminSubscriptionService;

/**
 * Bills standing coaching fees for every month a subscription covers.
 *
 * <p>⚠️ It <b>calls another bean</b> rather than carrying {@code @Transactional} itself. A
 * {@code @Scheduled} method annotated on its own class bypasses the AOP proxy and the transaction
 * quietly does not happen — and a test that calls the method directly still passes, which is exactly
 * how that bug survives. Same shape as {@code CommentFileRetentionScheduler}.
 *
 * <p>Runs daily rather than monthly, and the work it triggers catches up on every month it finds
 * missing. A box that fails to come up on the first would otherwise lose that month's fee silently
 * and for ever; the unique index on (user, month) makes revisiting old months free.
 *
 * <p>03:50 — after the comment-file sweep at 03:35, so the two do not start together.
 */
@Component
public class SubscriptionBillingScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionBillingScheduler.class);

    private final AdminSubscriptionService subscriptionService;

    public SubscriptionBillingScheduler(AdminSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Scheduled(cron = "0 50 3 * * *")
    public void billDueMonths() {
        int created = subscriptionService.billDueMonths();
        if (created > 0) {
            log.info("Billed {} standing coaching fees", created);
        }
    }
}
