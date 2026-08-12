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
import pl.nextsteppro.climbing.api.reservation.UserSeatReleaseService;
import pl.nextsteppro.climbing.domain.newsletter.NewsletterConsentLog;
import pl.nextsteppro.climbing.domain.newsletter.NewsletterConsentLogRepository;
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
    private AuthTokenRepository authTokenRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private MessageService msg;
    @Mock
    private NewsletterConsentLogRepository consentLogRepository;
    @Mock
    private UserSeatReleaseService userSeatReleaseService;
    @Mock
    private pl.nextsteppro.climbing.api.trainingcalendar.CommentFileSupport commentFileSupport;
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
            authTokenRepository,
            jwtService,
            msg,
            consentLogRepository,
            userSeatReleaseService,
            commentFileSupport,
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
        verify(userSeatReleaseService).releaseSeatsAndNotifyWaitlists(userId);
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
        verify(userSeatReleaseService).releaseSeatsAndNotifyWaitlists(userId);
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
    void shouldReleaseSeatsBeforeDeletingAccount() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", "hashedPassword")).thenReturn(true);

        // When
        userService.deleteAccount(userId, "correctPassword");

        // Then — verify order: release seats, delete tokens, delete user
        var inOrder = inOrder(userSeatReleaseService, authTokenRepository, userRepository);
        inOrder.verify(userSeatReleaseService).releaseSeatsAndNotifyWaitlists(userId);
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
        when(userSeatReleaseService.releaseSeatsAndNotifyWaitlists(userId)).thenReturn(1);

        // When
        userService.deleteAccount(userId, "correctPassword");

        // Then — seats are released via the shared collaborator and the admin is informed.
        // The release ordering itself is covered by UserSeatReleaseServiceTest.
        verify(userSeatReleaseService).releaseSeatsAndNotifyWaitlists(userId);
        verify(authMailService).sendAccountSelfDeletedAdminNotification(testUser, 1);
        verify(userRepository).delete(testUser);
    }

    @Test
    void shouldReleaseSeatsBeforeDeletingUser() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", "hashedPassword")).thenReturn(true);

        // When
        userService.deleteAccount(userId, "correctPassword");

        // Then — seats must be freed and re-offered while the user still exists; deleting first
        // would cascade the reservations away and leave the queue with nothing to be offered.
        var inOrder = inOrder(userSeatReleaseService, userRepository);
        inOrder.verify(userSeatReleaseService).releaseSeatsAndNotifyWaitlists(userId);
        inOrder.verify(userRepository).delete(testUser);
    }

    @Test
    void shouldStillNotifyAdminWhenDeletingAccountWithoutReservations() {
        // Given — no reservations (Mockito returns empty lists by default)
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", "hashedPassword")).thenReturn(true);

        // When
        userService.deleteAccount(userId, "correctPassword");

        // Then — admin notified with zero cancelled reservations (the collaborator reports 0)
        verify(authMailService).sendAccountSelfDeletedAdminNotification(testUser, 0);
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

    /**
     * The token is a permanent column on the user, not a row minted per mailing, so the lookup is
     * "who owns this token" rather than "is this token still alive".
     */
    @Test
    void shouldUnsubscribeByTokenSuccessfully() {
        testUser.setNewsletterSubscribed(true);
        String token = testUser.getNewsletterUnsubscribeToken().toString();
        when(userRepository.findByNewsletterUnsubscribeToken(testUser.getNewsletterUnsubscribeToken()))
            .thenReturn(Optional.of(testUser));

        userService.unsubscribeByToken(token);

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
        UUID unknown = UUID.randomUUID();
        when(userRepository.findByNewsletterUnsubscribeToken(unknown)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> userService.unsubscribeByToken(unknown.toString())
        );
        assertEquals("Invalid unsubscribe token", exception.getMessage());
    }

    /** A truncated or mangled link is not a lookup at all — it must not reach the repository. */
    @Test
    void shouldRejectATokenThatIsNotEvenAUuid() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> userService.unsubscribeByToken("not-a-uuid")
        );
        assertEquals("Invalid unsubscribe token", exception.getMessage());
        verify(userRepository, never()).findByNewsletterUnsubscribeToken(any(UUID.class));
    }

    /**
     * The confirmation page behind the unsubscribe link is fetched by mail security scanners as
     * often as by people — checking the link must not be the same thing as using it.
     */
    @Test
    void shouldNotUnsubscribeAnybodyWhenOnlyValidatingTheToken() {
        testUser.setNewsletterSubscribed(true);
        testUser.setPreferredLanguage("es");
        when(userRepository.findByNewsletterUnsubscribeToken(testUser.getNewsletterUnsubscribeToken()))
            .thenReturn(Optional.of(testUser));

        String language = userService.resolveUnsubscribeLanguage(
            testUser.getNewsletterUnsubscribeToken().toString());

        assertTrue(testUser.isNewsletterSubscribed(), "merely opening the link must leave the subscription alone");
        verify(userRepository, never()).save(any(User.class));
        verify(consentLogRepository, never()).save(any(NewsletterConsentLog.class));
        assertEquals("es", language, "the page has to speak the language the newsletter was written in");
    }

    @Test
    void shouldRejectAnInvalidTokenOnTheConfirmationPage() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findByNewsletterUnsubscribeToken(unknown)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> userService.resolveUnsubscribeLanguage(unknown.toString()));
    }

    // ========== NEWSLETTER UNSUBSCRIBE TOKEN ==========

    /**
     * Every mailing carries the same value, so every unsubscribe link we have ever sent keeps
     * working. The previous mechanism minted a fresh token per send and deleted the old one, which
     * meant only the newest email could unsubscribe anyone — and people click whichever message
     * they have open, rarely the newest.
     */
    @Test
    void shouldGiveEveryMailingTheSameUnsubscribeToken() {
        String first = userService.newsletterUnsubscribeToken(testUser);
        String second = userService.newsletterUnsubscribeToken(testUser);

        assertEquals(first, second);
        assertEquals(testUser.getNewsletterUnsubscribeToken().toString(), first);
        verifyNoInteractions(authTokenRepository);
    }

    @Test
    void shouldGiveDifferentUsersDifferentUnsubscribeTokens() {
        User other = new User("other@example.com", "Ann", "Nowak", "+48000000000", "ann");

        assertNotEquals(
            userService.newsletterUnsubscribeToken(testUser),
            userService.newsletterUnsubscribeToken(other));
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
