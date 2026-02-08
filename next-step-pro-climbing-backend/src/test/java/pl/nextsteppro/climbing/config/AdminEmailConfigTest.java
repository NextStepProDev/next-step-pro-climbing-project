package pl.nextsteppro.climbing.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AdminEmailConfig Tests")
class AdminEmailConfigTest {

    @Test
    @DisplayName("should match single admin email")
    void shouldMatchSingleAdminEmail() {
        AdminEmailConfig config = createConfig("admin@example.com");

        assertTrue(config.isAdminEmail("admin@example.com"));
        assertFalse(config.isAdminEmail("user@example.com"));
    }

    @Test
    @DisplayName("should match multiple comma-separated admin emails")
    void shouldMatchMultipleAdminEmails() {
        AdminEmailConfig config = createConfig("admin1@example.com,admin2@example.com");

        assertTrue(config.isAdminEmail("admin1@example.com"));
        assertTrue(config.isAdminEmail("admin2@example.com"));
        assertFalse(config.isAdminEmail("user@example.com"));
    }

    @Test
    @DisplayName("should match case-insensitively")
    void shouldMatchCaseInsensitively() {
        AdminEmailConfig config = createConfig("Admin@Example.COM");

        assertTrue(config.isAdminEmail("admin@example.com"));
        assertTrue(config.isAdminEmail("ADMIN@EXAMPLE.COM"));
        assertTrue(config.isAdminEmail("Admin@Example.com"));
    }

    @Test
    @DisplayName("should trim whitespace around emails")
    void shouldTrimWhitespace() {
        AdminEmailConfig config = createConfig("  admin1@example.com , admin2@example.com  ");

        assertTrue(config.isAdminEmail("admin1@example.com"));
        assertTrue(config.isAdminEmail("admin2@example.com"));
    }

    @Test
    @DisplayName("should handle empty config")
    void shouldHandleEmptyConfig() {
        AdminEmailConfig config = createConfig("");

        assertFalse(config.isAdminEmail("admin@example.com"));
    }

    @Test
    @DisplayName("should handle blank config")
    void shouldHandleBlankConfig() {
        AdminEmailConfig config = createConfig("   ");

        assertFalse(config.isAdminEmail("admin@example.com"));
    }

    @Test
    @DisplayName("should handle null email input")
    void shouldHandleNullEmailInput() {
        AdminEmailConfig config = createConfig("admin@example.com");

        assertFalse(config.isAdminEmail(null));
    }

    @Test
    @DisplayName("should trim input email before matching")
    void shouldTrimInputEmail() {
        AdminEmailConfig config = createConfig("admin@example.com");

        assertTrue(config.isAdminEmail("  admin@example.com  "));
    }

    private AdminEmailConfig createConfig(String adminEmail) {
        AppConfig appConfig = new AppConfig();
        appConfig.getAdmin().setEmail(adminEmail);
        return new AdminEmailConfig(appConfig);
    }
}
