package pl.nextsteppro.climbing.api.contact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.contact.ContactContextType;
import pl.nextsteppro.climbing.domain.contact.ContactConversation;
import pl.nextsteppro.climbing.domain.contact.ContactMessage;
import pl.nextsteppro.climbing.domain.contact.ContactMessageFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A message from the site. {@code name}/{@code email} and {@code consent} are for guests and
 * ignored for a signed-in sender, whose account already says who they are.
 *
 * @param website honeypot: a field hidden from people and filled in by form-filling bots
 */
record SendContactMessageRequest(
    @Nullable @Size(max = ContactConversation.MAX_GUEST_NAME_LENGTH) String name,
    @Nullable @Email @Size(max = ContactConversation.MAX_GUEST_EMAIL_LENGTH) String email,
    @NotBlank @Size(max = ContactMessage.MAX_BODY_LENGTH) String body,
    boolean consent,
    @Nullable @Size(max = 200) String website,
    @Nullable ContactContextType contextType,
    @Nullable UUID courseId,
    @Nullable LocalDate date,
    @Nullable @Size(max = 10) String language
) {}

/** @param inConversation the message went into the sender's own conversation in the app */
record ContactSentDto(boolean inConversation) {}

/**
 * One message as the PERSON sees it. Who on the team answered is not part of it: the person talks
 * to the team, and an admin's name has no business in a client's payload.
 */
record ConversationMessageDto(
    UUID id,
    boolean fromStaff,
    /* null for a message that is only files */
    @Nullable String body,
    ContactContextType contextType,
    @Nullable UUID courseId,
    @Nullable String courseTitle,
    @Nullable LocalDate date,
    Instant createdAt,
    List<ContactFileDto> files
) {
    /** Where the person's own chat serves a file - never the panel's endpoint. */
    static final String FILE_URL = "/api/conversation/files/";

    /**
     * @param files this message's files; the person may delete only what THEY sent - what the team
     *              sent them is not theirs to remove
     */
    static ConversationMessageDto from(ContactMessage m, List<ContactMessageFile> files) {
        boolean own = !m.isFromStaff();
        return new ConversationMessageDto(m.getId(), m.isFromStaff(), m.getBody(), m.getContextType(),
            m.getCourse() != null ? m.getCourse().getId() : null, m.getCourseTitle(), m.getContextDate(),
            m.getCreatedAt(),
            files.stream().map(f -> ContactFileSupport.toDto(f, FILE_URL, own)).toList());
    }
}

/**
 * The chat as it opens: the newest page, oldest first.
 *
 * @param hasOlder   whether scrolling up has more to load
 * @param teamReadAt when somebody on the team last had the conversation open (a reply counts too) -
 *                   the person's "read" mark. Shared by the team on purpose: WHICH admin read it is
 *                   not the person's business, as with the author of an answer.
 */
record MyConversationDto(List<ConversationMessageDto> messages, boolean unseenReply, boolean hasOlder,
                         @Nullable Instant teamReadAt) {}

/** One page above what the chat already shows, oldest first. */
record MyMessagePageDto(List<ConversationMessageDto> messages, boolean hasOlder) {}

/**
 * A poll: only what arrived after the newest message the chat has, whether an answer is unseen,
 * and the team's read mark - which moves while the chat stays open, so it travels with every poll.
 */
record MyConversationUpdateDto(List<ConversationMessageDto> messages, boolean unseenReply,
                               @Nullable Instant teamReadAt) {}

record UnseenReplyDto(boolean unseenReply) {}
