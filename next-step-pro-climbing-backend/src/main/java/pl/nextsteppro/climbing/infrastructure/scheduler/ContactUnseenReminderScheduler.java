package pl.nextsteppro.climbing.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.nextsteppro.climbing.api.contact.ContactUnseenReminderService;

import java.time.Instant;

/** Mails a reminder of a chat answer left unseen for 48 h; see {@link ContactUnseenReminderService}. */
@Component
public class ContactUnseenReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContactUnseenReminderScheduler.class);

    private final ContactUnseenReminderService reminders;

    public ContactUnseenReminderScheduler(ContactUnseenReminderService reminders) {
        this.reminders = reminders;
    }

    /**
     * Hourly at :25, offset from the other jobs. A reminder goes out at the first run inside the
     * daytime window (9:00-20:00 Warsaw, checked by the service) after it falls due; a run missed
     * during a restart is caught up by the next one, as due is "at least 48 h".
     */
    @Scheduled(cron = "0 25 * * * *", zone = "UTC")
    public void remind() {
        int sent = reminders.sendDue(Instant.now());
        if (sent > 0) {
            log.info("Chat reminders: {} unseen answer(s) reminded by mail", sent);
        }
    }
}
