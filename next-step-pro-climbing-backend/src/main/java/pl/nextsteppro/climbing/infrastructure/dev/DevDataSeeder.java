package pl.nextsteppro.climbing.infrastructure.dev;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.domain.user.UserRole;

import java.time.Instant;

/**
 * Seeds test data for local development.
 * ONLY runs when spring.profiles.active=dev
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String @NonNull ... args) {
        seedTestAdmin();
    }

    private void seedTestAdmin() {
        String testEmail = "djdefkon@gmail.com";

        if (userRepository.findByEmail(testEmail).isPresent()) {
            log.info("Test admin already exists: {}", testEmail);
            return;
        }

        User testAdmin = new User(
            testEmail,
            "Admin",
            "Test",
            "+48123456789",
            "admin"
        );
        testAdmin.setRole(UserRole.ADMIN);
        testAdmin.setPasswordHash(passwordEncoder.encode("password"));
        testAdmin.setEmailVerified(true);
        testAdmin.setEmailVerifiedAt(Instant.now());
        testAdmin.setEmailNotificationsEnabled(true);

        userRepository.save(testAdmin);
        log.info("✅ Created test admin: {} / password: password", testEmail);
    }
}
