package pl.nextsteppro.climbing.api.contact;

import org.springframework.stereotype.Component;
import pl.nextsteppro.climbing.domain.contact.ContactConversation;
import pl.nextsteppro.climbing.domain.contact.ContactConversationRepository;
import pl.nextsteppro.climbing.domain.contact.ContactMessage;
import pl.nextsteppro.climbing.domain.contact.ContactMessageRepository;
import pl.nextsteppro.climbing.domain.user.User;

import java.util.Optional;

/**
 * Hands the messages a guest wrote from an address to the account that has just CONFIRMED it.
 *
 * <p>Called from {@code AccountConfirmation}, the one place an address becomes trusted, and never
 * on registration: anybody can type somebody else's address into a sign-up form, and claiming on
 * that would show them that person's conversation with the coach. The confirmation link is what
 * proves the mailbox is theirs - the same proof the guest conversation ran on all along.
 *
 * <p>Runs in the caller's transaction (all three confirmation paths have one) and needs the user
 * row to exist - the OAuth sign-up saves it before confirming for the same reason.
 */
@Component
public class ContactConversationClaim {

    private final ContactConversationRepository conversationRepository;
    private final ContactMessageRepository messageRepository;

    public ContactConversationClaim(ContactConversationRepository conversationRepository,
                                    ContactMessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    public void claimFor(User user) {
        Optional<ContactConversation> guest = conversationRepository.findByGuestEmail(user.getEmail());
        if (guest.isEmpty()) return;
        ContactConversation guestConversation = guest.get();

        Optional<ContactConversation> own = conversationRepository.findByUserId(user.getId());
        if (own.isEmpty()) {
            guestConversation.assignTo(user);
            return;
        }
        // Both exist only when the address belonged to a confirmed account that later changed it
        // away and back; merge rather than leave half the history unreachable.
        //
        // Entity updates, not a bulk UPDATE: a bulk statement clears the persistence context, and
        // the caller's User is a managed entity with changes still to be written. Hibernate
        // flushes updates before deletes, so the moved rows are re-homed before the guest
        // conversation's ON DELETE CASCADE could reach them.
        ContactConversation target = own.get();
        target.absorbClocks(guestConversation);
        for (ContactMessage message : messageRepository.findByConversationId(guestConversation.getId())) {
            message.moveTo(target);
        }
        conversationRepository.delete(guestConversation);
    }
}
