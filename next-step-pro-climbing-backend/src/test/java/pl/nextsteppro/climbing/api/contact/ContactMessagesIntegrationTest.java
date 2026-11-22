package pl.nextsteppro.climbing.api.contact;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.nextsteppro.climbing.api.auth.AccountConfirmation;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;
import pl.nextsteppro.climbing.domain.contact.ContactContextType;
import pl.nextsteppro.climbing.domain.contact.ContactConversation;
import pl.nextsteppro.climbing.domain.contact.ContactConversationRepository;
import pl.nextsteppro.climbing.domain.contact.ContactMessage;
import pl.nextsteppro.climbing.domain.contact.ContactMessageRepository;
import pl.nextsteppro.climbing.domain.course.Course;
import pl.nextsteppro.climbing.domain.course.CourseRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.infrastructure.i18n.LocalizedMessage;
import pl.nextsteppro.climbing.infrastructure.mail.ContactMailService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The "write to us" conversation end to end at the service layer: what is stored, which
 * conversation it lands in, who gets mailed, and when a guest's history moves to an account.
 * The team's side is in {@code AdminContactIntegrationTest}, next to its package-private DTOs.
 */
class ContactMessagesIntegrationTest extends BaseIntegrationTest {

    @Autowired private ContactService contactService;
    @Autowired private ContactRetentionService retentionService;
    @Autowired private ContactConversationRepository conversationRepository;
    @Autowired private ContactMessageRepository messageRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private AccountConfirmation accountConfirmation;
    @Autowired private pl.nextsteppro.climbing.api.user.UserService userService;

    @MockitoBean private ContactMailService contactMailService;

    @Test
    void shouldStoreAGuestMessageAndForwardItWithTheSenderAsTheOneToReplyTo() {
        String email = uniqueEmail();

        contactService.send(null, uniqueIp(), guest("Ola", email, "Czy macie kurs dla dzieci?"));

        ContactConversation conversation = conversationRepository.findByGuestEmail(email).orElseThrow();
        assertNull(conversation.getUser());
        assertEquals("Ola", conversation.getGuestName());
        assertTrue(conversation.isUnreadByStaff());
        List<ContactMessage> messages = messageRepository.findByConversationId(conversation.getId());
        assertEquals(1, messages.size());
        assertEquals(ContactContextType.GENERAL, messages.getFirst().getContextType());
        verify(contactMailService).sendInboundToStaff(eq(conversation.getId()), eq("Ola"), eq(email), eq(false),
            eq("Czy macie kurs dla dzieci?"), any(), eq("pl"));
    }

    @Test
    void shouldStoreAGuestNameOnOneLine() {
        String email = uniqueEmail();

        contactService.send(null, uniqueIp(), guest("Ola\r\nBcc: someone@example.com\t K.", email, "Hej"));

        assertEquals("Ola Bcc: someone@example.com K.",
            conversationRepository.findByGuestEmail(email).orElseThrow().getGuestName(),
            "the name goes into a mail subject and a one-line list row");
    }

    @Test
    void shouldKeepOneConversationPerGuestAddressWhateverItsCase() {
        String email = uniqueEmail();

        contactService.send(null, uniqueIp(), guest("Ola", email, "Pierwsza"));
        contactService.send(null, uniqueIp(), guest("Ola K.", email.toUpperCase(), "Druga"));

        ContactConversation conversation = conversationRepository.findByGuestEmail(email).orElseThrow();
        assertEquals(2, messageRepository.findByConversationId(conversation.getId()).size());
        assertEquals("Ola K.", conversation.getGuestName(), "the name typed last is the one shown");
    }

    @Test
    void shouldDropAHoneypotSubmissionWithoutStoringOrMailingAnything() {
        String email = uniqueEmail();

        contactService.send(null, uniqueIp(), bot("Bot", email, "Buy now"));

        assertTrue(conversationRepository.findByGuestEmail(email).isEmpty());
        verify(contactMailService, never()).sendInboundToStaff(any(), anyString(), anyString(), anyBoolean(),
            anyString(), any(), anyString());
    }

    @Test
    void shouldRefuseAGuestWhoDidNotConsent() {
        var request = guestWithoutConsent("Ola", uniqueEmail(), "Hej");

        var refusal = assertThrows(LocalizedMessage.Rejected.class,
            () -> contactService.send(null, uniqueIp(), request));
        assertEquals("contact.consent.required", refusal.messageKey());
    }

    @Test
    void shouldCapGuestsPerAddressPerHour() {
        String ip = uniqueIp();
        for (int i = 0; i < 5; i++) {
            contactService.send(null, ip, guest("Ola", uniqueEmail(), "Wiadomość " + i));
        }

        var refusal = assertThrows(LocalizedMessage.Conflict.class,
            () -> contactService.send(null, ip, guest("Ola", uniqueEmail(), "Szósta")));
        assertEquals("contact.too.many", refusal.messageKey());
    }

