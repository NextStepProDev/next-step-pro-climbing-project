package pl.nextsteppro.climbing.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
