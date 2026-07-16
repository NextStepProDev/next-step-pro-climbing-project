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

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private User athlete;

    @Column(name = "training_date", nullable = false)
    private LocalDate trainingDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
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

    protected PersonalTraining() {}

    public PersonalTraining(User athlete, LocalDate trainingDate, LocalTime startTime, LocalTime endTime,
                            String title, @Nullable String description, boolean createdByAdmin) {
        this.athlete = athlete;
        this.trainingDate = trainingDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.title = title;
        this.description = description;
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

    public void update(LocalDate trainingDate, LocalTime startTime, LocalTime endTime,
                       String title, @Nullable String description, boolean modifiedByAdmin) {
        this.trainingDate = trainingDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.title = title;
        this.description = description;
        this.lastModifiedByAdmin = modifiedByAdmin;
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

    public LocalDate getTrainingDate() {
        return trainingDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

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
