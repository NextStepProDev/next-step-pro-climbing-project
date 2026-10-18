package pl.nextsteppro.climbing.api.mysubscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client's own subscription card. Seeded straight in SQL rather than through the admin
 * service: this package must not depend on the admin one, and the test should not either.
 */
class MySubscriptionIntegrationTest extends BaseIntegrationTest {

    private static final LocalDate THIS_MONTH = LocalDate.now(ZoneId.of("Europe/Warsaw")).withDayOfMonth(1);

    @Autowired private MySubscriptionService service;
    @Autowired private JdbcTemplate jdbc;

    private User client;
    private User other;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM subscription_skipped_months");
        jdbc.update("DELETE FROM session_payouts");
        jdbc.update("DELETE FROM subscriptions");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM settlements");
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();
        client = save("client@example.com");
        other = save("other@example.com");
    }

    private User save(String email) {
        User u = new User(email, "Anna", "Kowalska", "+48123456789", email.substring(0, 5));
        u.setRole(UserRole.USER);
        u.setEmailVerified(true);
        return userRepository.saveAndFlush(u);
    }

    private void subscription(User user, LocalDate start, LocalDate end, String amount) {
        jdbc.update("INSERT INTO subscriptions (user_id, amount, started_on, ended_on) VALUES (?, ?, ?, ?)",
            user.getId(), new BigDecimal(amount), start, end);
    }

    private void fee(User user, LocalDate month, String amount) {
        jdbc.update("INSERT INTO settlements (user_id, period_month, amount, updated_at) VALUES (?, ?, ?, now())",
            user.getId(), month, new BigDecimal(amount));
    }

    private void payment(User user, String amount, LocalDate on) {
        jdbc.update("INSERT INTO payments (user_id, amount, received_on) VALUES (?, ?, ?)",
            user.getId(), new BigDecimal(amount), on);
    }

    @Test
    void shouldBeEmptyForSomebodyWhoNeverHadOne() {
        MySubscriptionDto mine = service.forUser(client.getId());

        assertNull(mine.current(), "The card is not shown at all");
        assertTrue(mine.months().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(mine.due()));
    }

    @Test
    void shouldApplyPaymentsOldestFirstLikeTheCoachSees() {
        subscription(client, THIS_MONTH.minusMonths(2), null, "400");
        fee(client, THIS_MONTH.minusMonths(2), "400");
        fee(client, THIS_MONTH.minusMonths(1), "400");
        fee(client, THIS_MONTH, "400");
        payment(client, "600", THIS_MONTH);

        MySubscriptionDto mine = service.forUser(client.getId());

        assertEquals(0, new BigDecimal("600.00").compareTo(mine.due()));
        assertEquals(THIS_MONTH, mine.months().getFirst().month(), "Newest first");
        MonthFeeDto oldest = mine.months().getLast();
        assertEquals(0, oldest.remaining().signum(), "The oldest month is paid first");
        assertEquals(0, new BigDecimal("200.00").compareTo(mine.months().get(1).covered()));
        assertTrue(mine.current().active());
    }

    @Test
    void shouldListAnOldUnpaidMonthButNotAnOldPaidOne() {
        subscription(client, THIS_MONTH.minusMonths(20), null, "100");
        fee(client, THIS_MONTH.minusMonths(20), "100");
        fee(client, THIS_MONTH.minusMonths(19), "100");
        payment(client, "100", THIS_MONTH.minusMonths(20));

        MySubscriptionDto mine = service.forUser(client.getId());

        assertEquals(1, mine.months().size(), "Paid and older than a year: history, not news");
        assertEquals(THIS_MONTH.minusMonths(19), mine.months().getFirst().month());
    }

    @Test
    void shouldShowAWaivedMonthAsWaived() {
        subscription(client, THIS_MONTH.minusMonths(1), null, "400");
        fee(client, THIS_MONTH, "400");
        jdbc.update("INSERT INTO subscription_skipped_months (user_id, period_month) VALUES (?, ?)",
            client.getId(), THIS_MONTH.minusMonths(1));

        MySubscriptionDto mine = service.forUser(client.getId());

        MonthFeeDto waived = mine.months().getLast();
        assertTrue(waived.skipped());
        assertNull(waived.amount());
        assertEquals(0, new BigDecimal("400.00").compareTo(mine.due()));
    }

    @Test
    void shouldNeverShowSomebodyElsesMoney() {
        subscription(other, THIS_MONTH, null, "999");
        fee(other, THIS_MONTH, "999");

        MySubscriptionDto mine = service.forUser(client.getId());

        assertNull(mine.current());
        assertTrue(mine.months().isEmpty());
        assertFalse(service.forUser(other.getId()).months().isEmpty());
    }

    @Test
    void shouldShowAnEndedSubscriptionAsEnded() {
        UUID id = client.getId();
        subscription(client, THIS_MONTH.minusMonths(3), THIS_MONTH.minusMonths(2), "300");

        CurrentSubscriptionDto current = service.forUser(id).current();

        assertFalse(current.active());
        assertEquals(THIS_MONTH.minusMonths(2), current.endedOn());
    }
}
