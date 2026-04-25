package pl.nextsteppro.climbing.infrastructure.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.javamail.JavaMailSender;
import pl.nextsteppro.climbing.config.AdminEmailConfig;
import pl.nextsteppro.climbing.config.AppConfig;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MailService — verifies sending logic, notification routing, and fallback behaviour.
 *
 * Scope:
 * - Email is skipped when user has notifications disabled
 * - Admin notifications are sent to all configured admin emails
 * - resolveAdminEmails() falls back to MAIL_FROM when admin list is empty
 * - resolveAdminEmails() is silent (no exception, no send) when both are empty
 * - Newsletter mail is sent with footer
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private AppConfig appConfig;
    @Mock private AdminEmailConfig adminEmailConfig;
    @Mock private MessageService msg;

    private AppConfig.Mail mailConfig;
    private MailService mailService;

    @BeforeEach
    void setUp() {
        mailConfig = mock(AppConfig.Mail.class);
        when(appConfig.getMail()).thenReturn(mailConfig);
        when(mailConfig.getFrom()).thenReturn("noreply@nextsteppro.pl");
        when(appConfig.getSiteUrl()).thenReturn("https://nextsteppro.pl");

        // Stub MessageService to return non-null strings for any call variant (varargs or not)
        lenient().when(msg.getForLang(any(String.class), any(String.class)))
                .thenReturn("test-message");
        lenient().doAnswer(inv -> "test-message")
                .when(msg).getForLang(any(String.class), any(String.class), any());

        // MimeMessage requires a real instance — mock returns null by default
        lenient().when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage((Session) null));

        mailService = new MailService(mailSender, appConfig, adminEmailConfig, msg);
    }

    // ============================================================
    // sendReservationConfirmation
    // ============================================================

    @Test
    void shouldSkipReservationConfirmationWhenEmailNotificationsDisabled() {
        // Given
        User user = createUser("user@example.com", false);
        Reservation reservation = createReservation(user);

        // When
        mailService.sendReservationConfirmation(reservation, "Slot Title");

        // Then: no email sent at all
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void shouldSendReservationConfirmationWhenEmailNotificationsEnabled() {
        // Given
        User user = createUser("user@example.com", true);
        Reservation reservation = createReservation(user);

        // When
        mailService.sendReservationConfirmation(reservation, "Slot Title");

        // Then: email sent once
        verify(mailSender).send(any(MimeMessage.class));
    }

    // ============================================================
    // sendCancellationConfirmation
    // ============================================================

    @Test
    void shouldSkipCancellationConfirmationWhenEmailNotificationsDisabled() {
        // Given
        User user = createUser("user@example.com", false);
        Reservation reservation = createReservation(user);

        // When
        mailService.sendCancellationConfirmation(reservation);

        // Then
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void shouldSendCancellationConfirmationWhenEmailNotificationsEnabled() {
        // Given
        User user = createUser("user@example.com", true);
        Reservation reservation = createReservation(user);

        // When
        mailService.sendCancellationConfirmation(reservation);

        // Then
        verify(mailSender).send(any(MimeMessage.class));
    }

    // ============================================================
    // resolveAdminEmails — via sendAdminNotification
    // ============================================================

    @Test
    void shouldSendAdminNotificationToAllConfiguredAdminEmails() {
        // Given: 2 admin emails
        when(adminEmailConfig.getAdminEmails())
                .thenReturn(Set.of("admin1@example.com", "admin2@example.com"));
        User user = createUser("user@example.com", true);
        Reservation reservation = createReservation(user);

        // When
        mailService.sendAdminNotification(reservation, "Slot Title");

        // Then: one email per admin
        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void shouldFallBackToMailFromWhenAdminEmailsEmpty() {
        // Given: no admin emails configured, MAIL_FROM set
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of());
        User user = createUser("user@example.com", true);
        Reservation reservation = createReservation(user);

        // When
        mailService.sendAdminNotification(reservation, "Slot Title");

        // Then: email sent once to MAIL_FROM fallback
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void shouldNotSendOrThrowWhenBothAdminEmailAndMailFromEmpty() {
        // Given: no admin email, empty MAIL_FROM
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of());
        when(mailConfig.getFrom()).thenReturn("");
        User user = createUser("user@example.com", true);
        Reservation reservation = createReservation(user);

        // When / Then: no exception, no email sent
        mailService.sendAdminNotification(reservation, "Slot Title");
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void shouldNotSendOrThrowWhenAdminEmailsEmptyAndMailFromNull() {
        // Given: no admin email, null MAIL_FROM
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of());
        when(mailConfig.getFrom()).thenReturn(null);
        User user = createUser("user@example.com", true);
        Reservation reservation = createReservation(user);

        // When / Then: no exception, no email sent
        mailService.sendAdminNotification(reservation, "Slot Title");
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ============================================================
    // sendNewsletterMail
    // ============================================================

    @Test
    void shouldSendNewsletterMailWithFooter() {
        // Given
        when(msg.getForLang(eq("email.newsletter.footer"), anyString(), any()))
                .thenReturn("Unsubscribe footer text");

        // When
        mailService.sendNewsletterMail("subscriber@example.com", "Newsletter Subject", "<p>Content</p>", "pl");

        // Then
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void shouldSendNewsletterMailForEnglishLanguage() {
        // Given
        when(msg.getForLang(eq("email.newsletter.footer"), anyString(), any()))
                .thenReturn("Unsubscribe footer text");

        // When
        mailService.sendNewsletterMail("subscriber@example.com", "Newsletter", "<p>Content</p>", "en");

        // Then
        verify(mailSender).send(any(MimeMessage.class));
    }

    // ============================================================
    // sendCustomAdminMail
    // ============================================================

    @Test
    void shouldSendCustomAdminMail() {
        // When
        mailService.sendCustomAdminMail("recipient@example.com", "Custom Subject", "<p>HTML body</p>");

        // Then
        verify(mailSender).send(any(MimeMessage.class));
    }

    // ============================================================
    // Helpers
    // ============================================================

    private User createUser(String email, boolean emailNotificationsEnabled) {
        User user = new User(email, "Jan", "Kowalski", "+48123456789", "jankowalski");
        user.setEmailNotificationsEnabled(emailNotificationsEnabled);
        return user;
    }

    private Reservation createReservation(User user) {
        TimeSlot slot = new TimeSlot(
                LocalDate.now().plusDays(7),
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                10
        );
        return new Reservation(user, slot);
    }
}
