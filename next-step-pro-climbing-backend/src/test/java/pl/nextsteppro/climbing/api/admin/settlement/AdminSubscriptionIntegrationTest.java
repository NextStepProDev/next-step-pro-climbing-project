package pl.nextsteppro.climbing.api.admin.settlement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    @Autowired private JdbcTemplate jdbc;

    private User client;

    @BeforeEach
    void setUp() {
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
        jdbc.update("UPDATE settlements SET settled_on = ? WHERE period_month = ?",
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
