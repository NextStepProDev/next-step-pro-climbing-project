package pl.nextsteppro.climbing.domain.trainingtemplate;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * A reusable training template in the coach's shared library. Applying a template COPIES its
 * content into a new training (title, description, default duration, materials) — later edits to
 * the template never touch already-created trainings. Materials live in {@code training_attachments}
 * keyed by {@code template_id}.
 */
@Entity
@Table(name = "training_templates")
public class TrainingTemplate {

    public static final int MAX_TITLE_LENGTH = 150;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    public static final int MIN_DURATION_MINUTES = 15;
    public static final int MAX_DURATION_MINUTES = 720;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(length = MAX_DESCRIPTION_LENGTH)
    @Nullable
    private String description;

    @Column(name = "default_duration_minutes", nullable = false)
    private int defaultDurationMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TrainingTemplate() {}

    public TrainingTemplate(String title, @Nullable String description, int defaultDurationMinutes) {
        this.title = title;
        this.description = description;
        this.defaultDurationMinutes = defaultDurationMinutes;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Same UTF-8 HTML-escape as PersonalTraining.sanitizeText (keeps Polish diacritics). */
    @Nullable
    public static String sanitizeText(@Nullable String text, int maxLength) {
        if (text == null || text.isBlank()) return null;
        String escaped = HtmlUtils.htmlEscape(text.trim(), java.nio.charset.StandardCharsets.UTF_8.name());
        return escaped.length() > maxLength ? escaped.substring(0, maxLength) : escaped;
    }

    public void update(String title, @Nullable String description, int defaultDurationMinutes) {
        this.title = title;
        this.description = description;
        this.defaultDurationMinutes = defaultDurationMinutes;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public int getDefaultDurationMinutes() {
        return defaultDurationMinutes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
