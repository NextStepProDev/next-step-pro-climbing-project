package pl.nextsteppro.climbing.api.auth;

import org.springframework.stereotype.Component;
import pl.nextsteppro.climbing.domain.auth.AuthToken;
import pl.nextsteppro.climbing.domain.auth.AuthTokenRepository;
import pl.nextsteppro.climbing.domain.auth.TokenType;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.security.JwtService;

import java.time.Duration;
import java.time.Instant;

/**
 * Mints the token behind a confirmation link. Two places need one — registration and the reminder
 * that goes out before an unconfirmed account is deleted — and minting is four lines that must
 * agree on all four: lifetime, hashing, token type, and that the raw value never reaches the
 * database. Two copies of that would agree right up until one of them was edited.
 */
@Component
class VerificationLinkIssuer {

    /**
     * How long a confirmation link stays usable. Fifteen minutes used to be the value, and it read
     * as caution rather than as a cost — but the account it unlocks is kept for a week
     * ({@link UnverifiedAccountRetentionService#RETENTION}), so the two numbers promised different
     * things about the same registration, and people arrived at a dead link with an account still
     * waiting for them.
     *
     * <p>Unlike a password reset token this one hands out nothing: it marks the address confirmed
     * and no more, the password is still required to sign in, it is single-use, and the database
     * holds only its SHA-256 hash. The worst a stolen one buys is confirming someone else's
     * address for them.
     */
    static final Duration LIFETIME = Duration.ofHours(24);

    private final AuthTokenRepository authTokenRepository;
    private final JwtService jwtService;

    VerificationLinkIssuer(AuthTokenRepository authTokenRepository, JwtService jwtService) {
        this.authTokenRepository = authTokenRepository;
        this.jwtService = jwtService;
    }

    /**
     * @return the raw token to put in the mail; only its hash is stored
     */
    String issue(User user) {
        String token = jwtService.generateSecureToken();
        AuthToken authToken = new AuthToken(
            user,
            jwtService.hashToken(token),
            TokenType.EMAIL_VERIFICATION,
            Instant.now().plus(LIFETIME)
        );
        authTokenRepository.save(authToken);
        return token;
    }
}
