package pl.nextsteppro.climbing.api.admin.contact;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.contact.ContactConversationClaim;
import pl.nextsteppro.climbing.api.contact.ContactFileSupport;
import pl.nextsteppro.climbing.api.contact.ContactService;
import pl.nextsteppro.climbing.domain.contact.ContactConversation;
import pl.nextsteppro.climbing.domain.contact.ContactConversationRepository;
import pl.nextsteppro.climbing.domain.contact.ContactMessage;
import pl.nextsteppro.climbing.domain.contact.ContactMessageFileRepository;
import pl.nextsteppro.climbing.domain.contact.ContactMessageRepository;
import pl.nextsteppro.climbing.domain.contact.MessageWindow;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.infrastructure.i18n.LocalizedMessage;
import pl.nextsteppro.climbing.infrastructure.mail.ContactMailService;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;
import pl.nextsteppro.climbing.infrastructure.storage.TestImages;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
    @Autowired private ContactConversationClaim conversationClaim;
    @Autowired private ContactService contactService;
    @Autowired private ContactMessageFileRepository fileRepository;
    @Autowired private FileStorageService storage;

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

        var page = adminContactService.list(true, null, 0, 100);

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

    // ---- message centre: the team starts a conversation, messenger ordering, search ----

    @Test
    void shouldStartAConversationThatTheTeamHasNotGotToReadAndThePersonSeesInTheApp() {
        User admin = admin();
        User client = verifiedClient("Ewa", "Nowak");
        int unreadBefore = conversationRepository.countUnreadForStaff();

        StartConversationResultDto result = adminContactService.start(admin.getId(), client.getId(), "  Cześć, mamy wolne miejsce w sobotę.  ");
        conversationRepository.flush();

        ContactConversation conversation = conversationRepository.findById(result.conversationId()).orElseThrow();
        assertNull(conversation.getLastInboundAt(), "the person has not written anything yet");
        assertEquals("Cześć, mamy wolne miejsce w sobotę.", result.message().body());
        assertTrue(result.message().fromStaff());
        ConversationSummaryDto summary = adminContactService.get(conversation.getId()).summary();
        assertFalse(summary.unread(), "nothing from the person, so nothing for the team to read");
        assertTrue(summary.answered(), "the ball is in the person's court");
        assertEquals(unreadBefore, conversationRepository.countUnreadForStaff(), "the Admin badge must not light");
        assertTrue(conversation.isUnseenByUser(), "the person's chat dot lights");
        verify(contactMailService, never()).sendReply(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldWriteIntoThePersonsExistingConversationRatherThanOpenASecond() {
        User admin = admin();
        User client = verifiedClient("Jan", "Kowalski");
        ContactConversation existing = conversationRepository.save(ContactConversation.forUser(client, Instant.now()));
        messageRepository.save(ContactMessage.general(existing, "Pytanie", "pl", Instant.now()));

        StartConversationResultDto result = adminContactService.start(admin.getId(), client.getId(), "Odpowiadam tutaj");

        assertEquals(existing.getId(), result.conversationId());
        assertEquals(2, messageRepository.findByConversationId(existing.getId()).size());
        assertEquals(existing.getId(), adminContactService.findForUser(client.getId()).conversationId());
    }

    @Test
    void shouldLetThePersonAnswerAStartedConversationAndLightTheTeamsBadge() {
        User admin = admin();
        User client = verifiedClient("Ola", "Wiśniewska");
        UUID id = adminContactService.start(admin.getId(), client.getId(), "Dzień dobry").conversationId();
        ContactConversation conversation = conversationRepository.findById(id).orElseThrow();

        Instant later = Instant.now().plusSeconds(5);
        messageRepository.save(ContactMessage.general(conversation, "Dziękuję!", "pl", later));
        conversation.recordInbound(later);

        ConversationSummaryDto summary = adminContactService.get(id).summary();
        assertTrue(summary.unread());
        assertFalse(summary.answered());
        assertEquals(later, summary.lastActivityAt());
    }

    @Test
    void shouldRefuseToStartAConversationNobodyWouldRead() {
        User admin = admin();
        User unconfirmed = userRepository.save(new User(uniqueEmail(), "Nie", "Potwierdzony", "+48123456789", nick()));
        User otherAdmin = admin();

        var unconfirmedRefusal = assertThrows(LocalizedMessage.Conflict.class,
            () -> adminContactService.start(admin.getId(), unconfirmed.getId(), "Hej"));
        assertEquals("contact.user.unverified", unconfirmedRefusal.messageKey());
        var adminRefusal = assertThrows(LocalizedMessage.Rejected.class,
            () -> adminContactService.start(admin.getId(), otherAdmin.getId(), "Hej"));
        assertEquals("contact.user.admin", adminRefusal.messageKey());
        var selfRefusal = assertThrows(LocalizedMessage.Rejected.class,
            () -> adminContactService.start(admin.getId(), admin.getId(), "Hej"));
        assertEquals("contact.user.admin", selfRefusal.messageKey());
        var unknownRefusal = assertThrows(LocalizedMessage.Rejected.class,
            () -> adminContactService.start(admin.getId(), UUID.randomUUID(), "Hej"));
        assertEquals("contact.user.not.found", unknownRefusal.messageKey());
        var blankRefusal = assertThrows(LocalizedMessage.Rejected.class,
            () -> adminContactService.start(admin.getId(), verifiedClient("A", "B").getId(), "   "));
        assertEquals("contact.body.required", blankRefusal.messageKey());
        assertTrue(conversationRepository.findByUserId(unconfirmed.getId()).isEmpty());
    }

    @Test
    void shouldPutTheConversationWithTheNewestMessageInEitherDirectionOnTop() {
        User admin = admin();
        Instant now = Instant.now();
        ContactConversation older = guestConversationAt(now.minus(2, ChronoUnit.HOURS));
        ContactConversation newer = guestConversationAt(now.minus(1, ChronoUnit.HOURS));
        conversationRepository.flush();
        assertTrue(position(newer) < position(older));

        // Answering the older one is activity too: it moves to the top, like a messenger.
        adminContactService.reply(older.getId(), admin.getId(), "Już odpowiadam");
        conversationRepository.flush();

        assertTrue(position(older) < position(newer));
    }

    // The trap V109 avoids: ordering by the inbound clock puts a NULL first under DESC, so every
    // conversation the team started would be pinned above all the others.
    @Test
    void shouldNotPinAConversationTheTeamStartedAboveNewerOnes() {
        User client = verifiedClient("Piotr", "Zieliński");
        Instant now = Instant.now();
        ContactConversation started = conversationRepository.save(
            ContactConversation.startedByStaff(client, now.minus(3, ChronoUnit.HOURS)));
        messageRepository.save(ContactMessage.reply(started, admin(), "Stara wiadomość", "pl", now.minus(3, ChronoUnit.HOURS)));
        ContactConversation newer = guestConversationAt(now.minus(1, ChronoUnit.HOURS));
        conversationRepository.flush();

        assertTrue(position(newer) < position(started));
    }

    @Test
    void shouldNeverListAConversationOnlyTheTeamWroteInAsUnread() {
        User admin = admin();
        UUID started = adminContactService.start(admin.getId(), verifiedClient("Kasia", "Lis").getId(), "Hej").conversationId();
        conversationRepository.flush();

        var unread = adminContactService.list(true, null, 0, 100);

        assertTrue(unread.content().stream().noneMatch(c -> c.id().equals(started)));
    }

    @Test
    void shouldFindConversationsByTheNameOrAddressOfAnAccountOrAGuest() {
        User admin = admin();
        User client = verifiedClient("Bartłomiej", "Szukany");
        UUID accountConversation = adminContactService.start(admin.getId(), client.getId(), "Hej").conversationId();
        ContactConversation guest = guestConversation("pl");
        conversationRepository.flush();

        assertEquals(List.of(accountConversation), ids(adminContactService.list(false, "  bartłomiej szuk ", 0, 100)));
        assertEquals(List.of(accountConversation), ids(adminContactService.list(false, client.getEmail().toUpperCase(), 0, 100)));
        assertEquals(List.of(guest.getId()), ids(adminContactService.list(false, guest.getGuestEmail(), 0, 100)));
    }

    // A typed % or _ is a character to find, not a wildcard that turns a search into "everything".
    @Test
    void shouldSearchForAWildcardLiterally() {
        guestConversation("pl");
        conversationRepository.flush();

        assertTrue(adminContactService.list(false, "%", 0, 100).content().isEmpty());
        assertTrue(adminContactService.list(false, "_", 0, 100).content().isEmpty());
        assertEquals("%50\\%%", AdminContactService.likePattern(" 50% "));
        assertNull(AdminContactService.likePattern("   "));
    }

    @Test
    void shouldReportNoConversationForAPersonWhoHasNone() {
        assertNull(adminContactService.findForUser(verifiedClient("Bez", "Rozmowy").getId()).conversationId());
    }

    // A guest who confirms an account the team has already written to: the two conversations merge,
    // and the merged one must not keep the "nobody wrote" clock of the started one.
    @Test
    void shouldMergeAGuestConversationIntoOneTheTeamStarted() {
        User admin = admin();
        User client = verifiedClient("Marta", "Gość");
        UUID started = adminContactService.start(admin.getId(), client.getId(), "Witamy!").conversationId();
        Instant guestWrote = Instant.now().plusSeconds(10);
        ContactConversation guest = conversationRepository.save(ContactConversation.forGuest(client.getEmail(), "Marta", guestWrote));
        messageRepository.save(ContactMessage.general(guest, "Pisałam bez logowania", "pl", guestWrote));
        conversationRepository.flush();

        conversationClaim.claimFor(client);
        conversationRepository.flush();

        ContactConversation merged = conversationRepository.findById(started).orElseThrow();
        assertEquals(guestWrote, merged.getLastInboundAt());
        assertEquals(guestWrote, merged.getLastActivityAt());
        assertTrue(merged.isUnreadByStaff());
        assertEquals(2, messageRepository.findByConversationId(started).size());
        assertTrue(conversationRepository.findById(guest.getId()).isEmpty());
    }

    // ---- the chat window, team side: open on the newest page, page up, poll only what is new ----

    @Test
    void shouldOpenAConversationOnTheNewestPageAndPageUpToTheRest() {
        ContactConversation conversation = conversationRepository.save(
            ContactConversation.forGuest(uniqueEmail(), "Ola", Instant.now().minus(1, ChronoUnit.DAYS)));
        Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
        int total = MessageWindow.PAGE_SIZE + 4;
        for (int i = 0; i < total; i++) {
            messageRepository.save(ContactMessage.general(conversation, "w" + i, "pl", start.plusSeconds(i)));
        }
        conversationRepository.flush();

        ConversationDetailDto opened = adminContactService.get(conversation.getId());
        assertEquals(MessageWindow.PAGE_SIZE, opened.messages().size());
        assertTrue(opened.hasOlder());
        assertEquals("w" + (total - 1), opened.summary().preview(), "the preview is still the newest message");

        AdminConversationMessageDto oldest = opened.messages().getFirst();
        AdminMessagePageDto older = adminContactService.older(conversation.getId(), oldest.createdAt(), oldest.id());
        assertEquals(List.of("w0", "w1", "w2", "w3"), older.messages().stream().map(AdminConversationMessageDto::body).toList());
        assertFalse(older.hasOlder());
    }

    // The panel marks a conversation read when it shows an unread message - so a poll that brings a
    // new message from the person must also bring the unread flag, or the badge never goes out.
    @Test
    void shouldPollNewMessagesTogetherWithTheUnreadFlag() {
        User admin = admin();
        ContactConversation conversation = guestConversation("pl");
        adminContactService.reply(conversation.getId(), admin.getId(), "Odpowiedź");
        conversationRepository.flush();
        AdminConversationMessageDto newest = adminContactService.get(conversation.getId()).messages().getLast();

        AdminConversationUpdateDto idle = adminContactService.newer(conversation.getId(), newest.createdAt(), newest.id());
        assertTrue(idle.messages().isEmpty());
        assertFalse(idle.summary().unread());
        assertEquals("Odpowiedź", idle.summary().preview(), "an idle poll still knows the newest message");

        Instant later = Instant.now().plusSeconds(5);
        messageRepository.save(ContactMessage.general(conversation, "A w niedzielę?", "pl", later));
        conversation.recordInbound(later);
        conversationRepository.flush();

        AdminConversationUpdateDto update = adminContactService.newer(conversation.getId(), newest.createdAt(), newest.id());
        assertEquals(List.of("A w niedzielę?"), update.messages().stream().map(AdminConversationMessageDto::body).toList());
        assertTrue(update.summary().unread());
    }

    // The team's read mark is the person's last look at the chat - opened, on the first read, and
    // on every poll, since it moves while the panel stays open.
    @Test
    void shouldCarryThePersonsLookAtTheChatAsTheTeamsReadMark() {
        User admin = admin();
        User client = verifiedClient("Jan", "Kowalski");
        ContactConversation conversation = conversationRepository.save(ContactConversation.forUser(client, Instant.now()));
        messageRepository.save(ContactMessage.general(conversation, "Pytanie", "pl", Instant.now()));
        adminContactService.reply(conversation.getId(), admin.getId(), "Odpowiedź");
        conversationRepository.flush();
        assertNull(adminContactService.get(conversation.getId()).summary().userSeenAt(), "not looked yet");

        contactService.markSeen(client.getId());
        conversationRepository.flush();

        ConversationDetailDto opened = adminContactService.get(conversation.getId());
        Instant seen = opened.summary().userSeenAt();
        assertNotNull(seen);
        AdminConversationMessageDto newest = opened.messages().getLast();
        assertFalse(seen.isBefore(newest.createdAt()), "the look came after the answer it saw");
        assertEquals(seen, adminContactService.newer(conversation.getId(), newest.createdAt(), newest.id())
            .summary().userSeenAt());
    }

    @Test
    void shouldGiveNoReadMarkForAGuestWhoReadsTheAnswerByMail() {
        ContactConversation conversation = guestConversation("pl");
        // Whatever the column says, a guest has no chat it could describe
        conversation.markSeenByUser(Instant.now());
        conversationRepository.flush();

        assertNull(adminContactService.get(conversation.getId()).summary().userSeenAt());
    }

    @Test
    void shouldRefuseToPageAConversationThatDoesNotExist() {
        var refusal = assertThrows(LocalizedMessage.Rejected.class,
            () -> adminContactService.newer(UUID.randomUUID(), Instant.now(), UUID.randomUUID()));
        assertEquals("contact.conversation.not.found", refusal.messageKey());
    }

    // ---- files in the chat (V111), team side ----

    @Test
    void shouldReplyWithAFileToAnAccountWithoutMail() {
        User admin = admin();
        User client = verifiedClient("Ewa", "Plik");
        UUID conversationId = adminContactService.start(admin.getId(), client.getId(), "Dzień dobry").conversationId();

        ReplyResultDto result = adminContactService.replyWithFiles(conversationId, admin.getId(), null,
            List.of(photo("plan.jpg")));

        assertFalse(result.mailed());
        assertNull(result.message().body(), "a reply may be only the file");
        assertEquals(1, result.message().files().size());
        assertTrue(result.message().files().getFirst().url().startsWith("/api/admin/contact/files/"), "the panel's own endpoint");
        assertTrue(result.message().files().getFirst().canDelete());
        assertTrue(conversationRepository.findById(conversationId).orElseThrow().isUnseenByUser());
        conversationRepository.flush();
        ConversationSummaryDto summary = adminContactService.get(conversationId).summary();
        assertTrue(summary.previewIsFile(), "the list says the newest message is a file");
        assertNull(summary.preview());
    }

    // A guest is reached by mail alone, and the chat never mails a file.
    @Test
    void shouldRefuseAFileForAGuest() {
        User admin = admin();
        ContactConversation guest = guestConversation("pl");

        var refusal = assertThrows(LocalizedMessage.Rejected.class, () -> adminContactService.replyWithFiles(
            guest.getId(), admin.getId(), "Proszę", List.of(photo("x.jpg"))));

        assertEquals("contact.file.guest", refusal.messageKey());
        verify(contactMailService, never()).sendReply(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldTakeTheFilesOffTheDiskWithADeletedConversation() {
        User admin = admin();
        User client = verifiedClient("Jan", "Plik");
        UUID conversationId = adminContactService.start(admin.getId(), client.getId(), "Hej").conversationId();
        UUID fileId = adminContactService.replyWithFiles(conversationId, admin.getId(), "plan", List.of(photo("p.jpg")))
            .message().files().getFirst().id();
        String filename = fileRepository.findById(fileId).orElseThrow().getFilename();
        conversationRepository.flush();

        adminContactService.delete(conversationId);
        conversationRepository.flush();
        unlinkDeletedFiles(); // storage unlinks on COMMIT, and this test rolls back

        assertTrue(fileRepository.findById(fileId).isEmpty());
        assertFalse(storage.exists(filename, ContactFileSupport.FOLDER));
    }

    private static MultipartFile photo(String name) {
        return new MockMultipartFile("files", name, "image/jpeg", TestImages.jpeg(400, 300));
    }

    private int position(ContactConversation conversation) {
        List<UUID> order = ids(adminContactService.list(false, null, 0, 100));
        int index = order.indexOf(conversation.getId());
        assertTrue(index >= 0, "conversation missing from the list");
        return index;
    }

    private static List<UUID> ids(ConversationPageDto page) {
        return page.content().stream().map(ConversationSummaryDto::id).toList();
    }

    private ContactConversation guestConversationAt(Instant at) {
        ContactConversation conversation = conversationRepository.save(ContactConversation.forGuest(uniqueEmail(), "Ola", at));
        messageRepository.save(ContactMessage.general(conversation, "Pytanie", "pl", at));
        return conversation;
    }

    private User verifiedClient(String firstName, String lastName) {
        User user = new User(uniqueEmail(), firstName, lastName, "+48123456789", nick());
        user.setEmailVerified(true);
        return userRepository.save(user);
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
