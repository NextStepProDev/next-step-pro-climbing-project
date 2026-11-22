package pl.nextsteppro.climbing.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.nextsteppro.climbing.api.contact.ContactRetentionService;

import java.time.Instant;

/** Deletes "write to us" messages older than a year; see {@link ContactRetentionService}. */
@Component
public class ContactRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContactRetentionScheduler.class);

    private final ContactRetentionService retention;

    public ContactRetentionScheduler(ContactRetentionService retention) {
        this.retention = retention;
    }

    /** Daily at 03:55 - an idle hour, offset from the other sweeps. */
    @Scheduled(cron = "0 55 3 * * *", zone = "UTC")
    public void sweep() {
        int deleted = retention.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Contact message sweep: {} message(s) older than a year removed", deleted);
        }
    }
}
