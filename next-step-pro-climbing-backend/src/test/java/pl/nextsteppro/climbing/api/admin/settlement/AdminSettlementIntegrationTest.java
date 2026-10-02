package pl.nextsteppro.climbing.api.admin.settlement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reservation.GuestReservation;
import pl.nextsteppro.climbing.domain.reservation.GuestReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settlement feature end to end: pricing a session per participant, recording the money people
 * hand over, and the rules that keep those figures honest.
 *
 * <p>Two tests carry the decisions the tables were shaped for:
 * {@code shouldPriceAMultiDayEventOncePerPersonRatherThanOncePerDay} (why the target is never a
 * reservation) and {@code shouldKeepWhatWasHandedOverExactlyAsTypedWhileItPaysOffTheBacklog} (why
 * a payment is a row of its own since V100).
 */
class AdminSettlementIntegrationTest extends BaseIntegrationTest {

    @Autowired private AdminSettlementService service;
    @Autowired private AdminSettlementStatsService statsService;
    @Autowired private GuestReservationRepository guestReservationRepository;
    @Autowired private JdbcTemplate jdbc;

    private User client;
    private TimeSlot slot;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM settlements");
        guestReservationRepository.deleteAll();
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        eventRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        client = saveUser("client@example.com", "Anna", "Kowalska");
        date = LocalDate.now().plusDays(3);
        slot = timeSlotRepository.saveAndFlush(
            new TimeSlot(date, LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        reservationRepository.saveAndFlush(new Reservation(client, slot));
    }

    // --------------------------------------------------------------- the basics

    @Test
    @DisplayName("shouldListEveryBookedParticipantEvenBeforeAnythingIsPriced")
    void shouldListEveryBookedParticipantEvenBeforeAnythingIsPriced() {
        SettlementSectionDto section = service.getSection("slot", slot.getId());

        assertEquals(date, section.targetDate(),
            "The payment date prefills from the session, so the section has to carry it");
        assertEquals(1, section.lines().size());
        SettlementLineDto line = section.lines().getFirst();
        assertEquals("Anna Kowalska", line.name());
        assertNull(line.amount(), "Not priced yet is a different state from priced at zero");
        assertFalse(line.orphaned());
        assertTrue(line.payments().isEmpty());
    }

    @Test
    @DisplayName("shouldUpsertAnAmountAndThenCorrectIt")
    void shouldUpsertAnAmountAndThenCorrectIt() {
        charge(slot, client, "150");
        assertMoney("150", amountOf(service.getSection("slot", slot.getId())));

        charge(slot, client, "180");
        assertMoney("180", amountOf(service.getSection("slot", slot.getId())));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM settlements", Integer.class),
            "A correction must overwrite the row, not add a second one");
    }

    @Test
    @DisplayName("shouldNeverTouchAPaymentWhenTheChargeIsCorrected")
    void shouldNeverTouchAPaymentWhenTheChargeIsCorrected() {
        // Until V100 the price and the money were one PUT, so correcting the price rewrote what was
        // received. Now they are two facts: the payment survives every correction of the charge.
        charge(slot, client, "150");
        pay(client, "150", date, slot);

        charge(slot, client, "180");

        SettlementLineDto line = lineFor(slot, client);
        assertMoney("150", line.payments().getFirst().amount());
        assertMoney("150", line.covered());
        assertMoney("30", line.remaining(), "The new price is short by the difference, nothing more");
        assertNull(line.paidOn());
    }

    @Test
    @DisplayName("shouldAllowZeroAsADeliberateFreeOfChargeAndCountItAsPaid")
    void shouldAllowZeroAsADeliberateFreeOfChargeAndCountItAsPaid() {
        charge(slot, client, "0");

        SettlementLineDto line = lineFor(slot, client);
        assertEquals(0, BigDecimal.ZERO.compareTo(line.amount()),
            "Zero is a decision — the state that means 'not priced' is the absence of a row");
        assertMoney("0", line.remaining(), "Nothing is owed for a free session");
        assertEquals(0, stats().outstanding().count());
    }

