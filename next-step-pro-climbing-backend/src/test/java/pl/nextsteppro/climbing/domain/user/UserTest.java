package pl.nextsteppro.climbing.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("User Entity Tests")
class UserTest {

    @Test
    @DisplayName("should create user with required fields")
    void shouldCreateUserWithRequiredFields() {
        User user = new User("test@example.com", "Jan", "Kowalski", "+48123456789", "jank");

        assertNotNull(user);
        assertEquals("test@example.com", user.getEmail());
        assertEquals("Jan", user.getFirstName());
        assertEquals("Kowalski", user.getLastName());
        assertEquals("+48123456789", user.getPhone());
        assertEquals("jank", user.getNickname());
        assertEquals(UserRole.USER, user.getRole());
        assertFalse(user.isAdmin());
    }

    @Test
    @DisplayName("should return full name")
    void shouldReturnFullName() {
        User user = new User("test@example.com", "Jan", "Kowalski", "+48123456789", "jank");
        assertEquals("Jan Kowalski", user.getFullName());
    }

    @Test
    @DisplayName("should correctly identify admin user")
    void shouldCorrectlyIdentifyAdminUser() {
        User user = new User("admin@example.com", "Admin", "Test", "+48123456789", "admin");
        user.setRole(UserRole.ADMIN);

        assertTrue(user.isAdmin());
        assertEquals(UserRole.ADMIN, user.getRole());
    }

    @Test
    @DisplayName("should update phone and nickname")
    void shouldUpdatePhoneAndNickname() {
        User user = new User("test@example.com", "Jan", "Kowalski", "+48111111111", "oldnick");

        user.setPhone("+48999999999");
        user.setNickname("newnick");

        assertEquals("+48999999999", user.getPhone());
        assertEquals("newnick", user.getNickname());
    }

    @Test
    @DisplayName("should set OAuth provider and ID")
    void shouldSetOAuthProviderAndId() {
        User user = new User("test@example.com", "Jan", "Kowalski", "+48123456789", "jank");

        user.setOauthProvider("google");
        user.setOauthId("google-123456");

        assertEquals("google", user.getOauthProvider());
        assertEquals("google-123456", user.getOauthId());
    }

    @Test
    @DisplayName("should lock account after five consecutive failed attempts")
    void shouldLockAccountAfterFiveConsecutiveFailedAttempts() {
        User user = new User("test@example.com", "Jan", "Kowalski", "+48123456789", "jank");

        for (int i = 0; i < 5; i++) {
            user.incrementFailedLoginAttempts();
        }

        assertEquals(5, user.getFailedLoginAttempts());
        assertTrue(user.isAccountLocked());
    }

    @Test
    @DisplayName("should start a fresh attempt count when the previous lockout has expired")
    void shouldStartFreshCountWhenPreviousLockoutExpired() {
        User user = new User("test@example.com", "Jan", "Kowalski", "+48123456789", "jank");

        // Given: account locked at t0 after 5 failed attempts
        Instant t0 = Instant.now();
        for (int i = 0; i < 5; i++) {
            user.incrementFailedLoginAttempts(t0);
        }
        assertEquals(5, user.getFailedLoginAttempts());

        // When: a single wrong password arrives AFTER the 15-minute lockout window
        Instant afterExpiry = t0.plus(16, ChronoUnit.MINUTES);
        user.incrementFailedLoginAttempts(afterExpiry);

        // Then: it counts as the first attempt of a fresh window and does NOT re-lock
        assertEquals(1, user.getFailedLoginAttempts());
        assertFalse(user.isAccountLocked());
    }

    @Test
    @DisplayName("should keep counting up while the lockout is still active")
    void shouldKeepCountingWhileLockoutStillActive() {
        User user = new User("test@example.com", "Jan", "Kowalski", "+48123456789", "jank");

        Instant t0 = Instant.now();
        for (int i = 0; i < 5; i++) {
            user.incrementFailedLoginAttempts(t0);
        }

        // A wrong password 5 minutes in (lockout still active) must not reset the counter
        user.incrementFailedLoginAttempts(t0.plus(5, ChronoUnit.MINUTES));

        assertEquals(6, user.getFailedLoginAttempts());
        assertTrue(user.isAccountLocked());
    }

    @Test
    @DisplayName("should clear lockout state on successful login reset")
    void shouldClearLockoutStateOnReset() {
        User user = new User("test@example.com", "Jan", "Kowalski", "+48123456789", "jank");

        for (int i = 0; i < 5; i++) {
            user.incrementFailedLoginAttempts();
        }
        assertTrue(user.isAccountLocked());

        user.resetFailedLoginAttempts();

        assertEquals(0, user.getFailedLoginAttempts());
        assertFalse(user.isAccountLocked());
        assertNull(user.getLockedUntil());
    }
}
