package pl.nextsteppro.climbing.api.admin.contact;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.nextsteppro.climbing.domain.contact.ContactConversation;
import pl.nextsteppro.climbing.domain.contact.ContactConversationRepository;
import pl.nextsteppro.climbing.domain.contact.ContactMessage;
import pl.nextsteppro.climbing.domain.contact.ContactMessageRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.infrastructure.mail.ContactMailService;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AdminContactIntegrationTest extends BaseIntegrationTest {

    @Autowired private AdminContactService adminContactService;
    @Autowired private ContactConversationRepository conversationRepository;
    @Autowired private ContactMessageRepository messageRepository;

    @MockitoBean private ContactMailService contactMailService;

    @Test
    void shouldMailAGuestTheReplyInTheLanguageTheyWroteIn() {
        User admin = admin();
        ContactConversation conversation = guestConversation("en");

        ReplyResultDto result = adminContactService.reply(conversation.getId(), admin.getId(), "  We do, on Saturdays.  ");

        assertTrue(result.mailed());
        assertEquals("We do, on Saturdays.", result.message().body());
        assertEquals(admin.getFullName(), result.message().staffAuthorName());
        verify(contactMailService).sendReply(eq(conversation.getGuestEmail()), eq("Ola"),
            eq("We do, on Saturdays."), eq("en"));
    }

    @Test
    void shouldCountReplyingAsReadingAndAsAnswering() {
        User admin = admin();
        ContactConversation conversation = guestConversation("pl");
        assertTrue(adminContactService.get(conversation.getId()).summary().unread());

        adminContactService.reply(conversation.getId(), admin.getId(), "Odpowiedź");

        ConversationSummaryDto summary = adminContactService.get(conversation.getId()).summary();
        assertFalse(summary.unread());
        assertTrue(summary.answered());
        assertTrue(summary.previewFromStaff());
    }

    @Test
    void shouldLightTheBadgeAgainWhenThePersonWritesAfterTheReply() {
        User admin = admin();
        ContactConversation conversation = guestConversation("pl");
        adminContactService.reply(conversation.getId(), admin.getId(), "Odpowiedź");

        Instant later = Instant.now().plusSeconds(5);
        messageRepository.save(ContactMessage.general(conversation, "Dziękuję, a w niedzielę?", "pl", later));
        conversation.recordInbound(later);

        ConversationSummaryDto summary = adminContactService.get(conversation.getId()).summary();
        assertTrue(summary.unread());
        assertFalse(summary.answered());
    }

    @Test
    void shouldNeverMailAnAccountHolderEvenWithMailTurnedOnButStillLightTheirBadge() {
        User admin = admin();
        User client = new User(uniqueEmail(), "Jan", "Kowalski", "+48123456789", nick());
        // On purpose: the switch for every other mail does not reach this in-app chat.
        client.setEmailNotificationsEnabled(true);
        userRepository.save(client);
        ContactConversation conversation = conversationRepository.save(ContactConversation.forUser(client, Instant.now()));
        messageRepository.save(ContactMessage.general(conversation, "Pytanie", "pl", Instant.now()));

        ReplyResultDto result = adminContactService.reply(conversation.getId(), admin.getId(), "Odpowiedź");

        assertFalse(result.mailed());
        verify(contactMailService, never()).sendReply(anyString(), anyString(), anyString(), anyString());
        assertTrue(conversationRepository.findById(conversation.getId()).orElseThrow().isUnseenByUser());
    }

    @Test
    void shouldListOnlyUnreadConversationsWhenAskedTo() {
        User admin = admin();
        ContactConversation read = guestConversation("pl");
        ContactConversation unread = guestConversation("pl");
        adminContactService.markRead(read.getId());
        conversationRepository.flush();

        var page = adminContactService.list(true, 0, 100);

        assertTrue(page.content().stream().anyMatch(c -> c.id().equals(unread.getId())));
        assertTrue(page.content().stream().noneMatch(c -> c.id().equals(read.getId())));
        assertNotNull(admin);
    }

    @Test
    void shouldDeleteAConversationWithItsMessages() {
        ContactConversation conversation = guestConversation("pl");

        adminContactService.delete(conversation.getId());
        conversationRepository.flush();

        assertTrue(conversationRepository.findById(conversation.getId()).isEmpty());
        assertTrue(messageRepository.findByConversationId(conversation.getId()).isEmpty());
    }

    private ContactConversation guestConversation(String language) {
        ContactConversation conversation = conversationRepository.save(
            ContactConversation.forGuest(uniqueEmail(), "Ola", Instant.now()));
        messageRepository.save(ContactMessage.general(conversation, "Czy macie zajęcia dla dzieci?", language, Instant.now()));
        return conversation;
    }

    private User admin() {
        User admin = new User(uniqueEmail(), "Trener", "Admin", "+48123456789", nick());
        admin.setRole(UserRole.ADMIN);
        return userRepository.save(admin);
    }

    private static String uniqueEmail() {
        return "contact-admin-" + UUID.randomUUID() + "@example.com";
    }

    private static String nick() {
        return "n" + UUID.randomUUID().toString().substring(0, 8);
    }
}
