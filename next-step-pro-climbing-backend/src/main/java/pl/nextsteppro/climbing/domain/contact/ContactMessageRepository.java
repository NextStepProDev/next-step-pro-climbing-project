package pl.nextsteppro.climbing.domain.contact;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {

    @Query("""
        SELECT m FROM ContactMessage m LEFT JOIN FETCH m.staffAuthor
        WHERE m.conversation.id = :conversationId
        ORDER BY m.createdAt ASC
        """)
    List<ContactMessage> findByConversationId(UUID conversationId);

    /** An account's whole conversation in one read (the data export). */
    @Query("""
        SELECT m FROM ContactMessage m
        WHERE m.conversation.user.id = :userId
        ORDER BY m.createdAt ASC
        """)
    List<ContactMessage> findByConversationUserId(UUID userId);

    /**
     * The newest message of each listed conversation - the preview line in the admin list. One
     * query for the whole page instead of one per row.
     */
    @Query("""
        SELECT m FROM ContactMessage m
        WHERE m.conversation.id IN :conversationIds
          AND m.createdAt = (SELECT max(m2.createdAt) FROM ContactMessage m2 WHERE m2.conversation = m.conversation)
        """)
    List<ContactMessage> findLatestIn(Collection<UUID> conversationIds);

    /** The newest message the PERSON wrote, for the language and context of the reply. */
    @Query("""
        SELECT m FROM ContactMessage m
        WHERE m.conversation.id = :conversationId AND m.fromStaff = false
        ORDER BY m.createdAt DESC
        LIMIT 1
        """)
    List<ContactMessage> findLatestInbound(UUID conversationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ContactMessage m WHERE m.conversation.id = :conversationId")
    int deleteByConversationId(UUID conversationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ContactMessage m WHERE m.createdAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
