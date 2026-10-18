package pl.nextsteppro.climbing.api.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.AfterEach;
import pl.nextsteppro.climbing.api.auth.AuthDtos.AuthTokensResponse;
import pl.nextsteppro.climbing.api.auth.AuthDtos.LoginRequest;
import pl.nextsteppro.climbing.api.auth.AuthDtos.RefreshTokenRequest;
import pl.nextsteppro.climbing.api.user.UserService;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a session ends, and that it really does — against the database, because every one of these
 * is a write that a rollback could silently undo.
 *
 * <p>⚠️ NOT inside the base class's per-test transaction: the lockout count is written in a
 * {@code REQUIRES_NEW} transaction, which cannot see a user the test inserted but never committed.
 * So this class commits for real and cleans up after itself.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SessionRevocationIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "Korona-Gor-2026!";

    @Autowired private AuthService authService;
    @Autowired private OAuthExchangeCodes exchangeCodes;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private User user;

    @BeforeEach
    void setUp() {
        authTokenRepository.deleteAll();
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        userRepository.deleteAll();
        User u = new User("climber@example.com", "Ola", "Skała", "+48123456789", "ola");
        u.setRole(UserRole.USER);
        u.setEmailVerified(true);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user = userRepository.saveAndFlush(u);
    }

    @AfterEach
    void tearDown() {
        authTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private AuthTokensResponse login() {
        return authService.login(new LoginRequest("climber@example.com", PASSWORD));
    }

    private AuthTokensResponse refresh(String token) {
        return authService.refreshTokens(new RefreshTokenRequest(token));
    }

    @Test
    void shouldEndTheSessionOnTheServerWhenLoggingOut() {
        AuthTokensResponse session = login();

        authService.logout(session.refreshToken());

        // Before: logout only cleared the browser, and a copied refresh token lived seven more days.
        assertThrows(IllegalArgumentException.class, () -> refresh(session.refreshToken()));
    }

    @Test
    void shouldEndEveryOtherSessionWhenThePasswordChanges() {
        AuthTokensResponse otherDevice = login();

        AuthTokensResponse thisDevice = userService.changePassword(user.getId(), PASSWORD, "Nowe-Haslo-Wspin-2026!");

        assertThrows(IllegalArgumentException.class, () -> refresh(otherDevice.refreshToken()),
            "A stolen session must not outlive the password change meant to end it");
        assertDoesNotThrow(() -> refresh(thisDevice.refreshToken()), "This device carries on");
    }

    @Test
    void shouldEndEverySessionWhenARotatedRefreshTokenIsReplayed() {
        AuthTokensResponse first = login();
        AuthTokensResponse rotated = refresh(first.refreshToken());
        // Past the 30 s grace window that lets several tabs refresh at once
        jdbc.update("UPDATE auth_tokens SET used_at = now() - interval '5 minutes' WHERE used_at IS NOT NULL");

        assertThrows(IllegalArgumentException.class, () -> refresh(first.refreshToken()));

        // The replay is the theft signal: the session it was rotated INTO goes too, so whoever holds
        // the newer token (thief or owner) has to log in again.
        assertThrows(IllegalArgumentException.class, () -> refresh(rotated.refreshToken()));
    }

    @Test
    void shouldNotTreatAnUnknownTokenAsReuse() {
        AuthTokensResponse session = login();
        UUID nobody = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> refresh("not-a-token-" + nobody));

        assertDoesNotThrow(() -> refresh(session.refreshToken()));
    }

    @Test
    void shouldSwapAGoogleCodeForTokensExactlyOnce() {
        String code = exchangeCodes.issue(user);

        AuthTokensResponse tokens = exchangeCodes.exchange(code);

        assertNotNull(tokens.refreshToken());
        assertThrows(IllegalArgumentException.class, () -> exchangeCodes.exchange(code));
    }

    @Test
    void shouldRefuseAnExpiredGoogleCode() {
        String code = exchangeCodes.issue(user);
        jdbc.update("UPDATE auth_tokens SET expires_at = now() - interval '1 second' WHERE token_type = 'OAUTH_EXCHANGE'");

        assertThrows(IllegalArgumentException.class, () -> exchangeCodes.exchange(code));
    }

    @Test
    void shouldLockTheAccountAfterFiveWrongCurrentPasswords() {
        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalStateException.class,
                () -> userService.changePassword(user.getId(), "zgadywane", "Nowe-Haslo-Wspin-2026!"));
        }

        // The count survived the five refusals that announced it — written in its own transaction.
        User stored = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(5, stored.getFailedLoginAttempts());
        assertTrue(stored.isAccountLocked());
        // Locked means locked: even the right password is refused, here and at login.
        assertThrows(IllegalStateException.class,
            () -> userService.changePassword(user.getId(), PASSWORD, "Nowe-Haslo-Wspin-2026!"));
        assertThrows(IllegalStateException.class, this::login);
    }

    @Test
    void shouldCountAWrongPasswordOnAccountDeletionToo() {
        assertThrows(IllegalStateException.class, () -> userService.deleteAccount(user.getId(), "zgadywane"));

        assertEquals(1, userRepository.findById(user.getId()).orElseThrow().getFailedLoginAttempts());
    }
}
