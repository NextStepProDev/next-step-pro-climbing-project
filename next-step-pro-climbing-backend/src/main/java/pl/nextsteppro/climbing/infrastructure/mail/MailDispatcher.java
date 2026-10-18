package pl.nextsteppro.climbing.infrastructure.mail;

import jakarta.mail.MessagingException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import pl.nextsteppro.climbing.config.AppConfig;

/**
 * Single point of outbound email delivery for the whole application.
 *
 * <p>Builds the MIME message (HTML plus a plain-text alternative derived by {@link MailPlainText},
 * the shared inline logo and an optional iCalendar attachment) and sends it through
 * {@link JavaMailSender}, retrying on transient SMTP failures with a fixed backoff. After all
 * attempts are exhausted it logs an ERROR carrying the {@link #FAILURE_MARKER} marker (easy to
 * alert/grep on) and <strong>never throws</strong> — a mail failure must not break the business
 * transaction that triggered it.
 *
 * <p>For durable, restart-surviving delivery a database-backed outbox would be the next step;
 * in-memory retry is sufficient for the current volume and only covers transient blips.
 */
@Component
public class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    /** Stable, greppable marker logged when an email is dropped after exhausting retries. */
    static final String FAILURE_MARKER = "MAIL_DELIVERY_FAILED";

    private static final String LOGO_RESOURCE = "static/logo/logo-white.png";
    private static final String ICS_ATTACHMENT_NAME = "reservation.ics";

    /**
     * Inline logo read from the classpath once at startup, not per email. A bulk campaign of N
     * messages would otherwise re-open and re-read the JAR entry N times; the image never changes
     * at runtime, so a single cached copy is safe and cheaper.
     */
    private static final byte[] LOGO_BYTES = loadLogoBytes();

    private static byte[] loadLogoBytes() {
        try (var in = new ClassPathResource(LOGO_RESOURCE).getInputStream()) {
            return in.readAllBytes();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Missing inline mail logo resource: " + LOGO_RESOURCE, e);
        }
    }

    /** Delay before each retry; length + 1 = total attempts. */
    private static final long[] DEFAULT_RETRY_DELAYS_MS = {2_000L, 5_000L};

    private final JavaMailSender mailSender;
    private final AppConfig appConfig;
    private final long[] retryDelaysMs;

    @Autowired
    public MailDispatcher(JavaMailSender mailSender, AppConfig appConfig) {
        this(mailSender, appConfig, DEFAULT_RETRY_DELAYS_MS);
    }

    /** Test-only constructor allowing a shortened backoff so retry paths run fast. */
    MailDispatcher(JavaMailSender mailSender, AppConfig appConfig, long[] retryDelaysMs) {
        this.mailSender = mailSender;
        this.appConfig = appConfig;
        this.retryDelaysMs = retryDelaysMs.clone();
    }

    public void sendHtml(String to, String subject, String body) {
        sendHtml(to, subject, body, null);
    }

    public void sendHtml(String to, String subject, String body, @Nullable byte[] icsAttachment) {
        send(to, subject, body, icsAttachment, null);
    }

    /**
     * Bulk mail only (newsletter, admin campaigns): carries the RFC 8058 one-click unsubscribe
     * headers on top of the footer link.
     *
     * The footer link is what a person clicks; these headers are what a MAILBOX shows — Gmail's
     * "Unsubscribe" next to the sender, and the same in Yahoo and Outlook. Since 2024 both Gmail
     * and Yahoo require them of anyone sending in volume, and providers that do not find them
     * score the message worse — which is the shape of the problem that started this: wp.pl
     * rejecting a message outright as spam.
     *
     * The endpoint behind the URL already fits the RFC: POST unsubscribes, GET only renders a
     * confirmation page, so a mailbox provider's one-click POST works while a link-scanning
     * security gateway walking the same URL changes nothing. Never put these on transactional
     * mail (verification, reservations) — that is not a mailing list and cannot be left.
     */
    public void sendNewsletterHtml(String to, String subject, String body, String unsubscribeUrl) {
        send(to, subject, body, null, unsubscribeUrl);
    }

    private void send(String to, String subject, String body, @Nullable byte[] icsAttachment,
                      @Nullable String unsubscribeUrl) {
        int maxAttempts = retryDelaysMs.length + 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                var message = mailSender.createMimeMessage();
                var helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setTo(to);
                helper.setSubject(subject);
                // multipart/alternative: clients render the HTML, the plain twin is for text-only
                // readers and for spam filters that score an HTML-only message (MIME_HTML_ONLY).
                String plain = plainTextOrNull(body);
                if (plain != null) {
                    helper.setText(plain, body);
                } else {
                    helper.setText(body, true);
                }
                helper.setFrom(appConfig.getMail().getFrom());
                helper.addInline("logo", new ByteArrayResource(LOGO_BYTES), "image/png");
                if (icsAttachment != null) {
                    helper.addAttachment(ICS_ATTACHMENT_NAME,
                            new ByteArrayResource(icsAttachment), "text/calendar");
                }
                if (unsubscribeUrl != null) {
                    message.setHeader("List-Unsubscribe", "<" + unsubscribeUrl + ">");
                    // Declares that the URL above accepts the one-click POST. Without this second
                    // header Gmail treats the address as mailto-style and does not show the button.
                    message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
                }

                mailSender.send(message);
                if (attempt > 1) {
                    log.info("Email sent to {} on attempt {}/{}", maskAddress(to), attempt, maxAttempts);
                } else {
                    log.info("Email sent to {}", maskAddress(to));
                }
                return;
            } catch (MailException | MessagingException e) {
                if (attempt < maxAttempts) {
                    long delay = retryDelaysMs[attempt - 1];
                    log.warn("Email send to {} failed (attempt {}/{}), retrying in {} ms",
                            maskAddress(to), attempt, maxAttempts, delay, e);
                    sleep(delay);
                } else {
                    log.error("{}: dropping email to {} after {} failed attempts",
                            FAILURE_MARKER, maskAddress(to), maxAttempts, e);
                }
            }
        }
    }

    /**
     * The plain part is a nicety; the mail is not. A converter bug must cost the alternative,
     * never the message — the dispatcher's whole contract is that nothing here throws.
     */
    private static @Nullable String plainTextOrNull(String html) {
        try {
            return MailPlainText.fromHtml(html);
        } catch (RuntimeException | StackOverflowError e) {
            log.warn("Could not derive the plain-text part, sending HTML only", e);
            return null;
        }
    }

    /**
     * "j***@gmail.com". The server log outlives account deletion (it rotates by size, not by date),
     * so a full address there is personal data the erasure cannot reach. The domain is what a
     * delivery failure is usually about, and the first letter is enough to match a complaint.
     */
    public static String maskAddress(String address) {
        int at = address.lastIndexOf('@');
        if (at <= 0) {
            return "***";
        }
        return address.charAt(0) + "***" + address.substring(at);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
