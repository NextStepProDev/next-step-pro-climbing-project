package pl.nextsteppro.climbing.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int AUTH_LIMIT = 10;
    private static final int RESERVATION_LIMIT = 20;
    private static final int USER_LIMIT = 20;
    private static final int ADMIN_LIMIT = 60;

    private final Cache<String, AtomicInteger> requestCounts = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(1))
        .maximumSize(10_000)
        .build();

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
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Zbyt wiele żądań. Spróbuj ponownie za minutę.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private int resolveLimit(String path) {
        if (path.startsWith("/api/auth/")) return AUTH_LIMIT;
        if (path.startsWith("/api/reservations/")) return RESERVATION_LIMIT;
        if (path.startsWith("/api/user/")) return USER_LIMIT;
        if (path.startsWith("/api/admin/")) return ADMIN_LIMIT;
        return 0;
    }

    private String resolveBucket(String path) {
        if (path.startsWith("/api/auth/")) return "auth";
        if (path.startsWith("/api/reservations/")) return "reservations";
        if (path.startsWith("/api/user/")) return "user";
        if (path.startsWith("/api/admin/")) return "admin";
        return "default";
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
