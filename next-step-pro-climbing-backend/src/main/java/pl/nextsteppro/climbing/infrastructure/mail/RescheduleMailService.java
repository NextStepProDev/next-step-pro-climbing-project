package pl.nextsteppro.climbing.infrastructure.mail;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import pl.nextsteppro.climbing.config.AdminEmailConfig;
import pl.nextsteppro.climbing.config.AppConfig;
import pl.nextsteppro.climbing.domain.reschedule.RescheduleTerm;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Mails of a client's request to move their booked term: the coach hears about the request, the
 * client hears the answer.
 *
 * <p>Every argument is a plain value or a {@code User} already loaded on the caller's thread
 * ({@link AsyncMailEntities}) — these run on the mail thread after the request's session is gone.
 *
 * <p>The accepted mail goes ONLY to the requester. Everybody else on the moved term gets the
 * ordinary "your term changed" mail from {@code AdminService}, which never says who asked: their
 * term moved because the coach moved it, and naming a fellow client would hand out their name.
 */
@Service
public class RescheduleMailService {

    private static final Logger log = LoggerFactory.getLogger(RescheduleMailService.class);
    private static final String ADMIN_LANG = "pl";

    private final MailDispatcher mailDispatcher;
    private final AppConfig appConfig;
    private final AdminEmailConfig adminEmailConfig;
    private final MessageService msg;
    private final String siteUrl;

    public RescheduleMailService(MailDispatcher mailDispatcher, AppConfig appConfig,
                                 AdminEmailConfig adminEmailConfig, MessageService msg) {
        this.mailDispatcher = mailDispatcher;
        this.appConfig = appConfig;
        this.adminEmailConfig = adminEmailConfig;
        this.msg = msg;
        this.siteUrl = appConfig.getSiteUrl();
    }

    /**
     * @param comment         already HTML-escaped at save time — not escaped again
     * @param otherPeople     names of the other registered participants — the move takes them along
     * @param otherGuests     guest seats on the term (no account, so no name)
     */
    @Async("mailExecutor")
    public void sendRequestToAdmins(User requester, String title, RescheduleTerm oldTerm, RescheduleTerm newTerm,
                                    @Nullable String comment, List<String> otherPeople, int otherGuests) {
        String subject = msg.getForLang("email.reschedule.admin.subject", ADMIN_LANG, requester.getFullName());
        String commentLine = (comment != null && !comment.isBlank())
            ? "<p><strong>%s</strong> %s</p>".formatted(msg.getForLang("email.admin.comment", ADMIN_LANG), comment)
            : "";
        String othersBox = "";
        if (!otherPeople.isEmpty() || otherGuests > 0) {
            StringBuilder names = new StringBuilder();
            for (String name : otherPeople) {
                if (!names.isEmpty()) names.append(", ");
                names.append(esc(name));
            }
            if (otherGuests > 0) {
                if (!names.isEmpty()) names.append(", ");
                names.append(esc(msg.getForLang("email.reschedule.admin.guests", ADMIN_LANG, otherGuests)));
            }
            othersBox = """
                <div style="margin-top: 16px; padding: 12px 16px; background: #fffbeb; border: 1px solid #fcd34d; border-radius: 8px; color: #92400e; font-size: 14px;">
                    <strong>%s</strong> %s
                </div>
                """.formatted(esc(msg.getForLang("email.reschedule.admin.others", ADMIN_LANG)), names);
        }
        String body = page("""
                        <h2 style="color: #312e2b; margin-top: 0;">%s</h2>
                        <div style="background: #f5f5f5; padding: 20px; border-radius: 8px;">
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            %s
                            %s
                        </div>
                        %s
                        <div style="margin-top: 20px; text-align: center;">
                            <a href="%s" style="display: inline-block; padding: 12px 24px; background: #1a1816; color: white; text-decoration: none; border-radius: 8px; font-weight: bold;">%s</a>
                        </div>
            """.formatted(
            esc(msg.getForLang("email.reschedule.admin.title", ADMIN_LANG)),
            msg.getForLang("email.admin.client", ADMIN_LANG), esc(requester.getFullName()),
            msg.getForLang("email.admin.email", ADMIN_LANG), esc(requester.getEmail()),
            msg.getForLang("email.admin.phone", ADMIN_LANG), esc(requester.getPhone()),
            msg.getForLang("email.reschedule.entry", ADMIN_LANG), esc(title),
            changeRow(ADMIN_LANG, oldTerm, newTerm),
            commentLine,
            othersBox,
            siteUrl + "/admin/requests",
            esc(msg.getForLang("email.reschedule.admin.cta", ADMIN_LANG))
        ));
        for (String adminEmail : resolveAdminEmails()) {
            mailDispatcher.sendHtml(adminEmail, subject, body, null);
        }
    }

