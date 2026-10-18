package pl.nextsteppro.climbing.api.auth;

import org.springframework.stereotype.Component;
import pl.nextsteppro.climbing.api.auth.AuthDtos.AuthTokensResponse;
import pl.nextsteppro.climbing.domain.auth.AuthToken;
import pl.nextsteppro.climbing.domain.auth.AuthTokenRepository;
import pl.nextsteppro.climbing.domain.auth.TokenType;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.security.JwtService;

import java.time.Instant;
import java.util.UUID;

/**
 * The one place a session is opened or closed.
 *
 * <p>Four doors open a session — password login, refresh, the Google exchange, and a password
 * change that has just closed every other one — and before this they were two copies of the same
 * eight lines (one in {@code AuthService}, one in the OAuth success handler). Opening a session has
 * to agree on all of it at once: the pair, the stored hash of the refresh token, its expiry. A copy
 * that forgets the hash issues a refresh token nothing can ever revoke.
 */
@Component
public class SessionTokens {

    private final JwtService jwtService;
    private final AuthTokenRepository authTokenRepository;

    public SessionTokens(JwtService jwtService, AuthTokenRepository authTokenRepository) {
        this.jwtService = jwtService;
        this.authTokenRepository = authTokenRepository;
    }

    /** A new access/refresh pair; only the refresh token's hash is stored, never the token. */
    public AuthTokensResponse issue(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        Instant expiresAt = Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs());
        authTokenRepository.save(new AuthToken(user, jwtService.hashToken(refreshToken),
            TokenType.REFRESH_TOKEN, expiresAt));
        return new AuthTokensResponse(accessToken, refreshToken, jwtService.getAccessTokenExpirationSeconds());
    }

    /**
     * Ends every session this person has. Access tokens already handed out stay valid until they
     * expire (15 minutes): they are not looked up on each request, which is the price of a stateless
     * check, and the reason the window is short.
     */
    public void revokeAll(UUID userId) {
        authTokenRepository.deleteByUserIdAndTokenType(userId, TokenType.REFRESH_TOKEN);
    }
}
