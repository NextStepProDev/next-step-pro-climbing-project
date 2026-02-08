package pl.nextsteppro.climbing.infrastructure.security;

import pl.nextsteppro.climbing.domain.user.User;

import java.util.UUID;

/**
 * Record representing a JWT-authenticated user in the SecurityContext.
 */
public record JwtAuthenticatedUser(User user) {

    public UUID getUserId() {
        return user.getId();
    }

    public String getEmail() {
        return user.getEmail();
    }

    public boolean isAdmin() {
        return user.isAdmin();
    }
}
