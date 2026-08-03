package pl.nextsteppro.climbing.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final List<Locale> SUPPORTED_LOCALES = List.of(
        Locale.of("pl"), Locale.of("en"), Locale.of("es")
    );

    // Rate limits per IP per minute
    private static final int AUTH_LIMIT = 15;
    private static final int RESERVATION_LIMIT = 20;
    private static final int USER_LIMIT = 20;
    private static final int ADMIN_LIMIT = 60;
    private static final int TRAINING_LIMIT = 40;
    // Material uploads store a 10MB file on disk each; the endpoint is otherwise unthrottled, so a
    // tight per-IP cap bounds a disk-fill flood (orphans are also swept by the cleanup scheduler).
    private static final int UPLOAD_LIMIT = 12;

    // Uploads live under both /api/training-calendar/** and /api/admin/training-calendar/** — match
    // the shared suffix so the tighter cap covers both, and check it before the broader buckets.
    private static final String UPLOAD_PATH_SUFFIX = "/attachments/upload";

    private static final String TRAINING_CALENDAR_PATH = "/api/training-calendar";

    private final Cache<String, AtomicInteger> requestCounts = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(1))
        .maximumSize(10_000)
        .build();

    private final MessageSource messageSource;

    public RateLimitFilter(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        int limit = resolveLimit(path);

        if (limit <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        String cacheKey = getClientIp(request) + ":" + resolveBucket(path);
        AtomicInteger counter = requestCounts.get(cacheKey, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();

        if (count > limit) {
            Locale locale = resolveLocale(request);
            String message = messageSource.getMessage("rate.limit.exceeded", null, locale);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                "{\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"" + escapeJson(message) + "\",\"timestamp\":\"" + Instant.now() + "\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    // The two resolvers MUST test the same prefixes in the same order, or a request could be
    // capped by one bucket's limit while counting into another bucket's counter.
    private int resolveLimit(String path) {
        if (path.contains(UPLOAD_PATH_SUFFIX)) return UPLOAD_LIMIT;
        if (path.startsWith("/api/auth/")) return AUTH_LIMIT;
        if (path.startsWith("/api/reservations/")) return RESERVATION_LIMIT;
        if (path.startsWith("/api/user/")) return USER_LIMIT;
        if (path.startsWith("/api/admin/")) return ADMIN_LIMIT;
        if (isTrainingCalendar(path)) return TRAINING_LIMIT;
        return 0;
    }

    private String resolveBucket(String path) {
        if (path.contains(UPLOAD_PATH_SUFFIX)) return "upload";
        if (path.startsWith("/api/auth/")) return "auth";
        if (path.startsWith("/api/reservations/")) return "reservations";
        if (path.startsWith("/api/user/")) return "user";
        if (path.startsWith("/api/admin/")) return "admin";
        if (isTrainingCalendar(path)) return "training";
        return "default";
    }

    /**
     * The calendar RANGE endpoint is mapped on the bare base path, so its URI carries no trailing
     * slash — a plain {@code startsWith(base + "/")} let the heaviest query of the whole feature
     * (trainings + attachments + reservations + RPE + held seats + deletion log) through unthrottled.
     */
    private static boolean isTrainingCalendar(String path) {
        return path.equals(TRAINING_CALENDAR_PATH) || path.startsWith(TRAINING_CALENDAR_PATH + "/");
    }

    private Locale resolveLocale(HttpServletRequest request) {
        String header = request.getHeader("Accept-Language");
        if (header != null && !header.isEmpty()) {
            String lang = header.split("[,;_-]")[0].trim().toLowerCase();
            for (Locale supported : SUPPORTED_LOCALES) {
                if (supported.getLanguage().equals(lang)) {
                    return supported;
                }
            }
        }
        return Locale.of("pl");
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String getClientIp(HttpServletRequest request) {
        // Cloudflare sets CF-Connecting-IP to the real client IP. With the origin
        // firewalled to Cloudflare ranges, this header cannot be forged by reaching
        // the origin directly, so it is the trustworthy key for per-IP rate limiting.
        // (The first X-Forwarded-For hop is client-controlled when proxies append to
        // it, which let a spoofed XFF bypass the limit — see security tests 2026-06.)
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isBlank()) {
            return cfIp.trim();
        }
        // Fallback for non-Cloudflare environments (local/dev): first XFF hop, then peer.
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
