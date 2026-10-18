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
        jdbc.update("DELETE FROM payments");
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
        // She had paid up to the month after the one they actually stopped in: three fees of 400,
        // covered oldest first.
        settlements.addPayment(new AddPaymentRequest("user", client.getId(), new BigDecimal("1200"),
            monthsAgo(1), null, null));

        subscriptions.end(id, new EndSubscriptionRequest(monthsAgo(2)));

        // The money arrived; a date written down a week late does not overrule the bank.
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM settlements WHERE period_month = ?", Integer.class, monthsAgo(1)));
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

        subscriptions.changeAmount(id, new ChangeSubscriptionAmountRequest(new BigDecimal("450")));

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
            new SaveSettlementRequest(new BigDecimal("150")));

        var result = settlements.addPayment(new AddPaymentRequest(
            "user", client.getId(), new BigDecimal("550"),
            LocalDate.now(AdminSubscriptionService.WARSAW), null, null));

        assertEquals(0, BigDecimal.ZERO.compareTo(result.debt()),
            "The retainer and the session, from one payment");
        assertEquals(0, BigDecimal.ZERO.compareTo(result.credit()));
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
            new SaveSettlementRequest(new BigDecimal("150")));
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
    @DisplayName("shouldRefuseAStartSoFarBackThatItBillsYearsOfFeesAtOnce")
    void shouldRefuseAStartSoFarBackThatItBillsYearsOfFeesAtOnce() {
        // A slip of the year in the date field is one keystroke, and the run that follows it bills
        // every month since: six years produced 81 rows of debt in one 200 OK, and the only fee a
        // backdated end cannot take away afterwards is the one in the month it starts in.
        assertThrows(IllegalArgumentException.class, () -> subscriptions.create(client.getId(),
            new SaveSubscriptionRequest(new BigDecimal("400"), monthsAgo(72), null)));

        assertEquals(0, feeCount(), "And nothing is billed on the way to the refusal");
    }

    @Test
    @DisplayName("shouldStillAllowABackdateSomebodyWouldPlausiblyType")
    void shouldStillAllowABackdateSomebodyWouldPlausiblyType() {
        // A retainer entered late is ordinary, so the limit has to be generous enough to clear a
        // year and a half without an argument.
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(18), null));

        assertEquals(19, feeCount());
    }

    @Test
    @DisplayName("shouldDeleteAMonthlyFeeBilledByMistake")
    void shouldDeleteAMonthlyFeeBilledByMistake() {
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(1), null));
        assertEquals(2, feeCount());

        // The one row the app could create and not remove: a fee has no calendar entry, so the
        // per-participant delete cannot address it, and ending the rule leaves the month it started
        // in. Without this there is no way out of a mistyped subscription except editing the
        // database by hand.
        subscriptions.skipMonth(client.getId(), monthsAgo(1));

        assertEquals(1, feeCount());
    }

    @Test
    @DisplayName("shouldNotBillADeletedFeeAgainOnTheNextNightlyRun")
    void shouldNotBillADeletedFeeAgainOnTheNextNightlyRun() {
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(1), null));

        subscriptions.skipMonth(client.getId(), monthsAgo(1));
        int created = subscriptions.billDueMonths();

        // ⚠️ The catch-up run fills every covered month without a row — it could not tell "deleted
        // on purpose" from "not billed yet", so the fee came back the very next night, at the
        // subscription's CURRENT amount. Deleting means "this month is not charged", and stays so.
        assertEquals(0, created);
        assertEquals(1, feeCount());
        assertEquals(java.util.List.of(monthsAgo(1)), subscriptions.forUser(client.getId())
            .getFirst().skippedMonths());
    }

    @Test
    @DisplayName("shouldBillASkippedMonthAgainOnceItIsRestored")
    void shouldBillASkippedMonthAgainOnceItIsRestored() {
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(1), null));
        subscriptions.skipMonth(client.getId(), monthsAgo(1));

        subscriptions.restoreMonth(client.getId(), monthsAgo(1));

        assertEquals(2, feeCount(), "Restored straight away, not tomorrow night");
        assertTrue(subscriptions.forUser(client.getId()).getFirst().skippedMonths().isEmpty());
    }

    @Test
    @DisplayName("shouldNotRememberASkipForAMonthNoSubscriptionCovers")
    void shouldNotRememberASkipForAMonthNoSubscriptionCovers() {
        UUID id = subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(3), null)).id();
        settlements.addPayment(new AddPaymentRequest("user", client.getId(), new BigDecimal("1600"),
            monthsAgo(0), null, null));
        subscriptions.end(id, new EndSubscriptionRequest(monthsAgo(2)));

        // A paid fee left behind after a backdated end is a plain correction, not a free month: a
        // skip recorded here would silently swallow that month in a subscription started later.
        subscriptions.skipMonth(client.getId(), monthsAgo(1));
        UUID later = subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("500"), monthsAgo(1), null)).id();

        assertEquals(0, new BigDecimal("500.00").compareTo(jdbc.queryForObject(
            "SELECT amount FROM settlements WHERE period_month = ?", BigDecimal.class, monthsAgo(1))));
        assertTrue(subscriptions.forUser(client.getId()).stream()
            .filter(s -> s.id().equals(later)).findFirst().orElseThrow().skippedMonths().isEmpty());
    }

    @Test
    @DisplayName("shouldNotTouchAnotherSubscriptionsFeesWhenEndingOne")
    void shouldNotTouchAnotherSubscriptionsFeesWhenEndingOne() {
        UUID first = subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("300"), monthsAgo(6), monthsAgo(4))).id();
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("500"), monthsAgo(2), null));
        assertEquals(6, feeCount(), "Three months of the first, three of the second");

        subscriptions.end(first, new EndSubscriptionRequest(monthsAgo(5)));

        // ⚠️ The unpaid-after-the-end filter ran over the PERSON's fees, not this subscription's —
        // so shortening an old, closed retainer wiped every month of the one running now.
        assertEquals(5, feeCount(), "Only the first one's last month goes");
        assertEquals(3, jdbc.queryForObject(
            "SELECT COUNT(*) FROM settlements WHERE period_month >= ? AND amount = 500",
            Integer.class, monthsAgo(2)));
    }

    @Test
    @DisplayName("shouldRefuseASubscriptionThatOverlapsAnEndedOne")
    void shouldRefuseASubscriptionThatOverlapsAnEndedOne() {
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("300"), monthsAgo(3), monthsAgo(1)));

        // The month both would cover was already billed at the first one's price, and DO NOTHING
        // kept it: the new rate silently did not apply to the month it was entered for.
        assertThrows(IllegalArgumentException.class, () -> subscriptions.create(client.getId(),
            new SaveSubscriptionRequest(new BigDecimal("500"), monthsAgo(1), null)));
        // The month after is free, and that is the way out the message names.
        subscriptions.create(client.getId(),
            new SaveSubscriptionRequest(new BigDecimal("500"), monthsAgo(0), null));
    }

    @Test
    @DisplayName("shouldRefuseReopeningOverAMonthALaterSubscriptionCovers")
    void shouldRefuseReopeningOverAMonthALaterSubscriptionCovers() {
        UUID first = subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("300"), monthsAgo(6), monthsAgo(5))).id();
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("500"), monthsAgo(3), monthsAgo(2)));
        int before = feeCount();

        // ⚠️ Reopening made the first one open-ended from its start, so the run filled the gap
        // between them — and every month of the second — at the first one's price.
        assertThrows(IllegalArgumentException.class, () -> subscriptions.reopen(first));
        assertEquals(before, feeCount());
    }

    @Test
    @DisplayName("shouldBillTheMonthsGainedWhenAnEndIsMovedLater")
    void shouldBillTheMonthsGainedWhenAnEndIsMovedLater() {
        UUID id = subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(3), monthsAgo(2))).id();

        subscriptions.end(id, new EndSubscriptionRequest(monthsAgo(0)));

        assertEquals(4, feeCount(), "A later end covers more months, and they owe their fee now");
    }

    @Test
    @DisplayName("shouldRefuseMovingAnEndOverALaterSubscription")
    void shouldRefuseMovingAnEndOverALaterSubscription() {
        UUID first = subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("300"), monthsAgo(5), monthsAgo(4))).id();
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("500"), monthsAgo(2), null));

        assertThrows(IllegalArgumentException.class, () -> subscriptions.end(
            first, new EndSubscriptionRequest(monthsAgo(1))));
    }

    @Test
    @DisplayName("shouldRefuseChangingTheAmountOfAnEndedSubscription")
    void shouldRefuseChangingTheAmountOfAnEndedSubscription() {
        UUID id = subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(2), monthsAgo(1))).id();

        // Forward-only on something with no forward: it would answer 204 and change nothing anybody
        // can see, except the price of a month restored later.
        assertThrows(IllegalArgumentException.class, () -> subscriptions.changeAmount(
            id, new ChangeSubscriptionAmountRequest(new BigDecimal("450"))));
    }

    @Test
    @DisplayName("shouldReleaseASessionFromASubscriptionThatNoLongerCoversIt")
    void shouldReleaseASessionFromASubscriptionThatNoLongerCoversIt() {
        UUID id = subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(2), null)).id();
        TimeSlot slot = bookedSlot(monthsAgo(1).plusDays(3));
        payouts.assignSource("slot", slot.getId(), new AssignPayoutSourceRequest(null, client.getId()));
        assertEquals(0, unpricedCount());

        subscriptions.end(id, new EndSubscriptionRequest(monthsAgo(2)));

        // The mark claimed a retainer that no longer reaches that month: the session earned
        // nothing and said so nowhere. Released, it is ordinary unpriced work again.
        assertNull(settlements.getSection("slot", slot.getId()).coveredBy());
        assertEquals(1, unpricedCount());
    }

    @Test
    @DisplayName("shouldReleaseCoveredSessionsWhenTheSubscriptionIsDeleted")
    void shouldReleaseCoveredSessionsWhenTheSubscriptionIsDeleted() {
        UUID id = subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(1), null)).id();
        TimeSlot slot = bookedSlot(monthsAgo(1).plusDays(3));
        payouts.assignSource("slot", slot.getId(), new AssignPayoutSourceRequest(null, client.getId()));

        subscriptions.delete(id);

        assertNull(settlements.getSection("slot", slot.getId()).coveredBy());
    }

    @Test
    @DisplayName("shouldSayWhenACoverageMarkNoLongerHolds")
    void shouldSayWhenACoverageMarkNoLongerHolds() {
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(1), null));
        TimeSlot slot = bookedSlot(monthsAgo(1).plusDays(3));
        payouts.assignSource("slot", slot.getId(), new AssignPayoutSourceRequest(null, client.getId()));
        assertNull(settlements.getSection("slot", slot.getId()).coveredBy().staleReason());

        // A second person booked after the mark: the session is out of per-participant pricing, so
        // their amount cannot be entered — the very dead end the mark-time guard refuses. The mark
        // is checked once, so the section has to say it no longer holds.
        reservationRepository.saveAndFlush(new Reservation(saveOtherUser(), slot));
        assertEquals("shared", settlements.getSection("slot", slot.getId()).coveredBy().staleReason());

        reservationRepository.deleteAll();
        assertEquals("not.participant",
            settlements.getSection("slot", slot.getId()).coveredBy().staleReason());
    }

    @Test
    @DisplayName("shouldNotPrefillASessionWithTheMonthlyFee")
    void shouldNotPrefillASessionWithTheMonthlyFee() {
        TimeSlot earlier = bookedSlot(monthsAgo(1).plusDays(3));
        settlements.save("slot", earlier.getId(), "user", client.getId(),
            new SaveSettlementRequest(new BigDecimal("150")));
        subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(0), null));
        // Pinned rather than left to the two clocks: the fee's created_at comes from the database's
        // now(), the session's from the JVM, and the test container's clock is not the JVM's.
        jdbc.update("UPDATE settlements SET created_at = now() + interval '1 hour' WHERE period_month IS NOT NULL");
        TimeSlot next = bookedSlot(LocalDate.now(AdminSubscriptionService.WARSAW).minusDays(1));

        // The fee is the newest charge on the 1st of every month, so the session field offered the
        // retainer price — 400 where the person pays 150 a session.
        SettlementLineDto line = settlements.getSection("slot", next.getId()).lines().getFirst();
        assertEquals(0, new BigDecimal("150.00").compareTo(line.suggestedAmount()));
    }

    @Test
    @DisplayName("shouldNotPretendToMoveTheStartDateWhenOnlyTheAmountCanChange")
    void shouldNotPretendToMoveTheStartDateWhenOnlyTheAmountCanChange() {
        UUID id = subscriptions.create(client.getId(), new SaveSubscriptionRequest(
            new BigDecimal("400"), monthsAgo(1), null)).id();

        // The request used to carry startedOn, which this operation ignores, and answered 204 — so
        // an admin correcting a mistyped start date was told it worked.
        subscriptions.changeAmount(id, new ChangeSubscriptionAmountRequest(new BigDecimal("500")));

        SubscriptionDto after = subscriptions.forUser(client.getId()).getFirst();
        assertEquals(monthsAgo(1), after.startedOn());
        assertEquals(0, new BigDecimal("500.00").compareTo(after.amount()));
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
