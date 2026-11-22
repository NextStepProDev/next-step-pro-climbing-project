package pl.nextsteppro.climbing.api.contact;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.contact.ContactConversationRepository;
import pl.nextsteppro.climbing.domain.contact.ContactMessageRepository;

import java.time.Duration;
import java.time.Instant;

/**
 * Messages are kept for a year, then deleted - the window the privacy policy states (PL and EN).
 * Changing {@link #RETENTION} without changing the policy leaves a public document lying about
 * how long we keep what people wrote to us.
 *
 * <p>A message goes by its own age, so a conversation that is still going keeps its recent part;
 * a conversation left with nothing in it goes too. A separate bean from the scheduler that calls
 * it: {@code @Scheduled} calling {@code @Transactional} on its own class would run without a
 * transaction.
 */
@Service
public class ContactRetentionService {

    public static final Duration RETENTION = Duration.ofDays(365);

    private final ContactMessageRepository messageRepository;
    private final ContactConversationRepository conversationRepository;

    public ContactRetentionService(ContactMessageRepository messageRepository,
                                   ContactConversationRepository conversationRepository) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
    }

    /** @return messages deleted */
    @Transactional
    public int deleteExpired(Instant now) {
        int messages = messageRepository.deleteOlderThan(now.minus(RETENTION));
        conversationRepository.deleteEmpty();
        return messages;
    }
}
