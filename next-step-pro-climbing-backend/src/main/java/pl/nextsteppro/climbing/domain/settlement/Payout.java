package pl.nextsteppro.climbing.domain.settlement;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One transfer that arrived for a batch of work.
 *
 * <p>Two dates, and they answer different questions. {@code periodMonth} is the month the work was
 * done in — what the money is FOR, and the axis the derived rate is computed on. {@code receivedOn}
 * is when it landed, and revenue counts on that, exactly as {@code Settlement.settledOn} does, so a
 * monthly total keeps one axis no matter which way the money came in.
 *
 * <p>The amount is what reached the account. Net is the natural unit here because it is the only
 * figure the owner actually holds — gross and deductions are the payer's bookkeeping.
 */
@Entity
@Table(name = "payouts")
public class Payout {

    public static final BigDecimal MIN_AMOUNT = BigDecimal.ZERO;
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000");
    public static final int AMOUNT_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payout_source_id", nullable = false)
    private PayoutSource source;

    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Column(nullable = false, precision = 10, scale = AMOUNT_SCALE)
    private BigDecimal amount;

    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payout() {}

    public Payout(PayoutSource source, LocalDate periodMonth, BigDecimal amount, LocalDate receivedOn) {
        this.source = source;
        this.periodMonth = periodMonth;
        this.amount = amount;
        this.receivedOn = receivedOn;
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
     * Rounds to the column's scale and mirrors {@code chk_payouts_amount_range}, so a bad figure
     * comes back as a translated message rather than a constraint name.
     */
    public static BigDecimal normalizeAmount(BigDecimal amount, String outOfRangeMessage) {
        BigDecimal scaled = amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        if (scaled.compareTo(MIN_AMOUNT) < 0 || scaled.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException(outOfRangeMessage);
        }
        return scaled;
    }

    /** Any day of the month snaps to its first, which is what the CHECK in V93 stores. */
    public static LocalDate normalizePeriod(LocalDate anyDayOfMonth) {
        return anyDayOfMonth.withDayOfMonth(1);
    }

    public void update(LocalDate periodMonth, BigDecimal amount, LocalDate receivedOn) {
        this.periodMonth = periodMonth;
        this.amount = amount;
        this.receivedOn = receivedOn;
    }

    public UUID getId() {
        return id;
    }

    @Nullable
    public LocalDate getPeriodMonth() {
        return periodMonth;
    }
}
