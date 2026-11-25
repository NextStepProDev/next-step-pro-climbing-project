package pl.nextsteppro.climbing.api.admin.contact;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.StoredText;
import pl.nextsteppro.climbing.domain.contact.ContactConversation;
import pl.nextsteppro.climbing.domain.contact.ContactConversationRepository;
import pl.nextsteppro.climbing.domain.contact.ContactMessage;
import pl.nextsteppro.climbing.domain.contact.ContactMessageRepository;
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
 * The team's side of the "write to us" conversations: the list, a conversation, the reply.
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

    public AdminContactService(ContactConversationRepository conversationRepository,
                               ContactMessageRepository messageRepository,
                               UserRepository userRepository,
                               ContactMailService mailService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.mailService = mailService;
    }

    @Transactional(readOnly = true)
    public ConversationPageDto list(boolean unreadOnly, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        Page<ContactConversation> rows = unreadOnly
            ? conversationRepository.findUnreadForStaff(pageable)
            : conversationRepository.findAllForStaff(pageable);
        List<UUID> ids = rows.getContent().stream().map(ContactConversation::getId).toList();
        Map<UUID, ContactMessage> latest = ids.isEmpty() ? Map.of()
            : messageRepository.findLatestIn(ids).stream()
                .collect(Collectors.toMap(m -> m.getConversation().getId(), Function.identity(), (a, b) -> a));
        List<ConversationSummaryDto> content = rows.getContent().stream()
            .map(c -> summary(c, latest.get(c.getId())))
            .toList();
        return new ConversationPageDto(content, rows.getTotalElements(), rows.getTotalPages(), rows.getNumber());
    }

    @Transactional(readOnly = true)
    public ConversationDetailDto get(UUID conversationId) {
        ContactConversation conversation = require(conversationId);
        List<ContactMessage> messages = messageRepository.findByConversationId(conversationId);
        ContactMessage last = messages.isEmpty() ? null : messages.getLast();
        return new ConversationDetailDto(summary(conversation, last),
            messages.stream().map(AdminConversationMessageDto::from).toList());
    }

    /** Opening a conversation is reading it; the front calls this when the conversation is shown. */
    @Transactional
    public void markRead(UUID conversationId) {
        require(conversationId).markReadByStaff(Instant.now());
    }

    @Transactional
    public ReplyResultDto reply(UUID conversationId, UUID adminId, String rawBody) {
        ContactConversation conversation = require(conversationId);
        String body = StoredText.trimAndCap(rawBody, ContactMessage.MAX_BODY_LENGTH);
        if (body == null) throw new LocalizedMessage.Rejected("contact.body.required");
        User admin = userRepository.findById(adminId).orElseThrow();

        User owner = conversation.getUser();
        String language = owner != null
            ? owner.getPreferredLanguage()
            // A guest has no account to read a language from: answer in the language they wrote in.
            : messageRepository.findLatestInbound(conversationId).stream().findFirst()
                .map(ContactMessage::getLanguage).orElse("pl");

        Instant now = Instant.now();
        ContactMessage reply = messageRepository.save(ContactMessage.reply(conversation, admin, body, language, now));
        conversation.recordReply(now);

        // A guest has no other way to read the answer, so they always get it by mail. An account
        // holder's conversation is an in-app chat: they wrote signed in, so they read the answer
        // signed in - the unread dot lights and no mail goes, whatever their notification switch.
        boolean mailed = owner == null;
        if (mailed) {
            mailService.sendReply(Objects.requireNonNull(conversation.getGuestEmail()), Objects.requireNonNull(conversation.getGuestName()),
                body, language);
        }
        return new ReplyResultDto(AdminConversationMessageDto.from(reply), mailed);
    }

    /**
     * For spam, mostly. The messages go first, explicitly: the table cascades, but a message
     * already loaded in this persistence context would otherwise be flushed pointing at a deleted
     * conversation.
     */
    @Transactional
    public void delete(UUID conversationId) {
        require(conversationId);
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
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
            c.getLastInboundAt(),
            c.getLastReplyAt(),
            last != null ? preview(last.getBody()) : null,
            last != null && last.isFromStaff(),
            last != null ? last.getContextType() : null,
            last != null ? last.getCourseTitle() : null,
            last != null ? last.getContextDate() : null);
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
