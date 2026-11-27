package pl.nextsteppro.climbing.infrastructure.mail;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import pl.nextsteppro.climbing.config.AdminEmailConfig;
import pl.nextsteppro.climbing.config.AppConfig;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Mails of the "write to us" conversation: the team hears about a new message, the person hears
 * the team's answer.
 *
 * <p>Every argument is a plain value, never an entity: these run on the mail thread after the
 * request's persistence context is gone, and a value cannot be a lazy proxy.
 *
 * <p>Nothing here mails the WRITER about their own message. A "we got your message" receipt
 * would let anyone make this site mail any address they type, which is how a sending domain ends
 * up on a blocklist. The confirmation is on the screen.
 */
@Service
public class ContactMailService {

    private static final Logger log = LoggerFactory.getLogger(ContactMailService.class);
    private static final String ADMIN_LANG = "pl";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** What an inbound message was about, as plain values (see the class note). */
    public record Context(String type, @Nullable UUID courseId, @Nullable String courseTitle,
                          @Nullable LocalDate day) {

        public static Context general() {
            return new Context("GENERAL", null, null, null);
        }
    }

    private final MailDispatcher mailDispatcher;
    private final AppConfig appConfig;
    private final AdminEmailConfig adminEmailConfig;
    private final MessageService msg;
    private final String siteUrl;

    public ContactMailService(MailDispatcher mailDispatcher, AppConfig appConfig,
                              AdminEmailConfig adminEmailConfig, MessageService msg) {
        this.mailDispatcher = mailDispatcher;
        this.appConfig = appConfig;
        this.adminEmailConfig = adminEmailConfig;
        this.msg = msg;
        this.siteUrl = appConfig.getSiteUrl();
    }

    /**
     * Forwards a guest's new message to the team. Reply-To is the writer, so "Reply" in the team's
     * inbox answers them directly - that path works even if nobody ever opens the panel.
     * An account holder's message is never mailed: that conversation lives in the app only.
     */
    @Async("mailExecutor")
    public void sendInboundToStaff(UUID conversationId, String senderName, String senderEmail,
                                   String body, Context context, String language) {
        String subject = msg.getForLang("email.contact.admin.subject", ADMIN_LANG,
            subjectTag(context), senderName);
        String contextLine = contextLine(context);
        String accountLine = esc(msg.getForLang("email.contact.admin.guest", ADMIN_LANG));
        String html = page("""
                        <h2 style="color: #312e2b; margin-top: 0;">%s</h2>
                        <div style="background: #f5f5f5; padding: 20px; border-radius: 8px;">
                            <p style="margin: 0 0 8px 0;"><strong>%s</strong> %s</p>
                            <p style="margin: 0 0 8px 0;"><strong>%s</strong> %s</p>
                            <p style="margin: 0 0 8px 0; color: #6b7280; font-size: 13px;">%s · %s</p>
                            %s
                        </div>
                        <div style="margin-top: 16px; padding: 16px; border-left: 3px solid #334155; color: #333;">%s</div>
                        <p style="margin-top: 16px; color: #6b7280; font-size: 13px;">%s</p>
                        <div style="margin-top: 20px; text-align: center;">
                            <a href="%s" style="display: inline-block; padding: 12px 28px; background: #334155; color: white; text-decoration: none; border-radius: 8px; font-size: 15px; font-weight: bold;">%s</a>
                        </div>
            """.formatted(
            esc(msg.getForLang("email.contact.admin.title", ADMIN_LANG)),
            esc(msg.getForLang("email.admin.client", ADMIN_LANG)), esc(senderName),
            esc(msg.getForLang("email.admin.email", ADMIN_LANG)), esc(senderEmail),
            accountLine, esc(msg.getForLang("email.contact.admin.language", ADMIN_LANG, language.toUpperCase())),
            contextLine,
            multiline(body),
            esc(msg.getForLang("email.contact.admin.replyHint", ADMIN_LANG)),
            siteUrl + "/admin/messages?conversation=" + conversationId,
            esc(msg.getForLang("email.contact.admin.cta", ADMIN_LANG))
        ));
        for (String to : resolveAdminEmails()) {
            mailDispatcher.sendHtmlWithReplyTo(to, subject, html, senderEmail);
        }
    }

    /**
     * The team's answer to a guest - mail is the only place they can read it. A reply to this
     * mail lands in the team's inbox (From is the team). Account holders never get this mail:
     * their answer waits in the app.
     *
     * <p>The answer is written in the panel's rich-text editor, so it goes through
     * {@link MailRichText} - the server twin of the front's renderer, which escapes on its own -
     * or the guest would read raw {@code **markers**}.
     */
    @Async("mailExecutor")
    public void sendReply(String to, String recipientName, String body, String language) {
        String subject = msg.getForLang("email.contact.reply.subject", language);
        String html = page("""
                        <h2 style="color: #312e2b; margin-top: 0;">%s</h2>
                        <div style="padding: 16px; border-left: 3px solid #334155; color: #333;">%s</div>
                        <p style="margin-top: 16px; color: #6b7280; font-size: 13px;">%s</p>
                        <p style="color: #666; font-size: 14px; margin-top: 20px;">%s</p>
            """.formatted(
            esc(msg.getForLang("email.contact.reply.greeting", language, recipientName)),
            MailRichText.toHtml(body, "#312e2b"),
            esc(msg.getForLang("email.contact.reply.hint", language)),
            esc(msg.getForLang("email.reservation.team", language))
        ));
        mailDispatcher.sendHtml(to, subject, html);
    }

