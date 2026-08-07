package pl.nextsteppro.climbing.domain.athleteweight;

import jakarta.persistence.*;
import pl.nextsteppro.climbing.domain.user.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A single morning body-weight reading of an athlete.
 *
 * <p>One reading per day (unique index in V74): weighing twice is a correction, not a second
 * data point, so the service upserts on {@code (athlete, measuredOn)}. Only the athlete writes
 * these — the coach reads the series but never records anybody else's weight.
 *
 * <p>Day-to-day readings are noisy (water, salt, food timing); the meaningful signal is the
 * 7-day trend computed by {@link WeightTrendCalculator}, which is why nothing derived is
 * stored here.
 */
@Entity
@Table(name = "athlete_weights")
public class AthleteWeight {

    /** Wide enough for anyone, narrow enough to catch a slipped decimal point (7.42 / 742). */
    public static final BigDecimal MIN_KG = new BigDecimal("20");
    public static final BigDecimal MAX_KG = new BigDecimal("300");

    /** NUMERIC(5,2) in the DB — normalize on the way in so 70.333 is accepted, not rejected. */
    public static final int SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private User athlete;

    @Column(name = "measured_on", nullable = false)
    private LocalDate measuredOn;

    @Column(name = "weight_kg", nullable = false, precision = 5, scale = SCALE)
    private BigDecimal weightKg;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AthleteWeight() {}

    public AthleteWeight(User athlete, LocalDate measuredOn, BigDecimal weightKg) {
        this.athlete = athlete;
        this.measuredOn = measuredOn;
        this.weightKg = normalize(weightKg);
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

    /** The athlete typing 70.333 should get 70.33, not a 400 — the DB column is NUMERIC(5,2). */
    public static BigDecimal normalize(BigDecimal weightKg) {
        return weightKg.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public UUID getId() {
        return id;
    }

    public User getAthlete() {
        return athlete;
    }

    public LocalDate getMeasuredOn() {
        return measuredOn;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
