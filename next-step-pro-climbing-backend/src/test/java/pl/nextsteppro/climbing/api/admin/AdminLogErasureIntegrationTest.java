package pl.nextsteppro.climbing.api.admin;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogDto;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.api.user.UserService;
import pl.nextsteppro.climbing.domain.activitylog.ActivityLogRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Right to be forgotten (GDPR art. 17) in the admin's activity log.
 *
 * <p>ADMIN_* entries are filed under the admin who acted, so the cascade from deleting an account
 * never reaches them. Before V102 their description carried "Name (e-mail)" of the person the
 * action was about, and deleting that person left it there for good. What is pinned down here is
 * the outcome, read straight from the table: after EITHER deletion path, no entry names the person
 * — while the entries themselves (who did what, when) survive.
 */
class AdminLogErasureIntegrationTest extends BaseIntegrationTest {

    private static final String EMAIL = "zapomniana@example.com";
    private static final String LAST_NAME = "Zapomniana";

    @Autowired private AdminService adminService;
    @Autowired private UserService userService;
    @Autowired private ActivityLogService activityLogService;
    @Autowired private ActivityLogRepository activityLogRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User admin;
    private User person;

    @BeforeEach
    void setUp() {
        activityLogRepository.deleteAll();
        userRepository.deleteAll();

        admin = new User("coach@example.com", "Coach", "Admin", "+48100000000", "coach");
        admin.setPasswordHash("hash");
        admin.setRole(UserRole.ADMIN);
        admin = userRepository.save(admin);

        // No password: an OAuth-shaped account, so self-deletion needs none
        person = new User(EMAIL, "Anna", LAST_NAME, "+48200000000", "anna");
        person.setRole(UserRole.USER);
        person.setAthlete(true);
        person = userRepository.save(person);

        activityLogService.logAdminUserForceLogout(admin, person);
        activityLogService.logAdminUserAthleteToggled(admin, person, true);
        activityLogService.logAdminTrainingCreated(admin, person, "2026-10-20 Siła");
        activityLogService.logAdminUserDataExported(admin, person);
        // Separate requests in production: the deletion does not find these entries already
        // managed. Leaving them in the context would make Hibernate trip over its own stale
        // references to the deleted account, which is a property of the test, not of the code.
        entityManager.flush();
        entityManager.clear();
        admin = userRepository.findById(admin.getId()).orElseThrow();
        person = userRepository.findById(person.getId()).orElseThrow();
    }

    @Test
    void shouldNameThePersonWhileTheAccountExists() {
        List<ActivityLogDto> logs = activityLogService.getRecentLogs(0, 20);

        assertEquals(4, logs.size());
        assertTrue(logs.stream().allMatch(l -> EMAIL.equals(l.subjectEmail()) && !l.subjectErased()));
    }

    @Test
    void shouldEraseThePersonFromTheAdminLogWhenAnAdminDeletesTheAccount() {
        adminService.deleteUser(admin.getId(), person.getId());

        assertErased(5); // the four entries + the deletion itself
    }

    @Test
    void shouldEraseThePersonFromTheAdminLogWhenTheyDeleteTheirOwnAccount() {
        userService.deleteAccount(person.getId(), null);

        assertErased(4);
    }

    private void assertErased(int expectedEntries) {
        entityManager.flush();
        entityManager.clear();

        // Raw table, not the DTO: what matters is what is STORED, not what a screen chooses to show
        List<String> stored = jdbcTemplate.queryForList(
            "SELECT coalesce(description, '') || ' ' || coalesce(subject_user_id::text, '') FROM activity_logs",
            String.class);
        assertEquals(expectedEntries, stored.size(), "the entries themselves are the admin's accountability record");
        for (String row : stored) {
            assertFalse(row.contains(EMAIL), row);
            assertFalse(row.contains(LAST_NAME), row);
            assertFalse(row.contains(person.getId().toString()), row);
        }

        List<ActivityLogDto> logs = activityLogService.getRecentLogs(0, 20);
        assertTrue(logs.stream().allMatch(ActivityLogDto::subjectErased));
        assertTrue(logs.stream().noneMatch(l -> l.subjectEmail() != null || l.subjectFullName() != null));
        // Context that names nobody stays — the admin can still tell what was done
        assertTrue(logs.stream().anyMatch(l -> "2026-10-20 Siła".equals(l.description())));
    }
}
