package pl.nextsteppro.climbing.domain.newsletter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NewsletterConsentLogRepository extends JpaRepository<NewsletterConsentLog, UUID> {
}
