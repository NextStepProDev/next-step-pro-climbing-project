package pl.nextsteppro.climbing.api.contact;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.domain.contact.ContactConversation;
import pl.nextsteppro.climbing.domain.contact.ContactConversationRepository;
import pl.nextsteppro.climbing.domain.contact.ContactMessage;
import pl.nextsteppro.climbing.domain.contact.ContactMessageFile;
import pl.nextsteppro.climbing.domain.contact.ContactMessageFileRepository;
import pl.nextsteppro.climbing.domain.contact.ContactMessageRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.i18n.LocalizedMessage;
import pl.nextsteppro.climbing.infrastructure.mail.ContactMailService;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;
import pl.nextsteppro.climbing.infrastructure.storage.TestImages;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Photos and PDFs in the chat (V111), the person's side. The rules that matter: only the
 * conversation's own account reads a file, the person removes only what they sent, and no path
 * that deletes messages leaves the bytes behind.
 */
class ContactChatFilesIntegrationTest extends BaseIntegrationTest {

    @Autowired private ContactService contactService;
    @Autowired private ContactConversationErasure erasure;
    @Autowired private ContactRetentionService retention;
    @Autowired private ContactConversationRepository conversationRepository;
    @Autowired private ContactMessageRepository messageRepository;
    @Autowired private ContactMessageFileRepository fileRepository;
    @Autowired private FileStorageService storage;

    @MockitoBean private ContactMailService contactMailService;

    @Test
    void shouldSendPhotosAndAPdfWithAMessageAndLightTheTeamsBadge() {
        User person = verifiedUser();

        ConversationMessageDto sent = contactService.sendInChat(person.getId(), "Zdjęcie drogi i plan", "pl",
            List.of(photo("droga.jpg"), pdf("plan.pdf")));

        assertEquals("Zdjęcie drogi i plan", sent.body());
        assertEquals(2, sent.files().size());
        assertTrue(sent.files().getFirst().url().startsWith("/api/conversation/files/"), "the person's own endpoint");
        assertEquals("image/jpeg", sent.files().getFirst().mimeType());
        assertNotNull(sent.files().getFirst().width(), "a picture reserves its space");
        assertNull(sent.files().get(1).width(), "a PDF has no dimensions");
        assertTrue(sent.files().getFirst().canDelete(), "what the person sent, they may withdraw");
        assertTrue(conversationRepository.findByUserId(person.getId()).orElseThrow().isUnreadByStaff());
    }

    @Test
    void shouldAcceptAMessageThatIsOnlyAFileButNotOneThatIsNothing() {
        User person = verifiedUser();

        ConversationMessageDto onlyFile = contactService.sendInChat(person.getId(), "   ", "pl", List.of(photo("a.jpg")));
        assertNull(onlyFile.body());
        assertEquals(1, onlyFile.files().size());

        var refusal = assertThrows(LocalizedMessage.Rejected.class,
            () -> contactService.sendInChat(person.getId(), " ", "pl", List.of()));
        assertEquals("contact.body.required", refusal.messageKey());
    }

    @Test
    void shouldRefuseMoreThanThreeFilesInOneMessage() {
        User person = verifiedUser();

        var refusal = assertThrows(LocalizedMessage.Rejected.class, () -> contactService.sendInChat(person.getId(), "x", "pl",
            List.of(photo("1.jpg"), photo("2.jpg"), photo("3.jpg"), photo("4.jpg"))));

        assertEquals("contact.file.too.many", refusal.messageKey());
        assertTrue(messageRepository.findByConversationId(conversationOf(person).getId()).isEmpty(),
            "refused before anything was written");
    }

    @Test
    void shouldStopAtTheConversationsCapOfFiles() {
        User person = verifiedUser();
        ContactConversation conversation = conversationRepository.save(ContactConversation.forUser(person, Instant.now()));
        ContactMessage holder = messageRepository.save(ContactMessage.general(conversation, "stare", "pl", Instant.now()));
        for (int i = 0; i < ContactFileSupport.MAX_PER_CONVERSATION - 1; i++) {
            fileRepository.save(new ContactMessageFile(holder, UUID.randomUUID() + ".jpg", "old.jpg", "image/jpeg",
                100, null, null, 0, Instant.now().plus(Duration.ofDays(30))));
        }

        var refusal = assertThrows(LocalizedMessage.Conflict.class, () -> contactService.sendInChat(person.getId(), "x", "pl",
            List.of(photo("1.jpg"), photo("2.jpg"))));
        assertEquals("contact.file.conversation.limit", refusal.messageKey());
    }

