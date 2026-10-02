package pl.nextsteppro.climbing.domain.settlement;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One payment flattened for reading, with the payer's name so the export and the lists need no
 * second lookup. Exactly one of {@code userId} / {@code guestId} is set (CHECK in V100).
 *
 * @param enteredSlotId  the session it was typed in at, if any — context for the modal, not a target
 * @param enteredEventId the same, for an event
 * @param enteredTitle   that session's title, so a list can name where the money was taken
 * @param enteredDate    that session's day (an event's first)
 */
public record PaymentRow(
    UUID id,
    @Nullable UUID userId,
    @Nullable String firstName,
    @Nullable String lastName,
    @Nullable UUID guestId,
    @Nullable String guestNote,
    BigDecimal amount,
    LocalDate receivedOn,
    @Nullable UUID enteredSlotId,
    @Nullable UUID enteredEventId,
    @Nullable String enteredTitle,
    @Nullable LocalDate enteredDate,
    Instant createdAt
) {

    /** Who paid, as the screens print it. A guest has only the name that was written down. */
    public String payerName() {
        if (guestId != null) {
            return guestNote == null ? "" : guestNote;
        }
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }

    /** This row as the allocator sees it. */
    public PaymentAllocator.Receipt toReceipt() {
        return new PaymentAllocator.Receipt(id, amount, receivedOn, createdAt);
    }

    /** Same key as {@link SettlementRow#payerKey()}, so charges and payments group together. */
    public String payerKey() {
        return userId != null ? "u:" + userId : "g:" + guestId;
    }

    public boolean isGuest() {
        return guestId != null;
    }
}
