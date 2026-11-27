package pl.nextsteppro.climbing.api.contact;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.domain.StoredText;
import pl.nextsteppro.climbing.domain.contact.ContactContextType;
import pl.nextsteppro.climbing.domain.contact.ContactConversation;
import pl.nextsteppro.climbing.domain.contact.ContactConversationRepository;
import pl.nextsteppro.climbing.domain.contact.ContactMessage;
import pl.nextsteppro.climbing.domain.contact.ContactMessageFile;
import pl.nextsteppro.climbing.domain.contact.ContactMessageRepository;
import pl.nextsteppro.climbing.domain.contact.MessageWindow;
import pl.nextsteppro.climbing.domain.course.Course;
import pl.nextsteppro.climbing.domain.course.CourseRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.LocalizedMessage;
import pl.nextsteppro.climbing.infrastructure.mail.ContactMailService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The person's side of the "write to us" conversation: sending from the site, and reading the
 * conversation in the app.
 *
 * <p>A signed-in sender always writes into their ONE conversation (decision 2026-11-22) - the
 * contact page, a course and the calendar are three doors into the same room, and each message
 * remembers which door it came through. A guest writes into the conversation of their address.
 */
@Service
public class ContactService {

    private static final Logger log = LoggerFactory.getLogger(ContactService.class);
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final Set<String> LANGUAGES = Set.of("pl", "en", "es");
    /** A calendar question is about a day somebody could be looking at, not any date at all. */
    private static final int CALENDAR_DAYS_BACK = 31;
    private static final int CALENDAR_YEARS_AHEAD = 2;

    private final ContactConversationRepository conversationRepository;
    private final ContactMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final ContactSendThrottle throttle;
    private final ContactMailService mailService;
    private final ContactFileSupport files;

