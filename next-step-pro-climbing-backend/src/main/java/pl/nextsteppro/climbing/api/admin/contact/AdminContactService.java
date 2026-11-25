package pl.nextsteppro.climbing.api.admin.contact;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.contact.ContactFileSupport;
import pl.nextsteppro.climbing.domain.StoredText;
import pl.nextsteppro.climbing.domain.contact.ContactConversation;
import pl.nextsteppro.climbing.domain.contact.ContactConversationRepository;
import pl.nextsteppro.climbing.domain.contact.ContactMessage;
import pl.nextsteppro.climbing.domain.contact.ContactMessageFile;
import pl.nextsteppro.climbing.domain.contact.ContactMessageRepository;
import pl.nextsteppro.climbing.domain.contact.MessageWindow;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.LocalizedMessage;
import pl.nextsteppro.climbing.infrastructure.mail.ContactMailService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The team's side of the "write to us" conversations - the message centre: the list (messenger
 * order, search), a conversation, the reply, and starting a conversation with a registered person.
 *
 * <p>No activity log: like the private notes, the log audits actions that touch people's
 * bookings and accounts, and an entry per reply would turn the Activity tab into a second inbox.
 */
@Service
public class AdminContactService {

    private static final int PREVIEW_LENGTH = 140;

    private final ContactConversationRepository conversationRepository;
    private final ContactMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ContactMailService mailService;
    private final ContactFileSupport files;

