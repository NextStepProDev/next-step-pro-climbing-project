package pl.nextsteppro.climbing.api.admin.contact;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.contact.ContactContextType;
import pl.nextsteppro.climbing.api.contact.ContactFileDto;
import pl.nextsteppro.climbing.api.contact.ContactFileSupport;
import pl.nextsteppro.climbing.domain.contact.ContactMessage;
import pl.nextsteppro.climbing.domain.contact.ContactMessageFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One row of the admin list.
 *
 * @param userId         the account, or null for a guest known only by {@code email}
 * @param lastActivityAt the newest message in either direction - what the list is ordered by
 * @param lastInboundAt  the person's newest message; null when only the team has written
 * @param preview        the newest message, cut short; who wrote it is {@code previewFromStaff}
 * @param userSeenAt     when the person last had the chat open with a team answer on screen - the
 *                       team's "read" mark; null for a guest, who reads the answer by mail
 */
record ConversationSummaryDto(
    UUID id,
    @Nullable UUID userId,
    String name,
    String email,
    boolean unread,
    boolean answered,
    Instant lastActivityAt,
    @Nullable Instant lastInboundAt,
    @Nullable Instant lastReplyAt,
    @Nullable String preview,
    /* the newest message is only files (no text to preview) */
    boolean previewIsFile,
    boolean previewFromStaff,
    @Nullable ContactContextType previewContextType,
    @Nullable String previewCourseTitle,
    @Nullable LocalDate previewDate,
    @Nullable Instant userSeenAt
) {}

record ConversationPageDto(List<ConversationSummaryDto> content, long totalElements, int totalPages, int page) {}

/** @param staffAuthorName which admin answered - the team's own view, never sent to the person */
record AdminConversationMessageDto(
    UUID id,
    boolean fromStaff,
    @Nullable String staffAuthorName,
    @Nullable String body,
    ContactContextType contextType,
    @Nullable UUID courseId,
    @Nullable String courseTitle,
    @Nullable LocalDate date,
    String language,
    Instant createdAt,
    List<ContactFileDto> files
) {
    /** Where the panel serves a file - its own endpoint, never the person's. */
    static final String FILE_URL = "/api/admin/contact/files/";

    /** The team may remove any file in a conversation it answers, as in the training threads. */
    static AdminConversationMessageDto from(ContactMessage m, List<ContactMessageFile> files) {
        return new AdminConversationMessageDto(m.getId(), m.isFromStaff(),
            m.getStaffAuthor() != null ? m.getStaffAuthor().getFullName() : null,
            m.getBody(), m.getContextType(),
            m.getCourse() != null ? m.getCourse().getId() : null, m.getCourseTitle(), m.getContextDate(),
            m.getLanguage(), m.getCreatedAt(),
            files.stream().map(f -> ContactFileSupport.toDto(f, FILE_URL, true)).toList());
    }
}

/**
 * The conversation as the chat window opens it: the newest page, oldest first.
 *
 * @param hasOlder whether scrolling up has more to load ({@code GET …/messages/older})
 */
record ConversationDetailDto(ConversationSummaryDto summary, List<AdminConversationMessageDto> messages, boolean hasOlder) {}

/** One page above what the window already shows, oldest first. */
record AdminMessagePageDto(List<AdminConversationMessageDto> messages, boolean hasOlder) {}

/**
 * A poll: only what arrived after the newest message the window has, plus the summary, whose
 * unread flag the panel needs to mark a new message read while it is on screen.
 */
record AdminConversationUpdateDto(ConversationSummaryDto summary, List<AdminConversationMessageDto> messages) {}

/**
 * @param mailed whether the reply went out by mail - true for a guest, false for an account
 *               holder, whose conversation is an in-app chat that never mails
 */
record ReplyResultDto(AdminConversationMessageDto message, boolean mailed) {}

record ReplyRequest(@NotBlank @Size(max = ContactMessage.MAX_BODY_LENGTH) String body) {}

/** The team writes first to a registered person (the message centre's "New conversation"). */
record StartConversationRequest(
    @NotNull UUID userId,
    @NotBlank @Size(max = ContactMessage.MAX_BODY_LENGTH) String body
) {}

/**
 * @param conversationId the new conversation, or the person's existing one the message went into
 *                       (still one conversation per person)
 */
record StartConversationResultDto(UUID conversationId, AdminConversationMessageDto message) {}

/** @param conversationId the account's conversation, or null when there is none yet */
record ConversationLookupDto(@Nullable UUID conversationId) {}
