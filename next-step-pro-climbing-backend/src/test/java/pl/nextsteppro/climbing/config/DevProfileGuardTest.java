package pl.nextsteppro.climbing.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DevProfileGuardTest {

    @Test
    void shouldRefuseToStartDevWithTheProductionAddress() {
        // The image losing SPRING_PROFILES_ACTIVE would fall back to dev — password-less login and a
        // JWT secret printed in the repo, served from nextsteppro.pl. That must not start.
        assertThrows(IllegalStateException.class, () -> new DevProfileGuard("https://nextsteppro.pl", ""));
        assertThrows(IllegalStateException.class,
            () -> new DevProfileGuard("", "http://localhost:5173,https://www.nextsteppro.pl"));
    }

    @Test
    void shouldStartDevOnADevelopersMachine() {
        assertDoesNotThrow(() -> new DevProfileGuard("", ""));
        assertDoesNotThrow(() -> new DevProfileGuard("http://localhost:8080", "http://localhost:5173"));
    }
}
