package pl.nextsteppro.climbing.domain.settlement;

import org.junit.jupiter.api.Test;
import pl.nextsteppro.climbing.domain.settlement.PaymentAllocator.Allocation;
import pl.nextsteppro.climbing.domain.settlement.PaymentAllocator.Charge;
import pl.nextsteppro.climbing.domain.settlement.PaymentAllocator.Receipt;
import pl.nextsteppro.climbing.domain.settlement.PaymentAllocator.Share;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The split of payments over charges. Pure and clock-free, so every date here is explicit.
 *
 * <p>The first case is the one that motivated the ledger: a client with a small backlog hands over
 * a note bigger than today's price. Before V100 that rewrote the recorded payment; here the payment
 * is an input that cannot change, and the test asserts how it is read.
 */
class PaymentAllocatorTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static Charge charge(String amount, LocalDate date) {
        return new Charge(UUID.randomUUID(), new BigDecimal(amount), date, T0);
    }

    private static Receipt receipt(String amount, LocalDate on) {
        return new Receipt(UUID.randomUUID(), new BigDecimal(amount), on, T0);
    }

    private static BigDecimal pln(String amount) {
        return new BigDecimal(amount).setScale(2);
    }

    @Test
    void shouldPayTheBacklogAndKeepTheRestAsCreditWithoutTouchingThePaymentItself() {
        // Given: 20 still owed from 25.09 (120 charged, 100 paid then), 140 today, 200 handed over
        LocalDate old = LocalDate.of(2026, 9, 25);
        LocalDate today = LocalDate.of(2026, 10, 2);
        Charge earlier = charge("120", old);
        Charge todays = charge("140", today);
        Receipt firstCash = receipt("100", old);
        Receipt note = receipt("200", today);

        // When
        Allocation result = PaymentAllocator.allocate(List.of(todays, earlier), List.of(note, firstCash));

        // Then: both sessions are paid, 40 waits as credit, and the 200 splits 20 / 140 / 40
        assertTrue(result.chargeState(earlier.id()).isPaid());
        assertTrue(result.chargeState(todays.id()).isPaid());
        assertEquals(today, result.chargeState(earlier.id()).paidOn());
        assertEquals(today, result.chargeState(todays.id()).paidOn());
        assertEquals(pln("40"), result.credit());
        assertEquals(pln("0"), result.debt());
        assertEquals(pln("40"), result.balance());

        List<Share> split = result.sharesOf(note.id());
        assertEquals(2, split.size());
        assertEquals(new Share(note.id(), earlier.id(), pln("20")), split.get(0));
        assertEquals(new Share(note.id(), todays.id(), pln("140")), split.get(1));
        assertEquals(pln("40"), result.unallocatedOf(note.id()));
    }

    @Test
    void shouldLeaveTheRemainderOwedWhenThePaymentFallsShort() {
        // Given
        LocalDate day = LocalDate.of(2026, 3, 1);
        Charge first = charge("150", day);
        Charge second = charge("150", day.plusDays(7));
        Receipt part = receipt("200", day.plusDays(7));

        // When
        Allocation result = PaymentAllocator.allocate(List.of(first, second), List.of(part));

        // Then
        assertTrue(result.chargeState(first.id()).isPaid());
        assertEquals(pln("50"), result.chargeState(second.id()).covered());
        assertEquals(pln("100"), result.chargeState(second.id()).remaining());
        assertNull(result.chargeState(second.id()).paidOn());
        assertEquals(pln("100"), result.debt());
        assertEquals(pln("0"), result.credit());
    }

    @Test
    void shouldSpendAPrepaymentOnASessionChargedLater() {
        // Given: money arrived before the session was even priced
        Receipt prepaid = receipt("300", LocalDate.of(2026, 5, 1));
        Charge course = charge("300", LocalDate.of(2026, 5, 20));

        // When
        Allocation result = PaymentAllocator.allocate(List.of(course), List.of(prepaid));

        // Then
        assertTrue(result.chargeState(course.id()).isPaid());
        assertEquals(LocalDate.of(2026, 5, 1), result.chargeState(course.id()).paidOn());
    }

    @Test
    void shouldTreatAFreeSessionAsPaidAndNotConsumeAnything() {
        // Given
        LocalDate day = LocalDate.of(2026, 4, 1);
        Charge free = charge("0", day);
        Charge paid = charge("100", day.plusDays(1));
        Receipt cash = receipt("100", day.plusDays(1));

        // When
        Allocation result = PaymentAllocator.allocate(List.of(free, paid), List.of(cash));

        // Then
        assertTrue(result.chargeState(free.id()).isPaid());
        assertNull(result.chargeState(free.id()).paidOn());
        assertTrue(result.chargeState(paid.id()).isPaid());
        assertEquals(List.of(new Share(cash.id(), paid.id(), pln("100"))), result.sharesOf(cash.id()));
    }

    @Test
    void shouldOrderTwoSessionsOnOneDayByCreationThenIdSoTheSplitIsStable() {
        // Given: a morning and an evening slot on the same day, half the money for both
        LocalDate day = LocalDate.of(2026, 6, 1);
        Charge morning = new Charge(UUID.randomUUID(), new BigDecimal("100"), day, T0);
        Charge evening = new Charge(UUID.randomUUID(), new BigDecimal("100"), day, T0.plusSeconds(60));
        Receipt cash = receipt("100", day);

        // When: input order reversed on purpose
        Allocation result = PaymentAllocator.allocate(List.of(evening, morning), List.of(cash));

        // Then
        assertTrue(result.chargeState(morning.id()).isPaid());
        assertFalse(result.chargeState(evening.id()).isPaid());
    }

    @Test
    void shouldFreeTheMoneyAsCreditWhenTheChargeItCoveredIsGone() {
        // Given: the only charge was removed, the payment stays
        Receipt cash = receipt("140", LocalDate.of(2026, 10, 2));

        // When
        Allocation result = PaymentAllocator.allocate(List.of(), List.of(cash));

        // Then
        assertEquals(pln("140"), result.credit());
        assertEquals(pln("140"), result.unallocatedOf(cash.id()));
        assertTrue(result.sharesOf(cash.id()).isEmpty());
    }

    @Test
    void shouldReportTheWholeChargeOwedWhenNothingWasPaid() {
        // Given
        Charge unpaid = charge("80", LocalDate.of(2026, 7, 1));

        // When
        Allocation result = PaymentAllocator.allocate(List.of(unpaid), List.of());

        // Then
        assertEquals(pln("0"), result.chargeState(unpaid.id()).covered());
        assertEquals(pln("80"), result.debt());
        assertEquals(pln("-80"), result.balance());
    }
}