    /**
     * An account holder has not opened an answer from the team for 48 h. Deliberately WITHOUT the
     * answer: the chat stays in the app, and what people discuss there (health, money) does not
     * end up in a mailbox. A button to the chat, and where to turn these mails off.
     */
    @Async("mailExecutor")
    public void sendUnseenReminder(String to, String firstName, String language) {
        String subject = msg.getForLang("email.contact.unseen.subject", language);
        String html = page("""
                        <h2 style="color: #312e2b; margin-top: 0;">%s</h2>
                        <p style="color: #333; font-size: 15px;">%s</p>
                        <div style="margin-top: 20px; text-align: center;">
                            <a href="%s" style="display: inline-block; padding: 12px 28px; background: #334155; color: white; text-decoration: none; border-radius: 8px; font-size: 15px; font-weight: bold;">%s</a>
                        </div>
                        <p style="margin-top: 20px; color: #6b7280; font-size: 13px;">%s</p>
                        <p style="color: #666; font-size: 14px; margin-top: 20px;">%s</p>
            """.formatted(
            esc(msg.getForLang("email.contact.unseen.greeting", language, firstName)),
            esc(msg.getForLang("email.contact.unseen.body", language)),
            siteUrl + "/my-reservations?tab=messages",
            esc(msg.getForLang("email.contact.unseen.cta", language)),
            esc(msg.getForLang("email.contact.unseen.hint", language)),
            esc(msg.getForLang("email.reservation.team", language))
        ));
        mailDispatcher.sendHtml(to, subject, html);
    }

    private String subjectTag(Context context) {
        return switch (context.type()) {
            case "COURSE" -> msg.getForLang("email.contact.tag.course", ADMIN_LANG, nullToEmpty(context.courseTitle()));
            case "CALENDAR" -> context.day() != null
                ? msg.getForLang("email.contact.tag.calendar", ADMIN_LANG, context.day().format(DATE_FORMAT))
                : msg.getForLang("email.contact.tag.calendarAny", ADMIN_LANG);
            default -> msg.getForLang("email.contact.tag.general", ADMIN_LANG);
        };
    }

    private String contextLine(Context context) {
        String label = esc(msg.getForLang("email.contact.admin.about", ADMIN_LANG));
        return switch (context.type()) {
            case "COURSE" -> {
                String title = esc(context.courseTitle());
                String linked = context.courseId() != null
                    ? "<a href=\"%s\" style=\"color: #334155;\">%s</a>".formatted(siteUrl + "/kursy/" + context.courseId(), title)
                    : title;
                yield "<p style=\"margin: 0;\"><strong>%s</strong> %s %s</p>".formatted(label,
                    esc(msg.getForLang("email.contact.about.course", ADMIN_LANG)), linked);
            }
            case "CALENDAR" -> {
                LocalDate day = context.day();
                if (day == null) {
                    yield "<p style=\"margin: 0;\"><strong>%s</strong> <a href=\"%s\" style=\"color: #334155;\">%s</a></p>"
                        .formatted(label, siteUrl + "/calendar",
                            esc(msg.getForLang("email.contact.about.calendarAny", ADMIN_LANG)));
                }
                String linked = "<a href=\"%s\" style=\"color: #334155;\">%s</a>"
                    .formatted(siteUrl + "/calendar?date=" + day, day.format(DATE_FORMAT));
                yield "<p style=\"margin: 0;\"><strong>%s</strong> %s %s</p>".formatted(label,
                    esc(msg.getForLang("email.contact.about.calendar", ADMIN_LANG)), linked);
            }
            default -> "<p style=\"margin: 0;\"><strong>%s</strong> %s</p>".formatted(label,
                esc(msg.getForLang("email.contact.about.general", ADMIN_LANG)));
        };
    }

    /**
     * Escaped, with line breaks as {@code <br>}: a mail client may ignore {@code white-space}, and
     * the plain-text twin is derived from the HTML, where a bare newline is just whitespace.
     */
    private static String multiline(String text) {
        return esc(text.replace("\r\n", "\n").replace("\r", "\n")).replace("\n", "<br>");
    }

    private String page(String content) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #1a1816; padding: 20px; text-align: center;">
                        <a href="%s" style="display: inline-block; text-decoration: none; cursor: pointer; line-height: 0; font-size: 0;"><img src="cid:logo" alt="Next Step Pro Climbing" style="height: 100px; display: block; border: 0;" /></a>
                    </div>
                    <div style="padding: 30px;">
            %s
                    </div>
                </div>
            </body>
            </html>
            """.formatted(siteUrl, content);
    }

    private List<String> resolveAdminEmails() {
        var emails = adminEmailConfig.getAdminEmails();
        if (!emails.isEmpty()) {
            return List.copyOf(emails);
        }
        String fallback = appConfig.getMail().getFrom();
        if (fallback != null && !fallback.isBlank()) {
            return List.of(fallback.trim());
        }
        log.error("Cannot forward a contact message: no admin email configured");
        return List.of();
    }

    private static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static String esc(@Nullable String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name());
    }
}
