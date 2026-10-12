package pl.nextsteppro.climbing.domain.newsletter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NewsletterConsentLogRepository extends JpaRepository<NewsletterConsentLog, UUID> {

    /** One person's consent history, oldest first — the personal-data export. */
    List<NewsletterConsentLog> findByUserIdOrderByCreatedAtAsc(UUID userId);
}
