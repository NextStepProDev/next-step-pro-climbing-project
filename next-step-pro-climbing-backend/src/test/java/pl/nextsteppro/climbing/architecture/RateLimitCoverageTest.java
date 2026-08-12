package pl.nextsteppro.climbing.architecture;

import org.junit.jupiter.api.Test;
import pl.nextsteppro.climbing.config.RateLimitFilter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every controller base path must land in a rate-limit bucket, and land in the same one whether it
 * is called on the bare base or on a sub-path.
 *
 * <p>Both halves are drawn from the same incident. {@code /api/training-calendar} was listed in
 * {@link RateLimitFilter} and looked covered, but the rule tested {@code startsWith("/api/…/")} —
 * so the range endpoint, mapped on the bare base and by far the heaviest query in the feature,
 * carried no trailing slash and passed through unthrottled. Nothing about the rule looked wrong;
 * you had to hold the URI shape and the prefix in your head at the same time to see it.
 *
 * <p>The filter now denies by default, so a forgotten controller falls into the generic bucket
 * rather than through the filter. This gate is what turns "it gets some limit" into "somebody
 * chose its limit": a new base path that nobody thought about shows up here as the generic
 * bucket, at the moment it is added, rather than in an audit two years later.
 */
class RateLimitCoverageTest {

    private static final Path API_ROOT = Path.of("src/main/java/pl/nextsteppro/climbing/api");

    /** Class-level base path, e.g. {@code @RequestMapping("/api/reservations")}. */
    private static final Pattern CLASS_MAPPING = Pattern.compile(
        "@RequestMapping\\s*\\(\\s*(?:value\\s*=\\s*)?\"(/api[^\"]*)\"");

    /**
     * Bases whose traffic is deliberately generic rather than given a bucket of its own. Keep this
     * an explicit allowlist: an entry here is a decision someone made, an entry missing from the
     * rule table is an oversight, and the two must not look alike.
     */
    private static final List<String> INTENTIONALLY_GENERIC = List.of(
        "/api/dev" // dev profile only; never mapped in production
    );

    @Test
    void shouldGiveEveryControllerBasePathItsOwnRateLimitBucket() {
        List<String> bases = controllerBasePaths();

        for (String base : bases) {
            String bucket = RateLimitFilter.bucketFor(base);
            assertNotNull(bucket, base + " is not rate limited at all.");

            if (INTENTIONALLY_GENERIC.contains(base)) {
                continue;
            }
            assertTrue(!"default".equals(bucket), """
                %s falls into the generic "default" bucket, so nobody picked a limit for it.

                Add a rule for it in RateLimitFilter.RULES with a limit that fits what the endpoint
                actually costs (a write, a cached read, a file stream), or — if generic really is
                the right answer — say so by listing the base in INTENTIONALLY_GENERIC here.
                """.formatted(base));
        }
    }

    @Test
    void shouldCountTheBareBaseAndItsSubPathsIntoTheSameBucket() {
        for (String base : controllerBasePaths()) {
            String bareBucket = RateLimitFilter.bucketFor(base);
            String subPathBucket = RateLimitFilter.bucketFor(base + "/anything");

            assertEquals(subPathBucket, bareBucket, """
                %s is counted into "%s" on the bare path but "%s" on a sub-path.

                An endpoint mapped on the bare base carries no trailing slash, so a rule written as
                startsWith(base + "/") misses it entirely — that is how the training calendar range
                query ran unthrottled. Match with under(path, base), which accepts both.
                """.formatted(base, bareBucket, subPathBucket));
        }
    }

    /** Google sign-in is a sign-in attempt, and it does not live under /api. */
    @Test
    void shouldThrottleTheOauth2LoginPathsAsAuthentication() {
        assertEquals("auth", RateLimitFilter.bucketFor("/oauth2/authorization/google"));
        assertEquals("auth", RateLimitFilter.bucketFor("/login/oauth2/code/google"));
    }

    /**
     * The container healthcheck polls this every few seconds from one address. Throttling it would
     * flap the container to unhealthy, which is why the catch-all is scoped to /api.
     */
    @Test
    void shouldLeaveTheHealthEndpointUnthrottled() {
        assertNull(RateLimitFilter.bucketFor("/actuator/health"));
    }

    @Test
    void shouldFindTheControllersItClaimsToCheck() {
        // Guards the gate itself: if the regex or the path goes stale, every assertion above
        // passes over an empty list and this file becomes indistinguishable from one that checks
        // nothing.
        List<String> bases = controllerBasePaths();
        assertTrue(bases.size() >= 15,
            "Expected at least 15 controller base paths, found " + bases.size() + " " + bases
                + ". The mapping regex or the api/ source path is probably stale.");
    }

    private static List<String> controllerBasePaths() {
        List<String> bases = new ArrayList<>();
        for (Path file : controllerSources()) {
            Matcher matcher = CLASS_MAPPING.matcher(SourceFiles.readWithoutComments(file));
            while (matcher.find()) {
                String base = matcher.group(1);
                if (!bases.contains(base)) {
                    bases.add(base);
                }
            }
        }
        return bases;
    }

    private static List<Path> controllerSources() {
        return SourceFiles.mainJavaFiles().stream()
            .filter(path -> path.startsWith(API_ROOT))
            .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
            .toList();
    }
}
