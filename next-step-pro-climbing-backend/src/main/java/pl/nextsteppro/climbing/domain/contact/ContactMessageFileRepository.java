package pl.nextsteppro.climbing.domain.contact;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ContactMessageFileRepository extends JpaRepository<ContactMessageFile, UUID> {

    /** Every file of a page of messages in one read - one query per message would be N+1 per poll. */
    @Query("""
        SELECT f FROM ContactMessageFile f
        WHERE f.message.id IN :messageIds
        ORDER BY f.position ASC
        """)
    List<ContactMessageFile> findByMessageIds(Collection<UUID> messageIds);

    /** The file with its message and conversation, which every access check reads. */
    @Query("""
        SELECT f FROM ContactMessageFile f JOIN FETCH f.message m JOIN FETCH m.conversation c
        WHERE f.id = :fileId
        """)
    Optional<ContactMessageFile> findByIdWithConversation(UUID fileId);

    /** Live files of a conversation, for its cap. */
    @Query("SELECT count(f) FROM ContactMessageFile f WHERE f.message.conversation.id = :conversationId")
    long countForConversation(UUID conversationId);

    long countByMessageId(UUID messageId);

    /** Names on disk of a conversation about to be deleted (the rows go by cascade, unseen by Hibernate). */
    @Query("SELECT f.filename FROM ContactMessageFile f WHERE f.message.conversation.id = :conversationId")
    List<String> findFilenamesForConversation(UUID conversationId);

    /** Names on disk of an account's conversation, for account deletion. */
    @Query("SELECT f.filename FROM ContactMessageFile f WHERE f.message.conversation.user.id = :userId")
    List<String> findFilenamesForUser(UUID userId);

    /** Names on disk of messages the yearly sweep is about to delete. */
    @Query("SELECT f.filename FROM ContactMessageFile f WHERE f.message.createdAt < :cutoff")
    List<String> findFilenamesOfMessagesBefore(Instant cutoff);

    @Query("SELECT f FROM ContactMessageFile f JOIN FETCH f.message WHERE f.expiresAt < :now")
    List<ContactMessageFile> findExpiredBefore(Instant now);

    @Query("SELECT f.filename FROM ContactMessageFile f")
    Set<String> findAllFilenames();
}
