package pl.nextsteppro.climbing.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.security.JwtAuthenticatedUser;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for resolving the current authenticated user.
 * Supports JWT authentication, OAuth2 authentication, and dev session authentication.
 */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Get the current user from JWT, OAuth2 principal, or dev session.
     */
    public Optional<User> getCurrentUser(CustomOAuth2User oAuth2User, HttpServletRequest request) {
        // Try JWT first (from SecurityContext)
        Optional<User> jwtUser = getJwtAuthenticatedUser();
        if (jwtUser.isPresent()) {
            return jwtUser;
        }

        // Try OAuth2
        if (oAuth2User != null) {
            return Optional.of(oAuth2User.getUser());
        }

        // Fallback to dev session
        return getDevSessionUser(request);
    }

    /**
     * Get the current user ID from JWT, OAuth2 principal, or dev session.
     */
    public Optional<UUID> getCurrentUserId(CustomOAuth2User oAuth2User, HttpServletRequest request) {
        // Try JWT first (from SecurityContext)
        Optional<UUID> jwtUserId = getJwtAuthenticatedUserId();
        if (jwtUserId.isPresent()) {
            return jwtUserId;
        }

        // Try OAuth2
        if (oAuth2User != null) {
            return Optional.of(oAuth2User.getUserId());
        }

        // Fallback to dev session
        return getDevSessionUserId(request);
    }

    /**
     * Get the current user from SecurityContext (JWT authentication).
     */
    public Optional<User> getCurrentUser() {
        return getJwtAuthenticatedUser();
    }

    /**
     * Get the current user ID from SecurityContext (JWT authentication).
     */
    public Optional<UUID> getCurrentUserId() {
        return getJwtAuthenticatedUserId();
    }

    private Optional<User> getJwtAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtAuthenticatedUser jwtUser) {
            return Optional.of(jwtUser.user());
        }
        return Optional.empty();
    }

    private Optional<UUID> getJwtAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtAuthenticatedUser jwtUser) {
            return Optional.of(jwtUser.getUserId());
        }
        return Optional.empty();
    }

    private Optional<User> getDevSessionUser(HttpServletRequest request) {
        return getDevSessionUserId(request)
            .flatMap(userRepository::findById);
    }

    private Optional<UUID> getDevSessionUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }

        Object userId = session.getAttribute("DEV_USER_ID");
        if (userId instanceof UUID uuid) {
            return Optional.of(uuid);
        }
        return Optional.empty();
    }
}
