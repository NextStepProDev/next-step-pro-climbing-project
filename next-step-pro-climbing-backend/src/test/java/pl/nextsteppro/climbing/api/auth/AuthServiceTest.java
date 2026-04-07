package pl.nextsteppro.climbing.api.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.nextsteppro.climbing.api.auth.AuthDtos.*;
import pl.nextsteppro.climbing.config.AdminEmailConfig;
import pl.nextsteppro.climbing.domain.auth.AuthToken;
import pl.nextsteppro.climbing.domain.auth.AuthTokenRepository;
import pl.nextsteppro.climbing.domain.auth.TokenType;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.mail.AuthMailService;
import pl.nextsteppro.climbing.infrastructure.security.JwtService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for AuthService - handles registration, login, email verification, and password reset.
 *
 * Test coverage:
 * - Registration flow with email verification
 * - Login with password validation and failed attempt tracking
 * - Email verification with token validation
 * - Password reset flow
 * - Token refresh with rotation
 * - Admin email auto-promotion
 * - Edge cases: duplicate email, unverified login, expired tokens, account lockout
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthTokenRepository authTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthMailService authMailService;
    @Mock
    private AdminEmailConfig adminEmailConfig;
    @Mock
    private MessageService msg;

    private AuthService authService;
    private User testUser;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            userRepository,
            authTokenRepository,
            passwordEncoder,
            jwtService,
            authMailService,
            adminEmailConfig,
            msg
        );

        testUser = new User("test@example.com", "John", "Doe", "+48123456789", "johndoe");
        setUserIdViaReflection(testUser, UUID.randomUUID());
        testUser.setPasswordHash("hashedPassword");
        testUser.setEmailVerified(true);
        testUser.setRole(UserRole.USER);
    }

    // ========== REGISTRATION TESTS ==========

    @Test
    void shouldRegisterNewUserSuccessfully() {
        // Given
        RegisterRequest request = new RegisterRequest(
            "new@example.com",
            "password123",
            "Jane",
            "Smith",
            "+48987654321",
            null
        );
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("encodedPassword");
        when(adminEmailConfig.isAdminEmail(request.email())).thenReturn(false);
        when(jwtService.generateSecureToken()).thenReturn("secureToken123");
        when(jwtService.hashToken("secureToken123")).thenReturn("hashedToken123");
        when(msg.get("auth.register.success")).thenReturn("Registration successful");

        // When
        MessageResponse response = authService.register(request);

        // Then
        assertNotNull(response);
        assertEquals("Registration successful", response.message());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals(request.email(), savedUser.getEmail());
        assertEquals(request.firstName(), savedUser.getFirstName());
        assertEquals(request.lastName(), savedUser.getLastName());
        assertEquals(request.phone(), savedUser.getPhone());
        assertEquals("encodedPassword", savedUser.getPasswordHash());
        assertFalse(savedUser.isEmailVerified());
        assertEquals(UserRole.USER, savedUser.getRole());

        verify(authTokenRepository).save(any(AuthToken.class));
        verify(authMailService).sendVerificationEmail(any(User.class), eq("secureToken123"));
    }

    @Test
    void shouldThrowExceptionWhenEmailAlreadyExists() {
        // Given
        RegisterRequest request = new RegisterRequest(
            "existing@example.com",
            "password123",
            "Jane",
            "Smith",
            "+48987654321",
            null
        );
        when(userRepository.existsByEmail(request.email())).thenReturn(true);
        when(msg.get("auth.email.exists")).thenReturn("Email already exists");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.register(request)
        );
        assertEquals("Email already exists", exception.getMessage());

        verify(userRepository, never()).save(any(User.class));
        verify(authMailService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    void shouldAutoPromoteAdminDuringRegistration() {
        // Given
        RegisterRequest request = new RegisterRequest(
            "admin@nextsteppro.pl",
            "password123",
            "Admin",
            "User",
            "+48123456789",
            null
        );
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("encodedPassword");
        when(adminEmailConfig.isAdminEmail(request.email())).thenReturn(true);
        when(jwtService.generateSecureToken()).thenReturn("secureToken123");
        when(jwtService.hashToken("secureToken123")).thenReturn("hashedToken123");
        when(msg.get("auth.register.success")).thenReturn("Registration successful");

        // When
        authService.register(request);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals(UserRole.ADMIN, savedUser.getRole());
    }

    @Test
    void shouldGenerateUniqueNicknameDuringRegistration() {
        // Given
        RegisterRequest request = new RegisterRequest(
            "test@example.com",
            "password123",
            "John-Paul",
            "Smith",
            "+48123456789",
            null
        );
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("encodedPassword");
        when(adminEmailConfig.isAdminEmail(request.email())).thenReturn(false);
        when(jwtService.generateSecureToken()).thenReturn("secureToken123");
        when(jwtService.hashToken("secureToken123")).thenReturn("hashedToken123");
        when(msg.get("auth.register.success")).thenReturn("Registration successful");

        // When
        authService.register(request);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertTrue(savedUser.getNickname().startsWith("johnpaul_"));
        assertTrue(savedUser.getNickname().matches("^[a-z0-9_]+$"));
    }

    // ========== LOGIN TESTS ==========

    @Test
    void shouldLoginSuccessfullyWithValidCredentials() {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(request.password(), testUser.getPasswordHash())).thenReturn(true);
        when(adminEmailConfig.isAdminEmail(request.email())).thenReturn(false);
        when(jwtService.generateAccessToken(testUser)).thenReturn("accessToken");
        when(jwtService.generateRefreshToken(testUser)).thenReturn("refreshToken");
        when(jwtService.hashToken("refreshToken")).thenReturn("hashedRefreshToken");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        // When
        AuthTokensResponse response = authService.login(request);

        // Then
        assertNotNull(response);
        assertEquals("accessToken", response.accessToken());
        assertEquals("refreshToken", response.refreshToken());
        assertEquals(900L, response.expiresIn());

        verify(authTokenRepository).save(any(AuthToken.class));
    }

    @Test
    void shouldThrowExceptionWhenUserNotFound() {
        // Given
        LoginRequest request = new LoginRequest("nonexistent@example.com", "password123");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(msg.get("auth.login.invalid")).thenReturn("Invalid credentials");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.login(request)
        );
        assertEquals("Invalid credentials", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenPasswordIsWrong() {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "wrongPassword");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(request.password(), testUser.getPasswordHash())).thenReturn(false);
        when(msg.get("auth.login.invalid")).thenReturn("Invalid credentials");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.login(request)
        );
        assertEquals("Invalid credentials", exception.getMessage());

        verify(userRepository).save(testUser);
        assertEquals(1, testUser.getFailedLoginAttempts());
    }

    @Test
    void shouldIncrementFailedAttemptsAndLockAccountAfter5Failures() {
        // Given
        testUser.incrementFailedLoginAttempts(); // 1
        testUser.incrementFailedLoginAttempts(); // 2
        testUser.incrementFailedLoginAttempts(); // 3
        testUser.incrementFailedLoginAttempts(); // 4

        LoginRequest request = new LoginRequest("test@example.com", "wrongPassword");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(request.password(), testUser.getPasswordHash())).thenReturn(false);
        when(msg.get("auth.login.locked")).thenReturn("Account locked");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> authService.login(request)
        );
        assertEquals("Account locked", exception.getMessage());

        verify(userRepository).save(testUser);
        assertTrue(testUser.isAccountLocked());
        assertEquals(5, testUser.getFailedLoginAttempts());
    }

    @Test
    void shouldRejectLoginWhenAccountIsLocked() {
        // Given
        testUser.incrementFailedLoginAttempts();
        testUser.incrementFailedLoginAttempts();
        testUser.incrementFailedLoginAttempts();
        testUser.incrementFailedLoginAttempts();
        testUser.incrementFailedLoginAttempts();

        LoginRequest request = new LoginRequest("test@example.com", "password123");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(testUser));
        when(msg.get("auth.login.locked")).thenReturn("Account locked");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> authService.login(request)
        );
        assertEquals("Account locked", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenEmailNotVerified() {
        // Given
        testUser.setEmailVerified(false);
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(request.password(), testUser.getPasswordHash())).thenReturn(true);
        when(msg.get("auth.email.not.verified")).thenReturn("Email not verified");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> authService.login(request)
        );
        assertEquals("Email not verified", exception.getMessage());
    }

    @Test
    void shouldResetFailedAttemptsOnSuccessfulLogin() {
        // Given
        testUser.incrementFailedLoginAttempts();
        testUser.incrementFailedLoginAttempts();

        LoginRequest request = new LoginRequest("test@example.com", "password123");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(request.password(), testUser.getPasswordHash())).thenReturn(true);
        when(adminEmailConfig.isAdminEmail(request.email())).thenReturn(false);
        when(jwtService.generateAccessToken(testUser)).thenReturn("accessToken");
        when(jwtService.generateRefreshToken(testUser)).thenReturn("refreshToken");
        when(jwtService.hashToken("refreshToken")).thenReturn("hashedRefreshToken");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        // When
        authService.login(request);

        // Then
        assertEquals(0, testUser.getFailedLoginAttempts());
        assertNull(testUser.getLockedUntil());
        verify(userRepository).save(testUser); // Once for reset attempts
    }

    @Test
    void shouldThrowExceptionWhenOAuthUserTriesToLoginWithPassword() {
        // Given
        testUser.setPasswordHash(null);
        testUser.setOauthProvider("google");

        LoginRequest request = new LoginRequest("test@example.com", "password123");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(testUser));
        when(msg.get("auth.login.oauth")).thenReturn("OAuth user cannot login with password");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.login(request)
        );
        assertEquals("OAuth user cannot login with password", exception.getMessage());
    }

    @Test
    void shouldAutoPromoteAdminDuringLogin() {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(request.password(), testUser.getPasswordHash())).thenReturn(true);
        when(adminEmailConfig.isAdminEmail(testUser.getEmail())).thenReturn(true);
        when(jwtService.generateAccessToken(testUser)).thenReturn("accessToken");
        when(jwtService.generateRefreshToken(testUser)).thenReturn("refreshToken");
        when(jwtService.hashToken("refreshToken")).thenReturn("hashedRefreshToken");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        // When
        authService.login(request);

        // Then
        assertEquals(UserRole.ADMIN, testUser.getRole());
        verify(userRepository, atLeast(1)).save(testUser);
    }

    // ========== EMAIL VERIFICATION TESTS ==========

    @Test
    void shouldVerifyEmailSuccessfully() {
        // Given
        String token = "verificationToken123";
        String hashedToken = "hashedToken123";
        AuthToken authToken = new AuthToken(testUser, hashedToken, TokenType.EMAIL_VERIFICATION, Instant.now().plusSeconds(900));
        testUser.setEmailVerified(false);

        when(jwtService.hashToken(token)).thenReturn(hashedToken);
        when(authTokenRepository.findValidToken(eq(hashedToken), eq(TokenType.EMAIL_VERIFICATION), any(Instant.class)))
            .thenReturn(Optional.of(authToken));
        when(msg.get("auth.verify.success")).thenReturn("Email verified");

        // When
        MessageResponse response = authService.verifyEmail(token);

        // Then
        assertEquals("Email verified", response.message());
        assertTrue(testUser.isEmailVerified());
        assertNotNull(testUser.getEmailVerifiedAt());

        verify(userRepository).save(testUser);
        verify(authTokenRepository).save(authToken);
    }

    @Test
    void shouldThrowExceptionWhenVerificationTokenIsInvalid() {
        // Given
        String token = "invalidToken";
        String hashedToken = "hashedInvalidToken";

        when(jwtService.hashToken(token)).thenReturn(hashedToken);
        when(authTokenRepository.findValidToken(eq(hashedToken), eq(TokenType.EMAIL_VERIFICATION), any(Instant.class)))
            .thenReturn(Optional.empty());
        when(msg.get("auth.verify.invalid")).thenReturn("Invalid verification token");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.verifyEmail(token)
        );
        assertEquals("Invalid verification token", exception.getMessage());
    }

    // ========== RESEND VERIFICATION TESTS ==========

    @Test
    void shouldResendVerificationEmailSuccessfully() {
        // Given
        testUser.setEmailVerified(false);
        ResendVerificationRequest request = new ResendVerificationRequest("test@example.com");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(testUser));
        when(authTokenRepository.hasRecentUnusedToken(eq(testUser.getId()), eq(TokenType.EMAIL_VERIFICATION), any(Instant.class)))
            .thenReturn(false);
        when(jwtService.generateSecureToken()).thenReturn("newToken123");
        when(jwtService.hashToken("newToken123")).thenReturn("hashedNewToken123");
        when(msg.get("auth.resend.success")).thenReturn("Verification email sent");

        // When
        MessageResponse response = authService.resendVerification(request);

        // Then
        assertEquals("Verification email sent", response.message());
        verify(authTokenRepository).save(any(AuthToken.class));
        verify(authMailService).sendVerificationEmail(testUser, "newToken123");
    }

    @Test
    void shouldReturnSuccessWhenEmailAlreadyVerified() {
        // Given
        ResendVerificationRequest request = new ResendVerificationRequest("test@example.com");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(testUser));
        when(msg.get("auth.resend.already.verified")).thenReturn("Email already verified");

        // When
        MessageResponse response = authService.resendVerification(request);

        // Then
        assertEquals("Email already verified", response.message());
        verify(authTokenRepository, never()).save(any(AuthToken.class));
        verify(authMailService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    void shouldReturnSuccessWhenUserNotFoundForSecurity() {
        // Given
        ResendVerificationRequest request = new ResendVerificationRequest("nonexistent@example.com");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(msg.get("auth.resend.success")).thenReturn("Verification email sent");

        // When
        MessageResponse response = authService.resendVerification(request);

        // Then
        assertEquals("Verification email sent", response.message());
        verify(authTokenRepository, never()).save(any(AuthToken.class));
        verify(authMailService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    void shouldThrowExceptionWhenResendCooldownNotExpired() {
        // Given
        testUser.setEmailVerified(false);
        ResendVerificationRequest request = new ResendVerificationRequest("test@example.com");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(testUser));
        when(authTokenRepository.hasRecentUnusedToken(eq(testUser.getId()), eq(TokenType.EMAIL_VERIFICATION), any(Instant.class)))
            .thenReturn(true);
        when(msg.get("auth.resend.cooldown")).thenReturn("Please wait before resending");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> authService.resendVerification(request)
        );
        assertEquals("Please wait before resending", exception.getMessage());
    }

    // ========== FORGOT PASSWORD TESTS ==========

    @Test
    void shouldSendPasswordResetEmailSuccessfully() {
        // Given
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@example.com");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(testUser));
        when(authTokenRepository.hasRecentUnusedToken(eq(testUser.getId()), eq(TokenType.PASSWORD_RESET), any(Instant.class)))
            .thenReturn(false);
        when(jwtService.generateSecureToken()).thenReturn("resetToken123");
        when(jwtService.hashToken("resetToken123")).thenReturn("hashedResetToken123");
        when(msg.get("auth.forgot.success")).thenReturn("Reset email sent");

        // When
        MessageResponse response = authService.forgotPassword(request);

        // Then
        assertEquals("Reset email sent", response.message());
        verify(authTokenRepository).save(any(AuthToken.class));
        verify(authMailService).sendPasswordResetEmail(testUser, "resetToken123");
    }

    @Test
    void shouldReturnSuccessWhenUserNotFoundForSecurityInForgotPassword() {
        // Given
        ForgotPasswordRequest request = new ForgotPasswordRequest("nonexistent@example.com");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(msg.get("auth.forgot.success")).thenReturn("Reset email sent");

        // When
        MessageResponse response = authService.forgotPassword(request);

        // Then
        assertEquals("Reset email sent", response.message());
        verify(authTokenRepository, never()).save(any(AuthToken.class));
        verify(authMailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void shouldReturnSuccessWhenOAuthUserRequestsPasswordReset() {
        // Given
        testUser.setPasswordHash(null);
        testUser.setOauthProvider("google");
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@example.com");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(testUser));
        when(msg.get("auth.forgot.success")).thenReturn("Reset email sent");

        // When
        MessageResponse response = authService.forgotPassword(request);

        // Then
        assertEquals("Reset email sent", response.message());
        verify(authTokenRepository, never()).save(any(AuthToken.class));
        verify(authMailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void shouldThrowExceptionWhenForgotPasswordCooldownNotExpired() {
        // Given
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@example.com");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(testUser));
        when(authTokenRepository.hasRecentUnusedToken(eq(testUser.getId()), eq(TokenType.PASSWORD_RESET), any(Instant.class)))
            .thenReturn(true);
        when(msg.get("auth.forgot.cooldown")).thenReturn("Please wait before requesting another reset");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> authService.forgotPassword(request)
        );
        assertEquals("Please wait before requesting another reset", exception.getMessage());
    }

    // ========== RESET PASSWORD TESTS ==========

    @Test
    void shouldResetPasswordSuccessfully() {
        // Given
        String token = "resetToken123";
        String hashedToken = "hashedResetToken123";
        AuthToken authToken = new AuthToken(testUser, hashedToken, TokenType.PASSWORD_RESET, Instant.now().plusSeconds(3600));

        ResetPasswordRequest request = new ResetPasswordRequest(token, "newPassword123");

        when(jwtService.hashToken(token)).thenReturn(hashedToken);
        when(authTokenRepository.findValidToken(eq(hashedToken), eq(TokenType.PASSWORD_RESET), any(Instant.class)))
            .thenReturn(Optional.of(authToken));
        when(passwordEncoder.encode(request.newPassword())).thenReturn("encodedNewPassword");
        when(msg.get("auth.reset.success")).thenReturn("Password reset successfully");

        // When
        MessageResponse response = authService.resetPassword(request);

        // Then
        assertEquals("Password reset successfully", response.message());
        assertEquals("encodedNewPassword", testUser.getPasswordHash());

        verify(userRepository).save(testUser);
        verify(authTokenRepository).save(authToken);
        verify(authTokenRepository).deleteByUserIdAndTokenType(testUser.getId(), TokenType.REFRESH_TOKEN);
        verify(authMailService).sendPasswordChangedNotification(testUser);
    }

    @Test
    void shouldThrowExceptionWhenResetTokenIsInvalid() {
        // Given
        String token = "invalidResetToken";
        String hashedToken = "hashedInvalidToken";
        ResetPasswordRequest request = new ResetPasswordRequest(token, "newPassword123");

        when(jwtService.hashToken(token)).thenReturn(hashedToken);
        when(authTokenRepository.findValidToken(eq(hashedToken), eq(TokenType.PASSWORD_RESET), any(Instant.class)))
            .thenReturn(Optional.empty());
        when(msg.get("auth.reset.invalid")).thenReturn("Invalid reset token");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.resetPassword(request)
        );
        assertEquals("Invalid reset token", exception.getMessage());
    }

    @Test
    void shouldInvalidateAllRefreshTokensAfterPasswordReset() {
        // Given
        String token = "resetToken123";
        String hashedToken = "hashedResetToken123";
        AuthToken authToken = new AuthToken(testUser, hashedToken, TokenType.PASSWORD_RESET, Instant.now().plusSeconds(3600));
        ResetPasswordRequest request = new ResetPasswordRequest(token, "newPassword123");

        when(jwtService.hashToken(token)).thenReturn(hashedToken);
        when(authTokenRepository.findValidToken(eq(hashedToken), eq(TokenType.PASSWORD_RESET), any(Instant.class)))
            .thenReturn(Optional.of(authToken));
        when(passwordEncoder.encode(request.newPassword())).thenReturn("encodedNewPassword");
        when(msg.get("auth.reset.success")).thenReturn("Password reset successfully");

        // When
        authService.resetPassword(request);

        // Then
        verify(authTokenRepository).deleteByUserIdAndTokenType(testUser.getId(), TokenType.REFRESH_TOKEN);
    }

    // ========== REFRESH TOKEN TESTS ==========

    @Test
    void shouldRefreshTokensSuccessfully() {
        // Given
        String oldRefreshToken = "oldRefreshToken";
        String hashedOldToken = "hashedOldToken";
        AuthToken storedToken = new AuthToken(testUser, hashedOldToken, TokenType.REFRESH_TOKEN, Instant.now().plusSeconds(604800));

        RefreshTokenRequest request = new RefreshTokenRequest(oldRefreshToken);

        when(jwtService.validateToken(oldRefreshToken)).thenReturn(true);
        when(jwtService.isRefreshToken(oldRefreshToken)).thenReturn(true);
        when(jwtService.hashToken(oldRefreshToken)).thenReturn(hashedOldToken);
        when(authTokenRepository.findValidToken(eq(hashedOldToken), eq(TokenType.REFRESH_TOKEN), any(Instant.class)))
            .thenReturn(Optional.of(storedToken));
        when(jwtService.generateAccessToken(testUser)).thenReturn("newAccessToken");
        when(jwtService.generateRefreshToken(testUser)).thenReturn("newRefreshToken");
        when(jwtService.hashToken("newRefreshToken")).thenReturn("hashedNewRefreshToken");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        // When
        AuthTokensResponse response = authService.refreshTokens(request);

        // Then
        assertNotNull(response);
        assertEquals("newAccessToken", response.accessToken());
        assertEquals("newRefreshToken", response.refreshToken());
        assertEquals(900L, response.expiresIn());

        verify(authTokenRepository, times(2)).save(any(AuthToken.class)); // Old token marked as used + new token saved
    }

    @Test
    void shouldThrowExceptionWhenRefreshTokenIsInvalid() {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest("invalidToken");
        when(jwtService.validateToken("invalidToken")).thenReturn(false);
        when(msg.get("auth.refresh.invalid")).thenReturn("Invalid refresh token");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.refreshTokens(request)
        );
        assertEquals("Invalid refresh token", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenAccessTokenUsedAsRefreshToken() {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest("accessToken");
        when(jwtService.validateToken("accessToken")).thenReturn(true);
        when(jwtService.isRefreshToken("accessToken")).thenReturn(false);
        when(msg.get("auth.refresh.invalid.type")).thenReturn("Not a refresh token");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.refreshTokens(request)
        );
        assertEquals("Not a refresh token", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenRefreshTokenIsRevoked() {
        // Given
        String refreshToken = "revokedToken";
        String hashedToken = "hashedRevokedToken";

        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

        when(jwtService.validateToken(refreshToken)).thenReturn(true);
        when(jwtService.isRefreshToken(refreshToken)).thenReturn(true);
        when(jwtService.hashToken(refreshToken)).thenReturn(hashedToken);
        when(authTokenRepository.findValidToken(eq(hashedToken), eq(TokenType.REFRESH_TOKEN), any(Instant.class)))
            .thenReturn(Optional.empty());
        when(msg.get("auth.refresh.revoked")).thenReturn("Token has been revoked");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.refreshTokens(request)
        );
        assertEquals("Token has been revoked", exception.getMessage());
    }

    @Test
    void shouldRotateRefreshTokenOnRefresh() {
        // Given
        String oldRefreshToken = "oldRefreshToken";
        String hashedOldToken = "hashedOldToken";
        AuthToken storedToken = new AuthToken(testUser, hashedOldToken, TokenType.REFRESH_TOKEN, Instant.now().plusSeconds(604800));

        RefreshTokenRequest request = new RefreshTokenRequest(oldRefreshToken);

        when(jwtService.validateToken(oldRefreshToken)).thenReturn(true);
        when(jwtService.isRefreshToken(oldRefreshToken)).thenReturn(true);
        when(jwtService.hashToken(oldRefreshToken)).thenReturn(hashedOldToken);
        when(authTokenRepository.findValidToken(eq(hashedOldToken), eq(TokenType.REFRESH_TOKEN), any(Instant.class)))
            .thenReturn(Optional.of(storedToken));
        when(jwtService.generateAccessToken(testUser)).thenReturn("newAccessToken");
        when(jwtService.generateRefreshToken(testUser)).thenReturn("newRefreshToken");
        when(jwtService.hashToken("newRefreshToken")).thenReturn("hashedNewRefreshToken");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        // When
        authService.refreshTokens(request);

        // Then
        ArgumentCaptor<AuthToken> tokenCaptor = ArgumentCaptor.forClass(AuthToken.class);
        verify(authTokenRepository, times(2)).save(tokenCaptor.capture());

        // First save is marking old token as used
        // Second save is the new refresh token
        assertNotEquals("hashedOldToken", tokenCaptor.getAllValues().get(1).getTokenHash());
    }

    // ========== HELPER METHODS ==========

    private void setUserIdViaReflection(User user, UUID id) {
        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);

            var createdAtField = User.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(user, Instant.now());

            var updatedAtField = User.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(user, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException("Failed to set user ID", e);
        }
    }
}
