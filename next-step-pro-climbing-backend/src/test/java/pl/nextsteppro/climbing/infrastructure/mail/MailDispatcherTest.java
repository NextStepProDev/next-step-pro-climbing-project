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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    @DisplayName("newsletter mail carries the RFC 8058 one-click unsubscribe headers")
    void shouldSetOneClickUnsubscribeHeadersOnNewsletterMail() throws Exception {
        var sent = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);

        dispatcher.sendNewsletterHtml("user@example.com", "Subject", "<p>Body</p>",
                "https://nextsteppro.pl/api/user/unsubscribe?token=abc");

        verify(mailSender).send(sent.capture());
        assertArrayEquals(new String[]{"<https://nextsteppro.pl/api/user/unsubscribe?token=abc>"},
                sent.getValue().getHeader("List-Unsubscribe"));
        assertArrayEquals(new String[]{"List-Unsubscribe=One-Click"},
                sent.getValue().getHeader("List-Unsubscribe-Post"));
    }

    @Test
    @DisplayName("transactional mail carries no unsubscribe headers — it is not a mailing list")
    void shouldNotSetUnsubscribeHeadersOnTransactionalMail() throws Exception {
        var sent = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);

        dispatcher.sendHtml("user@example.com", "Verify your email", "<p>Body</p>");

        verify(mailSender).send(sent.capture());
        assertNull(sent.getValue().getHeader("List-Unsubscribe"));
        assertNull(sent.getValue().getHeader("List-Unsubscribe-Post"));
    }

    @Test
    @DisplayName("should send the HTML untouched with a plain-text alternative before it, keeping logo and ICS")
    void shouldSendPlainTextAlternativeAlongsideHtml() throws Exception {
        var sent = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        String html = "<html><body><a href=\"https://nextsteppro.pl\"><img src=\"cid:logo\" alt=\"Logo\"/></a>"
                + "<p>Body</p><a href=\"https://nextsteppro.pl/x\">Open</a></body></html>";

        dispatcher.sendHtml("user@example.com", "Subject", html, new byte[]{1, 2, 3});

        verify(mailSender).send(sent.capture());
        MimeMessage message = sent.getValue();
        message.saveChanges();
        var parts = new java.util.ArrayList<jakarta.mail.Part>();
        collectLeaves(message, parts);

        var types = parts.stream().map(p -> {
            try {
                return p.getContentType().split(";")[0].trim().toLowerCase();
            } catch (jakarta.mail.MessagingException e) {
                throw new IllegalStateException(e);
            }
        }).toList();
        // Order matters: clients show the LAST alternative they can render, so HTML must follow plain.
        assertEquals(java.util.List.of("text/plain", "text/html", "image/png", "text/calendar"), types);
        assertEquals("Body\n\nOpen (https://nextsteppro.pl/x)\n", parts.get(0).getContent());
        assertEquals(html, parts.get(1).getContent());
        assertArrayEquals(new String[]{"<logo>"}, parts.get(2).getHeader("Content-ID"));
    }

    private static void collectLeaves(jakarta.mail.Part part, java.util.List<jakarta.mail.Part> out) throws Exception {
        if (part.getContent() instanceof jakarta.mail.Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                collectLeaves(multipart.getBodyPart(i), out);
            }
        } else {
            out.add(part);
        }
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
