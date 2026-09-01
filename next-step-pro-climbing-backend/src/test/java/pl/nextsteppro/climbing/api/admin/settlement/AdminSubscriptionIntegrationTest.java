package pl.nextsteppro.climbing.api.admin.settlement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standing monthly coaching fees.
 *
 * <p>The two tests worth breaking a build over are the catch-up run — a box that misses the first of
 * the month must not lose that month for ever — and the backdated end, which may drop unpaid fees
 * but never a paid one.
 */
class AdminSubscriptionIntegrationTest extends BaseIntegrationTest {

    @Autowired private AdminSubscriptionService subscriptions;
    @Autowired private AdminSettlementStatsService stats;
    @Autowired private AdminPayoutService payouts;
    @Autowired private AdminSettlementService settlements;
    @Autowired private JdbcTemplate jdbc;

    private User client;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM session_payouts");
        jdbc.update("DELETE FROM subscriptions");
        jdbc.update("DELETE FROM settlements");
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User("client@example.com", "Anna", "Kowalska", "+48123456789", "anna");
        user.setRole(UserRole.USER);
        user.setEmailVerified(true);
        client = userRepository.saveAndFlush(user);
    }

    private LocalDate monthsAgo(int months) {
        return LocalDate.now(AdminSubscriptionService.WARSAW).withDayOfMonth(1).minusMonths(months);
    }

    private TimeSlot bookedSlot(LocalDate on) {
        TimeSlot slot = timeSlotRepository.saveAndFlush(
            new TimeSlot(on, LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        reservationRepository.saveAndFlush(new Reservation(client, slot));
        return slot;
    }

    private int unpricedCount() {
        return stats.buildOverview("all", LocalDate.now(AdminSubscriptionService.WARSAW))
            .unpriced().count();
    }

    private int feeCount() {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM settlements WHERE period_month IS NOT NULL", Integer.class);
    }

    @Test
    @DisplayName("shouldBillEveryMonthASubscriptionAlreadyCoversWhenItIsCreated")
    void shouldBillEveryMonthASubscriptionAlreadyCoversWhenItIsCreated() {
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(2), null));

        // Somebody adding a retainer that started two months ago expects those months, not a row
        // turning up tomorrow night.
        assertEquals(3, feeCount(), "Two past months and the current one");
        assertEquals(0, new BigDecimal("1200.00").compareTo(
            stats.buildOverview("all", LocalDate.now(AdminSubscriptionService.WARSAW))
                .outstanding().total()));
    }

    @Test
    @DisplayName("shouldCatchUpOnMissedMonthsRatherThanOnlyBillingToday")
    void shouldCatchUpOnMissedMonthsRatherThanOnlyBillingToday() {
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(3), null));
        jdbc.update("DELETE FROM settlements WHERE period_month >= ?", monthsAgo(1));

        int created = subscriptions.billDueMonths();

        // ⚠️ A box that fails to come up on the first would otherwise lose that month for ever.
        assertEquals(2, created);
        assertEquals(4, feeCount());
    }

    @Test
    @DisplayName("shouldBeIdempotentSoADoubleRunDoesNotDoubleTheFees")
    void shouldBeIdempotentSoADoubleRunDoesNotDoubleTheFees() {
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(1), null));
        int before = feeCount();

        subscriptions.billDueMonths();
        subscriptions.billDueMonths();

        assertEquals(before, feeCount(), "The unique index on (user, month) is what makes this free");
    }

    @Test
    @DisplayName("shouldNotOverwriteAFeeCorrectedByHand")
    void shouldNotOverwriteAFeeCorrectedByHand() {
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(1), null));
        jdbc.update("UPDATE settlements SET amount = 250 WHERE period_month = ?", monthsAgo(1));

        subscriptions.billDueMonths();

        // DO NOTHING, not DO UPDATE: the run revisits old months, and overwriting would silently
        // undo every correction and re-open every fee already settled.
        assertEquals(0, new BigDecimal("250.00").compareTo(jdbc.queryForObject(
            "SELECT amount FROM settlements WHERE period_month = ?", BigDecimal.class, monthsAgo(1))));
    }

    @Test
    @DisplayName("shouldDropUnpaidFeesAfterABackdatedEndButKeepThePaidOnes")
    void shouldDropUnpaidFeesAfterABackdatedEndButKeepThePaidOnes() {
        UUID id = subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(3), null)).id();
        // She paid for the month after the one they actually stopped in.
        jdbc.update("UPDATE settlements SET settled_on = ?, paid_amount = amount WHERE period_month = ?",
            monthsAgo(1), monthsAgo(1));

        subscriptions.end(id, new EndSubscriptionRequest(monthsAgo(2)));

        // The money arrived; a date written down a week late does not overrule the bank.
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM settlements WHERE period_month = ? AND settled_on IS NOT NULL",
            Integer.class, monthsAgo(1)));
        // The unpaid one after the end is gone.
        assertEquals(0, jdbc.queryForObject(
            "SELECT COUNT(*) FROM settlements WHERE period_month = ?", Integer.class, monthsAgo(0)));
        assertEquals(3, feeCount());
    }

    @Test
    @DisplayName("shouldStopBillingOnceEnded")
    void shouldStopBillingOnceEnded() {
        UUID id = subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(2), null)).id();
        subscriptions.end(id, new EndSubscriptionRequest(monthsAgo(2)));
        int after = feeCount();

        subscriptions.billDueMonths();

        assertEquals(after, feeCount());
    }

    @Test
    @DisplayName("shouldRefuseASecondRunningSubscriptionForTheSamePerson")
    void shouldRefuseASecondRunningSubscriptionForTheSamePerson() {
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(1), null));

        assertThrows(IllegalArgumentException.class, () -> subscriptions.create(client.getId(),
            new SaveSubscriptionRequest(new BigDecimal("500"), monthsAgo(0), null)));
    }

    @Test
    @DisplayName("shouldChangeTheAmountForwardOnly")
    void shouldChangeTheAmountForwardOnly() {
        UUID id = subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(1), null)).id();

        subscriptions.changeAmount(id, new SaveSubscriptionRequest(
            new BigDecimal("450"), monthsAgo(1), null));

        // A raise in June is not a claim about March.
        assertEquals(0, new BigDecimal("400.00").compareTo(jdbc.queryForObject(
            "SELECT amount FROM settlements WHERE period_month = ?", BigDecimal.class, monthsAgo(1))));
    }

    @Test
    @DisplayName("shouldPutAnUnpaidFeeInTheSameOutstandingListAsSessions")
    void shouldPutAnUnpaidFeeInTheSameOutstandingListAsSessions() {
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(0), null));

        var outstanding = stats.buildOverview("all",
            LocalDate.now(AdminSubscriptionService.WARSAW)).outstanding();

        // The whole reason a fee is a settlement and not its own table: it queues, groups and
        // settles with everything else that person owes.
        assertEquals(1, outstanding.count());
        assertEquals("Anna Kowalska", outstanding.items().getFirst().name());
        assertNull(outstanding.items().getFirst().targetId(),
            "A fee has no calendar entry, so nothing may try to link into one");
        assertTrue(outstanding.items().getFirst().targetType().equals("month"));
    }

    @Test
    @DisplayName("shouldSettleAFeeAndTheExtraSessionsBesideItInOneGo")
    void shouldSettleAFeeAndTheExtraSessionsBesideItInOneGo() {
        // ⚠️ This is what the fee being a settlement rather than its own table BUYS. One transfer
        // covers the retainer and the sessions that fell outside it, so it has to clear both — a
        // separate table would have meant a second queue, a second sum and a second click.
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(0), null));

        LocalDate sessionDay = LocalDate.now(AdminSubscriptionService.WARSAW).minusDays(2);
        TimeSlot extra = timeSlotRepository.saveAndFlush(
            new TimeSlot(sessionDay, LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        reservationRepository.saveAndFlush(new Reservation(client, extra));
        settlements.save("slot", extra.getId(), "user", client.getId(),
            new SaveSettlementRequest(new BigDecimal("150"), null, null));

        var result = settlements.settleOutstanding(new SettleOutstandingRequest(
            "user", client.getId(), LocalDate.now(AdminSubscriptionService.WARSAW),
            new BigDecimal("550")));

        assertEquals(2, result.settled(), "The retainer and the session, from one payment");
        assertEquals(0, BigDecimal.ZERO.compareTo(result.balance()));
        assertEquals(0, stats.buildOverview("all",
            LocalDate.now(AdminSubscriptionService.WARSAW)).outstanding().count(),
            "Nothing of hers is left open, fee or session");
    }

    @Test
    @DisplayName("shouldCoverASessionWithTheSubscriptionInsteadOfPricingItAtZero")
    void shouldCoverASessionWithTheSubscriptionInsteadOfPricingItAtZero() {
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(1), null));
        // Last month, so it has certainly happened — the queue only lists work already done.
        TimeSlot slot = bookedSlot(monthsAgo(1).plusDays(3));

        assertEquals(1, unpricedCount(), "Before the mark it is ordinary unpriced work");

        payouts.assignSource("slot", slot.getId(),
            new AssignPayoutSourceRequest(null, client.getId()));

        // The whole point: it leaves the queue WITHOUT a zero, so zero keeps meaning "free of
        // charge" and the revenue split does not claim one-to-one work earns nothing.
        assertEquals(0, unpricedCount());
        SettlementSectionDto section = settlements.getSection("slot", slot.getId());
        assertEquals("subscription", section.coveredBy().kind());
        assertEquals(client.getId(), section.coveredBy().id());
    }

    @Test
    @DisplayName("shouldRefuseToCoverASessionThePersonIsNotBookedOn")
    void shouldRefuseToCoverASessionThePersonIsNotBookedOn() {
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(1), null));
        TimeSlot empty = timeSlotRepository.saveAndFlush(new TimeSlot(
            monthsAgo(1).plusDays(3), LocalTime.of(18, 0), LocalTime.of(20, 0), 4));

        assertThrows(IllegalArgumentException.class, () -> payouts.assignSource(
            "slot", empty.getId(), new AssignPayoutSourceRequest(null, client.getId())));
    }

    @Test
    @DisplayName("shouldRefuseToCoverASessionSomebodyElseIsAlsoOn")
    void shouldRefuseToCoverASessionSomebodyElseIsAlsoOn() {
        // ⚠️ The mark covers the SESSION; a retainer covers one PERSON. Where somebody else is on
        // it those are different claims, and allowing it is a dead end with no way out: the mark
        // takes the whole session out of per-participant pricing, so the cash payer beside her has
        // nowhere to be entered — and pricing him first blocks the mark instead. Refused at the
        // click that causes it rather than later, at an amount that then cannot be saved.
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(1), null));
        TimeSlot group = bookedSlot(monthsAgo(1).plusDays(3));
        User cashPayer = saveOtherUser();
        reservationRepository.saveAndFlush(new Reservation(cashPayer, group));

        assertThrows(IllegalArgumentException.class, () -> payouts.assignSource(
            "slot", group.getId(), new AssignPayoutSourceRequest(null, client.getId())));

        // And the other participant can still be priced, which is the point of refusing.
        settlements.save("slot", group.getId(), "user", cashPayer.getId(),
            new SaveSettlementRequest(new BigDecimal("150"), null, null));
    }

    @Test
    @DisplayName("shouldStillLetAnInstitutionSettleAWholeGroupSession")
    void shouldStillLetAnInstitutionSettleAWholeGroupSession() {
        // The single-payer rule belongs to retainers only. A school really does pay for the room,
        // however many people are in it, so the bulk branch must stay open on a group.
        TimeSlot group = bookedSlot(monthsAgo(1).plusDays(3));
        reservationRepository.saveAndFlush(new Reservation(saveOtherUser(), group));
        UUID sourceId = payouts.createSource(new SavePayoutSourceRequest("Szkola XYZ")).id();

        payouts.assignSource("slot", group.getId(),
            new AssignPayoutSourceRequest(sourceId, null));

        assertEquals(0, unpricedCount(), "Marked in bulk, so it leaves the pricing queue");
    }

    private User saveOtherUser() {
        User user = new User("cash@example.com", "Piotr", "Nowak", "+48111222333", "piotr");
        user.setRole(UserRole.USER);
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("shouldRefuseToCoverASessionInAMonthTheSubscriptionDoesNotReach")
    void shouldRefuseToCoverASessionInAMonthTheSubscriptionDoesNotReach() {
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(1), null));
        TimeSlot lastYear = bookedSlot(monthsAgo(6).plusDays(3));

        // Otherwise the mark takes the session out of the queue and files it under a retainer that
        // never covered it — work that earns nothing and says so nowhere.
        assertThrows(IllegalArgumentException.class, () -> payouts.assignSource(
            "slot", lastYear.getId(), new AssignPayoutSourceRequest(null, client.getId())));
    }

    @Test
    @DisplayName("shouldRefuseTwoPayersOnOneSession")
    void shouldRefuseTwoPayersOnOneSession() {
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(1), null));
        TimeSlot slot = bookedSlot(monthsAgo(1).plusDays(3));

        assertThrows(IllegalArgumentException.class, () -> payouts.assignSource(
            "slot", slot.getId(), new AssignPayoutSourceRequest(UUID.randomUUID(), client.getId())));
    }

    @Test
    @DisplayName("shouldKeepBilledFeesWhenTheRuleItselfIsDeleted")
    void shouldKeepBilledFeesWhenTheRuleItselfIsDeleted() {
        UUID id = subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(1), null)).id();

        subscriptions.delete(id);

        // They are somebody's debts or somebody's payments, and neither stops being one because the
        // rule went away.
        assertEquals(2, feeCount());
    }
}
