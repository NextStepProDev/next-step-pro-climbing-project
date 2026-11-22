package pl.nextsteppro.climbing.api.admin.contact;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.contact.ContactContextType;
import pl.nextsteppro.climbing.domain.contact.ContactMessage;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One row of the admin list.
 *
 * @param userId  the account, or null for a guest known only by {@code email}
 * @param preview the newest message, cut short; who wrote it is {@code previewFromStaff}
 */
record ConversationSummaryDto(
    UUID id,
    @Nullable UUID userId,
    String name,
    String email,
    boolean unread,
    boolean answered,
    Instant lastInboundAt,
    @Nullable Instant lastReplyAt,
    @Nullable String preview,
    boolean previewFromStaff,
    @Nullable ContactContextType previewContextType,
    @Nullable String previewCourseTitle,
    @Nullable LocalDate previewDate
) {}

record ConversationPageDto(List<ConversationSummaryDto> content, long totalElements, int totalPages, int page) {}

/** @param staffAuthorName which admin answered - the team's own view, never sent to the person */
record AdminConversationMessageDto(
    UUID id,
    boolean fromStaff,
    @Nullable String staffAuthorName,
    String body,
    ContactContextType contextType,
    @Nullable UUID courseId,
    @Nullable String courseTitle,
    @Nullable LocalDate date,
    String language,
    Instant createdAt
) {
    static AdminConversationMessageDto from(ContactMessage m) {
        return new AdminConversationMessageDto(m.getId(), m.isFromStaff(),
            m.getStaffAuthor() != null ? m.getStaffAuthor().getFullName() : null,
            m.getBody(), m.getContextType(),
            m.getCourse() != null ? m.getCourse().getId() : null, m.getCourseTitle(), m.getContextDate(),
            m.getLanguage(), m.getCreatedAt());
    }
}

record ConversationDetailDto(ConversationSummaryDto summary, List<AdminConversationMessageDto> messages) {}

/**
 * @param mailed whether the reply went out by mail - false only for an account holder with mail
 *               notifications off, who will see it in the app instead
 */
record ReplyResultDto(AdminConversationMessageDto message, boolean mailed) {}

record ReplyRequest(@NotBlank @Size(max = ContactMessage.MAX_BODY_LENGTH) String body) {}