    public AdminContactService(ContactConversationRepository conversationRepository,
                               ContactMessageRepository messageRepository,
                               UserRepository userRepository,
                               ContactMailService mailService,
                               ContactFileSupport files) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.files = files;
    }

    @Transactional(readOnly = true)
    public ConversationPageDto list(boolean unreadOnly, @Nullable String query, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        Page<ContactConversation> rows = conversationRepository.findForStaff(unreadOnly, likePattern(query), pageable);
        List<UUID> ids = rows.getContent().stream().map(ContactConversation::getId).toList();
        Map<UUID, ContactMessage> latest = ids.isEmpty() ? Map.of()
            : messageRepository.findLatestIn(ids).stream()
                .collect(Collectors.toMap(m -> m.getConversation().getId(), Function.identity(), (a, b) -> a));
        List<ConversationSummaryDto> content = rows.getContent().stream()
            .map(c -> summary(c, latest.get(c.getId())))
            .toList();
        return new ConversationPageDto(content, rows.getTotalElements(), rows.getTotalPages(), rows.getNumber());
    }

    /** The chat window opens on the newest page; older pages come on scroll, newer ones on poll. */
    @Transactional(readOnly = true)
    public ConversationDetailDto get(UUID conversationId) {
        ContactConversation conversation = require(conversationId);
        MessageWindow window = messageRepository.latest(conversationId);
        List<ContactMessage> messages = window.messages();
        ContactMessage last = messages.isEmpty() ? null : messages.getLast();
        return new ConversationDetailDto(summary(conversation, last), toDtos(messages), window.hasOlder());
    }

    @Transactional(readOnly = true)
    public AdminMessagePageDto older(UUID conversationId, Instant at, UUID messageId) {
        require(conversationId);
        MessageWindow window = messageRepository.olderThan(conversationId, at, messageId);
        return new AdminMessagePageDto(toDtos(window.messages()), window.hasOlder());
    }

    @Transactional(readOnly = true)
    public AdminConversationUpdateDto newer(UUID conversationId, Instant at, UUID messageId) {
        ContactConversation conversation = require(conversationId);
        List<ContactMessage> messages = messageRepository.newerThan(conversationId, at, messageId);
        // The summary's preview is the newest message overall; when nothing arrived, read just that one.
        ContactMessage last = !messages.isEmpty() ? messages.getLast()
            : messageRepository.findNewestFirst(conversationId, PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        return new AdminConversationUpdateDto(summary(conversation, last), toDtos(messages));
    }

    /** Opening a conversation is reading it; the front calls this when the conversation is shown. */
    @Transactional
    public void markRead(UUID conversationId) {
        require(conversationId).markReadByStaff(Instant.now());
    }

    /** The account's conversation, if there is one - for "Write" on a user's card. */
    @Transactional(readOnly = true)
    public ConversationLookupDto findForUser(UUID userId) {
        return new ConversationLookupDto(
            conversationRepository.findByUserId(userId).map(ContactConversation::getId).orElse(null));
    }

    /**
     * The team writes to a registered person first. Still one conversation per person: when there
     * already is one, this is simply a reply in it, so the history stays in one place.
     *
     * <p>Only a confirmed, non-admin account. An unconfirmed one cannot sign in and is deleted
     * within 7 days, so the message would never be read; an admin has no chat link in the
     * navbar (they answer here), so it would never be seen either. A guest cannot be reached at
     * all: the only way to them is mail, and the chat never mails an account - starting a
     * conversation with a guest would be an unsolicited mail to an address from a contact form.
     */
    @Transactional
    public StartConversationResultDto start(UUID adminId, UUID userId, String rawBody) {
        String body = requireBody(rawBody);
        User person = userRepository.findById(userId)
            .orElseThrow(() -> new LocalizedMessage.Rejected("contact.user.not.found"));
        if (person.isAdmin()) throw new LocalizedMessage.Rejected("contact.user.admin");
        if (!person.isEmailVerified()) throw new LocalizedMessage.Conflict("contact.user.unverified", person.getFullName());

        ContactConversation existing = conversationRepository.findByUserId(userId).orElse(null);
        if (existing != null) {
            ReplyResultDto reply = reply(existing.getId(), adminId, body);
            return new StartConversationResultDto(existing.getId(), reply.message());
        }
        User admin = userRepository.findById(adminId).orElseThrow();
        Instant now = Instant.now();
        ContactConversation conversation = conversationRepository.save(ContactConversation.startedByStaff(person, now));
        ContactMessage first = messageRepository.save(
            ContactMessage.reply(conversation, admin, body, person.getPreferredLanguage(), now));
        return new StartConversationResultDto(conversation.getId(), AdminConversationMessageDto.from(first, List.of()));
    }

    @Transactional
    public ReplyResultDto reply(UUID conversationId, UUID adminId, String rawBody) {
        return replyWithFiles(conversationId, adminId, rawBody, List.of());
    }

    /**
     * A reply with optional files (V111). Text, files or both, never neither. Files only in an
     * account's chat: a guest is reached by mail alone, and the chat never mails a file.
     */
    @Transactional
    public ReplyResultDto replyWithFiles(UUID conversationId, UUID adminId, @Nullable String rawBody,
                                         List<MultipartFile> attachments) {
        ContactConversation conversation = require(conversationId);
        String body = StoredText.trimAndCap(rawBody, ContactMessage.MAX_BODY_LENGTH);
        if (body == null && attachments.isEmpty()) throw new LocalizedMessage.Rejected("contact.body.required");
        if (!attachments.isEmpty()) {
            if (conversation.getUser() == null) throw new LocalizedMessage.Rejected("contact.file.guest");
            files.checkLimits(conversationId, attachments);
        }
        User admin = userRepository.findById(adminId).orElseThrow();

        User owner = conversation.getUser();
        String language = owner != null
            ? owner.getPreferredLanguage()
            // A guest has no account to read a language from: answer in the language they wrote in.
            : messageRepository.findLatestInbound(conversationId).stream().findFirst()
                .map(ContactMessage::getLanguage).orElse("pl");

        Instant now = Instant.now();
        ContactMessage reply = messageRepository.save(ContactMessage.reply(conversation, admin, body, language, now));
        List<ContactMessageFile> saved = attachments.isEmpty() ? List.of() : files.attach(reply, attachments);
        conversation.recordReply(now);

        // A guest has no other way to read the answer, so they always get it by mail. An account
        // holder's conversation is an in-app chat: they wrote signed in, so they read the answer
        // signed in - the unread dot lights and no mail goes, whatever their notification switch.
        boolean mailed = owner == null;
        if (mailed) {
            // Never null here: a guest's reply cannot be files only (refused above).
            mailService.sendReply(Objects.requireNonNull(conversation.getGuestEmail()), Objects.requireNonNull(conversation.getGuestName()),
                Objects.requireNonNull(body), language);
        }
        return new ReplyResultDto(AdminConversationMessageDto.from(reply, saved), mailed);
    }

    @Transactional(readOnly = true)
    public ContactFileSupport.FileStream openFile(UUID fileId) {
        return files.stream(files.requireFile(fileId));
    }

    /** The team may remove any file in a conversation it answers, as in the training threads. */
    @Transactional
    public void deleteFile(UUID fileId) {
        files.deleteFile(files.requireFile(fileId));
    }

    /**
     * For spam, mostly. The files are unlinked first - their rows go by the database cascade,
     * which Hibernate never loads, so nothing after the delete could name them. Then the messages,
     * explicitly: the table cascades, but a message already loaded in this persistence context
     * would otherwise be flushed pointing at a deleted conversation.
     */
    @Transactional
    public void delete(UUID conversationId) {
        require(conversationId);
        files.purgeForConversation(conversationId);
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
    }

    private List<AdminConversationMessageDto> toDtos(List<ContactMessage> messages) {
        Map<UUID, List<ContactMessageFile>> byMessage = files.filesOf(messages.stream().map(ContactMessage::getId).toList());
        return messages.stream()
            .map(m -> AdminConversationMessageDto.from(m, byMessage.getOrDefault(m.getId(), List.of())))
            .toList();
    }

    private ConversationSummaryDto summary(ContactConversation c, @Nullable ContactMessage last) {
        User owner = c.getUser();
        String name = owner != null ? owner.getFullName() : Objects.requireNonNull(c.getGuestName());
        String email = owner != null ? owner.getEmail() : Objects.requireNonNull(c.getGuestEmail());
        return new ConversationSummaryDto(
            c.getId(),
            owner != null ? owner.getId() : null,
            name,
            email,
            c.isUnreadByStaff(),
            c.isAnswered(),
            c.getLastActivityAt(),
            c.getLastInboundAt(),
            c.getLastReplyAt(),
            last != null && last.getBody() != null ? preview(last.getBody()) : null,
            last != null && last.getBody() == null,
            last != null && last.isFromStaff(),
            last != null ? last.getContextType() : null,
            last != null ? last.getCourseTitle() : null,
            last != null ? last.getContextDate() : null);
    }

    private static String requireBody(String rawBody) {
        String body = StoredText.trimAndCap(rawBody, ContactMessage.MAX_BODY_LENGTH);
        if (body == null) throw new LocalizedMessage.Rejected("contact.body.required");
        return body;
    }

    /**
     * A lower-cased {@code %…%} pattern for a search, or null for none. The LIKE wildcards a person
     * might type are escaped away rather than honoured: "50%" is a search for "50%", and an
     * underscore in an address is an underscore.
     */
    static @Nullable String likePattern(@Nullable String query) {
        if (query == null) return null;
        String trimmed = query.trim().toLowerCase(java.util.Locale.ROOT);
        if (trimmed.isEmpty()) return null;
        String escaped = trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static String preview(String body) {
        String flat = body.replaceAll("\\s+", " ").trim();
        if (flat.length() <= PREVIEW_LENGTH) return flat;
        return flat.substring(0, PREVIEW_LENGTH).trim() + "…";
    }

    private ContactConversation require(UUID conversationId) {
        return conversationRepository.findById(conversationId)
            .orElseThrow(() -> new LocalizedMessage.Rejected("contact.conversation.not.found"));
    }
}
