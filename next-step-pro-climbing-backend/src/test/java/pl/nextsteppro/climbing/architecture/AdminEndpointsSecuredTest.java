package pl.nextsteppro.climbing.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code SecurityConfig} already guards {@code /api/admin/**} with {@code hasRole("ADMIN")}, and
 * every admin controller also carries a class-level {@code @PreAuthorize}. Belt and braces on
 * purpose: the URL rule protects by path, the annotation protects by class, and a controller that
 * is later remapped to a different base keeps its guard.
 *
 * <p>Selecting by mapped path rather than by package matters — {@code AdminTrainingCalendarController}
 * lives in {@code api/trainingcalendar/} (it shares package-private DTOs with the athlete side),
 * so a package-based check would silently skip the admin surface of the whole training calendar.
 */
class AdminEndpointsSecuredTest {

    private static final Pattern REQUEST_MAPPING = Pattern.compile("@RequestMapping\\(\"([^\"]+)\"\\)");

    @Test
    void shouldGuardEveryAdminControllerWithPreAuthorize() {
        List<String> unguarded = new ArrayList<>();
        int checked = 0;

        for (Path file : SourceFiles.mainJavaFiles()) {
            String source = SourceFiles.readWithoutComments(file);
            if (!source.contains("@RestController")) {
                continue;
            }
            Matcher mapping = REQUEST_MAPPING.matcher(source);
            if (!mapping.find() || !mapping.group(1).startsWith("/api/admin")) {
                continue;
            }
            checked++;
            if (!source.contains("@PreAuthorize")) {
                unguarded.add(file + "  (mapped to " + mapping.group(1) + ")");
            }
        }

        assertTrue(checked > 0, "Gate matched no admin controllers at all — the detection broke, "
            + "which is worse than a failing assertion because it passes silently.");
        assertTrue(unguarded.isEmpty(),
            "Admin controllers without @PreAuthorize:\n" + String.join("\n", unguarded));
    }

    @Test
    void shouldKeepTheTrainingCalendarAdminControllerInScope() {
        // Pins the case the package-based version of this gate would miss. If this controller is
        // ever moved or renamed, this fails and whoever moved it re-points the gate deliberately.
        Path file = Path.of("src/main/java/pl/nextsteppro/climbing/api/trainingcalendar/"
            + "AdminTrainingCalendarController.java");
        String source = SourceFiles.readWithoutComments(file);

        assertTrue(source.contains("@PreAuthorize"),
            "AdminTrainingCalendarController lost its class-level @PreAuthorize");
        assertTrue(source.contains("/api/admin/training-calendar"),
            "AdminTrainingCalendarController is no longer mapped under /api/admin — re-check the gate above");
    }

    @Test
    void shouldNotExposeDevAuthControllerOutsideTheDevProfile() {
        // The dev login shortcut mints tokens without credentials. It is fenced off by
        // @Profile("dev") plus a profile check in SecurityConfig; losing either would be severe.
        Path file = Path.of("src/main/java/pl/nextsteppro/climbing/api/dev/DevAuthController.java");
        String source = SourceFiles.readWithoutComments(file);

        assertTrue(source.contains("@Profile(\"dev\")"),
            "DevAuthController must stay annotated @Profile(\"dev\") — it issues tokens without a password");

        String securityConfig = SourceFiles.readWithoutComments(
            Path.of("src/main/java/pl/nextsteppro/climbing/config/SecurityConfig.java"));
        assertFalse(securityConfig.contains("\"/api/dev/**\").permitAll()")
                && !securityConfig.contains("contains(\"dev\")"),
            "/api/dev/** is permitAll without a dev-profile check in SecurityConfig");
    }
}
