package pl.nextsteppro.climbing.domain.personaltraining;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * A material attached to a training — currently a link (URL + optional label). YouTube/Instagram
 * links are rendered as an embedded player in the UI (the embed URL is computed in the DTO, never
 * stored). A follow-up adds uploaded files (PDF/images) on the same table.
 *
 * <p>Orchestrated by the service (no JPA collection on {@link PersonalTraining}), mirroring how
 * comments and deletions are handled.
 */
@Entity
@Table(name = "training_attachments")
public class TrainingAttachment {

    public static final int MAX_LABEL_LENGTH = 120;
    public static final int MAX_URL_LENGTH = 2048;
    public static final int MAX_PER_TRAINING = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_id", nullable = false)
    private PersonalTraining training;

    @Column(nullable = false, length = MAX_URL_LENGTH)
    private String url;

    @Column(length = MAX_LABEL_LENGTH)
    @Nullable
    private String label;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TrainingAttachment() {}

    public TrainingAttachment(PersonalTraining training, String url, @Nullable String label, int position) {
        this.training = training;
        this.url = url;
        this.label = label;
        this.position = position;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /** Same UTF-8 HTML-escape as PersonalTraining.sanitizeText; null/blank label is allowed. */
    @Nullable
    public static String sanitizeLabel(@Nullable String label) {
        if (label == null || label.isBlank()) return null;
        String escaped = HtmlUtils.htmlEscape(label.trim(), java.nio.charset.StandardCharsets.UTF_8.name());
        return escaped.length() > MAX_LABEL_LENGTH ? escaped.substring(0, MAX_LABEL_LENGTH) : escaped;
    }

    public UUID getId() {
        return id;
    }

    /** FK access without initialising the lazy proxy. NOT named getTrainingId() on purpose —
     * a "trainingId" bean property makes Spring Data derive findByTrainingId as the invalid
     * path a.trainingId instead of a.training.id. */
    public UUID trainingId() {
        return training.getId();
    }

    public String getUrl() {
        return url;
    }

    @Nullable
    public String getLabel() {
        return label;
    }

    public int getPosition() {
        return position;
    }
}
