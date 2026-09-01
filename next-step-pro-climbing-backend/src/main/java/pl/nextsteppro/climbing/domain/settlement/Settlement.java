package pl.nextsteppro.climbing.domain.settlement;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.reservation.GuestReservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What one participant owes for one calendar entry, and whether they have paid.
 *
 * <p><b>Admin-only, and the privacy does not depend on remembering.</b> Money about named people
 * never travels in a shared DTO: the calendar shapes are served to anonymous visitors and cached
 * under {@code calendarMonth/Week/Day} whenever {@code userId == null}. Everything about this
 * feature therefore lives in this package and {@code api.admin.settlement}, and
 * {@code SettlementIsolationTest} keeps the types unreachable anywhere else.
 *
 * <p><b>The target is a slot XOR an event XOR a month — never a reservation.</b> The third is a
 * standing coaching fee, which belongs to no single session; it lives here rather than in a table of
 * its own so that an unpaid fee lands in the same outstanding list as everything else that person
 * owes and settles in the same click. Booking a multi-day event
 * writes one {@code reservations} row <em>per day</em>, so a settlement hanging on a reservation
 * would price a three-day course three times. Pairing (target, payer) makes "one settlement per
 * person per entry" true by construction rather than by a de-duplication step at read time.
 * Consequence: an event's per-day slots are bookkeeping the admin never sees, so the service
 * refuses a slot that {@code belongsToEvent()} — the same boundary as the private note.
 *
 * <p><b>The payer is a user XOR a guest.</b> Guests count towards revenue (the owner's decision),
 * and a {@link GuestReservation} is already one row per booking — including for a multi-day event
 * — so it stands in for a payer without a further shape.
 *
 * <p>Writes go through the repository's upserts, so there is no {@code update()} here; the entity
 * exists for reads and for the schema.
 */
@Entity
@Table(name = "settlements")
public class Settlement {

    /** Mirrors {@code chk_settlements_amount_range}. Zero is allowed: "free of charge" is a decision. */
    public static final BigDecimal MIN_AMOUNT = BigDecimal.ZERO;
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("100000");

    /** Money, so two decimal places — the column is {@code NUMERIC(10,2)}. */
    public static final int AMOUNT_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id")
    @Nullable
    private TimeSlot timeSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    @Nullable
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @Nullable
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_reservation_id")
    @Nullable
    private GuestReservation guest;

    @Column(nullable = false, precision = 10, scale = AMOUNT_SCALE)
    private BigDecimal amount;

    /**
     * Third kind of target: the month a standing coaching fee is owed for. Not a foreign key — the
     * address is the pair (user, month), and the cascade on {@code user_id} already takes the row
     * away with the account. See V94.
     */
    @Column(name = "period_month")
    @Nullable
    private LocalDate periodMonth;

    /** {@code null} = not settled. A day label in Poland, not an instant — like {@code training_date}. */
    @Column(name = "settled_on")
    @Nullable
    private LocalDate settledOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Settlement() {}

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
     * Rounds to the column's scale so that a client sending {@code 149.999} cannot be stored as one
     * amount and read back as another, and mirrors {@code chk_settlements_amount_range} so the
     * caller gets a translated message instead of a constraint name.
     *
     * @throws IllegalArgumentException when the amount falls outside the allowed range
     */
    public static BigDecimal normalizeAmount(BigDecimal amount, String outOfRangeMessage) {
        BigDecimal scaled = amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        if (scaled.compareTo(MIN_AMOUNT) < 0 || scaled.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException(outOfRangeMessage);
        }
        return scaled;
    }

    // No accessors, deliberately, and it is not an oversight. This entity is never loaded: writes go
    // through the repository's upserts and every read goes through SettlementRow, so the class exists
    // for the schema mapping and to give JPQL a type to name. Getters here would be dead code that
    // reads like an invitation to fetch the entity and dereference its lazy associations one row at
    // a time — the shape AdminSettlementQueryCountTest exists to keep out.
}
