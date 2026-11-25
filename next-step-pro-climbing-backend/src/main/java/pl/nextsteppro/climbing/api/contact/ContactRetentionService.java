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

    /** An upload this young is spared by the orphan pass: its row may not be committed yet. */
    static final Duration ORPHAN_GRACE = Duration.ofHours(6);

    private final ContactMessageRepository messageRepository;
    private final ContactConversationRepository conversationRepository;
    private final ContactFileSupport files;

    public ContactRetentionService(ContactMessageRepository messageRepository,
                                   ContactConversationRepository conversationRepository,
                                   ContactFileSupport files) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.files = files;
    }

    /**
     * Files first (V111): each one's own year is up by its stored date, and the files of messages
     * about to go must be named before the bulk delete - their rows leave by the database cascade,
     * which nothing in Hibernate sees. Then the orphan pass, which is what makes "gone after a
     * year" checkable when an unlink failed.
     *
     * @return messages deleted
     */
    @Transactional
    public int deleteExpired(Instant now) {
        files.deleteExpired(now);
        Instant cutoff = now.minus(RETENTION);
        files.purgeForMessagesBefore(cutoff);
        int messages = messageRepository.deleteOlderThan(cutoff);
        conversationRepository.deleteEmpty();
        return messages;
    }

    /** Separate from the delete, so a failing folder listing cannot roll back the year's sweep. */
    public int sweepOrphanFiles() {
        return files.sweepOrphans(ORPHAN_GRACE);
    }
}
