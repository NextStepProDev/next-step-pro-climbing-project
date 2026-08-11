package pl.nextsteppro.climbing.domain.personaltraining;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.user.User;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * A single message in the athlete &lt;-&gt; coach thread attached to a personal training.
 *
 * <p>{@code authorIsAdmin} records the author's role at the time of writing (robust against
 * later role changes) and drives the unread counters: the athlete counts coach messages,
 * the coach counts athlete messages.
 */
@Entity
@Table(name = "training_comments")
public class TrainingComment {

    public static final int MAX_BODY_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_id", nullable = false)
    private PersonalTraining training;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "author_is_admin", nullable = false)
    private boolean authorIsAdmin;

    // Nullable since V80: a message can be nothing but an attachment ("look, this is how it went").
    // "Neither text nor file" is refused by TrainingCommentFileService — the condition reaches
    // another table, so it cannot be a CHECK.
    @Column(length = MAX_BODY_LENGTH)
    @Nullable
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TrainingComment() {}

    public TrainingComment(PersonalTraining training, User author, boolean authorIsAdmin, @Nullable String body) {
        this.training = training;
        this.author = author;
        this.authorIsAdmin = authorIsAdmin;
        this.body = body;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /** HTML-escapes and trims the message (UTF-8 variant — keeps Polish diacritics intact). */
    @Nullable
    public static String sanitizeBody(@Nullable String body) {
        if (body == null || body.isBlank()) return null;
        String escaped = HtmlUtils.htmlEscape(body.trim(), java.nio.charset.StandardCharsets.UTF_8.name());
        return escaped.length() > MAX_BODY_LENGTH ? escaped.substring(0, MAX_BODY_LENGTH) : escaped;
    }

    public UUID getId() {
        return id;
    }

    public PersonalTraining getTraining() {
        return training;
    }

    public User getAuthor() {
        return author;
    }

    public boolean isAuthorIsAdmin() {
        return authorIsAdmin;
    }

    @Nullable
    public String getBody() {
        return body;
    }

    /**
     * FK access without initialising the lazy proxy. NOT named {@code getTrainingId()} — a
     * "trainingId" bean property makes Spring Data derive {@code findByTrainingId} as the invalid
     * path {@code c.trainingId} instead of {@code c.training.id} (see {@link TrainingAttachment}).
     */
    public UUID trainingId() {
        return training.getId();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
