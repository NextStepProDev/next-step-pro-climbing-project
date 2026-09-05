package pl.nextsteppro.climbing.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import pl.nextsteppro.climbing.domain.auth.AuthTokenRepository;
import pl.nextsteppro.climbing.domain.event.EventRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;
import pl.nextsteppro.climbing.domain.user.UserRepository;

/**
 * Base class for integration tests with Testcontainers PostgreSQL setup.
 *
 * Provides:
 * - PostgreSQL 17 container
 * - All repositories auto-wired
 * - Transactional test isolation
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    @MockitoBean
    JavaMailSender javaMailSender;

    // Singleton pattern: one container for the entire JVM, shared across all subclasses.
    // @Testcontainers + @Container would stop the container after each test class finishes,
    // causing connection errors when subsequent test classes try to reuse the Spring context.
    //
    // ⚠️ This version must match the one production runs (hub/docker-compose.prod.yml), and nothing
    // enforces it: no Dependabot ecosystem reads Java source, so this string never moves on its own.
    // It already drifted once — production was bumped to 18 while this said 17, which quietly turns
    // 1300 integration tests into evidence about a database the application does not run on.
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.clean-on-validation-error", () -> "true");
    }

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected TimeSlotRepository timeSlotRepository;

    @Autowired
    protected EventRepository eventRepository;

    @Autowired
    protected ReservationRepository reservationRepository;

    @Autowired
    protected AuthTokenRepository authTokenRepository;
}