    public ContactService(ContactConversationRepository conversationRepository,
                          ContactMessageRepository messageRepository,
                          UserRepository userRepository,
                          CourseRepository courseRepository,
                          ContactSendThrottle throttle,
                          ContactMailService mailService,
                          ContactFileSupport files) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.throttle = throttle;
        this.mailService = mailService;
        this.files = files;
    }

    @Transactional
    public ContactSentDto send(@Nullable UUID userId, String clientIp, SendContactMessageRequest request) {
        // A bot fills every field it finds. Pretend it worked - an error would teach it which field
        // gave it away - and keep nothing of what it sent.
        if (request.website() != null && !request.website().isBlank()) {
            log.info("Contact form honeypot triggered; message dropped");
            return new ContactSentDto(userId != null);
        }

        String body = StoredText.trimAndCap(request.body(), ContactMessage.MAX_BODY_LENGTH);
        if (body == null) {
            throw new LocalizedMessage.Rejected("contact.body.required");
        }
        Instant now = Instant.now();

        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        ContactConversation conversation;
        // Only a guest is forwarded by mail, so only a guest needs a name and address for it.
        @Nullable String guestName = null;
        @Nullable String guestEmail = null;
        String language;
        if (user != null) {
            if (!throttle.tryAccount(user.getId().toString())) {
                throw new LocalizedMessage.Conflict("contact.too.many");
            }
            conversation = conversationRepository.findByUserId(user.getId())
                .orElseGet(() -> conversationRepository.save(ContactConversation.forUser(user, now)));
            language = normalizeLanguage(request.language(), user.getPreferredLanguage());
        } else {
            String name = StoredText.singleLineAndCap(request.name(), ContactConversation.MAX_GUEST_NAME_LENGTH);
            String email = request.email() == null ? null : request.email().trim();
            if (name == null) throw new LocalizedMessage.Rejected("contact.name.required");
            if (email == null || email.isEmpty()) throw new LocalizedMessage.Rejected("contact.email.required");
            if (!request.consent()) throw new LocalizedMessage.Rejected("contact.consent.required");
            if (!throttle.tryGuest(clientIp)) {
                throw new LocalizedMessage.Conflict("contact.too.many");
            }
            conversation = conversationRepository.findByGuestEmail(email).orElse(null);
            if (conversation == null) {
                conversation = conversationRepository.save(ContactConversation.forGuest(email, name, now));
            } else {
                conversation.renameGuest(name);
            }
            guestName = name;
            guestEmail = email;
            language = normalizeLanguage(request.language(), "pl");
        }

        ContactMessage message = buildMessage(conversation, body, request, language, now);
        messageRepository.save(message);
        conversation.recordInbound(now);

        // An account holder's conversation is an in-app chat: the team reads it in the panel
        // (the unread badge lights either way) and no mail goes in either direction.
        // Past the hourly guest-mail ceiling a guest's message is kept and the badge still
        // lights; only the mail is skipped (see ContactSendThrottle).
        if (guestName != null && guestEmail != null && throttle.tryForwardGuestMail()) {
            mailService.sendInboundToStaff(conversation.getId(), guestName, guestEmail,
                body, mailContext(message), language);
        }
        return new ContactSentDto(user != null);
    }

    @Transactional(readOnly = true)
    public MyConversationDto getMine(UUID userId) {
        return conversationRepository.findByUserId(userId)
            .map(c -> {
                MessageWindow window = messageRepository.latest(c.getId());
                return new MyConversationDto(toDtos(window.messages()), c.isUnseenByUser(), window.hasOlder(),
                    c.getStaffReadAt());
            })
            .orElseGet(() -> new MyConversationDto(List.of(), false, false, null));
    }

    /**
     * The page above (at, id) in the person's OWN conversation. The conversation comes from the
     * token, never from the request: a cursor is only a position, so one copied from somebody
     * else's chat still reads the caller's own messages and nothing of theirs.
     */
    @Transactional(readOnly = true)
    public MyMessagePageDto olderMine(UUID userId, Instant at, UUID messageId) {
        return conversationRepository.findByUserId(userId)
            .map(c -> {
                MessageWindow window = messageRepository.olderThan(c.getId(), at, messageId);
                return new MyMessagePageDto(toDtos(window.messages()), window.hasOlder());
            })
            .orElseGet(() -> new MyMessagePageDto(List.of(), false));
    }

    /** A poll: what arrived after (at, id), and whether an answer is still unseen. */
    @Transactional(readOnly = true)
    public MyConversationUpdateDto newerMine(UUID userId, Instant at, UUID messageId) {
        return conversationRepository.findByUserId(userId)
            .map(c -> new MyConversationUpdateDto(
                toDtos(messageRepository.newerThan(c.getId(), at, messageId)), c.isUnseenByUser(),
                c.getStaffReadAt()))
            .orElseGet(() -> new MyConversationUpdateDto(List.of(), false, null));
    }

    /**
     * A message from the person's own chat, with optional files (V111). Text, files or both - but
     * not neither. The chat is an account's only: no mail goes anywhere, the team's badge lights.
     */
    @Transactional
    public ConversationMessageDto sendInChat(UUID userId, @Nullable String rawBody, @Nullable String requestedLanguage,
                                             List<MultipartFile> attachments) {
        User user = userRepository.findById(userId).orElseThrow();
        String body = StoredText.trimAndCap(rawBody, ContactMessage.MAX_BODY_LENGTH);
        if (body == null && attachments.isEmpty()) {
            throw new LocalizedMessage.Rejected("contact.body.required");
        }
        if (!throttle.tryAccount(user.getId().toString())) {
            throw new LocalizedMessage.Conflict("contact.too.many");
        }
        Instant now = Instant.now();
        ContactConversation conversation = conversationRepository.findByUserId(user.getId())
            .orElseGet(() -> conversationRepository.save(ContactConversation.forUser(user, now)));
        if (!attachments.isEmpty()) {
            files.checkLimits(conversation.getId(), attachments);
        }
        String language = normalizeLanguage(requestedLanguage, user.getPreferredLanguage());
        ContactMessage message = messageRepository.save(ContactMessage.general(conversation, body, language, now));
        List<ContactMessageFile> saved = attachments.isEmpty() ? List.of() : files.attach(message, attachments);
        conversation.recordInbound(now);
        return ConversationMessageDto.from(message, saved);
    }

    /** A file of the person's OWN conversation, or "not found" - never a hint that another exists. */
    @Transactional(readOnly = true)
    public ContactMessageFile requireMyFile(UUID userId, UUID fileId) {
        ContactMessageFile file = files.requireFile(fileId);
        User owner = file.getMessage().getConversation().getUser();
        if (owner == null || !owner.getId().equals(userId)) {
            throw new LocalizedMessage.Rejected("contact.file.not.found");
        }
        return file;
    }

    @Transactional(readOnly = true)
    public ContactFileSupport.FileStream openMyFile(UUID userId, UUID fileId) {
        return files.stream(requireMyFile(userId, fileId));
    }

    /** The person withdraws a file THEY sent; what the team sent them is not theirs to remove. */
    @Transactional
    public void deleteMyFile(UUID userId, UUID fileId) {
        ContactMessageFile file = requireMyFile(userId, fileId);
        if (file.getMessage().isFromStaff()) {
            throw new LocalizedMessage.Rejected("contact.file.not.found");
        }
        files.deleteFile(file);
    }

    private List<ConversationMessageDto> toDtos(List<ContactMessage> messages) {
        Map<UUID, List<ContactMessageFile>> byMessage = files.filesOf(messages.stream().map(ContactMessage::getId).toList());
        return messages.stream()
            .map(m -> ConversationMessageDto.from(m, byMessage.getOrDefault(m.getId(), List.of())))
            .toList();
    }

    @Transactional(readOnly = true)
    public UnseenReplyDto unseenReply(UUID userId) {
        return new UnseenReplyDto(conversationRepository.findByUserId(userId)
            .map(ContactConversation::isUnseenByUser)
            .orElse(false));
    }

    @Transactional
    public void markSeen(UUID userId) {
        conversationRepository.findByUserId(userId).ifPresent(c -> c.markSeenByUser(Instant.now()));
    }

    private ContactMessage buildMessage(ContactConversation conversation, String body,
                                        SendContactMessageRequest request, String language, Instant now) {
        ContactContextType type = request.contextType() != null ? request.contextType() : ContactContextType.GENERAL;
        return switch (type) {
            case GENERAL -> ContactMessage.general(conversation, body, language, now);
            case COURSE -> {
                if (request.courseId() == null) throw new LocalizedMessage.Rejected("contact.course.not.found");
                // Published only: an unpublished course is not a page anybody could have asked from.
                Course course = courseRepository.findById(request.courseId())
                    .filter(Course::isPublished)
                    .orElseThrow(() -> new LocalizedMessage.Rejected("contact.course.not.found"));
                yield ContactMessage.aboutCourse(conversation, body, course, language, now);
            }
            case CALENDAR -> {
                // No day is a valid answer: written from a week or month view, nobody picked one.
                LocalDate day = request.date();
                LocalDate today = LocalDate.now(WARSAW);
                if (day != null && (day.isBefore(today.minusDays(CALENDAR_DAYS_BACK))
                        || day.isAfter(today.plusYears(CALENDAR_YEARS_AHEAD)))) {
                    throw new LocalizedMessage.Rejected("contact.date.invalid");
                }
                yield ContactMessage.aboutCalendar(conversation, body, day, language, now);
            }
        };
    }

    private static ContactMailService.Context mailContext(ContactMessage message) {
        return new ContactMailService.Context(message.getContextType().name(),
            message.getCourse() != null ? message.getCourse().getId() : null,
            message.getCourseTitle(), message.getContextDate());
    }

    static String normalizeLanguage(@Nullable String requested, String fallback) {
        if (requested != null) {
            String code = requested.trim().toLowerCase(Locale.ROOT);
            if (code.length() > 2) code = code.substring(0, 2);
            if (LANGUAGES.contains(code)) return code;
        }
        return LANGUAGES.contains(fallback) ? fallback : "pl";
    }
}