    /** To the requester: what moved, from when to when, and the new term for their calendar. */
    @Async("mailExecutor")
    public void sendAccepted(User user, String title, @Nullable String location,
                             RescheduleTerm oldTerm, RescheduleTerm newTerm) {
        if (!user.isEmailNotificationsEnabled()) return;
        String lang = user.getPreferredLanguage();
        String googleUrl = CalendarUtils.buildGoogleCalendarUrl(title, newTerm.date(), newTerm.endDate(),
            newTerm.startTime(), newTerm.endTime(), location);
        byte[] ics = CalendarUtils.buildIcsFile(title, newTerm.date(), newTerm.endDate(),
            newTerm.startTime(), newTerm.endTime(), location, null);

        String subject = msg.getForLang("email.reschedule.accepted.subject", lang);
        String body = page("""
                        <h2 style="color: #312e2b; margin-top: 0;">%s</h2>
                        <p style="color: #333;">%s</p>
                        <div style="background: #f0fdf4; border: 1px solid #bbf7d0; padding: 20px; border-radius: 8px;">
                            <p style="margin: 0 0 12px 0;"><strong>%s</strong> %s</p>
                            %s
                        </div>
                        <div style="margin-top: 16px; padding: 14px 20px; background: #f0f9ff; border: 1px solid #bae6fd; border-radius: 8px; text-align: center;">
                            <p style="margin: 0 0 8px 0; font-size: 14px; color: #0369a1; font-weight: bold;">%s</p>
                            <a href="%s" target="_blank" style="display: inline-block; padding: 8px 20px; background: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-size: 14px; font-weight: 500;">Google Calendar</a>
                            <p style="margin: 10px 0 0 0; font-size: 12px; color: #64748b;">%s</p>
                        </div>
                        <p style="margin-top: 20px; color: #333;">%s</p>
                        <p style="color: #666; font-size: 14px;">%s</p>
            """.formatted(
            msg.getForLang("email.reschedule.greeting", lang, esc(user.getFirstName())),
            esc(msg.getForLang("email.reschedule.accepted.body", lang)),
            msg.getForLang("email.reschedule.entry", lang), esc(title),
            changeRow(lang, oldTerm, newTerm),
            esc(msg.getForLang("email.calendar.title", lang)),
            esc(googleUrl),
            esc(msg.getForLang("email.calendar.ics.hint", lang)),
            esc(msg.getForLang("email.reschedule.accepted.closing", lang)),
            esc(msg.getForLang("email.reservation.team", lang))
        ));
        mailDispatcher.sendHtml(user.getEmail(), subject, body, ics);
    }

    /** To the requester: the booking stays where it was, with the coach's note when there is one. */
    @Async("mailExecutor")
    public void sendRejected(User user, String title, RescheduleTerm oldTerm, RescheduleTerm newTerm,
                             @Nullable String note) {
        if (!user.isEmailNotificationsEnabled()) return;
        String lang = user.getPreferredLanguage();
        String subject = msg.getForLang("email.reschedule.rejected.subject", lang);
        String noteSection = (note != null && !note.isBlank())
            ? """
              <p style="margin-top: 16px; margin-bottom: 4px; color: #333;"><strong>%s</strong></p>
              <p style="margin: 0; color: #333;">%s</p>
              """.formatted(esc(msg.getForLang("email.reschedule.rejected.note", lang)), esc(note))
            : "";
        String body = page("""
                        <h2 style="color: #312e2b; margin-top: 0;">%s</h2>
                        <p style="color: #333;">%s</p>
                        <div style="background: #f5f5f5; padding: 20px; border-radius: 8px;">
                            <p style="margin: 0 0 8px 0;"><strong>%s</strong> %s</p>
                            <p style="margin: 0 0 8px 0;"><strong>%s</strong> %s</p>
                            <p style="margin: 0;"><strong>%s</strong> <span style="text-decoration: line-through; color: #6b7280;">%s</span></p>
                        </div>
                        %s
                        <p style="margin-top: 20px; color: #333;">%s</p>
                        <p style="color: #666; font-size: 14px;">%s</p>
            """.formatted(
            msg.getForLang("email.reschedule.greeting", lang, esc(user.getFirstName())),
            esc(msg.getForLang("email.reschedule.rejected.body", lang)),
            msg.getForLang("email.reschedule.entry", lang), esc(title),
            msg.getForLang("email.reschedule.rejected.stays", lang), esc(oldTerm.label()),
            msg.getForLang("email.reschedule.rejected.asked", lang), esc(newTerm.label()),
            noteSection,
            esc(msg.getForLang("email.reschedule.rejected.closing", lang)),
            esc(msg.getForLang("email.reservation.team", lang))
        ));
        mailDispatcher.sendHtml(user.getEmail(), subject, body, null);
    }

    private String changeRow(String lang, RescheduleTerm oldTerm, RescheduleTerm newTerm) {
        return """
            <div style="margin: 0; font-size: 14px;">
                <strong>%s</strong>
                <span style="color: #6b7280; text-decoration: line-through; margin-left: 6px;">%s</span>
                <span style="margin: 0 6px; color: #374151;">→</span>
                <span style="color: #15803d; font-weight: bold;">%s</span>
            </div>
            """.formatted(esc(msg.getForLang("email.reschedule.term", lang)),
            esc(oldTerm.label()), esc(newTerm.label()));
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
        log.error("Cannot send reschedule request notification: no admin email configured");
        return List.of();
    }

    private static String esc(@Nullable String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name());
    }
}
