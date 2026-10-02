package pl.nextsteppro.climbing.domain.settlement;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Works out which of one person's payments covered which of their charges.
 *
 * <p>⚠️ <b>This is derived on every read and never written back, and that is the whole design.</b>
 * Until V100 a settlement row held its own {@code paid_amount}, which made one column answer two
 * questions: "what did this person hand over" and "which session does that money count towards".
 * Paying off a backlog had to answer the second one by rewriting the first — a 200 handed over at
 * today's session came back as 140 on today's row and +40 on an old one — so the record of what
 * actually happened was gone the moment the split was made. Here a payment is a fact that never
 * changes, and the split is a pure function of the facts.
 *
 * <p>Oldest first on both sides: charges by session date (then creation, then id — two sessions on
 * one day are ordinary, and without a total order the same payment would land on a different
 * invoice from one read to the next), payments by the day they arrived. A charge of zero is a free
 * session and is paid by definition.
 *
 * <p>Consequence worth knowing: money cannot sit as credit while anything is owed. Credit exists
 * only once every charge is covered, so the old distinction between "balance" and "credit" (a
 * client could net to zero with fifty parked on an overpaid row) no longer exists.
 *
 * <p>Pure: no Spring, no clock, no repositories — like {@code WeightTrendCalculator}. An empty
 * allocation ({@link #EMPTY}) stands for a payer with no history.
 */
public final class PaymentAllocator {

    private PaymentAllocator() {}

    /** Something owed: one settlement row. */
    public record Charge(UUID id, BigDecimal amount, LocalDate date, Instant createdAt) {}

    /** Something handed over: one payment row. */
    public record Receipt(UUID id, BigDecimal amount, LocalDate receivedOn, Instant createdAt) {}

    /** A slice of one payment applied to one charge. */
    public record Share(UUID paymentId, UUID chargeId, BigDecimal amount) {}

    /**
     * Where one charge stands.
     *
     * @param paidOn the day of the payment that completed it, or {@code null} while something is
     *               still owed — and for a free session, which nobody paid for.
     */
    public record ChargeState(BigDecimal covered, BigDecimal remaining, @Nullable LocalDate paidOn) {

        public boolean isPaid() {
            return remaining.signum() == 0;
        }
    }

    /** The whole picture for one payer. */
    public record Allocation(
        Map<UUID, ChargeState> charges,
        Map<UUID, List<Share>> sharesByPayment,
        Map<UUID, BigDecimal> unallocatedByPayment,
        BigDecimal debt,
        BigDecimal credit
    ) {

        public ChargeState chargeState(UUID chargeId) {
            ChargeState state = charges.get(chargeId);
            if (state == null) {
                throw new IllegalArgumentException("Unknown charge " + chargeId);
            }
            return state;
        }

        public List<Share> sharesOf(UUID paymentId) {
            return sharesByPayment.getOrDefault(paymentId, List.of());
        }

        public BigDecimal unallocatedOf(UUID paymentId) {
            return unallocatedByPayment.getOrDefault(paymentId, BigDecimal.ZERO);
        }

        /** Net position: positive when we are holding their money. Exactly one of the two is non-zero. */
        public BigDecimal balance() {
            return credit.subtract(debt);
        }
    }

    private static final Comparator<Charge> CHARGE_ORDER = Comparator
        .comparing(Charge::date).thenComparing(Charge::createdAt).thenComparing(Charge::id);

    private static final Comparator<Receipt> RECEIPT_ORDER = Comparator
        .comparing(Receipt::receivedOn).thenComparing(Receipt::createdAt).thenComparing(Receipt::id);

    /** Nobody's ledger: nothing owed, nothing held. Declared after the comparators it runs through. */
    public static final Allocation EMPTY = allocate(List.of(), List.of());

    /**
     * Groups a mixed list of charges and payments by payer and allocates each group on its own —
     * the shape every screen needs, since one read fetches several people's ledgers at once.
     * Keyed by {@link SettlementRow#payerKey()}; somebody with payments and no charges still gets
     * an entry (all of it credit).
     */
    public static Map<String, Allocation> byPayer(Collection<SettlementRow> charges,
                                                  Collection<PaymentRow> payments) {
        Map<String, List<Charge>> owed = new HashMap<>();
        for (SettlementRow row : charges) {
            owed.computeIfAbsent(row.payerKey(), key -> new ArrayList<>()).add(row.toCharge());
        }
        Map<String, List<Receipt>> paid = new HashMap<>();
        for (PaymentRow row : payments) {
            paid.computeIfAbsent(row.payerKey(), key -> new ArrayList<>()).add(row.toReceipt());
        }
        Map<String, Allocation> result = new HashMap<>();
        for (String key : owed.keySet()) {
            result.put(key, allocate(owed.get(key), paid.getOrDefault(key, List.of())));
        }
        for (String key : paid.keySet()) {
            result.computeIfAbsent(key, k -> allocate(List.of(), paid.get(k)));
        }
        return result;
    }

    /** Allocates ONE payer's payments to ONE payer's charges. Mixing payers is the caller's bug. */
    public static Allocation allocate(Collection<Charge> charges, Collection<Receipt> receipts) {
        List<Charge> owed = charges.stream().sorted(CHARGE_ORDER).toList();
        List<Receipt> paid = receipts.stream().sorted(RECEIPT_ORDER).toList();

        Map<UUID, BigDecimal> covered = new HashMap<>();
        Map<UUID, LocalDate> paidOn = new HashMap<>();
        Map<UUID, List<Share>> shares = new HashMap<>();
        Map<UUID, BigDecimal> unallocated = new HashMap<>();

        BigDecimal[] open = owed.stream().map(Charge::amount).toArray(BigDecimal[]::new);
        int cursor = nextOpen(open, 0);

        for (Receipt receipt : paid) {
            BigDecimal left = receipt.amount();
            List<Share> slices = new ArrayList<>();
            while (left.signum() > 0 && cursor < open.length) {
                Charge charge = owed.get(cursor);
                BigDecimal applied = left.min(open[cursor]);
                slices.add(new Share(receipt.id(), charge.id(), Amounts.scale(applied)));
                covered.merge(charge.id(), applied, BigDecimal::add);
                left = left.subtract(applied);
                open[cursor] = open[cursor].subtract(applied);
                if (open[cursor].signum() == 0) {
                    paidOn.put(charge.id(), receipt.receivedOn());
                    cursor = nextOpen(open, cursor + 1);
                }
            }
            shares.put(receipt.id(), List.copyOf(slices));
            unallocated.put(receipt.id(), Amounts.scale(left));
        }

        Map<UUID, ChargeState> states = new HashMap<>();
        BigDecimal debt = BigDecimal.ZERO;
        for (Charge charge : owed) {
            BigDecimal got = covered.getOrDefault(charge.id(), BigDecimal.ZERO);
            BigDecimal remaining = charge.amount().subtract(got);
            debt = debt.add(remaining);
            states.put(charge.id(), new ChargeState(
                Amounts.scale(got), Amounts.scale(remaining), paidOn.get(charge.id())));
        }
        BigDecimal credit = unallocated.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Allocation(states, shares, unallocated, Amounts.scale(debt), Amounts.scale(credit));
    }

    /** First index from {@code from} that still owes something — free sessions consume nothing. */
    private static int nextOpen(BigDecimal[] open, int from) {
        int i = from;
        while (i < open.length && open[i].signum() == 0) {
            i++;
        }
        return i;
    }
}
