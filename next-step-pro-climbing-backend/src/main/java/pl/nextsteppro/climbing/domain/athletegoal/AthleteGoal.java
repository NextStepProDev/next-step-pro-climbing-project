package pl.nextsteppro.climbing.domain.athletegoal;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import org.springframework.web.util.HtmlUtils;
import pl.nextsteppro.climbing.domain.user.User;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A training goal set by the coach for an athlete, displayed above the training calendar.
 *
 * <p>At most one ACTIVE goal per {@link GoalHorizon} (enforced by a partial unique index).
 * An achieved goal ({@code achievedAt} set) becomes immutable and is NEVER deleted — the
 * trophy chest lists the athlete's full history of achieved goals as motivation.
 */
@Entity
@Table(name = "athlete_goals")
public class AthleteGoal {

    public static final int MAX_CONTENT_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private User athlete;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GoalHorizon horizon;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "achieved_at")
    @Nullable
    private Instant achievedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AthleteGoal() {}

    public AthleteGoal(User athlete, GoalHorizon horizon, String content, LocalDate targetDate) {
        this.athlete = athlete;
        this.horizon = horizon;
        this.content = content;
        this.targetDate = targetDate;
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

    /** Same UTF-8 HTML-escape pattern as PersonalTraining.sanitizeText (keeps Polish diacritics). */
    @Nullable
    public static String sanitizeContent(@Nullable String content) {
        if (content == null || content.isBlank()) return null;
        String escaped = HtmlUtils.htmlEscape(content.trim(), java.nio.charset.StandardCharsets.UTF_8.name());
        return escaped.length() > MAX_CONTENT_LENGTH ? escaped.substring(0, MAX_CONTENT_LENGTH) : escaped;
    }

    /** Horizon is fixed for an active goal — replacing the horizon means replacing the goal. */
    public void update(String content, LocalDate targetDate) {
        this.content = content;
        this.targetDate = targetDate;
    }

    /** The coach may backdate the achievement (goals often fall days before the visit). */
    public void markAchieved(Instant achievedAt) {
        this.achievedAt = achievedAt;
    }

    public boolean isAchieved() {
        return achievedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public User getAthlete() {
        return athlete;
    }

    public GoalHorizon getHorizon() {
        return horizon;
    }

    public String getContent() {
        return content;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    @Nullable
    public Instant getAchievedAt() {
        return achievedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
