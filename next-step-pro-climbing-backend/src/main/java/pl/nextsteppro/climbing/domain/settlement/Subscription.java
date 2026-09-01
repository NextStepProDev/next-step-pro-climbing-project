package pl.nextsteppro.climbing.domain.settlement;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.user.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A standing monthly fee for coaching somebody — the plan, the feedback, the being available —
 * rather than for any one session they attended.
 *
 * <p>The subscription is only the RULE. What it produces is an ordinary settlement with a month for
 * its target, which is why an unpaid fee lands in the same outstanding list as everything else that
 * person owes and settles in the same click. A table of its own would have meant a second debt
 * queue, a second revenue sum and a second list on the user card.
 *
 * <p>{@code endedOn} may be in the past on purpose: a collaboration ends in a conversation and gets
 * written down a week later.
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    public static final BigDecimal MIN_AMOUNT = BigDecimal.ZERO;
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("100000");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    @Column(name = "ended_on")
    @Nullable
    private LocalDate endedOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Subscription() {}

    public Subscription(User user, BigDecimal amount, LocalDate startedOn, @Nullable LocalDate endedOn) {
        this.user = user;
        this.amount = amount;
        this.startedOn = startedOn;
        this.endedOn = endedOn;
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

    /** Any day of a month is that month; the day carries nothing but a look of precision. */
    public static LocalDate normalizeMonth(LocalDate anyDayOfMonth) {
        return anyDayOfMonth.withDayOfMonth(1);
    }

    /**
     * Changing the amount is deliberately forward-only: months already billed keep what they were
     * billed at, because a raise in June is not a claim about March.
     */
    public void changeAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void endOn(@Nullable LocalDate endedOn) {
        this.endedOn = endedOn;
    }

    /** Months this subscription owes a fee for, inclusive. */
    public boolean covers(LocalDate month) {
        return !month.isBefore(startedOn) && (endedOn == null || !month.isAfter(endedOn));
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return user.getId();
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getStartedOn() {
        return startedOn;
    }

    @Nullable
    public LocalDate getEndedOn() {
        return endedOn;
    }

    public boolean isActive() {
        return endedOn == null;
    }
}
