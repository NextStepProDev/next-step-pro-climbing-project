package pl.nextsteppro.climbing.api.contact;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.nextsteppro.climbing.domain.contact.ContactConversation;
import pl.nextsteppro.climbing.domain.contact.ContactConversationRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.infrastructure.mail.ContactMailService;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * One reminder by mail for an answer an account holder has not opened in 48 h, daytime only. Every test passes
 * "now" explicitly, so nothing waits for a clock.
 */
class ContactUnseenReminderIntegrationTest extends BaseIntegrationTest {

    @Autowired private ContactUnseenReminderService reminders;
    @Autowired private ContactConversationRepository conversationRepository;

    @MockitoBean private ContactMailService contactMailService;

    // Noon in Warsaw, fixed: reminders go out 9:00-20:00 only, and a "now" read from the clock
    // would make every test here red whenever the suite happens to run at night.
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private final Instant now = ZonedDateTime.of(2026, 11, 25, 12, 0, 0, 0, WARSAW).toInstant();

    @Test
    void shouldRemindOnceOfAnAnswerLeftUnseenForTwoDays() {
        User person = client(true);
        ContactConversation conversation = answeredAgo(person, Duration.ofHours(49));

        reminders.sendDue(now);
        reminders.sendDue(now.plus(1, ChronoUnit.HOURS));

        verify(contactMailService, times(1)).sendUnseenReminder(person.getEmail(), person.getFirstName(), person.getPreferredLanguage());
        assertEquals(now, conversation.getUnseenReminderSentAt());
    }

    @Test
    void shouldNotRemindBeforeTwoDaysHavePassed() {
        User person = client(true);
        answeredAgo(person, Duration.ofHours(47));

        assertEquals(0, reminders.sendDue(now));
        verifyNoReminder();
    }

    @Test
    void shouldNotRemindOfAnAnswerThePersonHasSeen() {
        User person = client(true);
        ContactConversation conversation = answeredAgo(person, Duration.ofHours(60));
        conversation.markSeenByUser(now.minus(10, ChronoUnit.HOURS));

        assertEquals(0, reminders.sendDue(now));
        verifyNoReminder();
    }

    // Several messages in a row are one conversation with one clock (the newest): one mail, not four.
    @Test
    void shouldRemindOnceOfSeveralMessagesSentInARow() {
        User person = client(true);
        ContactConversation conversation = answeredAgo(person, Duration.ofHours(52));
        conversation.recordReply(now.minus(51, ChronoUnit.HOURS));
        conversation.recordReply(now.minus(50, ChronoUnit.HOURS));
        conversation.recordReply(now.minus(49, ChronoUnit.HOURS));

        reminders.sendDue(now);
        reminders.sendDue(now.plus(1, ChronoUnit.HOURS));

        verify(contactMailService, times(1)).sendUnseenReminder(eq(person.getEmail()), anyString(), anyString());
    }

    // Somebody who does not react gets ONE mail, not one every few days: answers after a reminder
    // do not mail again until the person has looked in.
    @Test
    void shouldNotRemindAgainUntilThePersonHasOpenedTheChat() {
        User person = client(true);
        ContactConversation conversation = answeredAgo(person, Duration.ofHours(49));
        reminders.sendDue(now);

        Instant laterAnswer = now.plus(1, ChronoUnit.HOURS);
        conversation.recordReply(laterAnswer);

        assertEquals(0, reminders.sendDue(laterAnswer.plus(49, ChronoUnit.HOURS)));
        verify(contactMailService, times(1)).sendUnseenReminder(eq(person.getEmail()), anyString(), anyString());
    }

    @Test
    void shouldRemindOfANewAnswerAfterThePersonHasLookedIn() {
        User person = client(true);
        ContactConversation conversation = answeredAgo(person, Duration.ofHours(49));
        reminders.sendDue(now);

        conversation.markSeenByUser(now.plus(2, ChronoUnit.HOURS));          // they looked in
        Instant newAnswer = now.plus(3, ChronoUnit.HOURS);
        conversation.recordReply(newAnswer);                                  // a new answer they leave unread

        assertEquals(0, reminders.sendDue(newAnswer.plus(47, ChronoUnit.HOURS)), "not yet due");
        assertEquals(1, reminders.sendDue(newAnswer.plus(49, ChronoUnit.HOURS)));
        verify(contactMailService, times(2)).sendUnseenReminder(eq(person.getEmail()), anyString(), anyString());
    }

