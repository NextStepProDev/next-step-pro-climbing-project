package pl.nextsteppro.climbing.domain.settlement;

import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.event.EventType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One settlement flattened for reading: who owes what for which calendar entry, and when they paid.
 *
 * <p>Carries the target's date and title so that neither the modal section nor the Settlements tab
 * has to dereference a lazy association per row — the same reason
 * {@code ReservationStatsRow} and {@code UserBookingAggregate} exist. The whole tab is built from
 * these in one pass in Java, so the query count stays constant in the number of settlements.
 *
 * <p>Exactly one of {@code slotId} / {@code eventId} is set, and exactly one of {@code userId} /
 * {@code guestId} — the CHECKs in V92 guarantee it, so a reader may branch on {@code eventId != null}
 * without a third case.
 *
 * @param targetDate when the session is or was: the slot's date, or the event's first day. This is
 *                   the axis outstanding debt is counted on, because unpaid rows have no
 *                   {@code settledOn} to count on.
 * @param settledOn  when the money arrived, or {@code null} while it has not. Revenue is counted on
 *                   this axis.
 */
public record SettlementRow(
    UUID id,
    @Nullable UUID slotId,
    @Nullable UUID eventId,
    @Nullable LocalDate periodMonth,
    @Nullable UUID userId,
    @Nullable String firstName,
    @Nullable String lastName,
    @Nullable UUID guestId,
    @Nullable String guestNote,
    LocalDate targetDate,
    @Nullable String targetTitle,
    @Nullable EventType eventType,
    BigDecimal amount,
    BigDecimal paidAmount,
    @Nullable LocalDate settledOn
) {

    /** What is still owed on this row — never negative, because an overpayment is not a debt. */
    public BigDecimal remaining() {
        return amount.subtract(paidAmount).max(BigDecimal.ZERO);
    }

    /** Signed contribution to the payer's balance: positive when they gave more than they owed. */
    public BigDecimal balanceDelta() {
        return paidAmount.subtract(amount);
    }

    public boolean isFullyPaid() {
        return paidAmount.compareTo(amount) >= 0;
    }

    /**
     * True when this is a standing coaching fee rather than a session. Such a row has no calendar
     * entry behind it, so nothing may try to link into one.
     */
    public boolean isMonthlyFee() {
        return periodMonth != null;
    }

    /** True when the payer is a guest with no account — no user card to link to. */
    public boolean isGuest() {
        return guestId != null;
    }

    /**
     * A stable key for grouping the "by person" ranking. Guests have no account, so their own row id
     * is the key: two guests with the same written name are two payers, and merging them would
     * invent a client.
     */
    public String payerKey() {
        return userId != null ? "u:" + userId : "g:" + guestId;
    }
}
