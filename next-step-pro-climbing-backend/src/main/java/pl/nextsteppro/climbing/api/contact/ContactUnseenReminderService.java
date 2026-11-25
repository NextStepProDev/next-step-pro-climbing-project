package pl.nextsteppro.climbing.api.contact;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.contact.ContactConversation;
import pl.nextsteppro.climbing.domain.contact.ContactConversationRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.mail.ContactMailService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * One mail reminder when an answer from the team has waited unseen in an account holder's chat
 * for {@link #DELAY} (decision 2026-11-25). The chat never mails its content; this mail says only
 * that an answer is waiting and links to the chat, so what people discuss stays in the app.
 *
 * <ul>
 *   <li><b>One reminder until the person opens the chat.</b> The clock is the team's NEWEST
 *       answer, so several messages in a row make one reminder, not one each. After a reminder,
 *       further answers do not mail again until the person has looked in; only an answer left
 *       unread after that visit earns the next one.</li>
 *   <li><b>Daytime only, {@link #SEND_FROM}-{@link #SEND_UNTIL} Warsaw time.</b> A reminder that
 *       falls due at night waits for the first run of the morning. Warsaw explicitly - the server
 *       runs in UTC, and a summer-time shift must not move the window by an hour.</li>
 *   <li><b>Only for someone who would want it:</b> a confirmed account (an unconfirmed one cannot
 *       sign in to read it), not an admin (no chat link), with e-mail notifications ON - the
 *       reminder is a notification, and the switch is the person's word on those. Somebody with
 *       the switch off is left unmarked, so turning it on within the week still reminds them.</li>
 *   <li><b>Nothing older than {@link #MAX_AGE}.</b> Without the cap the first run after a deploy
 *       would remind people of answers from weeks ago.</li>
 *   <li>Guests are out by construction: their answers already went out by mail.</li>
 * </ul>
 *
 * A separate bean from the scheduler: {@code @Scheduled} calling {@code @Transactional} on its own
 * class would run without a transaction.
 */
@Service
public class ContactUnseenReminderService {

    public static final Duration DELAY = Duration.ofHours(48);
    public static final Duration MAX_AGE = Duration.ofDays(7);
    public static final LocalTime SEND_FROM = LocalTime.of(9, 0);
    public static final LocalTime SEND_UNTIL = LocalTime.of(20, 0);
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final ContactConversationRepository conversationRepository;
    private final ContactMailService mailService;

    public ContactUnseenReminderService(ContactConversationRepository conversationRepository,
                                        ContactMailService mailService) {
        this.conversationRepository = conversationRepository;
        this.mailService = mailService;
    }

    /** Whether {@code now} falls in the daytime window, [9:00, 20:00) in Warsaw. */
    static boolean isSendingHour(Instant now) {
        LocalTime local = now.atZone(WARSAW).toLocalTime();
        return !local.isBefore(SEND_FROM) && local.isBefore(SEND_UNTIL);
    }

    /** @return reminders sent */
    @Transactional
    public int sendDue(Instant now) {
        // Outside the window nothing is marked either, so what is due waits for the morning run.
        if (!isSendingHour(now)) return 0;
        int sent = 0;
        for (ContactConversation conversation : conversationRepository.findDueForUnseenReminder(
                now.minus(DELAY), now.minus(MAX_AGE))) {
            User person = Objects.requireNonNull(conversation.getUser());
            if (!person.isEmailVerified() || person.isAdmin() || !person.isEmailNotificationsEnabled()) {
                continue;
            }
            // A failed send is logged by the dispatcher and not retried here: a reminder is worth
            // less than a duplicate. The mail is async, so a transaction failing after this point
            // could repeat one reminder an hour later - accepted, as nothing here writes anything else.
            conversation.markUnseenReminderSent(now);
            mailService.sendUnseenReminder(person.getEmail(), person.getFirstName(), person.getPreferredLanguage());
            sent++;
        }
        return sent;
    }
}
