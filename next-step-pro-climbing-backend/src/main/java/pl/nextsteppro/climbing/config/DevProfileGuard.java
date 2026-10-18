package pl.nextsteppro.climbing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;

/**
 * Refuses to start the {@code dev} profile anywhere but on a developer's machine.
 *
 * <p>⚠️ {@code application.yml} defaults the profile to {@code dev} when {@code SPRING_PROFILES_ACTIVE}
 * is missing — convenient for {@code bootRun} and IntelliJ, and the reason production is safe only
 * because the image sets {@code prod}. {@code dev} switches on password-less login
 * ({@code DevAuthController}), the seeded admin and a JWT secret printed in this repository. An
 * image or compose file that ever loses the variable would publish all three, and nothing would look
 * wrong. This makes that a startup failure instead: dev with a non-local base URL is a
 * misconfiguration by definition.
 */
@Component
@Profile("dev")
class DevProfileGuard {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "0.0.0.0", "host.docker.internal");

    // The RAW environment variables, not app.base-url / app.site-url: application-dev.yml overrides
    // those to localhost unconditionally, so a guard reading them would always pass. Production
    // sets these variables in docker-compose.prod.yml; a developer's .env does not.
    DevProfileGuard(@Value("${APP_BASE_URL:}") String baseUrl, @Value("${SITE_URL:}") String siteUrl) {
        if (!baseUrl.isBlank()) requireLocal("APP_BASE_URL", baseUrl);
        for (String origin : siteUrl.split(",")) {
            if (!origin.isBlank()) requireLocal("SITE_URL", origin.trim());
        }
    }

    static void requireLocal(String property, String url) {
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            host = null;
        }
        if (host == null || !LOCAL_HOSTS.contains(host)) {
            throw new IllegalStateException("The 'dev' profile is active with " + property + "=" + url
                + " — dev enables password-less login and a public JWT secret, so it may only run "
                + "locally. Set SPRING_PROFILES_ACTIVE=prod.");
        }
    }
}
