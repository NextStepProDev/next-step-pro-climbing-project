package pl.nextsteppro.climbing.api.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.nextsteppro.climbing.domain.auth.AuthToken;
import pl.nextsteppro.climbing.domain.auth.AuthTokenRepository;
import pl.nextsteppro.climbing.domain.auth.TokenType;
import pl.nextsteppro.climbing.api.reservation.EventWaitlistService;
import pl.nextsteppro.climbing.api.reservation.WaitlistService;
import pl.nextsteppro.climbing.domain.newsletter.NewsletterConsentLog;
import pl.nextsteppro.climbing.domain.newsletter.NewsletterConsentLogRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.mail.AuthMailService;
import pl.nextsteppro.climbing.infrastructure.security.JwtService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for UserService - handles profile management, password changes,
 * account deletion, notification preferences, and newsletter subscriptions.
 *
 * Test coverage:
 * - Profile retrieval and update
 * - Password change with validation
 * - Account deletion (with password and OAuth)
 * - Notification preference updates
 * - Language preference updates
 * - Newsletter subscription/unsubscription
 * - Token-based newsletter unsubscribe
 * - Newsletter unsubscribe token generation
 * - Edge cases: user not found, wrong password, OAuth user password change
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthMailService authMailService;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private AuthTokenRepository authTokenRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private MessageService msg;
    @Mock
    private NewsletterConsentLogRepository consentLogRepository;
    @Mock
    private WaitlistService waitlistService;
    @Mock
    private EventWaitlistService eventWaitlistService;
    @Mock
    private pl.nextsteppro.climbing.infrastructure.storage.FileStorageService fileStorageService;
    @Mock
    private pl.nextsteppro.climbing.infrastructure.security.PasswordPolicyValidator passwordPolicy;

    private UserService userService;
    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userService = new UserService(
            userRepository,
            passwordEncoder,
            authMailService,
            reservationRepository,
            authTokenRepository,
            jwtService,
            msg,
            consentLogRepository,
            waitlistService,
            eventWaitlistService,
            fileStorageService,
            passwordPolicy
        );

        userId = UUID.randomUUID();
        testUser = new User("test@example.com", "John", "Doe", "+48123456789", "johndoe");
        setUserIdViaReflection(testUser, userId);
        testUser.setPasswordHash("hashedPassword");
    }

    // ========== GET PROFILE TESTS ==========

    @Test
    void shouldGetProfileSuccessfully() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        User result = userService.getProfile(userId);

        // Then
        assertNotNull(result);
        assertEquals("John", result.getFirstName());
        assertEquals("Doe", result.getLastName());
        assertEquals("test@example.com", result.getEmail());
    }

    @Test
    void shouldThrowExceptionWhenUserNotFoundForGetProfile() {
        // Given
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> userService.getProfile(unknownId)
        );
        assertEquals("User not found", exception.getMessage());
    }

    // ========== UPDATE PROFILE TESTS ==========

    @Test
    void shouldUpdateProfileSuccessfully() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        User result = userService.updateProfile(userId, "Jane", "Smith", "+48987654321", "janesmith");

        // Then
        assertNotNull(result);
        assertEquals("Jane", testUser.getFirstName());
        assertEquals("Smith", testUser.getLastName());
        assertEquals("+48987654321", testUser.getPhone());
        assertEquals("janesmith", testUser.getNickname());
        verify(userRepository).save(testUser);
    }

    @Test
    void shouldUpdateProfilePartially() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        userService.updateProfile(userId, "Jane", null, null, null);

        // Then
        assertEquals("Jane", testUser.getFirstName());
        assertEquals("Doe", testUser.getLastName()); // unchanged
        assertEquals("+48123456789", testUser.getPhone()); // unchanged
        assertEquals("johndoe", testUser.getNickname()); // unchanged
        verify(userRepository).save(testUser);
    }

    @Test
    void shouldThrowExceptionWhenUserNotFoundForUpdate() {
        // Given
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(
            IllegalArgumentException.class,
            () -> userService.updateProfile(unknownId, "Jane", "Smith", "+48987654321", "janesmith")
        );
        verify(userRepository, never()).save(any(User.class));
    }

    // ========== CHANGE PASSWORD TESTS ==========

    @Test
    void shouldChangePasswordSuccessfully() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("currentPassword", "hashedPassword")).thenReturn(true);
        when(passwordEncoder.encode("newPassword")).thenReturn("hashedNewPassword");

        // When
        userService.changePassword(userId, "currentPassword", "newPassword");

        // Then
        assertEquals("hashedNewPassword", testUser.getPasswordHash());
        verify(userRepository).save(testUser);
        verify(authMailService).sendPasswordChangedNotification(testUser);
    }

    @Test
    void shouldThrowExceptionWhenChangingPasswordForOAuthUser() {
        // Given
        testUser.setPasswordHash(null);
        testUser.setOauthProvider("google");
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(msg.get("user.no.password")).thenReturn("No password set");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> userService.changePassword(userId, "currentPassword", "newPassword")
        );
        assertEquals("No password set", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
        verify(authMailService, never()).sendPasswordChangedNotification(any());
    }

    @Test
    void shouldThrowExceptionWhenCurrentPasswordIsWrong() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);
        when(msg.get("user.wrong.current.password")).thenReturn("Wrong current password");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> userService.changePassword(userId, "wrongPassword", "newPassword")
        );
        assertEquals("Wrong current password", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldThrowExceptionWhenUserNotFoundForPasswordChange() {
        // Given
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(
            IllegalArgumentException.class,
            () -> userService.changePassword(unknownId, "current", "new")
        );
    }

    // ========== DELETE ACCOUNT TESTS ==========

    @Test
    void shouldDeleteAccountWithCorrectPassword() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", "hashedPassword")).thenReturn(true);

        // When
        userService.deleteAccount(userId, "correctPassword");

        // Then
        verify(reservationRepository).cancelConfirmedByUserId(userId);
        verify(authTokenRepository).deleteAllByUserId(userId);
        verify(userRepository).delete(testUser);
    }

    @Test
    void shouldDeleteOAuthAccountWithoutPassword() {
        // Given
        testUser.setPasswordHash(null);
        testUser.setOauthProvider("google");
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        userService.deleteAccount(userId, null);

        // Then
        verify(reservationRepository).cancelConfirmedByUserId(userId);
        verify(authTokenRepository).deleteAllByUserId(userId);
        verify(userRepository).delete(testUser);
    }

    @Test
    void shouldThrowExceptionWhenDeleteAccountWithWrongPassword() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);
        when(msg.get("user.wrong.password")).thenReturn("Wrong password");

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> userService.deleteAccount(userId, "wrongPassword")
        );
        assertEquals("Wrong password", exception.getMessage());
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void shouldCancelReservationsBeforeDeletingAccount() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", "hashedPassword")).thenReturn(true);

        // When
        userService.deleteAccount(userId, "correctPassword");

        // Then — verify order: cancel reservations, delete tokens, delete user
        var inOrder = inOrder(reservationRepository, authTokenRepository, userRepository);
        inOrder.verify(reservationRepository).cancelConfirmedByUserId(userId);
        inOrder.verify(authTokenRepository).deleteAllByUserId(userId);
        inOrder.verify(userRepository).delete(testUser);
    }

    @Test
    void shouldNotifyWaitlistsAndAdminWhenDeletingAccountWithReservations() {
        // Given — user has a confirmed standalone slot reservation and an event reservation
        UUID slotId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", "hashedPassword")).thenReturn(true);
        when(reservationRepository.findConfirmedSlotIdsByUserId(userId)).thenReturn(List.of(slotId));
        when(reservationRepository.findConfirmedEventIdsByUserId(userId)).thenReturn(List.of(eventId));

        // When
        userService.deleteAccount(userId, "correctPassword");

        // Then — freed spots trigger waitlist notifications and admin is informed
        verify(reservationRepository).cancelConfirmedByUserId(userId);
        verify(waitlistService).notifyAll(slotId);
        verify(eventWaitlistService).notifyAll(eventId);
        verify(waitlistService).removeUserFromAllWaitlists(userId);
        verify(eventWaitlistService).removeUserFromAllWaitlists(userId);
        verify(authMailService).sendAccountSelfDeletedAdminNotification(testUser, 1);
        verify(userRepository).delete(testUser);
    }

    @Test
    void shouldFreeSpotsBeforeNotifyingWaitlistAndDeleteUserLast() {
        // Given
        UUID slotId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", "hashedPassword")).thenReturn(true);
        when(reservationRepository.findConfirmedSlotIdsByUserId(userId)).thenReturn(List.of(slotId));

        // When
        userService.deleteAccount(userId, "correctPassword");

        // Then — reservation must be cancelled (spot freed) before waitlist is notified, user deleted last
        var inOrder = inOrder(reservationRepository, waitlistService, userRepository);
        inOrder.verify(reservationRepository).cancelConfirmedByUserId(userId);
        inOrder.verify(waitlistService).notifyAll(slotId);
        inOrder.verify(userRepository).delete(testUser);
    }

    @Test
    void shouldStillNotifyAdminWhenDeletingAccountWithoutReservations() {
        // Given — no reservations (Mockito returns empty lists by default)
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", "hashedPassword")).thenReturn(true);

        // When
        userService.deleteAccount(userId, "correctPassword");

        // Then — admin notified with zero cancelled reservations, no waitlist offers
        verify(authMailService).sendAccountSelfDeletedAdminNotification(testUser, 0);
        verify(waitlistService, never()).notifyAll(any());
        verify(eventWaitlistService, never()).notifyAll(any());
        verify(userRepository).delete(testUser);
    }

    // ========== NOTIFICATION PREFERENCE TESTS ==========

    @Test
    void shouldUpdateNotificationPreferenceToEnabled() {
        // Given
        testUser.setEmailNotificationsEnabled(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        userService.updateNotificationPreference(userId, true);

        // Then
        assertTrue(testUser.isEmailNotificationsEnabled());
        verify(userRepository).save(testUser);
    }

    @Test
    void shouldUpdateNotificationPreferenceToDisabled() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        userService.updateNotificationPreference(userId, false);

        // Then
        assertFalse(testUser.isEmailNotificationsEnabled());
        verify(userRepository).save(testUser);
    }

    // ========== LANGUAGE PREFERENCE TESTS ==========

    @Test
    void shouldUpdateLanguagePreference() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        userService.updateLanguagePreference(userId, "en");

        // Then
        assertEquals("en", testUser.getPreferredLanguage());
        verify(userRepository).save(testUser);
    }

    @Test
    void shouldThrowExceptionWhenUserNotFoundForLanguageUpdate() {
        // Given
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(
            IllegalArgumentException.class,
            () -> userService.updateLanguagePreference(unknownId, "en")
        );
    }

    // ========== NEWSLETTER SUBSCRIPTION TESTS ==========

    @Test
    void shouldSubscribeToNewsletter() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        userService.updateNewsletterSubscription(userId, true);

        // Then
        assertTrue(testUser.isNewsletterSubscribed());
        assertTrue(testUser.isNewsletterChoiceMade());
        assertNotNull(testUser.getNewsletterSubscribedAt());
        verify(userRepository).save(testUser);

        ArgumentCaptor<NewsletterConsentLog> logCaptor = ArgumentCaptor.forClass(NewsletterConsentLog.class);
        verify(consentLogRepository).save(logCaptor.capture());
        assertEquals("SUBSCRIBED", logCaptor.getValue().getAction());
        assertEquals("SETTINGS", logCaptor.getValue().getSource());
    }

    @Test
    void shouldUnsubscribeFromNewsletter() {
        // Given
        testUser.setNewsletterSubscribed(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        userService.updateNewsletterSubscription(userId, false);

        // Then
        assertFalse(testUser.isNewsletterSubscribed());
        assertTrue(testUser.isNewsletterChoiceMade());
        verify(userRepository).save(testUser);

        ArgumentCaptor<NewsletterConsentLog> logCaptor = ArgumentCaptor.forClass(NewsletterConsentLog.class);
        verify(consentLogRepository).save(logCaptor.capture());
        assertEquals("UNSUBSCRIBED", logCaptor.getValue().getAction());
        assertEquals("SETTINGS", logCaptor.getValue().getSource());
    }

    @Test
    void shouldNotSetSubscribedAtWhenUnsubscribing() {
        // Given
        testUser.setNewsletterSubscribed(true);
        testUser.setNewsletterSubscribedAt(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        userService.updateNewsletterSubscription(userId, false);

        // Then
        assertNull(testUser.getNewsletterSubscribedAt());
    }

    // ========== UNSUBSCRIBE BY TOKEN TESTS ==========

    @Test
    void shouldUnsubscribeByTokenSuccessfully() {
        // Given
        String token = "unsubscribeToken123";
        String tokenHash = "hashedToken123";
        AuthToken authToken = new AuthToken(testUser, tokenHash, TokenType.NEWSLETTER_UNSUBSCRIBE, Instant.now().plusSeconds(86400));
        testUser.setNewsletterSubscribed(true);

        when(jwtService.hashToken(token)).thenReturn(tokenHash);
        when(authTokenRepository.findValidToken(eq(tokenHash), eq(TokenType.NEWSLETTER_UNSUBSCRIBE), any(Instant.class)))
            .thenReturn(Optional.of(authToken));

        // When
        userService.unsubscribeByToken(token);

        // Then
        assertFalse(testUser.isNewsletterSubscribed());
        assertTrue(testUser.isNewsletterChoiceMade());
        verify(userRepository).save(testUser);

        ArgumentCaptor<NewsletterConsentLog> logCaptor = ArgumentCaptor.forClass(NewsletterConsentLog.class);
        verify(consentLogRepository).save(logCaptor.capture());
        assertEquals("UNSUBSCRIBED", logCaptor.getValue().getAction());
        assertEquals("EMAIL_LINK", logCaptor.getValue().getSource());
    }

    @Test
    void shouldThrowExceptionWhenUnsubscribeTokenIsInvalid() {
        // Given
        String token = "invalidToken";
        String tokenHash = "hashedInvalidToken";

        when(jwtService.hashToken(token)).thenReturn(tokenHash);
        when(authTokenRepository.findValidToken(eq(tokenHash), eq(TokenType.NEWSLETTER_UNSUBSCRIBE), any(Instant.class)))
            .thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> userService.unsubscribeByToken(token)
        );
        assertEquals("Invalid unsubscribe token", exception.getMessage());
    }

    // ========== GENERATE NEWSLETTER UNSUBSCRIBE TOKEN TESTS ==========

    @Test
    void shouldGenerateNewsletterUnsubscribeToken() {
        // Given
        when(jwtService.generateSecureToken()).thenReturn("newToken123");
        when(jwtService.hashToken("newToken123")).thenReturn("hashedNewToken123");

        // When
        String result = userService.generateNewsletterUnsubscribeToken(testUser);

        // Then
        assertEquals("newToken123", result);
        verify(authTokenRepository).deleteByUserIdAndTokenType(userId, TokenType.NEWSLETTER_UNSUBSCRIBE);

        ArgumentCaptor<AuthToken> tokenCaptor = ArgumentCaptor.forClass(AuthToken.class);
        verify(authTokenRepository).save(tokenCaptor.capture());
        AuthToken savedToken = tokenCaptor.getValue();
        assertEquals("hashedNewToken123", savedToken.getTokenHash());
        assertEquals(TokenType.NEWSLETTER_UNSUBSCRIBE, savedToken.getTokenType());
    }

    @Test
    void shouldDeleteOldUnsubscribeTokenBeforeGeneratingNew() {
        // Given
        when(jwtService.generateSecureToken()).thenReturn("newToken");
        when(jwtService.hashToken("newToken")).thenReturn("hashedNewToken");

        // When
        userService.generateNewsletterUnsubscribeToken(testUser);

        // Then — verify delete happens before save
        var inOrder = inOrder(authTokenRepository);
        inOrder.verify(authTokenRepository).deleteByUserIdAndTokenType(userId, TokenType.NEWSLETTER_UNSUBSCRIBE);
        inOrder.verify(authTokenRepository).save(any(AuthToken.class));
    }

    @Test
    void shouldGenerateTokenWithLongExpiry() {
        // Given
        when(jwtService.generateSecureToken()).thenReturn("token");
        when(jwtService.hashToken("token")).thenReturn("hashedToken");

        // When
        userService.generateNewsletterUnsubscribeToken(testUser);

        // Then
        ArgumentCaptor<AuthToken> tokenCaptor = ArgumentCaptor.forClass(AuthToken.class);
        verify(authTokenRepository).save(tokenCaptor.capture());

        AuthToken savedToken = tokenCaptor.getValue();
        // Token should expire ~10 years in the future (3650 days)
        Instant expectedMinExpiry = Instant.now().plusSeconds(3650 * 24 * 3600 - 60);
        assertTrue(savedToken.getExpiresAt().isAfter(expectedMinExpiry));
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
