package pl.nextsteppro.climbing.domain.settlement;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.reservation.GuestReservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Money one person handed over, on one day. A fact, not a figure to keep in step.
 *
 * <p>⚠️ <b>Never edited.</b> A correction is a delete and a new entry, the same rule as a bulk
 * {@link Payout}. The point of this table (V100) is that "she paid 200 on the 2nd" stays true
 * however the money is later split across her sessions; an update path would bring back exactly
 * the rewrite this replaced.
 *
 * <p><b>Hangs on the payer, not on a session.</b> Money is shared across everything a person owes,
 * and {@link PaymentAllocator} works out what it covers. {@code enteredSlot}/{@code enteredEvent}
 * only record where it was typed in, so the modal can show it there; deleting that session must
 * not take the money with it, which is why those foreign keys are {@code ON DELETE SET NULL}.
 *
 * <p>No accessors, for the reason spelled out on {@link Settlement}: writes are native inserts and
 * every read goes through {@link PaymentRow}.
 */
@Entity
@Table(name = "payments")
public class Payment {

    /** Mirrors {@code chk_payments_amount_range}: zero is not a payment, "free" is a charge of 0. */
    public static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");
    public static final BigDecimal MAX_AMOUNT = Settlement.MAX_AMOUNT;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @Nullable
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_reservation_id")
    @Nullable
    private GuestReservation guest;

    @Column(nullable = false, precision = 10, scale = Amounts.SCALE)
    private BigDecimal amount;

    /** A day label in Poland, not an instant. The revenue axis. */
    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entered_slot_id")
    @Nullable
    private TimeSlot enteredSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entered_event_id")
    @Nullable
    private Event enteredEvent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {}

    /**
     * Rounded to the column's scale and checked against {@code chk_payments_amount_range}, so the
     * caller gets a translated message instead of a constraint name.
     *
     * @throws IllegalArgumentException when the amount falls outside the allowed range
     */
    public static BigDecimal normalizeAmount(BigDecimal amount, String outOfRangeMessage) {
        return Amounts.normalize(amount, MIN_AMOUNT, MAX_AMOUNT, outOfRangeMessage);
    }
}
