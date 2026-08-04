package pl.nextsteppro.climbing.domain.personaltraining;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.user.User;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * A personal training entry in an athlete's training calendar (TrainingPeaks-style).
 *
 * <p>The calendar is a shared plan between the athlete and the coach (admin): either side
 * may create, edit or delete any entry. {@code createdByAdmin} keeps provenance visible;
 * {@code lastModifiedByAdmin} drives the athlete's "new from coach" unread counters.
 *
 * <p>Completion: the athlete marks the training done ({@code completedAt}) with optional
 * feedback text and an RPE rating (1-10). A "missed" state is never stored — it is derived
 * as: not completed and the training's end lies in the past (Europe/Warsaw).
 */
@Entity
@Table(name = "personal_trainings")
public class PersonalTraining {

    public static final int MAX_TITLE_LENGTH = 150;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    public static final int MAX_FEEDBACK_LENGTH = 2000;
    // Mirrors the CHECK in V77: below 500 and above 10000 is a slipped digit, not a diet.
    public static final int MIN_TARGET_CALORIES = 500;
    public static final int MAX_TARGET_CALORIES = 10000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private User athlete;

    // Set once, at construction. There is deliberately no setter and no path through update():
    // see TrainingKind.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrainingKind kind = TrainingKind.TRAINING;

    // TASK only, and optional there — a task can be "drink 3 litres" with no number at all.
    @Column(name = "target_calories")
    @Nullable
    private Integer targetCalories;

    @Column(name = "training_date", nullable = false)
    private LocalDate trainingDate;

    // Untimed ("all-day") training: both null. Otherwise both set. Never exactly one (service-enforced).
    @Column(name = "start_time")
    @Nullable
    private LocalTime startTime;

    @Column(name = "end_time")
    @Nullable
    private LocalTime endTime;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(length = MAX_DESCRIPTION_LENGTH)
    @Nullable
    private String description;

    @Column(name = "created_by_admin", nullable = false)
    private boolean createdByAdmin = false;

    @Column(name = "last_modified_by_admin", nullable = false)
    private boolean lastModifiedByAdmin = false;

    @Column(name = "completed_at")
    @Nullable
    private Instant completedAt;

    @Column(length = MAX_FEEDBACK_LENGTH)
    @Nullable
    private String feedback;

    @Nullable
    private Integer rpe;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Optimistic lock: the calendar is a shared plan (athlete + coach edit the same row), so a
    // concurrent edit must fail loudly (409) instead of silently overwriting. See V73.
    @Version
    @Column(nullable = false)
    private long version;

    protected PersonalTraining() {}

    public PersonalTraining(User athlete, LocalDate trainingDate, @Nullable LocalTime startTime, @Nullable LocalTime endTime,
                            String title, @Nullable String description, boolean createdByAdmin) {
        this(athlete, TrainingKind.TRAINING, trainingDate, startTime, endTime, title, description, null, createdByAdmin);
    }

    public PersonalTraining(User athlete, TrainingKind kind, LocalDate trainingDate,
                            @Nullable LocalTime startTime, @Nullable LocalTime endTime,
                            String title, @Nullable String description, @Nullable Integer targetCalories,
                            boolean createdByAdmin) {
        this.athlete = athlete;
        this.kind = kind;
        this.trainingDate = trainingDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.title = title;
        this.description = description;
        this.targetCalories = targetCalories;
        this.createdByAdmin = createdByAdmin;
        this.lastModifiedByAdmin = createdByAdmin;
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

    /**
     * HTML-escapes and trims free text (same pattern as TrainingRequest.sanitizeComment).
     * The UTF-8 variant escapes only dangerous characters (&lt; &gt; " &amp; '); the one-arg variant
     * assumes ISO-8859-1 and would turn diacritics into entities (ó → &amp;oacute;), mangling Polish text.
     */
    @Nullable
    public static String sanitizeText(@Nullable String text, int maxLength) {
        if (text == null || text.isBlank()) return null;
        String escaped = HtmlUtils.htmlEscape(text.trim(), java.nio.charset.StandardCharsets.UTF_8.name());
        return escaped.length() > maxLength ? escaped.substring(0, maxLength) : escaped;
    }

    // No `kind` parameter, on purpose: an entry is a training or a task from birth. Everything else
    // about it, including a task's calorie target, stays editable.
    public void update(LocalDate trainingDate, @Nullable LocalTime startTime, @Nullable LocalTime endTime,
                       String title, @Nullable String description, @Nullable Integer targetCalories,
                       boolean modifiedByAdmin) {
        this.trainingDate = trainingDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.title = title;
        this.description = description;
        this.targetCalories = targetCalories;
        this.lastModifiedByAdmin = modifiedByAdmin;
    }

    public boolean isTask() {
        return kind == TrainingKind.TASK;
    }

    // Completion is an athlete-only action. It must also clear lastModifiedByAdmin:
    // @PreUpdate bumps updatedAt, and with the flag still true (an earlier coach edit)
    // the athlete's own completion would light their own "new from coach" badge.
    public void complete(@Nullable String feedback, @Nullable Integer rpe) {
        this.completedAt = Instant.now();
        this.feedback = feedback;
        this.rpe = rpe;
        this.lastModifiedByAdmin = false;
    }

    public void uncomplete() {
        this.completedAt = null;
        this.feedback = null;
        this.rpe = null;
        this.lastModifiedByAdmin = false;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public User getAthlete() {
        return athlete;
    }

    public TrainingKind getKind() {
        return kind;
    }

    @Nullable
    public Integer getTargetCalories() {
        return targetCalories;
    }

    public LocalDate getTrainingDate() {
        return trainingDate;
    }

    @Nullable
    public LocalTime getStartTime() {
        return startTime;
    }

    @Nullable
    public LocalTime getEndTime() {
        return endTime;
    }

    public String getTitle() {
        return title;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public boolean isCreatedByAdmin() {
        return createdByAdmin;
    }

    public boolean isLastModifiedByAdmin() {
        return lastModifiedByAdmin;
    }

    @Nullable
    public Instant getCompletedAt() {
        return completedAt;
    }

    @Nullable
    public String getFeedback() {
        return feedback;
    }

    @Nullable
    public Integer getRpe() {
        return rpe;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
