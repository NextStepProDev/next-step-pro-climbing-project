package pl.nextsteppro.climbing.api.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.nextsteppro.climbing.api.auth.AccountConfirmation;
import pl.nextsteppro.climbing.domain.activitylog.ActivityActionType;
import pl.nextsteppro.climbing.domain.activitylog.ActivityLogRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The admin panel badge for newly confirmed accounts.
 *
 * <p>Reads the counter through {@link AdminService#getNotifications} rather than the repository
 * method behind it, because what is worth pinning down is the pairing: which marker the count is
 * measured against, and that clearing that marker — and only that one — makes the badge go away.
 *
 * <p>Lives in this package rather than next to the other integration tests because
 * {@code AdminNotificationsDto} is package-private.
 */
class AdminNewAccountBadgeIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AdminService adminService;

    @Autowired
    private AccountConfirmation accountConfirmation;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    private UUID adminId;

    @BeforeEach
    void setUp() {
        activityLogRepository.deleteAll();
        userRepository.deleteAll();

        User admin = new User("coach@example.com", "Coach", "Admin", "+48100000000", "coach");
        admin.setPasswordHash("hash");
        admin.setRole(UserRole.ADMIN);
        adminId = userRepository.save(admin).getId();
        // The admin's own account is not what these tests are about; start them from a read panel
        // so the assertions below are not each off by one for a reason no reader could guess.
        adminService.markUsersSeen(adminId);
    }

    /** Saves first, then confirms: the activity entry points at the account, which has to exist. */
    private User confirmedClient(String email, AccountConfirmation.ConfirmationSource source) {
        User user = new User(email, "New", "Client", "+48200000000", email.substring(0, email.indexOf('@')));
        user.setPasswordHash("hash");
        user.setRole(UserRole.USER);
        User saved = userRepository.save(user);
        accountConfirmation.confirm(saved, source);
        return saved;
    }

    @Test
    void shouldCountAnAccountConfirmedAfterTheMarker() {
        confirmedClient("fresh@example.com", AccountConfirmation.ConfirmationSource.EMAIL_LINK);

        assertEquals(1, adminService.getNotifications(adminId).newUsers());
    }

    @Test
    void shouldCountAnAccountAnOauthProviderVouchedFor() {
        // A Google sign-up never sees a confirmation link, so a counter wired to that link alone
        // would silently miss an entire route into the service.
        confirmedClient("google@example.com", AccountConfirmation.ConfirmationSource.OAUTH);

        assertEquals(1, adminService.getNotifications(adminId).newUsers());
    }

    @Test
    void shouldNotCountAnAccountThatNeverConfirmed() {
        // No email_verified_at, so there is nothing to announce — nobody can sign in to it yet.
        User pending = new User("pending@example.com", "Pen", "Ding", "+48300000000", "pending");
        pending.setPasswordHash("hash");
        pending.setRole(UserRole.USER);
        userRepository.save(pending);

        assertEquals(0, adminService.getNotifications(adminId).newUsers());
    }

    @Test
    void shouldStopCountingOnceTheAdminHasReadThem() {
        confirmedClient("fresh@example.com", AccountConfirmation.ConfirmationSource.EMAIL_LINK);

        adminService.markUsersSeen(adminId);

        assertEquals(0, adminService.getNotifications(adminId).newUsers());
    }

    @Test
    void shouldKeepTheUsersMarkerIndependentOfTheReservationsOne() {
        // Two counters, two markers: opening the Reservations tab must not swallow a new account
        // the admin has not looked at.
        confirmedClient("fresh@example.com", AccountConfirmation.ConfirmationSource.EMAIL_LINK);

        adminService.markReservationsSeen(adminId);

        assertEquals(1, adminService.getNotifications(adminId).newUsers());
    }

    @Test
    void shouldRecordConfirmationOnTheUsersOwnTimeline() {
        // The actor is the user, not an admin — that is what puts the entry on their own card.
        UUID userId = confirmedClient("fresh@example.com", AccountConfirmation.ConfirmationSource.OAUTH).getId();

        assertTrue(activityLogRepository.findAll().stream()
                .anyMatch(log -> log.getActionType() == ActivityActionType.USER_ACCOUNT_CONFIRMED
                    && log.getUser().getId().equals(userId)
                    && "Google".equals(log.getDescription())),
            "A confirmed account must leave an entry the admin timeline can show");
    }
}
