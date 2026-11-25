package pl.nextsteppro.climbing.api.contact;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.nextsteppro.climbing.domain.contact.ContactConversation;
import pl.nextsteppro.climbing.domain.contact.ContactConversationRepository;
import pl.nextsteppro.climbing.domain.contact.ContactMessage;
import pl.nextsteppro.climbing.domain.contact.ContactMessageRepository;
import pl.nextsteppro.climbing.domain.contact.MessageWindow;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.mail.ContactMailService;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The chat window reads a conversation in pages: the newest page first, older ones on scroll,
 * only what is new on each poll. The one thing that must never happen is a message lost or shown
 * twice at a page boundary - so every test here reads the whole conversation through the windows
 * and compares it with the plain full read.
 */
class ContactChatWindowIntegrationTest extends BaseIntegrationTest {

    @Autowired private ContactService contactService;
    @Autowired private ContactConversationRepository conversationRepository;
    @Autowired private ContactMessageRepository messageRepository;

    @MockitoBean private ContactMailService contactMailService;

    @Test
    void shouldOpenOnTheNewestPageAndReachTheFirstMessageByScrollingWithoutGapsOrRepeats() {
        User user = verifiedUser();
        ContactConversation conversation = conversationOf(user);
        Instant start = Instant.now().minus(2, ChronoUnit.DAYS);
        int total = MessageWindow.PAGE_SIZE * 2 + 5;
        for (int i = 0; i < total; i++) {
            write(conversation, "wiadomość " + i, start.plusSeconds(i));
        }
        conversationRepository.flush();

        MyConversationDto opened = contactService.getMine(user.getId());
        assertEquals(MessageWindow.PAGE_SIZE, opened.messages().size());
        assertTrue(opened.hasOlder());
        assertEquals("wiadomość " + (total - 1), opened.messages().getLast().body(), "newest at the bottom");

        List<UUID> seen = new ArrayList<>(opened.messages().stream().map(ConversationMessageDto::id).toList());
        ConversationMessageDto oldest = opened.messages().getFirst();
        boolean hasOlder = opened.hasOlder();
        while (hasOlder) {
            MyMessagePageDto page = contactService.olderMine(user.getId(), oldest.createdAt(), oldest.id());
            seen.addAll(0, page.messages().stream().map(ConversationMessageDto::id).toList());
            oldest = page.messages().getFirst();
            hasOlder = page.hasOlder();
        }

        assertEquals(allIds(conversation), seen);
    }

    // Two messages can carry the same instant (a flood, a merge, a clock tick). A cursor on time
    // alone would skip every tied message on the far side of a page boundary for good.
    @Test
    void shouldNotLoseOrRepeatMessagesThatShareATimestampAcrossAPageBoundary() {
        User user = verifiedUser();
        ContactConversation conversation = conversationOf(user);
        Instant same = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        for (int i = 0; i < MessageWindow.PAGE_SIZE + 7; i++) {
            write(conversation, "remis " + i, same);
        }
        conversationRepository.flush();

        MyConversationDto opened = contactService.getMine(user.getId());
        ConversationMessageDto oldest = opened.messages().getFirst();
        MyMessagePageDto older = contactService.olderMine(user.getId(), oldest.createdAt(), oldest.id());

        List<UUID> seen = new ArrayList<>(older.messages().stream().map(ConversationMessageDto::id).toList());
        seen.addAll(opened.messages().stream().map(ConversationMessageDto::id).toList());
        assertFalse(older.hasOlder());
        assertEquals(new HashSet<>(seen).size(), seen.size(), "no message twice");
        assertEquals(allIds(conversation), seen, "every message, in the same order as the full read");
    }