    @Test
    @DisplayName("shouldRemoveAnAmountIdempotently")
    void shouldRemoveAnAmountIdempotently() {
        charge(slot, client, "150");
        service.delete("slot", slot.getId(), "user", client.getId());
        service.delete("slot", slot.getId(), "user", client.getId());

        assertNull(lineFor(slot, client).amount());
    }

    @Test
    @DisplayName("shouldTurnThePaymentIntoCreditRatherThanLoseItWhenTheChargeIsRemoved")
    void shouldTurnThePaymentIntoCreditRatherThanLoseItWhenTheChargeIsRemoved() {
        charge(slot, client, "150");
        pay(client, "150", date, slot);

        service.delete("slot", slot.getId(), "user", client.getId());

        SettlementLineDto line = lineFor(slot, client);
        assertMoney("150", line.accountCredit(),
            "The money was handed over; removing a price cannot make it un-happen");
        assertEquals(1, line.payments().size(), "And it still shows where it was typed in");
    }

    @Test
    @DisplayName("shouldSuggestWhatThisPersonWasLastChargedButNotApplyIt")
    void shouldSuggestWhatThisPersonWasLastChargedButNotApplyIt() {
        charge(slot, client, "150");

        TimeSlot next = bookedSlot(date.plusDays(7));

        SettlementLineDto line = lineFor(next, client);
        assertNull(line.amount(), "A suggestion is an offer, never a saved amount");
        assertMoney("150", line.suggestedAmount());
    }

    // ------------------------------------------------------------ the whole point

    @Test
    @DisplayName("shouldPriceAMultiDayEventOncePerPersonRatherThanOncePerDay")
    void shouldPriceAMultiDayEventOncePerPersonRatherThanOncePerDay() {
        Event event = eventRepository.saveAndFlush(
            new Event("Kurs skalny", EventType.COURSE, date, date.plusDays(2), 8));
        // Booking an event writes ONE reservation per day — the exact reason a settlement hangs on
        // the event and not on a reservation.
        for (int day = 0; day <= 2; day++) {
            TimeSlot eventSlot = timeSlotRepository.saveAndFlush(
                new TimeSlot(event, date.plusDays(day), LocalTime.of(9, 0), LocalTime.of(17, 0), 8));
            reservationRepository.saveAndFlush(new Reservation(client, eventSlot));
        }

        SettlementSectionDto section = service.getSection("event", event.getId());
        assertEquals(1, section.lines().size(),
            "Three booking rows for one person must collapse into one line, or a 600 zl course "
                + "shows three fields and counts as 1800 zl of revenue");
        assertEquals(date, section.targetDate(), "The event's first day is the payment-date prefill");

        save("event", event.getId(), "user", client.getId(), "600");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM settlements", Integer.class));
        assertMoney("600", amountOf(service.getSection("event", event.getId())));
    }

    @Test
    @DisplayName("shouldKeepWhatWasHandedOverExactlyAsTypedWhileItPaysOffTheBacklog")
    void shouldKeepWhatWasHandedOverExactlyAsTypedWhileItPaysOffTheBacklog() {
        // The owner's report (2026-10-02), in its own numbers: 20 still owed from an earlier session,
        // 140 today, 200 handed over. The old pool rewrote today's "received" to 140 and parked +40
        // on the OLD row with today's date — the balance was right and both rows lied, and
        // "correcting" 140 back to 200 counted the same notes twice.
        TimeSlot earlier = bookedSlot(date.minusDays(7));
        charge(earlier, client, "120");
        pay(client, "100", earlier.getDate(), earlier);
        charge(slot, client, "140");

        PaymentResultDto result = pay(client, "200", date, slot);

        assertMoney("0", result.debt());
        assertMoney("40", result.credit());

        SettlementLineDto today = lineFor(slot, client);
        assertEquals(1, today.payments().size());
        LinePaymentDto payment = today.payments().getFirst();
        assertMoney("200", payment.amount(), "⚠️ The record of what she handed over, unchanged");
        assertTrue(payment.enteredHere());
        assertEquals(date, payment.receivedOn());
        assertEquals(2, payment.shares().size(), "20 to the old session, 140 to this one");
        assertMoney("20", payment.shares().get(0).amount());
        assertFalse(payment.shares().get(0).thisEntry());
        assertMoney("140", payment.shares().get(1).amount());
        assertTrue(payment.shares().get(1).thisEntry());
        assertMoney("40", payment.unallocated(), "And 40 waits as credit");
        assertMoney("0", today.remaining());
        assertEquals(date, today.paidOn());

        SettlementLineDto old = lineFor(earlier, client);
        assertMoney("120", old.covered(), "The old session is paid — by its own 100 and 20 of today's");
        assertEquals(date, old.paidOn(), "Completed on the day the 200 arrived");
        assertEquals(2, old.payments().size(),
            "Its own payment and today's, which covers part of it — both listed where they matter");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE amount = 200", Integer.class));

        assertMoney("300", stats().revenue().total(), "100 + 200 arrived, and that is what was earned");
    }

