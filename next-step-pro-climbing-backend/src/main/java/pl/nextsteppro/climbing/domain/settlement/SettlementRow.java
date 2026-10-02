package pl.nextsteppro.climbing.domain.settlement;

import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.event.EventType;

import java.math.BigDecimal;
import java.time.Instant;
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
 * <p>A charge only — whether it is paid comes from {@link PaymentAllocator}, never from this row.
 *
 * @param targetDate when the session is or was: the slot's date, or the event's first day. This is
 *                   the axis outstanding debt is counted on, and the order payments are applied in.
 * @param createdAt  the tie-break between two charges on one day, so the same payment lands on the
 *                   same charge on every read.
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
    Instant createdAt
) {

    /** This row as the allocator sees it. */
    public PaymentAllocator.Charge toCharge() {
        return new PaymentAllocator.Charge(id, amount, targetDate, createdAt);
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
