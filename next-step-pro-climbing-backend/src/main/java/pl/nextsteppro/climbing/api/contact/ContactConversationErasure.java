package pl.nextsteppro.climbing.api.contact;

import org.springframework.stereotype.Component;
import pl.nextsteppro.climbing.domain.contact.ContactConversationRepository;
import pl.nextsteppro.climbing.domain.contact.ContactMessageRepository;
import pl.nextsteppro.climbing.domain.user.User;

/**
 * What goes with an account besides its own conversation's rows (those cascade with the user row):
 * the files of that conversation on disk, and a guest conversation under the account's address.
 *
 * <p>A person with a confirmed account who writes from the form while signed out lands in a GUEST
 * conversation under their own address — the form cannot know it is them. Deleting the account
 * left that conversation behind, with their name, address and words, while the privacy policy
 * promises messages go with the account. Both deletion paths call this; the twin paths of this
 * codebase drift whenever each keeps its own copy. The data export reads the same rows
 * ({@code ContactMessageRepository.findAllWrittenBy}).
 *
 * <p>Only for a CONFIRMED address. An unconfirmed account proves nothing about the mailbox, and
 * deleting it (the 7-day retention sweep) must not take a stranger's conversation with it — the
 * same rule that keeps {@link ContactConversationClaim} away from registration.
 *
 * <p>Entity deletes, messages first, not a bulk statement: a bulk DELETE clears the persistence
 * context in the middle of an account deletion whose later steps still hold managed entities, and
 * deleting the conversation while its messages are managed fails the flush.
 */
@Component
public class ContactConversationErasure {

    private final ContactConversationRepository conversationRepository;
    private final ContactMessageRepository messageRepository;
    private final ContactFileSupport files;

    public ContactConversationErasure(ContactConversationRepository conversationRepository,
                                      ContactMessageRepository messageRepository,
                                      ContactFileSupport files) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.files = files;
    }

    /**
     * Everything of the chat that the account's own cascade cannot reach: the files of its
     * conversation on disk (V111 - the rows cascade, the bytes are unlinked here, unseen by the
     * database), and the guest conversation under its confirmed address.
     */
    public void eraseChatOf(User user) {
        files.purgeForUser(user.getId());
        if (user.getEmailVerifiedAt() == null) return;
        conversationRepository.findByGuestEmail(user.getEmail()).ifPresent(conversation -> {
            messageRepository.deleteAll(messageRepository.findByConversationId(conversation.getId()));
            conversationRepository.delete(conversation);
        });
    }
}