    @Test
    @DisplayName("shouldSpendCreditOnTheNextSessionWithNothingChangingHands")
    void shouldSpendCreditOnTheNextSessionWithNothingChangingHands() {
        // A hundred handed over for a fifty session; the change covers the next one by itself.
        charge(slot, client, "50");
        pay(client, "100", date, slot);

        TimeSlot next = bookedSlot(date.plusMonths(2));
        charge(next, client, "50");

        SettlementLineDto line = lineFor(next, client);
        assertMoney("0", line.remaining(), "Covered without anybody pressing a button");
        assertEquals(date, line.paidOn(), "By the payment that actually paid for it");
        assertEquals(0, stats().outstanding().count());
        assertMoney("100", stats().revenue().total(),
            "Spending a credit moves nothing between months: a hundred arrived, a hundred is counted");
    }

    @Test
    @DisplayName("shouldKeepChasingTheRemainderWhenSomebodyPaysTooLittle")
    void shouldKeepChasingTheRemainderWhenSomebodyPaysTooLittle() {
        charge(slot, client, "150");

        PaymentResultDto result = pay(client, "100", date, slot);

        assertMoney("50", result.debt());
        assertEquals(1, stats().outstanding().count());
        assertMoney("50", stats().outstanding().total(), "Fifty still owed, not the whole hundred and fifty");
    }

    @Test
    @DisplayName("shouldPayOffTheOldestDebtFirstWhenTheMoneyDoesNotCoverEverything")
    void shouldPayOffTheOldestDebtFirstWhenTheMoneyDoesNotCoverEverything() {
        // ⚠️ The order is not cosmetic. A backlog is paid off the way it accumulated, and the client
        // asking "so which sessions am I straight for?" has to get the same answer the screen gives.
        TimeSlot later = bookedSlot(date.plusDays(7));
        charge(slot, client, "150");
        charge(later, client, "80");

        // Typed in at the NEWER session — still pays the older one first.
        pay(client, "150", date.plusDays(20), later);

        assertMoney("0", lineFor(slot, client).remaining(), "The older session is the one that closes");
        assertMoney("80", lineFor(later, client).remaining(), "And the newer one is still open");
        assertMoney("80", stats().outstanding().total());
    }

    @Test
    @DisplayName("shouldSplitAPartPaymentAcrossDebtsInsteadOfPickingOne")
    void shouldSplitAPartPaymentAcrossDebtsInsteadOfPickingOne() {
        TimeSlot later = bookedSlot(date.plusDays(7));
        charge(slot, client, "150");
        charge(later, client, "80");

        PaymentResultDto result = payWithoutTarget(client, "200", date.plusDays(20));

        assertMoney("30", result.debt());
        assertMoney("30", stats().outstanding().total(),
            "Thirty of the second session is still owed — not eighty, and not nothing");
        assertEquals(1, stats().outstanding().count(), "And only the part-paid row is still listed");
    }

