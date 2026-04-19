package pl.nextsteppro.climbing.domain.newsletter;

import jakarta.persistence.*;
import pl.nextsteppro.climbing.domain.user.User;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "newsletter_consent_log")
public class NewsletterConsentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NewsletterConsentLog() {}

    public NewsletterConsentLog(User user, String action, String source) {
        this.user = user;
        this.action = action;
        this.source = source;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getAction() { return action; }
    public String getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }
}