    // The conversation comes from the token: a file id from somebody else's chat reads nothing.
    @Test
    void shouldServeAFileOnlyToTheConversationsOwnAccount() {
        User owner = verifiedUser();
        User stranger = verifiedUser();
        UUID fileId = contactService.sendInChat(owner.getId(), null, "pl", List.of(photo("prywatne.jpg"))).files().getFirst().id();

        ContactFileSupport.FileStream mine = contactService.openMyFile(owner.getId(), fileId);
        assertEquals("image/jpeg", mine.mimeType());
        assertTrue(mine.size() > 0);

        var refusal = assertThrows(LocalizedMessage.Rejected.class, () -> contactService.openMyFile(stranger.getId(), fileId));
        assertEquals("contact.file.not.found", refusal.messageKey());
        assertThrows(LocalizedMessage.Rejected.class, () -> contactService.deleteMyFile(stranger.getId(), fileId));
        assertTrue(fileRepository.findById(fileId).isPresent());
    }

    @Test
    void shouldLetThePersonWithdrawOnlyWhatTheySent() {
        User person = verifiedUser();
        ConversationMessageDto sent = contactService.sendInChat(person.getId(), null, "pl", List.of(photo("moje.jpg")));
        UUID mineId = sent.files().getFirst().id();
        String filename = fileRepository.findById(mineId).orElseThrow().getFilename();

        contactService.deleteMyFile(person.getId(), mineId);
        fileRepository.flush();
        unlinkDeletedFiles(); // storage unlinks on COMMIT, and this test rolls back

        assertTrue(fileRepository.findById(mineId).isEmpty());
        assertTrue(messageRepository.findById(sent.id()).isEmpty(), "a message that was only the file goes with it");
        assertFalse(storage.exists(filename, ContactFileSupport.FOLDER), "the bytes go too");
    }

    @Test
    void shouldTakeTheFilesOffTheDiskWhenTheAccountIsErased() {
        User person = verifiedUser();
        UUID fileId = contactService.sendInChat(person.getId(), "x", "pl", List.of(photo("a.jpg"))).files().getFirst().id();
        String filename = fileRepository.findById(fileId).orElseThrow().getFilename();

        erasure.eraseChatOf(person);
        unlinkDeletedFiles();

        assertFalse(storage.exists(filename, ContactFileSupport.FOLDER));
    }

    @Test
    void shouldTakeAFileOffWhenItsYearIsUp() {
        User person = verifiedUser();
        ContactConversation conversation = conversationRepository.save(ContactConversation.forUser(person, Instant.now()));
        ContactMessage message = messageRepository.save(ContactMessage.general(conversation, "z tekstem", "pl", Instant.now()));
        ContactMessageFile expired = fileRepository.save(new ContactMessageFile(message, UUID.randomUUID() + ".jpg",
            "stare.jpg", "image/jpeg", 100, null, null, 0, Instant.now().minus(Duration.ofDays(1))));
        fileRepository.flush();

        retention.deleteExpired(Instant.now());
        fileRepository.flush();

        assertTrue(fileRepository.findById(expired.getId()).isEmpty());
        assertTrue(messageRepository.findById(message.getId()).isPresent(), "a message with text keeps its words");
    }

    private ContactConversation conversationOf(User person) {
        return conversationRepository.findByUserId(person.getId())
            .orElseGet(() -> conversationRepository.save(ContactConversation.forUser(person, Instant.now())));
    }

    private static MultipartFile photo(String name) {
        return new MockMultipartFile("files", name, "image/jpeg", TestImages.jpeg(400, 300));
    }

    private static MultipartFile pdf(String name) {
        return new MockMultipartFile("files", name, "application/pdf", TestImages.pdf());
    }

    private User verifiedUser() {
        String email = "chat-files-" + UUID.randomUUID() + "@example.com";
        User user = new User(email, "Ola", "Plik", "+48123456789", "n" + UUID.randomUUID().toString().substring(0, 8));
        user.setEmailVerified(true);
        return userRepository.save(user);
    }
}
