package pl.nextsteppro.climbing.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.auth.AuthDtos.*;
import pl.nextsteppro.climbing.domain.auth.AuthToken;
import pl.nextsteppro.climbing.domain.auth.AuthTokenRepository;
import pl.nextsteppro.climbing.domain.auth.TokenType;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.mail.AuthMailService;
import pl.nextsteppro.climbing.infrastructure.security.JwtService;

import java.time.Duration;
import java.time.Instant;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Duration EMAIL_VERIFICATION_EXPIRATION = Duration.ofMinutes(15);
    private static final Duration PASSWORD_RESET_EXPIRATION = Duration.ofHours(1);
    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthMailService authMailService;

    public AuthService(
            UserRepository userRepository,
            AuthTokenRepository authTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthMailService authMailService) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authMailService = authMailService;
    }

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Konto z tym adresem email już istnieje");
        }

        User user = new User(
            request.email(),
            request.firstName(),
            request.lastName(),
            request.phone(),
            generateNickname(request.firstName())
        );
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        userRepository.save(user);
        log.info("User registered: {}", user.getEmail());

        sendVerificationEmail(user);

        return new MessageResponse("Konto zostało utworzone. Sprawdź swoją skrzynkę email, aby potwierdzić adres.");
    }

    @Transactional
    public AuthTokensResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new IllegalArgumentException("Nieprawidłowy email lub hasło"));

        if (user.getPasswordHash() == null) {
            throw new IllegalArgumentException("Konto zostało utworzone przez OAuth. Użyj logowania przez Google/Apple.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.debug("Invalid password attempt for: {}", request.email());
            throw new IllegalArgumentException("Nieprawidłowy email lub hasło");
        }

        if (!user.isEmailVerified()) {
            throw new IllegalStateException("Adres email nie został potwierdzony. Sprawdź swoją skrzynkę email.");
        }

        log.info("User logged in: {}", user.getEmail());
        return generateTokens(user);
    }

    @Transactional
    public MessageResponse verifyEmail(String token) {
        String tokenHash = jwtService.hashToken(token);

        AuthToken authToken = authTokenRepository.findValidToken(tokenHash, TokenType.EMAIL_VERIFICATION, Instant.now())
            .orElseThrow(() -> new IllegalArgumentException("Nieprawidłowy lub wygasły link weryfikacyjny"));

        User user = authToken.getUser();
        user.markEmailVerified();
        authToken.markAsUsed();

        userRepository.save(user);
        authTokenRepository.save(authToken);

        log.info("Email verified for: {}", user.getEmail());
        return new MessageResponse("Adres email został potwierdzony. Możesz się teraz zalogować.");
    }

    @Transactional
    public MessageResponse resendVerification(ResendVerificationRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);

        // Return success even if user doesn't exist (security - don't reveal if email exists)
        if (user == null) {
            return new MessageResponse("Jeśli konto istnieje, email weryfikacyjny został wysłany.");
        }

        if (user.isEmailVerified()) {
            return new MessageResponse("Adres email jest już potwierdzony.");
        }

        // Check cooldown
        if (authTokenRepository.hasRecentUnusedToken(user.getId(), TokenType.EMAIL_VERIFICATION, Instant.now().minus(RESEND_COOLDOWN))) {
            throw new IllegalStateException("Poczekaj chwilę przed ponownym wysłaniem emaila.");
        }

        sendVerificationEmail(user);
        return new MessageResponse("Jeśli konto istnieje, email weryfikacyjny został wysłany.");
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);

        // Return success even if user doesn't exist (security)
        if (user == null) {
            return new MessageResponse("Jeśli konto istnieje, email z linkiem do resetu hasła został wysłany.");
        }

        if (user.getPasswordHash() == null) {
            // User registered via OAuth, no password to reset
            return new MessageResponse("Jeśli konto istnieje, email z linkiem do resetu hasła został wysłany.");
        }

        // Check cooldown
        if (authTokenRepository.hasRecentUnusedToken(user.getId(), TokenType.PASSWORD_RESET, Instant.now().minus(RESEND_COOLDOWN))) {
            throw new IllegalStateException("Poczekaj chwilę przed ponownym wysłaniem emaila.");
        }

        sendPasswordResetEmail(user);
        return new MessageResponse("Jeśli konto istnieje, email z linkiem do resetu hasła został wysłany.");
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        String tokenHash = jwtService.hashToken(request.token());

        AuthToken authToken = authTokenRepository.findValidToken(tokenHash, TokenType.PASSWORD_RESET, Instant.now())
            .orElseThrow(() -> new IllegalArgumentException("Nieprawidłowy lub wygasły link do resetu hasła"));

        User user = authToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        authToken.markAsUsed();

        // Invalidate all refresh tokens for this user (force re-login on all devices)
        authTokenRepository.deleteByUserIdAndTokenType(user.getId(), TokenType.REFRESH_TOKEN);

        userRepository.save(user);
        authTokenRepository.save(authToken);

        authMailService.sendPasswordChangedNotification(user);

        log.info("Password reset for: {}", user.getEmail());
        return new MessageResponse("Hasło zostało zmienione. Możesz się teraz zalogować.");
    }

    @Transactional
    public AuthTokensResponse refreshTokens(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();

        if (!jwtService.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Nieprawidłowy refresh token");
        }

        if (!jwtService.isRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("Nieprawidłowy typ tokena");
        }

        // Verify refresh token exists in database (not revoked)
        String tokenHash = jwtService.hashToken(refreshToken);
        AuthToken storedToken = authTokenRepository.findValidToken(tokenHash, TokenType.REFRESH_TOKEN, Instant.now())
            .orElseThrow(() -> new IllegalArgumentException("Refresh token został unieważniony"));

        User user = storedToken.getUser();

        // Invalidate old refresh token (rotation)
        storedToken.markAsUsed();
        authTokenRepository.save(storedToken);

        log.debug("Tokens refreshed for: {}", user.getEmail());
        return generateTokens(user);
    }

    private AuthTokensResponse generateTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        // Store refresh token hash in database for revocation capability
        String refreshTokenHash = jwtService.hashToken(refreshToken);
        Instant refreshExpiration = Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs());

        AuthToken storedRefreshToken = new AuthToken(user, refreshTokenHash, TokenType.REFRESH_TOKEN, refreshExpiration);
        authTokenRepository.save(storedRefreshToken);

        return new AuthTokensResponse(
            accessToken,
            refreshToken,
            jwtService.getAccessTokenExpirationSeconds()
        );
    }

    private void sendVerificationEmail(User user) {
        String token = jwtService.generateSecureToken();
        String tokenHash = jwtService.hashToken(token);
        Instant expiration = Instant.now().plus(EMAIL_VERIFICATION_EXPIRATION);

        AuthToken authToken = new AuthToken(user, tokenHash, TokenType.EMAIL_VERIFICATION, expiration);
        authTokenRepository.save(authToken);

        authMailService.sendVerificationEmail(user, token);
    }

    private void sendPasswordResetEmail(User user) {
        String token = jwtService.generateSecureToken();
        String tokenHash = jwtService.hashToken(token);
        Instant expiration = Instant.now().plus(PASSWORD_RESET_EXPIRATION);

        AuthToken authToken = new AuthToken(user, tokenHash, TokenType.PASSWORD_RESET, expiration);
        authTokenRepository.save(authToken);

        authMailService.sendPasswordResetEmail(user, token);
    }

    private String generateNickname(String firstName) {
        return firstName.toLowerCase().replaceAll("[^a-z0-9]", "") + "_" + System.currentTimeMillis() % 10000;
    }
}
