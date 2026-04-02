package pl.nextsteppro.climbing.infrastructure.security;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for JwtService - the security foundation of the application.
 *
 * Tests cover:
 * - Access & Refresh token generation
 * - Token validation (expiration, malformed, signature)
 * - Claims extraction (userId, email, role)
 * - Token type detection (access vs refresh)
 * - Secure token generation for email/password reset
 * - SHA-256 token hashing
 */
class JwtServiceTest {

    private JwtService jwtService;
    private JwtConfig jwtConfig;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Setup test JWT config
        jwtConfig = new JwtConfig();
        jwtConfig.setSecret("test-secret-key-minimum-32-characters-long-for-security");
        jwtConfig.setAccessTokenExpirationMs(15 * 60 * 1000); // 15 minutes
        jwtConfig.setRefreshTokenExpirationMs(7 * 24 * 60 * 60 * 1000); // 7 days
        jwtConfig.setIssuer("nextsteppro.pl");

        jwtService = new JwtService(jwtConfig);

        // Create test user
        testUser = new User("test@example.com", "John", "Doe", "+48123456789", "johndoe");
        // Use reflection to set ID since it's auto-generated
        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(testUser, UUID.randomUUID());

            var createdAtField = User.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(testUser, Instant.now());

            var updatedAtField = User.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(testUser, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup test user", e);
        }
        testUser.setRole(UserRole.USER);
        testUser.setEmailVerified(true);
    }

    // ========== ACCESS TOKEN TESTS ==========

    @Test
    void shouldGenerateValidAccessToken() {
        // When
        String token = jwtService.generateAccessToken(testUser);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.split("\\.").length == 3, "JWT should have 3 parts (header.payload.signature)");

        // Verify it's a valid token
        assertTrue(jwtService.validateToken(token));
        assertTrue(jwtService.isAccessToken(token));
        assertFalse(jwtService.isRefreshToken(token));
    }

    @Test
    void shouldExtractUserIdFromAccessToken() {
        // Given
        String token = jwtService.generateAccessToken(testUser);

        // When
        UUID extractedUserId = jwtService.extractUserId(token);

        // Then
        assertEquals(testUser.getId(), extractedUserId);
    }

    @Test
    void shouldExtractEmailFromAccessToken() {
        // Given
        String token = jwtService.generateAccessToken(testUser);

        // When
        String extractedEmail = jwtService.extractEmail(token);

        // Then
        assertEquals(testUser.getEmail(), extractedEmail);
    }

    @Test
    void shouldGenerateAccessTokenWithCorrectExpiration() {
        // When
        String token = jwtService.generateAccessToken(testUser);

        // Then
        long expirationSeconds = jwtService.getAccessTokenExpirationSeconds();
        assertEquals(15 * 60, expirationSeconds, "Access token should expire in 15 minutes");

        // Token should be valid immediately after generation
        assertTrue(jwtService.validateToken(token));
    }

    // ========== REFRESH TOKEN TESTS ==========

    @Test
    void shouldGenerateValidRefreshToken() {
        // When
        String token = jwtService.generateRefreshToken(testUser);

        // Then
        assertNotNull(token);
        assertTrue(jwtService.validateToken(token));
        assertTrue(jwtService.isRefreshToken(token));
        assertFalse(jwtService.isAccessToken(token));
    }

    @Test
    void shouldExtractUserIdFromRefreshToken() {
        // Given
        String token = jwtService.generateRefreshToken(testUser);

        // When
        UUID extractedUserId = jwtService.extractUserId(token);

        // Then
        assertEquals(testUser.getId(), extractedUserId);
    }

    @Test
    void shouldGenerateRefreshTokenWithLongerExpiration() {
        // When
        String refreshToken = jwtService.generateRefreshToken(testUser);

        // Then
        long refreshExpirationMs = jwtService.getRefreshTokenExpirationMs();
        assertEquals(7 * 24 * 60 * 60 * 1000, refreshExpirationMs,
                     "Refresh token should expire in 7 days");

        // Verify token is valid
        assertTrue(jwtService.validateToken(refreshToken));
    }

    @Test
    void accessAndRefreshTokensShouldBeDifferent() {
        // When
        String accessToken = jwtService.generateAccessToken(testUser);
        String refreshToken = jwtService.generateRefreshToken(testUser);

        // Then
        assertNotEquals(accessToken, refreshToken, "Access and refresh tokens should be different");
        assertTrue(jwtService.isAccessToken(accessToken));
        assertTrue(jwtService.isRefreshToken(refreshToken));
    }

    // ========== TOKEN VALIDATION TESTS ==========

    @Test
    void shouldValidateCorrectToken() {
        // Given
        String token = jwtService.generateAccessToken(testUser);

        // When
        boolean isValid = jwtService.validateToken(token);

        // Then
        assertTrue(isValid);
    }

    @Test
    void shouldRejectMalformedToken() {
        // Given
        String malformedToken = "not.a.valid.jwt.token";

        // When
        boolean isValid = jwtService.validateToken(malformedToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void shouldRejectTokenWithInvalidSignature() {
        // Given
        String token = jwtService.generateAccessToken(testUser);
        String tamperedToken = token.substring(0, token.length() - 10) + "tampered123";

        // When
        boolean isValid = jwtService.validateToken(tamperedToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void shouldRejectExpiredToken() {
        // Given: Create JWT config with 1ms expiration
        JwtConfig shortLivedConfig = new JwtConfig();
        shortLivedConfig.setSecret("test-secret-key-minimum-32-characters-long-for-security");
        shortLivedConfig.setAccessTokenExpirationMs(1); // 1ms
        shortLivedConfig.setRefreshTokenExpirationMs(1);
        shortLivedConfig.setIssuer("nextsteppro.pl");

        JwtService shortLivedService = new JwtService(shortLivedConfig);
        String token = shortLivedService.generateAccessToken(testUser);

        // When: Wait for token to expire
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then
        assertFalse(shortLivedService.validateToken(token));
        assertThrows(ExpiredJwtException.class, () -> shortLivedService.extractUserId(token));
    }

    @Test
    void shouldRejectNullToken() {
        // When & Then
        // Note: JWT library throws exception for null, which is caught by validateToken
        assertThrows(Exception.class, () -> jwtService.validateToken(null));
    }

    @Test
    void shouldRejectEmptyToken() {
        // When & Then
        // Empty string causes JWT library to throw exception (no dots for split)
        assertThrows(Exception.class, () -> jwtService.validateToken(""));
    }

    // ========== TOKEN TYPE DETECTION TESTS ==========

    @Test
    void shouldCorrectlyIdentifyAccessToken() {
        // Given
        String accessToken = jwtService.generateAccessToken(testUser);

        // When & Then
        assertTrue(jwtService.isAccessToken(accessToken));
        assertFalse(jwtService.isRefreshToken(accessToken));
    }

    @Test
    void shouldCorrectlyIdentifyRefreshToken() {
        // Given
        String refreshToken = jwtService.generateRefreshToken(testUser);

        // When & Then
        assertTrue(jwtService.isRefreshToken(refreshToken));
        assertFalse(jwtService.isAccessToken(refreshToken));
    }

    @Test
    void shouldReturnFalseForMalformedTokenTypeCheck() {
        // Given
        String malformedToken = "malformed.token";

        // When & Then
        assertFalse(jwtService.isAccessToken(malformedToken));
        assertFalse(jwtService.isRefreshToken(malformedToken));
    }

    // ========== SECURE TOKEN GENERATION TESTS ==========

    @Test
    void shouldGenerateSecureRandomToken() {
        // When
        String token = jwtService.generateSecureToken();

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
        // URL-safe Base64 without padding should only contain A-Za-z0-9_-
        assertTrue(token.matches("^[A-Za-z0-9_-]+$"),
                   "Token should be URL-safe Base64 without padding");
    }

    @Test
    void shouldGenerateUniqueSecureTokens() {
        // When
        String token1 = jwtService.generateSecureToken();
        String token2 = jwtService.generateSecureToken();
        String token3 = jwtService.generateSecureToken();

        // Then
        assertNotEquals(token1, token2);
        assertNotEquals(token2, token3);
        assertNotEquals(token1, token3);
    }

    @Test
    void secureTokenShouldHaveSufficientEntropy() {
        // When
        String token = jwtService.generateSecureToken();

        // Then
        // 32 bytes of random data → ~43 characters in base64
        assertTrue(token.length() >= 40,
                   "Token should have at least 40 characters for security");
    }

    // ========== TOKEN HASHING TESTS ==========

    @Test
    void shouldHashTokenUsingSHA256() {
        // Given
        String plainToken = "my-secure-token-12345";

        // When
        String hashedToken = jwtService.hashToken(plainToken);

        // Then
        assertNotNull(hashedToken);
        assertEquals(64, hashedToken.length(), "SHA-256 hash should be 64 hex characters");
        assertTrue(hashedToken.matches("^[a-f0-9]{64}$"),
                   "Hash should only contain lowercase hex characters");
    }

    @Test
    void shouldProduceSameHashForSameInput() {
        // Given
        String plainToken = "test-token";

        // When
        String hash1 = jwtService.hashToken(plainToken);
        String hash2 = jwtService.hashToken(plainToken);

        // Then
        assertEquals(hash1, hash2, "Same input should produce same hash");
    }

    @Test
    void shouldProduceDifferentHashForDifferentInput() {
        // When
        String hash1 = jwtService.hashToken("token1");
        String hash2 = jwtService.hashToken("token2");

        // Then
        assertNotEquals(hash1, hash2, "Different inputs should produce different hashes");
    }

    @Test
    void shouldHashBeIrreversible() {
        // Given
        String originalToken = jwtService.generateSecureToken();
        String hashedToken = jwtService.hashToken(originalToken);

        // Then
        // Hash should not contain original token
        assertFalse(hashedToken.contains(originalToken));
        assertNotEquals(originalToken, hashedToken);

        // Hash should always be same length regardless of input
        String shortHash = jwtService.hashToken("a");
        String longHash = jwtService.hashToken("very long token with many characters");
        assertEquals(64, shortHash.length());
        assertEquals(64, longHash.length());
    }

    // ========== EDGE CASES & SECURITY TESTS ==========

    @Test
    void shouldHandleAdminUser() {
        // Given
        testUser.setRole(UserRole.ADMIN);
        String token = jwtService.generateAccessToken(testUser);

        // When
        UUID userId = jwtService.extractUserId(token);
        String email = jwtService.extractEmail(token);

        // Then
        assertEquals(testUser.getId(), userId);
        assertEquals(testUser.getEmail(), email);
        assertTrue(jwtService.validateToken(token));
    }

    @Test
    void shouldGenerateDifferentTokensForSameUserAtDifferentTimes() throws InterruptedException {
        // When
        String token1 = jwtService.generateAccessToken(testUser);
        Thread.sleep(1100); // Wait > 1 second to ensure different 'iat' claim
        String token2 = jwtService.generateAccessToken(testUser);

        // Then
        assertNotEquals(token1, token2,
                       "Tokens generated at different times should be different due to iat claim");
    }

    @Test
    void shouldThrowOnExtractingUserIdFromInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";

        // When & Then
        assertThrows(Exception.class, () -> jwtService.extractUserId(invalidToken));
    }

    @Test
    void shouldThrowOnExtractingEmailFromInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";

        // When & Then
        assertThrows(Exception.class, () -> jwtService.extractEmail(invalidToken));
    }

    @Test
    void shouldHandleSpecialCharactersInEmail() {
        // Given
        User userWithSpecialEmail = new User(
            "test+tag@example.co.uk",
            "Test",
            "User",
            "+48123456789",
            "testuser"
        );
        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(userWithSpecialEmail, UUID.randomUUID());

            var createdAtField = User.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(userWithSpecialEmail, Instant.now());

            var updatedAtField = User.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(userWithSpecialEmail, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        userWithSpecialEmail.setRole(UserRole.USER);

        // When
        String token = jwtService.generateAccessToken(userWithSpecialEmail);
        String extractedEmail = jwtService.extractEmail(token);

        // Then
        assertEquals("test+tag@example.co.uk", extractedEmail);
        assertTrue(jwtService.validateToken(token));
    }
}
