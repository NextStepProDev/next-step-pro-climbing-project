package pl.nextsteppro.climbing.infrastructure.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import pl.nextsteppro.climbing.config.AppConfig;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MailDispatcher} — verifies the retry-on-transient-failure behaviour
 * and the guarantee that delivery failures never propagate as exceptions.
 *
 * <p>The dispatcher is built with a zero backoff so retry paths execute instantly.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MailDispatcher Tests")
class MailDispatcherTest {

    @Mock private JavaMailSender mailSender;
    @Mock private AppConfig appConfig;
    @Mock private AppConfig.Mail mailConfig;

    private MailDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        when(appConfig.getMail()).thenReturn(mailConfig);
        when(mailConfig.getFrom()).thenReturn("noreply@nextsteppro.pl");
        // Fresh MimeMessage per attempt; mock returns null otherwise
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));

        // 2 retries (3 attempts total) with no waiting between them
        dispatcher = new MailDispatcher(mailSender, appConfig, new long[]{0L, 0L});
    }

    @Test
    @DisplayName("should send successfully on the first attempt")
    void shouldSendOnFirstAttempt() {
        dispatcher.sendHtml("user@example.com", "Subject", "<p>Body</p>");

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("should retry and succeed after a transient failure")
    void shouldRetryAndSucceedAfterTransientFailure() {
        // First send fails (transient SMTP error), second succeeds
        doThrow(new MailSendException("smtp blip"))
                .doNothing()
                .when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() ->
                dispatcher.sendHtml("user@example.com", "Subject", "<p>Body</p>"));

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("should exhaust all attempts then give up without throwing")
    void shouldGiveUpGracefullyAfterExhaustingRetries() {
        doThrow(new MailSendException("smtp down"))
                .when(mailSender).send(any(MimeMessage.class));

        // Never throws — a dropped email must not break the calling transaction
        assertDoesNotThrow(() ->
                dispatcher.sendHtml("user@example.com", "Subject", "<p>Body</p>"));

        // 1 initial attempt + 2 retries
        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }
}
