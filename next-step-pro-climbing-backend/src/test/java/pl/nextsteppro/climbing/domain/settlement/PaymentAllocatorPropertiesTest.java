package pl.nextsteppro.climbing.domain.settlement;

import org.junit.jupiter.api.Test;
import pl.nextsteppro.climbing.domain.settlement.PaymentAllocator.Allocation;
import pl.nextsteppro.climbing.domain.settlement.PaymentAllocator.Charge;
import pl.nextsteppro.climbing.domain.settlement.PaymentAllocator.ChargeState;
import pl.nextsteppro.climbing.domain.settlement.PaymentAllocator.Receipt;
import pl.nextsteppro.climbing.domain.settlement.PaymentAllocator.Share;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invariants of the allocator over thousands of generated ledgers, not a handful of examples.
 *
 * <p>Every money screen — the user card, the settlements tab, the client's own subscription card and
 * both exports — reads this one function, and nothing it computes is stored. So a wrong split is
 * not a bad row to correct but a wrong answer everywhere at once. The example tests pin the cases
 * someone thought of; these pin the rules no example can exhaust: money is neither created nor lost,
 * debt and credit never coexist, the oldest charge is always served first, and the answer does not
 * depend on the order rows came back from the database.
 *
 * <p>Seeded, so a failure reproduces: the seed is in the assertion message.
 */
class PaymentAllocatorPropertiesTest {

    private static final int TRIALS = 3_000;
    private static final LocalDate BASE = LocalDate.of(2026, 1, 1);

    @Test
    void shouldHoldEveryInvariantOnRandomLedgers() {
        Random seeds = new Random(20261018L);
        for (int trial = 0; trial < TRIALS; trial++) {
            long seed = seeds.nextLong();
            Random random = new Random(seed);
            List<Charge> charges = charges(random);
            List<Receipt> receipts = receipts(random);

            Allocation allocation = PaymentAllocator.allocate(charges, receipts);
            String where = "seed " + seed;

            BigDecimal owed = sum(charges.stream().map(Charge::amount).toList());
            BigDecimal paid = sum(receipts.stream().map(Receipt::amount).toList());
            BigDecimal covered = sum(allocation.charges().values().stream().map(ChargeState::covered).toList());

            // Money is neither created nor lost, on either side of the ledger.
            assertEquals(0, covered.add(allocation.credit()).compareTo(paid), where + ": paid = covered + credit");
            assertEquals(0, covered.add(allocation.debt()).compareTo(owed), where + ": owed = covered + debt");

            // Credit only once nothing is owed.
            assertFalse(allocation.debt().signum() > 0 && allocation.credit().signum() > 0,
                where + ": debt and credit at once");

            // Per payment: what it applied plus what is left of it is exactly what was handed over.
            for (Receipt receipt : receipts) {
                BigDecimal applied = sum(allocation.sharesOf(receipt.id()).stream().map(Share::amount).toList());
                assertEquals(0, applied.add(allocation.unallocatedOf(receipt.id())).compareTo(receipt.amount()),
                    where + ": payment " + receipt.amount() + " split into " + applied);
            }

            List<Charge> fifo = charges.stream()
                .sorted(Comparator.comparing(Charge::date).thenComparing(Charge::createdAt).thenComparing(Charge::id))
                .toList();
            boolean openSeen = false;
            for (Charge charge : fifo) {
                ChargeState state = allocation.chargeState(charge.id());
                assertTrue(state.covered().signum() >= 0 && state.covered().compareTo(charge.amount()) <= 0,
                    where + ": covered outside [0, amount]");
                assertEquals(0, state.covered().add(state.remaining()).compareTo(charge.amount()),
                    where + ": covered + remaining = amount");

                BigDecimal fromShares = sum(allocation.sharesByPayment().values().stream()
                    .flatMap(List::stream).filter(s -> s.chargeId().equals(charge.id())).map(Share::amount).toList());
                assertEquals(0, fromShares.compareTo(state.covered()), where + ": shares of a charge = covered");

                // Oldest first: nothing reaches a later charge while an earlier one is still open.
                if (openSeen) {
                    assertEquals(0, state.covered().signum(), where + ": a later charge was paid before an earlier one");
                }
                if (!state.isPaid()) openSeen = true;

                // A paid-for charge knows the day it was completed; an open one and a free one do not.
                boolean paidFor = state.isPaid() && charge.amount().signum() > 0;
                assertEquals(paidFor, state.paidOn() != null, where + ": paidOn iff paid and not free");
            }

            // The database returns rows in no promised order; the split must not care.
            List<Charge> shuffledCharges = new ArrayList<>(charges);
            List<Receipt> shuffledReceipts = new ArrayList<>(receipts);
            Collections.shuffle(shuffledCharges, random);
            Collections.shuffle(shuffledReceipts, random);
            Allocation again = PaymentAllocator.allocate(shuffledCharges, shuffledReceipts);
            assertEquals(allocation.charges(), again.charges(), where + ": depends on input order");
            assertEquals(allocation.unallocatedByPayment(), again.unallocatedByPayment(), where);
            assertEquals(0, allocation.debt().compareTo(again.debt()), where);
            assertEquals(0, allocation.credit().compareTo(again.credit()), where);
        }
    }

    private static List<Charge> charges(Random random) {
        int n = random.nextInt(8);
        List<Charge> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            // Free sessions, small and large amounts, and several charges on one day — the cases
            // where an ordering or a zero would trip.
            BigDecimal amount = random.nextInt(6) == 0 ? BigDecimal.ZERO : money(random, 1, 60_000);
            out.add(new Charge(UUID.randomUUID(), amount, BASE.plusDays(random.nextInt(5)),
                Instant.ofEpochSecond(1_700_000_000L + random.nextInt(3))));
        }
        return out;
    }

    private static List<Receipt> receipts(Random random) {
        int n = random.nextInt(6);
        List<Receipt> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new Receipt(UUID.randomUUID(), money(random, 1, 80_000), BASE.plusDays(random.nextInt(10)),
                Instant.ofEpochSecond(1_700_000_000L + random.nextInt(3))));
        }
        return out;
    }

    /** A whole number of grosze in [minGrosze, maxGrosze], as złote with two decimals. */
    private static BigDecimal money(Random random, int minGrosze, int maxGrosze) {
        return BigDecimal.valueOf(minGrosze + random.nextInt(maxGrosze - minGrosze + 1), 2);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
