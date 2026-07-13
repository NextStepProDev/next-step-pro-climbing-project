package pl.nextsteppro.climbing.infrastructure.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pl.nextsteppro.climbing.config.AdminEmailConfig;
import pl.nextsteppro.climbing.config.AppConfig;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class WaitlistMailService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistMailService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final String ADMIN_LANG = "pl";

    private final MailDispatcher mailDispatcher;
    private final AppConfig appConfig;
    private final AdminEmailConfig adminEmailConfig;
    private final MessageService msg;
    private final String siteUrl;

    public WaitlistMailService(MailDispatcher mailDispatcher, AppConfig appConfig,
                               AdminEmailConfig adminEmailConfig, MessageService msg) {
        this.mailDispatcher = mailDispatcher;
        this.appConfig = appConfig;
        this.adminEmailConfig = adminEmailConfig;
        this.msg = msg;
        this.siteUrl = appConfig.getSiteUrl();
    }

    @Async
    public void sendWaitlistOfferNotification(User user, TimeSlot slot, Instant deadline) {
        if (!user.isEmailNotificationsEnabled()) return;

        String lang = user.getPreferredLanguage();
        String deadlineFormatted = DEADLINE_FORMAT.format(deadline.atZone(WARSAW));

        String subject = msg.getForLang("email.waitlist.offer.subject", lang);
        String body = buildOfferNotificationBody(lang, user, slot, deadlineFormatted);

        sendEmail(user.getEmail(), subject, body);
    }

    @Async
    public void sendWaitlistReservationConfirmed(User user, TimeSlot slot) {
        if (!user.isEmailNotificationsEnabled()) return;

        String lang = user.getPreferredLanguage();
        String title = slot.getDisplayTitle() != null ? slot.getDisplayTitle() : "Next Step Pro Climbing";
        String googleUrl = CalendarUtils.buildGoogleCalendarUrl(title, slot.getDate(), null, slot.getStartTime(), slot.getEndTime(), null);
        byte[] ics = CalendarUtils.buildIcsFile(title, slot.getDate(), null, slot.getStartTime(), slot.getEndTime(), null, null);

        String subject = msg.getForLang("email.waitlist.confirmed.subject", lang);
        String body = buildConfirmedBody(lang, user, slot, googleUrl);

        sendEmail(user.getEmail(), subject, body, ics);
    }

    private String buildOfferNotificationBody(String lang, User user, TimeSlot slot, String deadline) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #1a1816; padding: 20px; text-align: center;">
                        <a href="%s" style="display: inline-block; text-decoration: none;"><img src="cid:logo" alt="Next Step Pro Climbing" style="height: 80px; display: block;" /></a>
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #312e2b; margin-top: 0;">%s</h2>
                        <p style="color: #333;">%s</p>
                        <div style="background: #eff6ff; border: 1px solid #bfdbfe; padding: 20px; border-radius: 8px; margin: 20px 0;">
                            <p style="margin: 0 0 8px 0;"><strong>%s</strong> %s</p>
                            <p style="margin: 0;"><strong>%s</strong> %s - %s</p>
                        </div>
                        <div style="background: #fef9c3; border: 1px solid #fde047; padding: 16px; border-radius: 8px; margin: 20px 0;">
                            <p style="margin: 0; color: #713f12; font-weight: bold;">%s</p>
                            <p style="margin: 8px 0 0 0; color: #713f12;">%s</p>
                        </div>
                        <p style="color: #333;">%s</p>
                        <p style="color: #666; font-size: 14px;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            siteUrl,
            msg.getForLang("email.waitlist.offer.greeting", lang, user.getFirstName()),
            msg.getForLang("email.waitlist.offer.body", lang),
            msg.getForLang("email.reservation.date", lang), slot.getDate().format(DATE_FORMAT),
            msg.getForLang("email.reservation.time", lang), slot.getStartTime().format(TIME_FORMAT), slot.getEndTime().format(TIME_FORMAT),
            msg.getForLang("email.waitlist.offer.deadline.label", lang),
            msg.getForLang("email.waitlist.offer.deadline", lang, deadline),
            msg.getForLang("email.waitlist.offer.action", lang),
            msg.getForLang("email.reservation.team", lang)
        );
    }

    private String buildConfirmedBody(String lang, User user, TimeSlot slot, String googleCalendarUrl) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #1a1816; padding: 20px; text-align: center;">
                        <a href="%s" style="display: inline-block; text-decoration: none;"><img src="cid:logo" alt="Next Step Pro Climbing" style="height: 80px; display: block;" /></a>
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #312e2b; margin-top: 0;">%s</h2>
                        <p style="color: #333;">%s</p>
                        <div style="background: #eff6ff; border: 1px solid #bfdbfe; padding: 20px; border-radius: 8px;">
                            <p style="margin: 0 0 8px 0;"><strong>%s</strong> %s</p>
                            <p style="margin: 0;"><strong>%s</strong> %s - %s</p>
                        </div>
                        %s
                        <p style="margin-top: 20px; color: #333;">%s</p>
                        <p style="color: #666; font-size: 14px;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            siteUrl,
            msg.getForLang("email.waitlist.confirmed.greeting", lang, user.getFirstName()),
            msg.getForLang("email.waitlist.confirmed.body", lang),
            msg.getForLang("email.reservation.date", lang), slot.getDate().format(DATE_FORMAT),
            msg.getForLang("email.reservation.time", lang), slot.getStartTime().format(TIME_FORMAT), slot.getEndTime().format(TIME_FORMAT),
            buildCalendarSection(lang, googleCalendarUrl),
            msg.getForLang("email.reservation.see.you", lang),
            msg.getForLang("email.reservation.team", lang)
        );
    }

    @Async
    public void sendEventWaitlistOfferNotification(User user, Event event, Instant deadline) {
        if (!user.isEmailNotificationsEnabled()) return;

        String lang = user.getPreferredLanguage();
        String deadlineFormatted = DEADLINE_FORMAT.format(deadline.atZone(WARSAW));

        String subject = msg.getForLang("email.event.waitlist.offer.subject", lang);
        String body = buildEventOfferBody(lang, user, event, deadlineFormatted);
        sendEmail(user.getEmail(), subject, body);
    }

    @Async
    public void sendEventWaitlistReservationConfirmed(User user, Event event) {
        if (!user.isEmailNotificationsEnabled()) return;

        String lang = user.getPreferredLanguage();
        String googleUrl = CalendarUtils.buildGoogleCalendarUrl(event.getTitle(), event.getStartDate(), event.getEndDate(), event.getStartTime(), event.getEndTime(), event.getLocation());
        byte[] ics = CalendarUtils.buildIcsFile(event.getTitle(), event.getStartDate(), event.getEndDate(), event.getStartTime(), event.getEndTime(), event.getLocation(), event.getDescription());

        String subject = msg.getForLang("email.event.waitlist.confirmed.subject", lang);
        String body = buildEventConfirmedBody(lang, user, event, googleUrl);
        sendEmail(user.getEmail(), subject, body, ics);
    }

    private String buildEventOfferBody(String lang, User user, Event event, String deadline) {
        String dates = event.getStartDate().format(DATE_FORMAT);
        if (!event.getStartDate().equals(event.getEndDate())) {
            dates += " - " + event.getEndDate().format(DATE_FORMAT);
        }
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #1a1816; padding: 20px; text-align: center;">
                        <a href="%s" style="display: inline-block; text-decoration: none;"><img src="cid:logo" alt="Next Step Pro Climbing" style="height: 80px; display: block;" /></a>
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #312e2b; margin-top: 0;">%s</h2>
                        <p style="color: #333;">%s</p>
                        <div style="background: #eff6ff; border: 1px solid #bfdbfe; padding: 20px; border-radius: 8px; margin: 20px 0;">
                            <p style="margin: 0 0 8px 0;"><strong>%s</strong></p>
                            <p style="margin: 0;"><strong>%s</strong> %s</p>
                        </div>
                        <div style="background: #fef9c3; border: 1px solid #fde047; padding: 16px; border-radius: 8px; margin: 20px 0;">
                            <p style="margin: 0; color: #713f12; font-weight: bold;">%s</p>
                            <p style="margin: 8px 0 0 0; color: #713f12;">%s</p>
                        </div>
                        <p style="color: #333;">%s</p>
                        <p style="color: #666; font-size: 14px;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            siteUrl,
            msg.getForLang("email.waitlist.offer.greeting", lang, user.getFirstName()),
            msg.getForLang("email.event.waitlist.offer.body", lang),
            event.getTitle(),
            msg.getForLang("email.reservation.date", lang), dates,
            msg.getForLang("email.waitlist.offer.deadline.label", lang),
            msg.getForLang("email.waitlist.offer.deadline", lang, deadline),
            msg.getForLang("email.waitlist.offer.action", lang),
            msg.getForLang("email.reservation.team", lang)
        );
    }

    private String buildEventConfirmedBody(String lang, User user, Event event, String googleCalendarUrl) {
        String dates = event.getStartDate().format(DATE_FORMAT);
        if (!event.getStartDate().equals(event.getEndDate())) {
            dates += " - " + event.getEndDate().format(DATE_FORMAT);
        }
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #1a1816; padding: 20px; text-align: center;">
                        <a href="%s" style="display: inline-block; text-decoration: none;"><img src="cid:logo" alt="Next Step Pro Climbing" style="height: 80px; display: block;" /></a>
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #312e2b; margin-top: 0;">%s</h2>
                        <p style="color: #333;">%s</p>
                        <div style="background: #eff6ff; border: 1px solid #bfdbfe; padding: 20px; border-radius: 8px;">
                            <p style="margin: 0 0 8px 0;"><strong>%s</strong></p>
                            <p style="margin: 0;"><strong>%s</strong> %s</p>
                        </div>
                        %s
                        <p style="margin-top: 20px; color: #333;">%s</p>
                        <p style="color: #666; font-size: 14px;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            siteUrl,
            msg.getForLang("email.waitlist.confirmed.greeting", lang, user.getFirstName()),
            msg.getForLang("email.event.waitlist.confirmed.body", lang),
            event.getTitle(),
            msg.getForLang("email.reservation.date", lang), dates,
            buildCalendarSection(lang, googleCalendarUrl),
            msg.getForLang("email.reservation.see.you", lang),
            msg.getForLang("email.reservation.team", lang)
        );
    }

    @Async
    public void sendWaitlistJoinedConfirmation(User user, TimeSlot slot) {
        if (!user.isEmailNotificationsEnabled()) return;

        String lang = user.getPreferredLanguage();
        String subject = msg.getForLang("email.waitlist.joined.subject", lang);
        String body = buildJoinedBody(lang, user, slot);

        sendEmail(user.getEmail(), subject, body);
    }

    @Async
    public void sendEventWaitlistJoinedConfirmation(User user, Event event) {
        if (!user.isEmailNotificationsEnabled()) return;

        String lang = user.getPreferredLanguage();
        String subject = msg.getForLang("email.event.waitlist.joined.subject", lang);
        String body = buildEventJoinedBody(lang, user, event);

        sendEmail(user.getEmail(), subject, body);
    }

    private String buildJoinedBody(String lang, User user, TimeSlot slot) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #1a1816; padding: 20px; text-align: center;">
                        <a href="%s" style="display: inline-block; text-decoration: none;"><img src="cid:logo" alt="Next Step Pro Climbing" style="height: 80px; display: block;" /></a>
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #312e2b; margin-top: 0;">%s</h2>
                        <p style="color: #333;">%s</p>
                        <div style="background: #eff6ff; border: 1px solid #bfdbfe; padding: 20px; border-radius: 8px;">
                            <p style="margin: 0 0 8px 0;"><strong>%s</strong> %s</p>
                            <p style="margin: 0;"><strong>%s</strong> %s - %s</p>
                        </div>
                        <p style="margin-top: 20px; color: #666; font-size: 14px;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            siteUrl,
            msg.getForLang("email.waitlist.joined.greeting", lang, user.getFirstName()),
            msg.getForLang("email.waitlist.joined.body", lang),
            msg.getForLang("email.reservation.date", lang), slot.getDate().format(DATE_FORMAT),
            msg.getForLang("email.reservation.time", lang), slot.getStartTime().format(TIME_FORMAT), slot.getEndTime().format(TIME_FORMAT),
            msg.getForLang("email.reservation.team", lang)
        );
    }

    private String buildEventJoinedBody(String lang, User user, Event event) {
        String dates = event.getStartDate().format(DATE_FORMAT);
        if (!event.getStartDate().equals(event.getEndDate())) {
            dates += " - " + event.getEndDate().format(DATE_FORMAT);
        }
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #1a1816; padding: 20px; text-align: center;">
                        <a href="%s" style="display: inline-block; text-decoration: none;"><img src="cid:logo" alt="Next Step Pro Climbing" style="height: 80px; display: block;" /></a>
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #312e2b; margin-top: 0;">%s</h2>
                        <p style="color: #333;">%s</p>
                        <div style="background: #eff6ff; border: 1px solid #bfdbfe; padding: 20px; border-radius: 8px;">
                            <p style="margin: 0 0 8px 0;"><strong>%s</strong></p>
                            <p style="margin: 0;"><strong>%s</strong> %s</p>
                        </div>
                        <p style="margin-top: 20px; color: #666; font-size: 14px;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            siteUrl,
            msg.getForLang("email.event.waitlist.joined.greeting", lang, user.getFirstName()),
            msg.getForLang("email.event.waitlist.joined.body", lang),
            event.getTitle(),
            msg.getForLang("email.reservation.date", lang), dates,
            msg.getForLang("email.reservation.team", lang)
        );
    }

    // Powiadomienia adminowe idą przez ten serwis, nie MailService — wstrzyknięcie MailService
    // do Waitlist/EventWaitlistService tworzyłoby cykl (MailService → UserService → WaitlistService).
    @Async
    public void sendWaitlistAdminNotification(User user, TimeSlot slot) {
        String subject = msg.getForLang("email.admin.waitlist.confirmed.subject", ADMIN_LANG, user.getFullName());
        String body = buildWaitlistAdminBody(user, slot);
        sendToAdmins(subject, body);
    }

    @Async
    public void sendEventWaitlistAdminNotification(User user, Event event) {
        String subject = msg.getForLang("email.admin.event.waitlist.confirmed.subject", ADMIN_LANG, user.getFullName());
        String body = buildEventWaitlistAdminBody(user, event);
        sendToAdmins(subject, body);
    }

    private String buildWaitlistAdminBody(User user, TimeSlot slot) {
        String titleLine = slot.getDisplayTitle() != null
            ? "<p><strong>%s</strong> %s</p>".formatted(msg.getForLang("email.slot.title.label", ADMIN_LANG), slot.getDisplayTitle())
            : "";
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #1a1816; padding: 20px; text-align: center;">
                        <a href="%s" style="display: inline-block; text-decoration: none;"><img src="cid:logo" alt="Next Step Pro Climbing" style="height: 80px; display: block;" /></a>
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #312e2b; margin-top: 0;">%s</h2>
                        <p style="color: #333;">%s</p>
                        <div style="background: #f5f5f5; padding: 20px; border-radius: 8px;">
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            %s
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s - %s</p>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            siteUrl,
            msg.getForLang("email.admin.waitlist.confirmed.title", ADMIN_LANG),
            msg.getForLang("email.admin.waitlist.confirmed.body", ADMIN_LANG),
            msg.getForLang("email.admin.client", ADMIN_LANG), esc(user.getFullName()),
            msg.getForLang("email.admin.email", ADMIN_LANG), user.getEmail(),
            msg.getForLang("email.admin.phone", ADMIN_LANG), user.getPhone(),
            titleLine,
            msg.getForLang("email.admin.date", ADMIN_LANG), slot.getDate().format(DATE_FORMAT),
            msg.getForLang("email.admin.time", ADMIN_LANG), slot.getStartTime().format(TIME_FORMAT), slot.getEndTime().format(TIME_FORMAT)
        );
    }

    private String buildEventWaitlistAdminBody(User user, Event event) {
        String dates = event.getStartDate().format(DATE_FORMAT);
        if (!event.getStartDate().equals(event.getEndDate())) {
            dates += " - " + event.getEndDate().format(DATE_FORMAT);
        }
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #1a1816; padding: 20px; text-align: center;">
                        <a href="%s" style="display: inline-block; text-decoration: none;"><img src="cid:logo" alt="Next Step Pro Climbing" style="height: 80px; display: block;" /></a>
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #312e2b; margin-top: 0;">%s</h2>
                        <p style="color: #333;">%s</p>
                        <div style="background: #f5f5f5; padding: 20px; border-radius: 8px;">
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            siteUrl,
            msg.getForLang("email.admin.event.waitlist.confirmed.title", ADMIN_LANG),
            msg.getForLang("email.admin.waitlist.confirmed.body", ADMIN_LANG),
            msg.getForLang("email.admin.client", ADMIN_LANG), esc(user.getFullName()),
            msg.getForLang("email.admin.email", ADMIN_LANG), user.getEmail(),
            msg.getForLang("email.admin.phone", ADMIN_LANG), user.getPhone(),
            msg.getForLang("email.admin.event.event", ADMIN_LANG), esc(event.getTitle()),
            msg.getForLang("email.admin.event.dates", ADMIN_LANG), dates
        );
    }

    private List<String> resolveAdminEmails() {
        var emails = adminEmailConfig.getAdminEmails();
        if (!emails.isEmpty()) {
            return List.copyOf(emails);
        }
        String fallback = appConfig.getMail().getFrom();
        if (fallback != null && !fallback.isBlank()) {
            log.warn("ADMIN_EMAIL is empty, falling back to MAIL_FROM: '{}'", fallback);
            return List.of(fallback.trim());
        }
        log.error("Cannot send admin notification: no admin email configured (ADMIN_EMAIL and MAIL_FROM are both empty)");
        return List.of();
    }

    private void sendToAdmins(String subject, String body) {
        for (String adminEmail : resolveAdminEmails()) {
            sendEmail(adminEmail, subject, body);
        }
    }

    private static String esc(@org.jspecify.annotations.Nullable String value) {
        return value == null ? "" : org.springframework.web.util.HtmlUtils.htmlEscape(value);
    }

    private String buildCalendarSection(String lang, String googleCalendarUrl) {
        return """
                        <div style="margin-top: 16px; padding: 14px 20px; background: #f0f9ff; border: 1px solid #bae6fd; border-radius: 8px; text-align: center;">
                            <p style="margin: 0 0 8px 0; font-size: 14px; color: #0369a1; font-weight: bold;">%s</p>
                            <a href="%s" target="_blank" style="display: inline-block; padding: 8px 20px; background: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-size: 14px; font-weight: 500;">📅 Google Calendar</a>
                            <p style="margin: 10px 0 0 0; font-size: 12px; color: #64748b;">%s</p>
                        </div>
            """.formatted(
            msg.getForLang("email.calendar.title", lang),
            googleCalendarUrl,
            msg.getForLang("email.calendar.ics.hint", lang)
        );
    }

    private void sendEmail(String to, String subject, String body) {
        sendEmail(to, subject, body, null);
    }

    private void sendEmail(String to, String subject, String body, @org.jspecify.annotations.Nullable byte[] icsAttachment) {
        mailDispatcher.sendHtml(to, subject, body, icsAttachment);
    }
}
