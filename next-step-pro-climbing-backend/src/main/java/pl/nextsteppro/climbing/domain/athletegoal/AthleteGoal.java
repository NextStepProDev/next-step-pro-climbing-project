package pl.nextsteppro.climbing.domain.athletegoal;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import org.springframework.web.util.HtmlUtils;
import pl.nextsteppro.climbing.domain.athleteweight.AthleteWeight;
import pl.nextsteppro.climbing.domain.athleteweight.WeightTrendCalculator.ConfirmedTrend;
import pl.nextsteppro.climbing.domain.user.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A goal set by the coach for an athlete, displayed above the training calendar.
 *
 * <p>At most one ACTIVE goal per {@link GoalKind} + {@link GoalHorizon} pair (enforced by a
 * partial unique index), so the banner renders two rows of at most three cards. An achieved
 * goal ({@code achievedAt} set) is NEVER deleted — the trophy chest lists the athlete's full
 * history of achieved goals as motivation.
 *
 * <p><b>Immutability, with one exception.</b> A goal the COACH closed by hand stays closed
 * forever. A {@link GoalKind#WEIGHT} goal that closed ITSELF off a weigh-in
 * ({@code achievedAutomatically}) may be reopened — a mistyped weight must not permanently
 * hand out a trophy nobody earned.
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
    private GoalKind kind = GoalKind.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GoalHorizon horizon;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    /** WEIGHT goals only: the weight the athlete is chasing (kg). */
    @Column(name = "target_weight_kg", precision = 5, scale = 2)
    @Nullable
    private BigDecimal targetWeightKg;

    /**
     * WEIGHT goals only: snapshot of the trend when the goal was set. Frozen on purpose —
     * it is the left edge of the progress bar, so a later weigh-in must not move it.
     */
    @Column(name = "start_weight_kg", precision = 5, scale = 2)
    @Nullable
    private BigDecimal startWeightKg;

    @Column(name = "achieved_at")
    @Nullable
    private Instant achievedAt;

    /** True only when a weigh-in closed this goal — the sole case the coach may reopen. */
    @Column(name = "achieved_automatically", nullable = false)
    private boolean achievedAutomatically;

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

    /**
     * A weight goal. {@code startWeightKg} is the trend at the moment of creation — the coach
     * never types it, because a hand-entered starting point would let the progress bar lie.
     */
    public static AthleteGoal weightGoal(User athlete, GoalHorizon horizon, String content,
                                         LocalDate targetDate, BigDecimal targetWeightKg,
                                         BigDecimal startWeightKg) {
        AthleteGoal goal = new AthleteGoal(athlete, horizon, content, targetDate);
        goal.kind = GoalKind.WEIGHT;
        goal.targetWeightKg = AthleteWeight.normalize(targetWeightKg);
        goal.startWeightKg = AthleteWeight.normalize(startWeightKg);
        return goal;
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
        this.achievedAutomatically = false;
    }

    /** Closed by a weigh-in rather than by a person — and therefore reversible. */
    public void markAchievedAutomatically(Instant achievedAt) {
        this.achievedAt = achievedAt;
        this.achievedAutomatically = true;
    }

    /** Undo of an automatic closure only; the caller enforces that (see AthleteGoalService). */
    public void reopen() {
        this.achievedAt = null;
        this.achievedAutomatically = false;
    }

    public boolean isAchieved() {
        return achievedAt != null;
    }

    /**
     * Losing when the target sits below where the athlete started, gaining otherwise —
     * which way the comparison in {@link #isMetBy} runs.
     */
    public boolean isLossGoal() {
        return targetWeightKg != null && startWeightKg != null
            && targetWeightKg.compareTo(startWeightKg) < 0;
    }

    /**
     * Has the athlete reached this weight goal?
     *
     * <p>Takes a {@link ConfirmedTrend} and nothing else, on purpose: only a trend backed by
     * enough readings may close a goal, and making that a TYPE constraint means a future
     * refactor cannot quietly close goals off a single lucky morning reading.
     */
    public boolean isMetBy(ConfirmedTrend trend) {
        if (targetWeightKg == null) return false;
        return isLossGoal()
            ? trend.value().compareTo(targetWeightKg) <= 0
            : trend.value().compareTo(targetWeightKg) >= 0;
    }

    public UUID getId() {
        return id;
    }

    public User getAthlete() {
        return athlete;
    }

    public GoalKind getKind() {
        return kind;
    }

    public GoalHorizon getHorizon() {
        return horizon;
    }

    @Nullable
    public BigDecimal getTargetWeightKg() {
        return targetWeightKg;
    }

    @Nullable
    public BigDecimal getStartWeightKg() {
        return startWeightKg;
    }

    public boolean isAchievedAutomatically() {
        return achievedAutomatically;
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