    @Test
    void shouldPutEveryMessageOfASignedInPersonIntoTheirOneConversationWithItsOwnContext() {
        User user = userRepository.save(verifiedUser(uniqueEmail()));
        Course course = publishedCourse("Kurs wspinaczki od podstaw");
        LocalDate day = LocalDate.now(ZoneId.of("Europe/Warsaw")).plusDays(10);

        contactService.send(user.getId(), uniqueIp(), aboutCourse("Ile osób w grupie?", course.getId()));
        contactService.send(user.getId(), uniqueIp(), aboutDay("Czy ten dzień jest wolny?", day));

        ContactConversation conversation = conversationRepository.findByUserId(user.getId()).orElseThrow();
        List<ContactMessage> messages = messageRepository.findByConversationId(conversation.getId());
        assertEquals(2, messages.size());
        assertEquals(ContactContextType.COURSE, messages.get(0).getContextType());
        assertEquals("Kurs wspinaczki od podstaw", messages.get(0).getCourseTitle());
        assertEquals(ContactContextType.CALENDAR, messages.get(1).getContextType());
        assertEquals(day, messages.get(1).getContextDate());
        verify(contactMailService).sendInboundToStaff(eq(conversation.getId()), eq(user.getFullName()),
            eq(user.getEmail()), eq(true), eq("Ile osób w grupie?"),
            eq(new ContactMailService.Context("COURSE", course.getId(), "Kurs wspinaczki od podstaw", null)), eq("pl"));
    }

    @Test
    void shouldKeepACalendarQuestionWithoutADayRatherThanInventOne() {
        String email = uniqueEmail();
        var request = new SendContactMessageRequest("Ola", email, "Macie wolne terminy w grudniu?", true, null,
            ContactContextType.CALENDAR, null, null, "pl");

        contactService.send(null, uniqueIp(), request);

        ContactConversation conversation = conversationRepository.findByGuestEmail(email).orElseThrow();
        ContactMessage message = messageRepository.findByConversationId(conversation.getId()).getFirst();
        assertEquals(ContactContextType.CALENDAR, message.getContextType());
        assertNull(message.getContextDate(), "no day was selected, so none may be stored");
        messageRepository.flush(); // the CHECK constraint must accept it, not only the entity
    }

    @Test
    void shouldRefuseAQuestionAboutACourseNobodyCanSee() {
        Course hidden = new Course("Szkic kursu");
        courseRepository.save(hidden);
        var request = new SendContactMessageRequest("Ola", uniqueEmail(), "Pytanie", true, null,
            ContactContextType.COURSE, hidden.getId(), null, "pl");

        var refusal = assertThrows(LocalizedMessage.Rejected.class,
            () -> contactService.send(null, uniqueIp(), request));
        assertEquals("contact.course.not.found", refusal.messageKey());
    }

    @Test
    void shouldHandAGuestConversationToTheAccountThatConfirmsItsAddress() {
        String email = uniqueEmail();
        contactService.send(null, uniqueIp(), guest("Ola", email, "Pytanie jako gość"));
        User user = userRepository.save(new User(email.toUpperCase(), "Ola", "Nowak", "+48123456789", "ola" + UUID.randomUUID().toString().substring(0, 6)));

        accountConfirmation.confirm(user, AccountConfirmation.ConfirmationSource.EMAIL_LINK);
        conversationRepository.flush();

        ContactConversation conversation = conversationRepository.findByUserId(user.getId()).orElseThrow();
        assertNull(conversation.getGuestEmail());
        assertEquals(1, messageRepository.findByConversationId(conversation.getId()).size());
        assertTrue(conversationRepository.findByGuestEmail(email).isEmpty());
    }

    @Test
    void shouldMergeAGuestConversationIntoAnAccountThatAlreadyHasOne() {
        String email = uniqueEmail();
        User user = userRepository.save(new User(email, "Ola", "Nowak", "+48123456789", "ola" + UUID.randomUUID().toString().substring(0, 6)));
        contactService.send(user.getId(), uniqueIp(), general("Z konta"));
        // Guest conversation with the same address, as if the account had been unconfirmed then.
        conversationRepository.save(ContactConversation.forGuest(email, "Ola", Instant.now()));
        ContactConversation guest = conversationRepository.findByGuestEmail(email).orElseThrow();
        messageRepository.save(ContactMessage.general(guest, "Jako gość", "pl", Instant.now()));

        accountConfirmation.confirm(user, AccountConfirmation.ConfirmationSource.EMAIL_LINK);
        conversationRepository.flush();

        ContactConversation own = conversationRepository.findByUserId(user.getId()).orElseThrow();
        assertEquals(2, messageRepository.findByConversationId(own.getId()).size());
        assertTrue(conversationRepository.findByGuestEmail(email).isEmpty());
        assertTrue(userRepository.findById(user.getId()).orElseThrow().getEmailVerifiedAt() != null,
            "the confirmation itself must survive the merge");
    }

