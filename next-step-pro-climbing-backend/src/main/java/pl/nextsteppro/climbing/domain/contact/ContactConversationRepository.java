package pl.nextsteppro.climbing.domain.contact;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ContactConversationRepository extends JpaRepository<ContactConversation, UUID> {

    Optional<ContactConversation> findByUserId(UUID userId);

    @Query("SELECT c FROM ContactConversation c WHERE lower(c.guestEmail) = lower(:email)")
    Optional<ContactConversation> findByGuestEmail(String email);

    /** The admin list: newest inbound first, the account loaded for its name in the same query. */
    @Query(value = """
        SELECT c FROM ContactConversation c LEFT JOIN FETCH c.user
        ORDER BY c.lastInboundAt DESC
        """,
        countQuery = "SELECT count(c) FROM ContactConversation c")
    Page<ContactConversation> findAllForStaff(Pageable pageable);

    @Query(value = """
        SELECT c FROM ContactConversation c LEFT JOIN FETCH c.user
        WHERE c.staffReadAt IS NULL OR c.lastInboundAt > c.staffReadAt
        ORDER BY c.lastInboundAt DESC
        """,
        countQuery = """
        SELECT count(c) FROM ContactConversation c
        WHERE c.staffReadAt IS NULL OR c.lastInboundAt > c.staffReadAt
        """)
    Page<ContactConversation> findUnreadForStaff(Pageable pageable);

    @Query("""
        SELECT count(c) FROM ContactConversation c
        WHERE c.staffReadAt IS NULL OR c.lastInboundAt > c.staffReadAt
        """)
    int countUnreadForStaff();

    /** Conversations left with no message once the retention sweep has run. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        DELETE FROM ContactConversation c
        WHERE NOT EXISTS (SELECT 1 FROM ContactMessage m WHERE m.conversation = c)
        """)
    int deleteEmpty();
}