    @Test
    @DisplayName("shouldSettleAGuestsOwnDebtsOnly")
    void shouldSettleAGuestsOwnDebtsOnly() {
        GuestReservation guest = guestReservationRepository.saveAndFlush(
            new GuestReservation(slot, "Marek — kolega Ani", 1));
        save("slot", slot.getId(), "guest", guest.getId(), "150");
        charge(slot, client, "150");

        service.addPayment(new AddPaymentRequest("guest", guest.getId(), new BigDecimal("150"), date,
            "slot", slot.getId()));

        assertMoney("150", lineFor(slot, client).remaining(),
            "Somebody else's debt on the same session must not be paid by it");
        SettlementLineDto guestLine = service.getSection("slot", slot.getId()).lines().stream()
            .filter(line -> line.payerId().equals(guest.getId())).findFirst().orElseThrow();
        assertMoney("0", guestLine.remaining());
    }

    @Test
    @DisplayName("shouldUndoAPaymentByDeletingItAndPutTheDebtBack")
    void shouldUndoAPaymentByDeletingItAndPutTheDebtBack() {
        charge(slot, client, "150");
        pay(client, "150", date, slot);
        UUID paymentId = lineFor(slot, client).payments().getFirst().id();

        service.deletePayment(paymentId);
        service.deletePayment(paymentId);

        assertMoney("150", lineFor(slot, client).remaining(), "Idempotent, and the debt is back");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM payments", Integer.class));
    }

    @Test
    @DisplayName("shouldAcceptAPrepaymentAndHoldItAsCredit")
    void shouldAcceptAPrepaymentAndHoldItAsCredit() {
        // Before V100 a payment from somebody who owed nothing was refused — a row carried one date,
        // so piling money onto it rewrote when the older money arrived. A payment is its own row now,
        // so a prepayment is simply credit that covers the next charge.
        charge(slot, client, "0");

        PaymentResultDto result = pay(client, "300", date, slot);

        assertMoney("300", result.credit());
        assertEquals(1, stats().credits().payers());
    }

    @Test
    @DisplayName("shouldRefuseAPaymentFromSomebodyThisLedgerDoesNotKnow")
    void shouldRefuseAPaymentFromSomebodyThisLedgerDoesNotKnow() {
        User stranger = saveUser("stranger@example.com", "Nikt", "Obcy");

        assertThrows(IllegalArgumentException.class,
            () -> payWithoutTarget(stranger, "100", date), "No charge anywhere: a phantom client");
        assertThrows(IllegalArgumentException.class,
            () -> pay(stranger, "100", date, slot), "Not booked on the session it names");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM payments", Integer.class));
    }

    @Test
    @DisplayName("shouldRejectAPaymentOutsideTheAllowedRange")
    void shouldRejectAPaymentOutsideTheAllowedRange() {
        charge(slot, client, "150");

        assertThrows(IllegalArgumentException.class, () -> pay(client, "0", date, slot),
            "Zero is not a payment — a free session is a charge of zero");
        assertThrows(IllegalArgumentException.class, () -> pay(client, "100000.01", date, slot));
        assertThrows(IllegalArgumentException.class, () -> service.addPayment(new AddPaymentRequest(
            "sponsor", client.getId(), new BigDecimal("10"), date, null, null)));
    }

    // ------------------------------------------------------------------- guests

    @Test
    @DisplayName("shouldRefuseToPriceASingleDayOfAnEvent")
    void shouldRefuseToPriceASingleDayOfAnEvent() {
        Event event = eventRepository.saveAndFlush(
            new Event("Kurs skalny", EventType.COURSE, date, date.plusDays(1), 8));
        TimeSlot eventSlot = timeSlotRepository.saveAndFlush(
            new TimeSlot(event, date, LocalTime.of(9, 0), LocalTime.of(17, 0), 8));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> save("slot", eventSlot.getId(), "user", client.getId(), "300"));
        assertTrue(ex.getMessage().toLowerCase().contains("wydarzen")
                || ex.getMessage().toLowerCase().contains("event"),
            "The refusal must name the reason, not a constraint: " + ex.getMessage());

