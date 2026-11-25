package pl.nextsteppro.climbing.domain.contact;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactConversationRepository extends JpaRepository<ContactConversation, UUID> {

    Optional<ContactConversation> findByUserId(UUID userId);

    @Query("SELECT c FROM ContactConversation c WHERE lower(c.guestEmail) = lower(:email)")
    Optional<ContactConversation> findByGuestEmail(String email);

    /**
     * The admin list, ordered like a messenger: the newest message in either direction first, the
     * id breaking ties so a page boundary never shows a row twice or skips one. The account is
     * fetched in the same query for its name.
     *
     * <p>{@code pattern} is a lower-cased {@code %…%} LIKE pattern, or null for no search; it
     * matches the account's name and address, or the guest's. Unread means "the person wrote
     * something nobody on the team has read" - a conversation only the team wrote in never is
     * (its inbound clock is null, and the comparison is spelled out rather than left to NULL
     * semantics). The casts are not decoration: a null parameter reaches Postgres untyped.
     */
    @Query(value = """
        SELECT c FROM ContactConversation c LEFT JOIN FETCH c.user u
        WHERE (:unreadOnly = false
               OR (c.lastInboundAt IS NOT NULL AND (c.staffReadAt IS NULL OR c.lastInboundAt > c.staffReadAt)))
          AND (cast(:pattern as String) IS NULL
               OR lower(concat(u.firstName, ' ', u.lastName)) LIKE cast(:pattern as String)
               OR lower(u.email) LIKE cast(:pattern as String)
               OR lower(c.guestName) LIKE cast(:pattern as String)
               OR lower(c.guestEmail) LIKE cast(:pattern as String))
        ORDER BY c.lastActivityAt DESC, c.id
        """,
        countQuery = """
        SELECT count(c) FROM ContactConversation c LEFT JOIN c.user u
        WHERE (:unreadOnly = false
               OR (c.lastInboundAt IS NOT NULL AND (c.staffReadAt IS NULL OR c.lastInboundAt > c.staffReadAt)))
          AND (cast(:pattern as String) IS NULL
               OR lower(concat(u.firstName, ' ', u.lastName)) LIKE cast(:pattern as String)
               OR lower(u.email) LIKE cast(:pattern as String)
               OR lower(c.guestName) LIKE cast(:pattern as String)
               OR lower(c.guestEmail) LIKE cast(:pattern as String))
        """)
    Page<ContactConversation> findForStaff(boolean unreadOnly, @Nullable String pattern, Pageable pageable);

    @Query("""
        SELECT count(c) FROM ContactConversation c
        WHERE c.lastInboundAt IS NOT NULL AND (c.staffReadAt IS NULL OR c.lastInboundAt > c.staffReadAt)
        """)
    int countUnreadForStaff();

    /**
     * Account conversations whose newest answer from the team has waited unseen since before
     * {@code dueBefore} and is newer than {@code notOlderThan} - and that have had no reminder
     * since the person last opened the chat. A reminder counts until they look in: more answers
     * after it do not earn another one, so somebody who does not react gets one mail, not one
     * every few days. (The newest answer is the clock, so several messages in a row are one.)
     */
    @Query("""
        SELECT c FROM ContactConversation c JOIN FETCH c.user u
        WHERE c.lastReplyAt IS NOT NULL
          AND c.lastReplyAt <= :dueBefore
          AND c.lastReplyAt > :notOlderThan
          AND (c.userSeenAt IS NULL OR c.userSeenAt < c.lastReplyAt)
          AND (c.unseenReminderSentAt IS NULL
               OR (c.userSeenAt IS NOT NULL AND c.unseenReminderSentAt < c.userSeenAt))
        """)
    List<ContactConversation> findDueForUnseenReminder(Instant dueBefore, Instant notOlderThan);

    /** Conversations left with no message once the retention sweep has run. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        DELETE FROM ContactConversation c
        WHERE NOT EXISTS (SELECT 1 FROM ContactMessage m WHERE m.conversation = c)
        """)
    int deleteEmpty();
}