    @Test
    void shouldPollOnlyWhatArrivedAfterTheNewestMessageTheChatHas() {
        User user = verifiedUser();
        ContactConversation conversation = conversationOf(user);
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        write(conversation, "pierwsza", start);
        ContactMessage newest = write(conversation, "druga", start.plusSeconds(1));
        conversationRepository.flush();

        MyConversationUpdateDto nothing = contactService.newerMine(user.getId(), newest.getCreatedAt(), newest.getId());
        assertTrue(nothing.messages().isEmpty(), "an idle poll carries no history");

        ContactMessage arrived = write(conversation, "trzecia", start.plusSeconds(2));
        conversationRepository.flush();
        MyConversationUpdateDto update = contactService.newerMine(user.getId(), newest.getCreatedAt(), newest.getId());
        assertEquals(List.of(arrived.getId()), update.messages().stream().map(ConversationMessageDto::id).toList());
    }

    @Test
    void shouldCapOnePollAndCarryOnFromWhereItStopped() {
        User user = verifiedUser();
        ContactConversation conversation = conversationOf(user);
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        ContactMessage first = write(conversation, "start", start);
        for (int i = 1; i <= MessageWindow.NEWER_CAP + 3; i++) {
            write(conversation, "potop " + i, start.plusMillis(i));
        }
        conversationRepository.flush();

        MyConversationUpdateDto firstPoll = contactService.newerMine(user.getId(), first.getCreatedAt(), first.getId());
        assertEquals(MessageWindow.NEWER_CAP, firstPoll.messages().size());
        ConversationMessageDto last = firstPoll.messages().getLast();
        MyConversationUpdateDto secondPoll = contactService.newerMine(user.getId(), last.createdAt(), last.id());
        assertEquals(3, secondPoll.messages().size());
    }

    // The conversation comes from the token; a cursor is only a position. A cursor lifted from
    // somebody else's chat must still read the caller's own conversation and nothing else.
    @Test
    void shouldReadOnlyTheCallersOwnConversationWhateverTheCursor() {
        User owner = verifiedUser();
        User other = verifiedUser();
        ContactConversation mine = conversationOf(owner);
        ContactConversation theirs = conversationOf(other);
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        write(mine, "moja", start);
        ContactMessage theirMessage = write(theirs, "cudza", start.plusSeconds(1));
        conversationRepository.flush();

        MyMessagePageDto older = contactService.olderMine(owner.getId(), Instant.now().plus(1, ChronoUnit.DAYS), theirMessage.getId());
        MyConversationUpdateDto newer = contactService.newerMine(owner.getId(), Instant.EPOCH, theirMessage.getId());

        assertEquals(List.of("moja"), older.messages().stream().map(ConversationMessageDto::body).toList());
        assertEquals(List.of("moja"), newer.messages().stream().map(ConversationMessageDto::body).toList());
    }

    @Test
    void shouldAnswerAPersonWithNoConversationWithAnEmptyWindow() {
        User user = verifiedUser();

        assertTrue(contactService.getMine(user.getId()).messages().isEmpty());
        assertFalse(contactService.olderMine(user.getId(), Instant.now(), UUID.randomUUID()).hasOlder());
        assertTrue(contactService.newerMine(user.getId(), Instant.now(), UUID.randomUUID()).messages().isEmpty());
    }

    private List<UUID> allIds(ContactConversation conversation) {
        return messageRepository.findByConversationId(conversation.getId()).stream().map(ContactMessage::getId).toList();
    }

    private ContactMessage write(ContactConversation conversation, String body, Instant at) {
        ContactMessage message = messageRepository.save(ContactMessage.general(conversation, body, "pl", at));
        conversation.recordInbound(at);
        return message;
    }

    private ContactConversation conversationOf(User user) {
        return conversationRepository.save(ContactConversation.forUser(user, Instant.now().minus(3, ChronoUnit.DAYS)));
    }

    private User verifiedUser() {
        String email = "chat-window-" + UUID.randomUUID() + "@example.com";
        User user = new User(email, "Ala", "Okno", "+48123456789", "n" + UUID.randomUUID().toString().substring(0, 8));
        user.setEmailVerified(true);
        return userRepository.save(user);
    }
}