    // Due at night: nothing goes out and nothing is marked, so the first morning run sends it.
    @Test
    void shouldHoldANightReminderUntilTheMorning() {
        User person = client(true);
        Instant night = ZonedDateTime.of(2026, 11, 25, 23, 25, 0, 0, WARSAW).toInstant();
        ContactConversation conversation = conversationRepository.save(
            ContactConversation.forUser(person, night.minus(60, ChronoUnit.HOURS)));
        conversation.recordReply(night.minus(49, ChronoUnit.HOURS));
        conversationRepository.flush();

        assertEquals(0, reminders.sendDue(night));
        assertNull(conversation.getUnseenReminderSentAt());

        Instant morning = ZonedDateTime.of(2026, 11, 26, 9, 25, 0, 0, WARSAW).toInstant();
        assertEquals(1, reminders.sendDue(morning));
    }

    // In Warsaw's own time, so the window does not slide by an hour when summer time starts.
    @Test
    void shouldSendOnlyBetweenNineAndEightPmWarsawTimeInWinterAndSummer() {
        for (int month : new int[] {1, 7}) {
            assertFalse(ContactUnseenReminderService.isSendingHour(at(month, 8, 59)));
            assertTrue(ContactUnseenReminderService.isSendingHour(at(month, 9, 0)));
            assertTrue(ContactUnseenReminderService.isSendingHour(at(month, 19, 59)));
            assertFalse(ContactUnseenReminderService.isSendingHour(at(month, 20, 0)));
            assertFalse(ContactUnseenReminderService.isSendingHour(at(month, 3, 0)));
        }
    }

    private static Instant at(int month, int hour, int minute) {
        return ZonedDateTime.of(2026, month, 15, hour, minute, 0, 0, WARSAW).toInstant();
    }

    // The reminder is a notification, and the switch is the person's word on those. Left unmarked,
    // so turning the switch on within the week still reminds them.
    @Test
    void shouldRespectTheEmailNotificationSwitch() {
        User person = client(false);
        ContactConversation conversation = answeredAgo(person, Duration.ofHours(49));

        assertEquals(0, reminders.sendDue(now));
        verifyNoReminder();
        assertNull(conversation.getUnseenReminderSentAt());

        person.setEmailNotificationsEnabled(true);
        assertEquals(1, reminders.sendDue(now.plus(1, ChronoUnit.HOURS)));
    }

    @Test
    void shouldNotRemindAnUnconfirmedAccountOrAnAdmin() {
        User unconfirmed = userRepository.save(new User(email(), "Nie", "Potwierdzony", "+48123456789", nick()));
        User admin = client(true);
        admin.setRole(UserRole.ADMIN);
        answeredAgo(unconfirmed, Duration.ofHours(49));
        answeredAgo(admin, Duration.ofHours(49));

        assertEquals(0, reminders.sendDue(now));
        verifyNoReminder();
    }

    // Without the cap the first run after a deploy would remind people of answers from weeks ago.
    @Test
    void shouldNotRemindOfAnAnswerOlderThanAWeek() {
        User person = client(true);
        answeredAgo(person, ContactUnseenReminderService.MAX_AGE.plusHours(1));

        assertEquals(0, reminders.sendDue(now));
        verifyNoReminder();
    }

    @Test
    void shouldNeverRemindAGuestWhoseAnswerAlreadyWentByMail() {
        ContactConversation guest = conversationRepository.save(
            ContactConversation.forGuest(email(), "Gość", now.minus(60, ChronoUnit.HOURS)));
        guest.recordReply(now.minus(49, ChronoUnit.HOURS));
        conversationRepository.flush();

        assertEquals(0, reminders.sendDue(now));
        verifyNoReminder();
    }

    private void verifyNoReminder() {
        verify(contactMailService, never()).sendUnseenReminder(anyString(), anyString(), anyString());
    }

    private ContactConversation answeredAgo(User person, Duration ago) {
        ContactConversation conversation = conversationRepository.save(
            ContactConversation.forUser(person, now.minus(ago).minus(1, ChronoUnit.HOURS)));
        conversation.recordReply(now.minus(ago));
        conversationRepository.flush();
        return conversation;
    }

    private User client(boolean notificationsOn) {
        User user = new User(email(), "Ala", "Klientka", "+48123456789", nick());
        user.setEmailVerified(true);
        user.setEmailNotificationsEnabled(notificationsOn);
        return userRepository.save(user);
    }

    private static String email() {
        return "reminder-" + UUID.randomUUID() + "@example.com";
    }

    private static String nick() {
        return "n" + UUID.randomUUID().toString().substring(0, 8);
    }
}
