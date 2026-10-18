package pl.nextsteppro.climbing.api.auth;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.auth.AuthDtos.AuthTokensResponse;
import pl.nextsteppro.climbing.domain.auth.AuthToken;
import pl.nextsteppro.climbing.domain.auth.AuthTokenRepository;
import pl.nextsteppro.climbing.domain.auth.TokenType;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.security.JwtService;

import java.time.Duration;
import java.time.Instant;

/**
 * The one-time code the browser gets after Google sign-in, in place of the tokens.
 *
 * <p>⚠️ The tokens used to ride the redirect URL ({@code /oauth-callback?accessToken=…&refreshToken=…}),
 * so a seven-day refresh token sat in the nginx access log, in the browser history and in anything
 * else that records URLs, before the page could strip it. The code goes in the FRAGMENT (never sent
 * to a server), lives a minute, works once, and only its hash is stored.
 *
 * <p>Its own component rather than methods on {@code AuthService}: the OAuth success handler is
 * built by the security configuration, and {@code AuthService} needs the password encoder that the
 * same configuration defines — depending on it from the handler is a bean cycle and the context
 * does not start.
 */
@Component
public class OAuthExchangeCodes {

    static final Duration TTL = Duration.ofSeconds(60);

    private final JwtService jwtService;
    private final AuthTokenRepository authTokenRepository;
    private final SessionTokens sessionTokens;
    private final MessageService msg;

    public OAuthExchangeCodes(JwtService jwtService, AuthTokenRepository authTokenRepository,
                              SessionTokens sessionTokens, MessageService msg) {
        this.jwtService = jwtService;
        this.authTokenRepository = authTokenRepository;
        this.sessionTokens = sessionTokens;
        this.msg = msg;
    }

    @Transactional
    public String issue(User user) {
        String code = jwtService.generateSecureToken();
        authTokenRepository.save(new AuthToken(user, jwtService.hashToken(code),
            TokenType.OAUTH_EXCHANGE, Instant.now().plus(TTL)));
        return code;
    }

    @Transactional
    public AuthTokensResponse exchange(String code) {
        AuthToken stored = authTokenRepository
            .findValidToken(jwtService.hashToken(code), TokenType.OAUTH_EXCHANGE, Instant.now())
            .orElseThrow(() -> new IllegalArgumentException(msg.get("auth.oauth.code.invalid")));
        // Deleted rather than marked used: there is nothing to trace a spent code back to.
        authTokenRepository.delete(stored);
        return sessionTokens.issue(stored.getUser());
    }
}
