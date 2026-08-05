package pl.nextsteppro.climbing.domain.trainingtemplate;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import org.springframework.web.util.HtmlUtils;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingKind;

import java.time.Instant;
import java.util.UUID;

/**
 * A reusable entry template in the coach's shared library. Applying a template COPIES its content
 * into a new entry (kind, title, description, default duration or calorie target, materials) —
 * later edits to the template never touch already-created trainings. Materials live in
 * {@code training_attachments} keyed by {@code template_id}.
 *
 * <p>A template comes in the same two shapes an entry does: a TRAINING carries a default duration
 * and no calorie target, a TASK carries the reverse. Unlike {@code personal_trainings.kind}, the
 * kind here is editable — that one is frozen because flipping a completed training would have to
 * discard its RPE, and a template holds no completion, rating or history to lose.
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrainingKind kind = TrainingKind.TRAINING;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(length = MAX_DESCRIPTION_LENGTH)
    @Nullable
    private String description;

    /** Trainings only — a task holds for the whole day, so there is no span to prefill. */
    @Column(name = "default_duration_minutes")
    @Nullable
    private Integer defaultDurationMinutes;

    /** Tasks only, and optional there too: "drink 3 litres" carries its number in the title. */
    @Column(name = "target_calories")
    @Nullable
    private Integer targetCalories;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TrainingTemplate() {}

    public TrainingTemplate(TrainingKind kind, String title, @Nullable String description,
                            @Nullable Integer defaultDurationMinutes, @Nullable Integer targetCalories) {
        this.kind = kind;
        this.title = title;
        this.description = description;
        this.defaultDurationMinutes = defaultDurationMinutes;
        this.targetCalories = targetCalories;
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

    /**
     * The kind travels with the update: a library entry may change its mind about what it makes,
     * and the caller has already checked that duration/calories match the new shape.
     */
    public void update(TrainingKind kind, String title, @Nullable String description,
                       @Nullable Integer defaultDurationMinutes, @Nullable Integer targetCalories) {
        this.kind = kind;
        this.title = title;
        this.description = description;
        this.defaultDurationMinutes = defaultDurationMinutes;
        this.targetCalories = targetCalories;
    }

    public UUID getId() {
        return id;
    }

    public TrainingKind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    @Nullable
    public Integer getDefaultDurationMinutes() {
        return defaultDurationMinutes;
    }

    @Nullable
    public Integer getTargetCalories() {
        return targetCalories;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
