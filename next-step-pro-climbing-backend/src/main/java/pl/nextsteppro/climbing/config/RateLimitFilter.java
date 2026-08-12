package pl.nextsteppro.climbing.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.function.Predicate;

/**
 * Per-IP request throttling, keyed on the real client address and bucketed by path.
 *
 * <p><strong>Deny by default.</strong> Anything under {@code /api} that no rule claims falls into
 * {@link #DEFAULT_LIMIT}, rather than through the filter untouched. The old behaviour — unmatched
 * path means no limit — made every new controller start life unthrottled, and nothing at the call
 * site looked wrong: {@code /api/training-calendar} was "in the filter" for months while its
 * heaviest query, mapped on the bare base path, was not. {@code RateLimitCoverageTest} keeps the
 * table honest; this default keeps the gap survivable in the meantime.
 */
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
    // Proposing a time is a write, and each rejected one still costs validation and queries. The
    // "3 PENDING per user" rule caps what survives, not what the endpoint has to process.
    private static final int TRAINING_REQUEST_LIMIT = 20;
    // Material uploads store a 10MB file on disk each; the endpoint is otherwise unthrottled, so a
    // tight per-IP cap bounds a disk-fill flood (orphans are also swept by the cleanup scheduler).
    private static final int UPLOAD_LIMIT = 12;
    // Reading a private file is one request per image, and a thread can hold dozens. Sharing the
    // 40/min calendar bucket would have a coach opening a long conversation throttled by their own
    // photos; these are cheap streamed reads, so they get their own, roomier bucket.
    private static final int FILE_READ_LIMIT = 120;
    // Public media, same shape as above: a gallery page is one request per photo, and forty in a
    // row is a visitor scrolling, not an attack.
    private static final int PUBLIC_FILE_LIMIT = 300;
    // The heaviest public read. The month/week/day cache only covers anonymous viewers
    // (CalendarService caches for userId == null), so a logged-in viewer recomputes availability
    // per request. Prefetching neighbouring months is a burst of a few per second, never 60 a minute.
    private static final int CALENDAR_LIMIT = 60;
    // Published CMS content: cached, cheap, and a first page load pulls several at once.
    private static final int CONTENT_LIMIT = 120;
    // Deliberately generous: it exists to stop a script, not to surprise a household or an office
    // behind one NAT address.
    private static final int DEFAULT_LIMIT = 120;

    // Uploads live under both /api/training-calendar/** and /api/admin/training-calendar/** — match
    // the shared suffix so the tighter cap covers both, and check it before the broader buckets.
    private static final String UPLOAD_PATH_SUFFIX = "/attachments/upload";
    // Attachments posted with a message. Distinct suffix, so it does not collide with the above.
    private static final String COMMENT_UPLOAD_SUFFIX = "/comments/attachments";
    // Authenticated file streams (comment attachments and coach materials alike).
    private static final String COMMENT_FILE_PATH = "/api/training-calendar/comment-files/";
    private static final String MATERIAL_FILE_PATH = "/api/training-calendar/files/";

    /**
     * One bucket, one limit, one predicate — so a request can no longer be counted into one
     * bucket while being measured against another's limit. That was a standing hazard of the two
     * parallel {@code if} ladders this replaced: they had to test the same prefixes in the same
     * order, and nothing but a comment enforced it.
     */
    record Rule(String bucket, int limit, Predicate<String> matches) {}

    /** First match wins, so narrower rules (upload suffixes, private files) come first. */
    private static final List<Rule> RULES = List.of(
        new Rule("upload", UPLOAD_LIMIT,
            path -> path.contains(UPLOAD_PATH_SUFFIX) || path.contains(COMMENT_UPLOAD_SUFFIX)),
        new Rule("privatefile", FILE_READ_LIMIT, RateLimitFilter::isPrivateFileRead),
        // Google sign-in is a sign-in attempt like any other, and lives outside /api entirely.
        new Rule("auth", AUTH_LIMIT, path -> under(path, "/api/auth")
            || under(path, "/oauth2") || under(path, "/login/oauth2")),
        new Rule("reservations", RESERVATION_LIMIT, path -> under(path, "/api/reservations")),
        new Rule("user", USER_LIMIT, path -> under(path, "/api/user")),
        new Rule("admin", ADMIN_LIMIT, path -> under(path, "/api/admin")),
        new Rule("training", TRAINING_LIMIT, path -> under(path, "/api/training-calendar")),
        new Rule("trainingreq", TRAINING_REQUEST_LIMIT, path -> under(path, "/api/training-requests")),
        new Rule("publicfile", PUBLIC_FILE_LIMIT, path -> under(path, "/api/files")),
        new Rule("calendar", CALENDAR_LIMIT, path -> under(path, "/api/calendar")
            || under(path, "/api/events")),
        new Rule("content", CONTENT_LIMIT, path -> under(path, "/api/news")
            || under(path, "/api/courses")
            || under(path, "/api/gallery")
            || under(path, "/api/instructors")
            || under(path, "/api/videos")
            || under(path, "/api/settings")
            || under(path, "/api/og")
            || under(path, "/api/sitemap.xml"))
    );

    /**
     * Catch-all for {@code /api}. Scoped to the API on purpose: {@code /actuator/health} is polled
     * by the container healthcheck from a single address every few seconds, and throttling it would
     * flap the container to unhealthy.
     */
    private static final Rule DEFAULT_RULE =
        new Rule("default", DEFAULT_LIMIT, path -> under(path, "/api"));

    private final Cache<String, AtomicInteger> requestCounts = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(1))
        .maximumSize(10_000)
        .build();

    private final MessageSource messageSource;
    private final boolean enabled;

    @Autowired
    public RateLimitFilter(MessageSource messageSource,
                           @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.messageSource = messageSource;
        this.enabled = enabled;
    }

    /** Test/default wiring: throttling on, as in production. */
    public RateLimitFilter(MessageSource messageSource) {
        this(messageSource, true);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Rule rule = enabled ? resolveRule(request.getRequestURI()) : null;

        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String cacheKey = getClientIp(request) + ":" + rule.bucket();
        AtomicInteger counter = requestCounts.get(cacheKey, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();

        if (count > rule.limit()) {
            Locale locale = resolveLocale(request);
            String message = messageSource.getMessage("rate.limit.exceeded", null, locale);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            // The window is fixed and one minute long, so 60 is an upper bound: it can send a
            // well-behaved client back a moment late, never too early.
            response.setHeader("Retry-After", "60");
            response.getWriter().write(
                "{\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"" + escapeJson(message) + "\",\"timestamp\":\"" + Instant.now() + "\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * The bucket and limit a path is subject to, or {@code null} when it is outside the API and
     * left alone.
     */
    static @Nullable Rule resolveRule(String path) {
        for (Rule rule : RULES) {
            if (rule.matches().test(path)) {
                return rule;
            }
        }
        return DEFAULT_RULE.matches().test(path) ? DEFAULT_RULE : null;
    }

    /**
     * Name of the bucket a path counts into, or {@code null} when it is not throttled at all.
     * Exposed for the architecture gate, which asserts that every controller base path lands in
     * some bucket, and in the same one as its sub-paths.
     */
    public static @Nullable String bucketFor(String path) {
        Rule rule = resolveRule(path);
        return rule == null ? null : rule.bucket();
    }

    /**
     * Base-path match that includes the base itself. Endpoints mapped on the bare base carry no
     * trailing slash, so a plain {@code startsWith(base + "/")} misses them — that is how the
     * heaviest query of the training calendar (trainings + attachments + reservations + RPE + held
     * seats + deletion log) went unthrottled. Requiring the separator on the sub-path side is what
     * keeps {@code /api/training-calendars-export} from being read as the calendar.
     */
    private static boolean under(String path, String base) {
        return path.equals(base) || path.startsWith(base + "/");
    }

    /** Checked before the training bucket, so a file read never counts against the calendar. */
    private static boolean isPrivateFileRead(String path) {
        return path.startsWith(COMMENT_FILE_PATH) || path.startsWith(MATERIAL_FILE_PATH);
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
