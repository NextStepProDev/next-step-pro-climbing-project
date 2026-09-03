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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settlement feature end to end: pricing a session per participant, and the rules that keep
 * those figures honest.
 *
 * <p>The test this file exists for is
 * {@code shouldPriceAMultiDayEventOncePerPersonRatherThanOncePerDay} — everything else here is
 * ordinary coverage, but that one guards the decision the whole table shape was chosen for.
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
    }

    @Test
    @DisplayName("shouldUpsertAnAmountAndThenCorrectIt")
    void shouldUpsertAnAmountAndThenCorrectIt() {
        save("slot", slot.getId(), "user", client.getId(), "150", null);
        assertEquals(0, new BigDecimal("150.00").compareTo(amountOf(service.getSection("slot", slot.getId()))));

        save("slot", slot.getId(), "user", client.getId(), "180", date);
        SettlementSectionDto section = service.getSection("slot", slot.getId());
        assertEquals(0, new BigDecimal("180.00").compareTo(amountOf(section)));
        assertEquals(date, section.lines().getFirst().settledOn());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM settlements", Integer.class),
            "A correction must overwrite the row, not add a second one");
    }

    @Test
    @DisplayName("shouldTreatAMissingPaymentDateAsOutstandingBecausePutReplacesTheWholeRow")
    void shouldTreatAMissingPaymentDateAsOutstandingBecausePutReplacesTheWholeRow() {
        save("slot", slot.getId(), "user", client.getId(), "150", date);
        save("slot", slot.getId(), "user", client.getId(), "150", null);

        assertNull(service.getSection("slot", slot.getId()).lines().getFirst().settledOn(),
            "PUT is a full replace: there is no separate 'paid' flag to leave behind");
    }

    @Test
    @DisplayName("shouldAllowZeroAsADeliberateFreeOfCharge")
    void shouldAllowZeroAsADeliberateFreeOfCharge() {
        save("slot", slot.getId(), "user", client.getId(), "0", date);

        SettlementLineDto line = service.getSection("slot", slot.getId()).lines().getFirst();
        assertEquals(0, BigDecimal.ZERO.compareTo(line.amount()),
            "Zero is a decision — the state that means 'not priced' is the absence of a row");
    }

    @Test
    @DisplayName("shouldRemoveAnAmountIdempotently")
    void shouldRemoveAnAmountIdempotently() {
        save("slot", slot.getId(), "user", client.getId(), "150", null);
        service.delete("slot", slot.getId(), "user", client.getId());
        service.delete("slot", slot.getId(), "user", client.getId());

        assertNull(service.getSection("slot", slot.getId()).lines().getFirst().amount());
    }

    @Test
    @DisplayName("shouldSuggestWhatThisPersonWasLastChargedButNotApplyIt")
    void shouldSuggestWhatThisPersonWasLastChargedButNotApplyIt() {
        save("slot", slot.getId(), "user", client.getId(), "150", date);

        TimeSlot next = timeSlotRepository.saveAndFlush(
            new TimeSlot(date.plusDays(7), LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        reservationRepository.saveAndFlush(new Reservation(client, next));

        SettlementLineDto line = service.getSection("slot", next.getId()).lines().getFirst();
        assertNull(line.amount(), "A suggestion is an offer, never a saved amount");
        assertEquals(0, new BigDecimal("150.00").compareTo(line.suggestedAmount()));
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

        save("event", event.getId(), "user", client.getId(), "600", date);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM settlements", Integer.class));
        assertEquals(0, new BigDecimal("600.00")
            .compareTo(amountOf(service.getSection("event", event.getId()))));
    }

    @Test
    @DisplayName("shouldRefuseToPriceASingleDayOfAnEvent")
    void shouldRefuseToPriceASingleDayOfAnEvent() {
        Event event = eventRepository.saveAndFlush(
            new Event("Kurs skalny", EventType.COURSE, date, date.plusDays(1), 8));
        TimeSlot eventSlot = timeSlotRepository.saveAndFlush(
            new TimeSlot(event, date, LocalTime.of(9, 0), LocalTime.of(17, 0), 8));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> save("slot", eventSlot.getId(), "user", client.getId(), "300", null));
        assertTrue(ex.getMessage().toLowerCase().contains("wydarzen")
                || ex.getMessage().toLowerCase().contains("event"),
            "The refusal must name the reason, not a constraint: " + ex.getMessage());

        assertThrows(IllegalArgumentException.class,
            () -> service.getSection("slot", eventSlot.getId()));
    }

    // ------------------------------------------------------------------- guests

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

        save("event", event.getId(), "guest", onEvent.getId(), "1800", date);
        save("event", event.getId(), "guest", onDay.getId(), "600", null);

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

        save("event", event.getId(), "guest", onDay.getId(), "600", date);

        // Writing to an address the read cannot reach is worse than refusing the write: the admin
        // sees the amount accepted, comes back, and finds the line blank again.
        SettlementLineDto line = service.getSection("event", event.getId()).lines().stream()
            .filter(candidate -> candidate.payerId().equals(onDay.getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("the guest vanished from the section"));
        assertEquals(0, new BigDecimal("600.00").compareTo(line.amount()),
            "The amount was written onto the event's per-day slot, which no read of the event ever "
                + "looks at — so it is invisible everywhere the admin typed it");
        assertEquals(date, line.settledOn());
    }

    @Test
    @DisplayName("shouldRefuseAGuestFromAnotherSession")
    void shouldRefuseAGuestFromAnotherSession() {
        TimeSlot other = timeSlotRepository.saveAndFlush(
            new TimeSlot(date.plusDays(1), LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        GuestReservation guest = guestReservationRepository.saveAndFlush(
            new GuestReservation(other, "Ktos zupelnie inny", 1));

        assertThrows(IllegalArgumentException.class,
            () -> save("slot", slot.getId(), "guest", guest.getId(), "150", null));
    }

    // ---------------------------------------------- guards reject change, not state

    @Test
    @DisplayName("shouldRefuseANewAmountForSomebodyWhoIsNotBookedOnTheSession")
    void shouldRefuseANewAmountForSomebodyWhoIsNotBookedOnTheSession() {
        User stranger = saveUser("stranger@example.com", "Nikt", "Obcy");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> save("slot", slot.getId(), "user", stranger.getId(), "150", null));
        assertTrue(ex.getMessage().length() > 0);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM settlements", Integer.class));
    }

    @Test
    @DisplayName("shouldKeepAnExistingAmountEditableAfterTheBookingIsCancelled")
    void shouldKeepAnExistingAmountEditableAfterTheBookingIsCancelled() {
        save("slot", slot.getId(), "user", client.getId(), "150", date);

        Reservation reservation = reservationRepository.findByUserIdAndTimeSlotId(client.getId(), slot.getId());
        reservation.cancel();
        reservationRepository.saveAndFlush(reservation);

        // The guard rejects the CHANGE (a brand-new amount for a stranger), never the STATE. Money
        // that changed hands must stay correctable after somebody cancels.
        save("slot", slot.getId(), "user", client.getId(), "120", date);
        assertEquals(0, new BigDecimal("120.00").compareTo(amountOf(service.getSection("slot", slot.getId()))));

        service.delete("slot", slot.getId(), "user", client.getId());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM settlements", Integer.class));
    }

    @Test
    @DisplayName("shouldStillShowAPaidClientWhoLaterCancelledFlaggedAsOrphaned")
    void shouldStillShowAPaidClientWhoLaterCancelledFlaggedAsOrphaned() {
        save("slot", slot.getId(), "user", client.getId(), "150", date);

        Reservation reservation = reservationRepository.findByUserIdAndTimeSlotId(client.getId(), slot.getId());
        reservation.cancel();
        reservationRepository.saveAndFlush(reservation);

        List<SettlementLineDto> lines = service.getSection("slot", slot.getId()).lines();
        assertEquals(1, lines.size(),
            "Dropping the row would make the money vanish from the screen while it still counts "
                + "in the monthly total");
        assertTrue(lines.getFirst().orphaned());
        assertEquals(0, new BigDecimal("150.00").compareTo(lines.getFirst().amount()));
    }

    @Test
    @DisplayName("shouldRejectUnknownTargetAndPayerSegments")
    void shouldRejectUnknownTargetAndPayerSegments() {
        assertThrows(IllegalArgumentException.class,
            () -> service.getSection("reservation", slot.getId()));
        assertThrows(IllegalArgumentException.class,
            () -> save("slot", slot.getId(), "sponsor", client.getId(), "150", null));
        assertThrows(IllegalArgumentException.class,
            () -> service.getSection("slot", UUID.randomUUID()));
    }

    @Test
    @DisplayName("shouldRejectAnAmountOutsideTheAllowedRange")
    void shouldRejectAnAmountOutsideTheAllowedRange() {
        assertThrows(IllegalArgumentException.class,
            () -> save("slot", slot.getId(), "user", client.getId(), "-1", null));
        assertThrows(IllegalArgumentException.class,
            () -> save("slot", slot.getId(), "user", client.getId(), "100000.01", null));
    }

    // -------------------------------------------- settling a month in one go

    @Test
    @DisplayName("shouldSettleAWholeMonthOfOnePersonOnTheDayTheyActuallyPaid")
    void shouldSettleAWholeMonthOfOnePersonOnTheDayTheyActuallyPaid() {
        User other = saveUser("other@example.com", "Piotr", "Nowak");
        TimeSlot second = timeSlotRepository.saveAndFlush(
            new TimeSlot(date.plusDays(7), LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        reservationRepository.saveAndFlush(new Reservation(client, second));
        reservationRepository.saveAndFlush(new Reservation(other, second));

        save("slot", slot.getId(), "user", client.getId(), "150", null);
        save("slot", second.getId(), "user", client.getId(), "80", null);
        save("slot", second.getId(), "user", other.getId(), "150", null);

        LocalDate paidOn = date.plusDays(20);
        int settled = service.settleOutstanding(
            new SettleOutstandingRequest("user", client.getId(), paidOn, new BigDecimal("230")))
            .settled();

        assertEquals(2, settled, "Both of hers, and only hers");
        // One transfer covered the month, so one date is true of all of it — taking each session's
        // own day would scatter a single payment across the months it paid for.
        assertEquals(paidOn, service.getSection("slot", slot.getId()).lines().getFirst().settledOn());
        assertEquals(paidOn, lineFor(second, client).settledOn());
        assertNull(lineFor(second, other).settledOn(),
            "Somebody else's debt on the same session must not be settled by it");
    }

    @Test
    @DisplayName("shouldRefuseToRecordMoneyAgainstAnAccountThatOwesNothing")
    void shouldRefuseToRecordMoneyAgainstAnAccountThatOwesNothing() {
        save("slot", slot.getId(), "user", client.getId(), "150", date);

        // Nothing open and no credit to draw on: there is no row for the money to land against, and
        // silently doing nothing would look like it had been recorded.
        assertThrows(IllegalArgumentException.class, () -> service.settleOutstanding(
            new SettleOutstandingRequest("user", client.getId(), date.plusDays(20), BigDecimal.ZERO)));
        assertEquals(date, service.getSection("slot", slot.getId()).lines().getFirst().settledOn(),
            "And the date somebody corrected by hand is untouched");
    }

    @Test
    @DisplayName("shouldKeepTheChangeAsCreditWhenSomebodyPaysMoreThanTheyOwe")
    void shouldKeepTheChangeAsCreditWhenSomebodyPaysMoreThanTheyOwe() {
        save("slot", slot.getId(), "user", client.getId(), "150", null);

        // A two-hundred note against a hundred-and-fifty session, which is how cash usually goes.
        SettleOutstandingResultDto result = service.settleOutstanding(new SettleOutstandingRequest(
            "user", client.getId(), date, new BigDecimal("200")));

        assertEquals(1, result.settled());
        assertEquals(0, new BigDecimal("50.00").compareTo(result.balance()),
            "The change does not vanish — it stays on their account");
        assertEquals(0, service.getSection("slot", slot.getId()).lines().getFirst()
            .balance().compareTo(new BigDecimal("50.00")),
            "And it is in front of you at the moment you type the next amount");
    }

    @Test
    @DisplayName("shouldSpendThatCreditOnTheNextSessionInsteadOfLeavingItStranded")
    void shouldSpendThatCreditOnTheNextSessionInsteadOfLeavingItStranded() {
        save("slot", slot.getId(), "user", client.getId(), "150", null);
        service.settleOutstanding(new SettleOutstandingRequest(
            "user", client.getId(), date, new BigDecimal("200")));

        TimeSlot next = timeSlotRepository.saveAndFlush(
            new TimeSlot(date.plusDays(7), LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        reservationRepository.saveAndFlush(new Reservation(client, next));
        save("slot", next.getId(), "user", client.getId(), "150", null);

        // He hands over 100 and the 50 already held covers the rest.
        SettleOutstandingResultDto result = service.settleOutstanding(new SettleOutstandingRequest(
            "user", client.getId(), date.plusDays(7), new BigDecimal("100")));

        assertEquals(0, BigDecimal.ZERO.compareTo(result.balance()), "Square");
        // ⚠️ And the rows agree with the balance. Leaving the credit where it was would net to zero
        // while the second session still read as owing 50 — two true-looking figures disagreeing.
        assertEquals(0, stats().outstanding().count(),
            "Nothing is owed, so nothing is listed");
    }

    @Test
    @DisplayName("shouldCloseASessionThatTheCreditAlonePaysForWithNothingChangingHands")
    void shouldCloseASessionThatTheCreditAlonePaysForWithNothingChangingHands() {
        // The reported case, and the one the per-participant fields cannot express: a hundred handed
        // over for a fifty session, then a session two months later that the change already covers.
        // Writing "charged 50, received 0" on that second row is true and settles nothing — the row
        // stays open while the balance reads zero, which is the disagreement this whole mechanism
        // exists to prevent. Only spending the credit moves both figures at once.
        save("slot", slot.getId(), "user", client.getId(), "50", null);
        service.settleOutstanding(new SettleOutstandingRequest(
            "user", client.getId(), date, new BigDecimal("100")));

        TimeSlot next = timeSlotRepository.saveAndFlush(
            new TimeSlot(date.plusMonths(2), LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        reservationRepository.saveAndFlush(new Reservation(client, next));
        save("slot", next.getId(), "user", client.getId(), "50", null);

        // Nothing changed hands this time, which is the whole point of the zero.
        SettleOutstandingResultDto result = service.settleOutstanding(new SettleOutstandingRequest(
            "user", client.getId(), date.plusMonths(2), BigDecimal.ZERO));

        assertEquals(1, result.settled(), "The credit reached the open session");
        assertEquals(0, BigDecimal.ZERO.compareTo(result.balance()), "Square");
        assertEquals(0, stats().outstanding().count(),
            "And the session no longer reads as owed, because it is not");
        assertEquals(date.plusMonths(2), lineFor(next, client).settledOn(),
            "The session it paid for is the one that carries the date");
        // ⚠️ The property the owner's books depend on: spending a credit MOVES revenue between
        // months, it does not mint or destroy any. A hundred came through the door and a hundred is
        // still counted — fifty of it now attributed to the session it actually paid for. Getting
        // this wrong would be invisible on every screen except the yearly total.
        assertEquals(0, new BigDecimal("100.00").compareTo(stats().revenue().total()),
            "One hundred arrived in total, so one hundred is the revenue however it was allocated");
        assertEquals(0, new BigDecimal("50.00").compareTo(lineFor(slot, client).paidAmount()),
            "And the row that was holding the change is back to exactly what it charged");
    }

    @Test
    @DisplayName("shouldLeaveTheRestOfAnOverpaymentOnAccountWhenItOutrunsTheSession")
    void shouldLeaveTheRestOfAnOverpaymentOnAccountWhenItOutrunsTheSession() {
        // Eighty of credit against a fifty session: the fifty closes, and the remaining thirty has
        // to stay his — spending a credit must never round somebody's money away.
        save("slot", slot.getId(), "user", client.getId(), "50", null);
        service.settleOutstanding(new SettleOutstandingRequest(
            "user", client.getId(), date, new BigDecimal("130")));

        TimeSlot next = timeSlotRepository.saveAndFlush(
            new TimeSlot(date.plusDays(7), LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        reservationRepository.saveAndFlush(new Reservation(client, next));
        save("slot", next.getId(), "user", client.getId(), "50", null);

        SettleOutstandingResultDto result = service.settleOutstanding(new SettleOutstandingRequest(
            "user", client.getId(), date.plusDays(7), BigDecimal.ZERO));

        assertEquals(0, new BigDecimal("30.00").compareTo(result.balance()),
            "Thirty of his is still here, ready for the session after this one");
        assertEquals(0, stats().outstanding().count(), "And nothing is owed");
        SettlementLineDto line = lineFor(next, client);
        assertEquals(0, new BigDecimal("80.00").compareTo(line.paidAmount()),
            "The change rides on the row it landed against — 50 charged, 80 held");
        assertEquals(0, new BigDecimal("30.00").compareTo(line.credit()),
            "So the section offers that thirty next time, and no button while nothing is owed");
    }

    @Test
    @DisplayName("shouldPayWhatItCanAndKeepChasingTheRestWhenTheCreditFallsShort")
    void shouldPayWhatItCanAndKeepChasingTheRestWhenTheCreditFallsShort() {
        // Thirty of credit against a fifty session: the twenty short stays a debt, on the same row,
        // rather than the session dropping off the list as though it had been dealt with.
        save("slot", slot.getId(), "user", client.getId(), "50", null);
        service.settleOutstanding(new SettleOutstandingRequest(
            "user", client.getId(), date, new BigDecimal("80")));

        TimeSlot next = timeSlotRepository.saveAndFlush(
            new TimeSlot(date.plusDays(7), LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        reservationRepository.saveAndFlush(new Reservation(client, next));
        save("slot", next.getId(), "user", client.getId(), "50", null);

        SettleOutstandingResultDto result = service.settleOutstanding(new SettleOutstandingRequest(
            "user", client.getId(), date.plusDays(7), BigDecimal.ZERO));

        assertEquals(0, new BigDecimal("-20.00").compareTo(result.balance()),
            "Twenty short, and the screen says so rather than showing a negative balance");
        assertEquals(1, stats().outstanding().count(), "The session is still owed for");
        assertEquals(0, new BigDecimal("20.00").compareTo(stats().outstanding().total()),
            "Twenty, not the whole fifty — the credit really did land");
        SettlementLineDto line = lineFor(next, client);
        assertEquals(0, new BigDecimal("30.00").compareTo(line.paidAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(line.credit()),
            "Nothing left to spend, so the button is gone and the row is simply short");
    }

    @Test
    @DisplayName("shouldSpendACreditOnTheOldestDebtFirstEvenFromANewerSession")
    void shouldSpendACreditOnTheOldestDebtFirstEvenFromANewerSession() {
        // Clicking on today's session does not make today's session the one that gets paid: a
        // backlog is paid off in the order it accumulated, which is why the button reports the
        // balance instead of claiming the row in front of you is now settled.
        save("slot", slot.getId(), "user", client.getId(), "50", null);
        service.settleOutstanding(new SettleOutstandingRequest(
            "user", client.getId(), date, new BigDecimal("100")));

        TimeSlot older = timeSlotRepository.saveAndFlush(
            new TimeSlot(date.plusDays(3), LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        TimeSlot newer = timeSlotRepository.saveAndFlush(
            new TimeSlot(date.plusDays(9), LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        reservationRepository.saveAndFlush(new Reservation(client, older));
        reservationRepository.saveAndFlush(new Reservation(client, newer));
        save("slot", older.getId(), "user", client.getId(), "50", null);
        save("slot", newer.getId(), "user", client.getId(), "50", null);

        service.settleOutstanding(new SettleOutstandingRequest(
            "user", client.getId(), date.plusDays(9), BigDecimal.ZERO));

        assertNotNull(lineFor(older, client).settledOn(), "The older session is the one it paid");
        assertNull(lineFor(newer, client).settledOn(),
            "And the one on screen is still owed for, which the result message reports");
    }

    @Test
    @DisplayName("shouldKeepChasingTheRemainderWhenSomebodyPaysTooLittle")
    void shouldKeepChasingTheRemainderWhenSomebodyPaysTooLittle() {
        save("slot", slot.getId(), "user", client.getId(), "150", null);

        SettleOutstandingResultDto result = service.settleOutstanding(new SettleOutstandingRequest(
            "user", client.getId(), date, new BigDecimal("100")));

        assertEquals(0, new BigDecimal("-50.00").compareTo(result.balance()));
        assertEquals(1, stats().outstanding().count());
        assertEquals(0, new BigDecimal("50.00").compareTo(stats().outstanding().total()),
            "Fifty still owed, not the whole hundred and fifty");
    }

    @Test
    @DisplayName("shouldSettleAGuestsOwnDebtsOnly")
    void shouldSettleAGuestsOwnDebtsOnly() {
        GuestReservation guest = guestReservationRepository.saveAndFlush(
            new GuestReservation(slot, "Marek — kolega Ani", 1));
        save("slot", slot.getId(), "guest", guest.getId(), "150", null);
        save("slot", slot.getId(), "user", client.getId(), "150", null);

        assertEquals(1, service.settleOutstanding(
            new SettleOutstandingRequest("guest", guest.getId(), date, new BigDecimal("150"))).settled());
        assertNull(service.getSection("slot", slot.getId()).lines().stream()
            .filter(line -> line.payerId().equals(client.getId()))
            .findFirst().orElseThrow().settledOn());
    }

    @Test
    @DisplayName("shouldRejectAnUnknownPayerTypeForABatch")
    void shouldRejectAnUnknownPayerTypeForABatch() {
        assertThrows(IllegalArgumentException.class, () -> service.settleOutstanding(
            new SettleOutstandingRequest("sponsor", client.getId(), date, new BigDecimal("150"))));
    }

    @Test
    @DisplayName("shouldPayOffTheOldestDebtFirstWhenTheMoneyDoesNotCoverEverything")
    void shouldPayOffTheOldestDebtFirstWhenTheMoneyDoesNotCoverEverything() {
        // ⚠️ The order is not cosmetic. A backlog is paid off the way it accumulated, and the client
        // asking "so which sessions am I straight for?" has to get the same answer the screen gives.
        // With the order reversed both totals stay right and every individual row is wrong.
        TimeSlot later = timeSlotRepository.saveAndFlush(
            new TimeSlot(date.plusDays(7), LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        reservationRepository.saveAndFlush(new Reservation(client, later));
        save("slot", slot.getId(), "user", client.getId(), "150", null);
        save("slot", later.getId(), "user", client.getId(), "80", null);

        // Enough for the older one only.
        int settled = service.settleOutstanding(new SettleOutstandingRequest(
            "user", client.getId(), date.plusDays(20), new BigDecimal("150"))).settled();

        assertEquals(1, settled);
        assertNotNull(lineFor(slot, client).settledOn(), "The older session is the one that closes");
        assertNull(lineFor(later, client).settledOn(), "And the newer one is still open");
        assertEquals(0, new BigDecimal("80.00").compareTo(stats().outstanding().total()));
    }

    @Test
    @DisplayName("shouldSplitAPartPaymentAcrossDebtsInsteadOfPickingOne")
    void shouldSplitAPartPaymentAcrossDebtsInsteadOfPickingOne() {
        // 200 against 150 + 80: the first closes and the second takes the remaining 50, leaving 30.
        // Money that stopped at a row boundary would leave the client credited and still in debt.
        TimeSlot later = timeSlotRepository.saveAndFlush(
            new TimeSlot(date.plusDays(7), LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        reservationRepository.saveAndFlush(new Reservation(client, later));
        save("slot", slot.getId(), "user", client.getId(), "150", null);
        save("slot", later.getId(), "user", client.getId(), "80", null);

        SettleOutstandingResultDto result = service.settleOutstanding(new SettleOutstandingRequest(
            "user", client.getId(), date.plusDays(20), new BigDecimal("200")));

        assertEquals(2, result.settled(), "The money reached both rows");
        assertEquals(0, new BigDecimal("-30.00").compareTo(result.balance()));
        assertEquals(0, new BigDecimal("30.00").compareTo(stats().outstanding().total()),
            "Thirty of the second session is still owed — not eighty, and not nothing");
        assertEquals(1, stats().outstanding().count(),
            "And only the part-paid row is still listed");
    }

    @Test
    @DisplayName("shouldRefuseAnOverpaymentTooBigToLandOnOneRow")
    void shouldRefuseAnOverpaymentTooBigToLandOnOneRow() {
        // ⚠️ The leftover of a batch lands on ONE row, and the pool it comes from is not just what
        // was handed over — it also absorbs credit the person had already left on other rows. So the
        // carrier can breach that row's ceiling while every individual figure was in range. It takes
        // credit spread across several rows to get there, which is why one overpaid row cannot do
        // it. Left to the database this came back as a bare 409 naming no field.
        TimeSlot second = timeSlotRepository.saveAndFlush(
            new TimeSlot(date.plusDays(7), LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        reservationRepository.saveAndFlush(new Reservation(client, second));

        service.save("slot", slot.getId(), "user", client.getId(),
            new SaveSettlementRequest(BigDecimal.ONE, new BigDecimal("100000"), date));
        service.save("slot", second.getId(), "user", client.getId(),
            new SaveSettlementRequest(BigDecimal.ONE, new BigDecimal("100000"), date));

        assertThrows(IllegalArgumentException.class, () -> service.settleOutstanding(
            new SettleOutstandingRequest("user", client.getId(), date, BigDecimal.ZERO)));
    }

    // ------------------------------------------------------------ database rules

    @Test
    @DisplayName("shouldDropTheSettlementWhenItsSessionOrItsPayerGoesAway")
    void shouldDropTheSettlementWhenItsSessionOrItsPayerGoesAway() {
        save("slot", slot.getId(), "user", client.getId(), "150", date);

        jdbc.update("DELETE FROM reservations WHERE time_slot_id = ?", slot.getId());
        jdbc.update("DELETE FROM time_slots WHERE id = ?", slot.getId());

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM settlements", Integer.class),
            "The foreign keys are the mechanism: an amount must not outlive the session it prices");
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
        save("slot", slot.getId(), "user", client.getId(), "150", null);

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "INSERT INTO settlements (time_slot_id, user_id, amount) VALUES (?, ?, 200)",
            slot.getId(), client.getId()));
    }

    // ------------------------------------------------------------------ fixtures

    private User saveUser(String email, String firstName, String lastName) {
        User user = new User(email, firstName, lastName, "+48123456789", email.split("@")[0]);
        user.setRole(UserRole.USER);
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }

    private void save(String target, UUID targetId, String payer, UUID payerId,
                      String amount, LocalDate settledOn) {
        service.save(target, targetId, payer, payerId, new SaveSettlementRequest(
            new BigDecimal(amount), settledOn == null ? null : new BigDecimal(amount), settledOn));
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
}
