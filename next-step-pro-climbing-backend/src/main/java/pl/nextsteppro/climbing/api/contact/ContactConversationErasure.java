package pl.nextsteppro.climbing.api.contact;

import org.springframework.stereotype.Component;
import pl.nextsteppro.climbing.domain.contact.ContactConversationRepository;
import pl.nextsteppro.climbing.domain.contact.ContactMessageRepository;
import pl.nextsteppro.climbing.domain.user.User;

/**
 * What goes with an account besides its own conversation (that one cascades with the user row).
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

    public ContactConversationErasure(ContactConversationRepository conversationRepository,
                                      ContactMessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    public void eraseGuestConversationOf(User user) {
        if (user.getEmailVerifiedAt() == null) return;
        conversationRepository.findByGuestEmail(user.getEmail()).ifPresent(conversation -> {
            messageRepository.deleteAll(messageRepository.findByConversationId(conversation.getId()));
            conversationRepository.delete(conversation);
        });
    }
}
