package pl.nextsteppro.climbing.domain.contact;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {

    /** Every message, oldest first; the id breaks a tie in time exactly as the paged reads do. */
    @Query("""
        SELECT m FROM ContactMessage m LEFT JOIN FETCH m.staffAuthor
        WHERE m.conversation.id = :conversationId
        ORDER BY m.createdAt ASC, m.id ASC
        """)
    List<ContactMessage> findByConversationId(UUID conversationId);

    // ---- the chat window: a page of the newest messages, then older on scroll, newer on poll ----
    //
    // The cursor is the (createdAt, id) PAIR of a message the reader already has, never the time
    // alone: two messages can share a timestamp, and a time-only cursor would skip one of them for
    // good. Messages never change once written (no edit, no delete of a single one), so "after the
    // last one I have" is the whole of what a poll needs to ask.

    @Query("""
        SELECT m FROM ContactMessage m LEFT JOIN FETCH m.staffAuthor
        WHERE m.conversation.id = :conversationId
        ORDER BY m.createdAt DESC, m.id DESC
        """)
    List<ContactMessage> findNewestFirst(UUID conversationId, Pageable pageable);

    @Query("""
        SELECT m FROM ContactMessage m LEFT JOIN FETCH m.staffAuthor
        WHERE m.conversation.id = :conversationId
          AND (m.createdAt < :at OR (m.createdAt = :at AND m.id < :id))
        ORDER BY m.createdAt DESC, m.id DESC
        """)
    List<ContactMessage> findOlderThan(UUID conversationId, Instant at, UUID id, Pageable pageable);

    @Query("""
        SELECT m FROM ContactMessage m LEFT JOIN FETCH m.staffAuthor
        WHERE m.conversation.id = :conversationId
          AND (m.createdAt > :at OR (m.createdAt = :at AND m.id > :id))
        ORDER BY m.createdAt ASC, m.id ASC
        """)
    List<ContactMessage> findNewerThan(UUID conversationId, Instant at, UUID id, Pageable pageable);

    /** The newest {@link MessageWindow#PAGE_SIZE} messages, oldest first, and whether more lie above. */
    default MessageWindow latest(UUID conversationId) {
        return MessageWindow.ofNewestFirst(
            findNewestFirst(conversationId, PageRequest.of(0, MessageWindow.PAGE_SIZE + 1)));
    }

    /** The page just above the message at (at, id), oldest first. */
    default MessageWindow olderThan(UUID conversationId, Instant at, UUID id) {
        return MessageWindow.ofNewestFirst(
            findOlderThan(conversationId, at, id, PageRequest.of(0, MessageWindow.PAGE_SIZE + 1)));
    }

    /**
     * What arrived after the message at (at, id), oldest first. Capped, so a reader who comes back
     * to a tab after a flood gets it in a few polls instead of one unbounded read.
     */
    default List<ContactMessage> newerThan(UUID conversationId, Instant at, UUID id) {
        return findNewerThan(conversationId, at, id, PageRequest.of(0, MessageWindow.NEWER_CAP));
    }

    /**
     * Everything a person wrote, in one read (the data export): their account's conversation, and
     * — only when {@code confirmedEmail} is given — the guest conversation under that address,
     * which is where they land when they write from the form signed out. Pass null for an
     * unconfirmed account: until the address is proven, mail from it is not this account's data.
     *
     * <p>The casts are not decoration: a null parameter reaches Postgres untyped, and
     * {@code lower(:p)} on it fails as {@code lower(bytea)}.
     */
    @Query("""
        SELECT m FROM ContactMessage m JOIN m.conversation c
        WHERE c.user.id = :userId
           OR (cast(:confirmedEmail as String) IS NOT NULL
               AND lower(c.guestEmail) = lower(cast(:confirmedEmail as String)))
        ORDER BY m.createdAt ASC
        """)
    List<ContactMessage> findAllWrittenBy(UUID userId, @Nullable String confirmedEmail);

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