    @Test
    void shouldEraseWhatAConfirmedPersonWroteAsAGuestWhenTheyDeleteTheirAccount() {
        String email = uniqueEmail();
        User user = userRepository.save(verifiedUser(email));
        // Signed out, writing from the contact page under their own address
        contactService.send(null, uniqueIp(), guest("Jan", email, "Pytanie bez logowania"));
        conversationRepository.flush();

        userService.deleteAccount(user.getId(), null);
        conversationRepository.flush();

        assertTrue(conversationRepository.findByGuestEmail(email).isEmpty(),
            "the policy promises messages go with the account");
    }

    @Test
    void shouldNotEraseAStrangersGuestConversationWhenAnUnconfirmedAccountGoes() {
        String email = uniqueEmail();
        contactService.send(null, uniqueIp(), guest("Prawdziwa Ola", email, "Moja wiadomość"));
        // Somebody registered with Ola's address and never confirmed it
        User impostor = userRepository.save(new User(email, "Ktoś", "Inny", "+48123456789", "x" + UUID.randomUUID().toString().substring(0, 6)));
        conversationRepository.flush();

        userService.deleteAccount(impostor.getId(), null);
        conversationRepository.flush();

        assertTrue(conversationRepository.findByGuestEmail(email).isPresent());
    }

    @Test
    void shouldDeleteMessagesOlderThanAYearAndTheConversationsLeftEmpty() {
        String email = uniqueEmail();
        contactService.send(null, uniqueIp(), guest("Ola", email, "Stara wiadomość"));
        UUID conversationId = conversationRepository.findByGuestEmail(email).orElseThrow().getId();
        conversationRepository.flush();

        retentionService.deleteExpired(Instant.now().plus(Duration.ofDays(366)));

        assertTrue(messageRepository.findByConversationId(conversationId).isEmpty());
        assertTrue(conversationRepository.findById(conversationId).isEmpty());
    }

    @Test
    void shouldKeepMessagesYoungerThanAYear() {
        String email = uniqueEmail();
        contactService.send(null, uniqueIp(), guest("Ola", email, "Świeża wiadomość"));
        UUID conversationId = conversationRepository.findByGuestEmail(email).orElseThrow().getId();
        conversationRepository.flush();

        retentionService.deleteExpired(Instant.now().plus(Duration.ofDays(300)));

        assertEquals(1, messageRepository.findByConversationId(conversationId).size());
    }

    private Course publishedCourse(String title) {
        Course course = new Course(title);
        course.setPublished(true);
        course.setPublishedAt(Instant.now());
        return courseRepository.save(course);
    }

    static SendContactMessageRequest guest(String name, String email, String body) {
        return new SendContactMessageRequest(name, email, body, true, null, null, null, null, "pl");
    }

    static SendContactMessageRequest guestWithoutConsent(String name, String email, String body) {
        return new SendContactMessageRequest(name, email, body, false, null, null, null, null, "pl");
    }

    /** What a form-filling bot sends: every field, the hidden one included. */
    static SendContactMessageRequest bot(String name, String email, String body) {
        return new SendContactMessageRequest(name, email, body, true, "https://spam.example", null, null, null, "pl");
    }

    static SendContactMessageRequest general(String body) {
        return new SendContactMessageRequest(null, null, body, false, null, ContactContextType.GENERAL, null, null, "pl");
    }

    static SendContactMessageRequest aboutCourse(String body, UUID courseId) {
        return new SendContactMessageRequest(null, null, body, false, null, ContactContextType.COURSE, courseId, null, "pl");
    }

    static SendContactMessageRequest aboutDay(String body, LocalDate day) {
        return new SendContactMessageRequest(null, null, body, false, null, ContactContextType.CALENDAR, null, day, "pl");
    }

    static User verifiedUser(String email) {
        User user = new User(email, "Jan", "Kowalski", "+48123456789", "jan" + UUID.randomUUID().toString().substring(0, 6));
        user.markEmailVerified();
        user.setRole(UserRole.USER);
        return user;
    }

    static String uniqueEmail() {
        return "contact-" + UUID.randomUUID() + "@example.com";
    }

    /** The send throttle is a singleton keyed on the address, so every test writes from its own. */
    static String uniqueIp() {
        return "test-" + UUID.randomUUID();
    }
}