        assertThrows(IllegalArgumentException.class,
            () -> service.getSection("slot", eventSlot.getId()));
    }

    @Test
    @DisplayName("shouldChargeAGuestAttachedToTheEventAndOneAttachedToOneOfItsDays")
    void shouldChargeAGuestAttachedToTheEventAndOneAttachedToOneOfItsDays() {
        Event event = eventRepository.saveAndFlush(
            new Event("Wyjazd", EventType.WORKSHOP, date, date.plusDays(1), 8));
        TimeSlot eventSlot = timeSlotRepository.saveAndFlush(
            new TimeSlot(event, date, LocalTime.of(9, 0), LocalTime.of(17, 0), 8));

        GuestReservation onEvent = guestReservationRepository.saveAndFlush(
            new GuestReservation(event, "Ekipa z Krakowa", 3));
        GuestReservation onDay = guestReservationRepository.saveAndFlush(
            new GuestReservation(eventSlot, "Marek dopisany z widoku dnia", 1));

        List<SettlementLineDto> lines = service.getSection("event", event.getId()).lines();
        assertEquals(2, lines.size(),
            "A guest written onto one day of an event must still have a way to be charged");
        assertTrue(lines.stream().allMatch(line -> "guest".equals(line.payerType())));
        assertTrue(lines.stream().anyMatch(line -> line.participants() == 3),
            "The headcount is shown because the amount prices the whole booking, not a head");

        save("event", event.getId(), "guest", onEvent.getId(), "1800");
        save("event", event.getId(), "guest", onDay.getId(), "600");

        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM settlements", Integer.class));
        // ⚠️ BOTH hang on the EVENT, including the guest whose own row points at a day slot. The
        // settlement takes the target the caller addressed, not the one on the guest row: writing
        // it onto a per-day slot puts the amount at an address no read of the event ever visits.
        assertEquals(2, jdbc.queryForObject(
            "SELECT COUNT(*) FROM settlements WHERE event_id = ?", Integer.class, event.getId()));
        assertEquals(0, jdbc.queryForObject(
            "SELECT COUNT(*) FROM settlements WHERE time_slot_id = ?", Integer.class, eventSlot.getId()));
    }

    @Test
    @DisplayName("shouldReadBackAnAmountWrittenForAGuestAttachedToOneDayOfAnEvent")
    void shouldReadBackAnAmountWrittenForAGuestAttachedToOneDayOfAnEvent() {
        Event event = eventRepository.saveAndFlush(
            new Event("Wyjazd", EventType.WORKSHOP, date, date.plusDays(1), 8));
        TimeSlot eventSlot = timeSlotRepository.saveAndFlush(
            new TimeSlot(event, date, LocalTime.of(9, 0), LocalTime.of(17, 0), 8));
        GuestReservation onDay = guestReservationRepository.saveAndFlush(
            new GuestReservation(eventSlot, "Marek dopisany z widoku dnia", 1));

        save("event", event.getId(), "guest", onDay.getId(), "600");
        service.addPayment(new AddPaymentRequest("guest", onDay.getId(), new BigDecimal("600"), date,
            "event", event.getId()));

        // Writing to an address the read cannot reach is worse than refusing the write: the admin
        // sees the amount accepted, comes back, and finds the line blank again.
        SettlementLineDto line = service.getSection("event", event.getId()).lines().stream()
            .filter(candidate -> candidate.payerId().equals(onDay.getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("the guest vanished from the section"));
        assertMoney("600", line.amount());
        assertEquals(date, line.paidOn());
        assertTrue(line.payments().getFirst().enteredHere());
    }

    @Test
    @DisplayName("shouldRefuseAGuestFromAnotherSession")
    void shouldRefuseAGuestFromAnotherSession() {
        TimeSlot other = timeSlotRepository.saveAndFlush(
            new TimeSlot(date.plusDays(1), LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        GuestReservation guest = guestReservationRepository.saveAndFlush(
            new GuestReservation(other, "Ktos zupelnie inny", 1));

        assertThrows(IllegalArgumentException.class,
            () -> save("slot", slot.getId(), "guest", guest.getId(), "150"));
    }

    // ---------------------------------------------- guards reject change, not state

    @Test
    @DisplayName("shouldRefuseANewAmountForSomebodyWhoIsNotBookedOnTheSession")
    void shouldRefuseANewAmountForSomebodyWhoIsNotBookedOnTheSession() {
        User stranger = saveUser("stranger@example.com", "Nikt", "Obcy");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> charge(slot, stranger, "150"));
        assertTrue(ex.getMessage().length() > 0);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM settlements", Integer.class));
    }

    @Test
    @DisplayName("shouldKeepAnExistingAmountEditableAfterTheBookingIsCancelled")
    void shouldKeepAnExistingAmountEditableAfterTheBookingIsCancelled() {
        charge(slot, client, "150");
        cancelBooking();

        // The guard rejects the CHANGE (a brand-new amount for a stranger), never the STATE. Money
        // that changed hands must stay correctable after somebody cancels.
        charge(slot, client, "120");
        assertMoney("120", amountOf(service.getSection("slot", slot.getId())));
        pay(client, "120", date, slot);

        service.delete("slot", slot.getId(), "user", client.getId());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM settlements", Integer.class));
    }

    @Test
    @DisplayName("shouldStillShowAPaidClientWhoLaterCancelledFlaggedAsOrphaned")
    void shouldStillShowAPaidClientWhoLaterCancelledFlaggedAsOrphaned() {
        charge(slot, client, "150");
        pay(client, "150", date, slot);
        cancelBooking();

        List<SettlementLineDto> lines = service.getSection("slot", slot.getId()).lines();
        assertEquals(1, lines.size(),
            "Dropping the row would make the money vanish from the screen while it still counts "
                + "in the monthly total");
        assertTrue(lines.getFirst().orphaned());
        assertMoney("150", lines.getFirst().amount());
        assertEquals(1, lines.getFirst().payments().size());
    }

    @Test
    @DisplayName("shouldRejectUnknownTargetAndPayerSegments")
    void shouldRejectUnknownTargetAndPayerSegments() {
        assertThrows(IllegalArgumentException.class,
            () -> service.getSection("reservation", slot.getId()));
        assertThrows(IllegalArgumentException.class,
            () -> save("slot", slot.getId(), "sponsor", client.getId(), "150"));
        assertThrows(IllegalArgumentException.class,
            () -> service.getSection("slot", UUID.randomUUID()));
    }

    @Test
    @DisplayName("shouldRejectAnAmountOutsideTheAllowedRange")
    void shouldRejectAnAmountOutsideTheAllowedRange() {
        assertThrows(IllegalArgumentException.class, () -> charge(slot, client, "-1"));
        assertThrows(IllegalArgumentException.class, () -> charge(slot, client, "100000.01"));
    }

    // ------------------------------------------------------------ database rules

    @Test
    @DisplayName("shouldDropTheChargeButKeepTheMoneyWhenItsSessionGoesAway")
    void shouldDropTheChargeButKeepTheMoneyWhenItsSessionGoesAway() {
        charge(slot, client, "150");
        pay(client, "150", date, slot);

        jdbc.update("DELETE FROM reservations WHERE time_slot_id = ?", slot.getId());
        jdbc.update("DELETE FROM time_slots WHERE id = ?", slot.getId());

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM settlements", Integer.class),
            "The foreign keys are the mechanism: an amount must not outlive the session it prices");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE entered_slot_id IS NULL", Integer.class),
            "⚠️ But the money stays: it was handed over by a person, and the session was only where "
                + "it was typed in (SET NULL, not CASCADE)");
    }

    @Test
    @DisplayName("shouldDropThePaymentsWithTheirPayer")
    void shouldDropThePaymentsWithTheirPayer() {
        charge(slot, client, "150");
        pay(client, "150", date, slot);

        jdbc.update("DELETE FROM reservations WHERE user_id = ?", client.getId());
        jdbc.update("DELETE FROM users WHERE id = ?", client.getId());

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM payments", Integer.class));
    }

    // One violation per test on purpose: a failed statement aborts the surrounding transaction, so
    // a second insert in the same method fails with "current transaction is aborted" and the test
    // would be asserting Postgres's bookkeeping rather than the CHECK it names.

    @Test
    @DisplayName("shouldRefuseARowNamingTwoTargets")
    void shouldRefuseARowNamingTwoTargets() {
        Event event = eventRepository.saveAndFlush(new Event("Kurs", EventType.COURSE, date, date, 8));

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "INSERT INTO settlements (time_slot_id, event_id, user_id, amount) VALUES (?, ?, ?, 100)",
            slot.getId(), event.getId(), client.getId()));
    }

    @Test
    @DisplayName("shouldRefuseARowWithNoTarget")
    void shouldRefuseARowWithNoTarget() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "INSERT INTO settlements (user_id, amount) VALUES (?, 100)", client.getId()));
    }

    @Test
    @DisplayName("shouldRefuseARowWithNoPayer")
    void shouldRefuseARowWithNoPayer() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "INSERT INTO settlements (time_slot_id, amount) VALUES (?, 100)", slot.getId()));
    }

    @Test
    @DisplayName("shouldRefuseANegativeAmountAtTheDatabaseLevelToo")
    void shouldRefuseANegativeAmountAtTheDatabaseLevelToo() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "INSERT INTO settlements (time_slot_id, user_id, amount) VALUES (?, ?, -5)",
            slot.getId(), client.getId()));
    }

    @Test
    @DisplayName("shouldRefuseASecondAmountForTheSamePairOfSessionAndPayer")
    void shouldRefuseASecondAmountForTheSamePairOfSessionAndPayer() {
        charge(slot, client, "150");

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "INSERT INTO settlements (time_slot_id, user_id, amount) VALUES (?, ?, 200)",
            slot.getId(), client.getId()));
    }

    @Test
    @DisplayName("shouldRefuseAZeroPaymentAtTheDatabaseLevelToo")
    void shouldRefuseAZeroPaymentAtTheDatabaseLevelToo() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "INSERT INTO payments (user_id, amount, received_on) VALUES (?, 0, ?)",
            client.getId(), date));
    }

    @Test
    @DisplayName("shouldRefuseAPaymentWithNoPayer")
    void shouldRefuseAPaymentWithNoPayer() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "INSERT INTO payments (amount, received_on) VALUES (10, ?)", date));
    }

    // ------------------------------------------------------------------ fixtures

    private User saveUser(String email, String firstName, String lastName) {
        User user = new User(email, firstName, lastName, "+48123456789", email.split("@")[0]);
        user.setRole(UserRole.USER);
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }

    private TimeSlot bookedSlot(LocalDate on) {
        TimeSlot booked = timeSlotRepository.saveAndFlush(
            new TimeSlot(on, LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        reservationRepository.saveAndFlush(new Reservation(client, booked));
        return booked;
    }

    private void cancelBooking() {
        Reservation reservation = reservationRepository.findByUserIdAndTimeSlotId(client.getId(), slot.getId());
        reservation.cancel();
        reservationRepository.saveAndFlush(reservation);
    }

    private void save(String target, UUID targetId, String payer, UUID payerId, String amount) {
        service.save(target, targetId, payer, payerId, new SaveSettlementRequest(new BigDecimal(amount)));
    }

    private void charge(TimeSlot on, User payer, String amount) {
        save("slot", on.getId(), "user", payer.getId(), amount);
    }

    private PaymentResultDto pay(User payer, String amount, LocalDate on, TimeSlot at) {
        return service.addPayment(new AddPaymentRequest(
            "user", payer.getId(), new BigDecimal(amount), on, "slot", at.getId()));
    }

    private PaymentResultDto payWithoutTarget(User payer, String amount, LocalDate on) {
        return service.addPayment(new AddPaymentRequest(
            "user", payer.getId(), new BigDecimal(amount), on, null, null));
    }

    private SettlementOverviewDto stats() {
        return statsService.buildOverview("all", LocalDate.now());
    }

    private SettlementLineDto lineFor(TimeSlot on, User payer) {
        return service.getSection("slot", on.getId()).lines().stream()
            .filter(line -> line.payerId().equals(payer.getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no line for " + payer.getId()));
    }

    private BigDecimal amountOf(SettlementSectionDto section) {
        return section.lines().getFirst().amount();
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertMoney(expected, actual, "expected " + expected + " but was " + actual);
    }

    private static void assertMoney(String expected, BigDecimal actual, String message) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), message + " (was " + actual + ")");
    }
}
