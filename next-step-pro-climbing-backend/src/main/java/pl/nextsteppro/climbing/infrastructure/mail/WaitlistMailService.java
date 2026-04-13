package pl.nextsteppro.climbing.infrastructure.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pl.nextsteppro.climbing.config.AppConfig;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class WaitlistMailService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistMailService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final JavaMailSender mailSender;
    private final AppConfig appConfig;
    private final MessageService msg;

    public WaitlistMailService(JavaMailSender mailSender, AppConfig appConfig, MessageService msg) {
        this.mailSender = mailSender;
        this.appConfig = appConfig;
        this.msg = msg;
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
        String subject = msg.getForLang("email.waitlist.confirmed.subject", lang);
        String body = buildConfirmedBody(lang, user, slot);

        sendEmail(user.getEmail(), subject, body);
    }

    private String buildOfferNotificationBody(String lang, User user, TimeSlot slot, String deadline) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #0f0f1a; padding: 20px; text-align: center;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #1a1a2e; margin-top: 0;">%s</h2>
                        <p style="color: #333;">%s</p>
                        <div style="background: #1a1a2e; color: white; padding: 20px; border-radius: 8px; margin: 20px 0;">
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

    private String buildConfirmedBody(String lang, User user, TimeSlot slot) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #0f0f1a; padding: 20px; text-align: center;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #1a1a2e; margin-top: 0;">%s</h2>
                        <p style="color: #333;">%s</p>
                        <div style="background: #1a1a2e; color: white; padding: 20px; border-radius: 8px;">
                            <p style="margin: 0 0 8px 0;"><strong>%s</strong> %s</p>
                            <p style="margin: 0;"><strong>%s</strong> %s - %s</p>
                        </div>
                        <p style="margin-top: 20px; color: #333;">%s</p>
                        <p style="color: #666; font-size: 14px;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            msg.getForLang("email.waitlist.confirmed.greeting", lang, user.getFirstName()),
            msg.getForLang("email.waitlist.confirmed.body", lang),
            msg.getForLang("email.reservation.date", lang), slot.getDate().format(DATE_FORMAT),
            msg.getForLang("email.reservation.time", lang), slot.getStartTime().format(TIME_FORMAT), slot.getEndTime().format(TIME_FORMAT),
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
        String subject = msg.getForLang("email.event.waitlist.confirmed.subject", lang);
        String body = buildEventConfirmedBody(lang, user, event);
        sendEmail(user.getEmail(), subject, body);
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
                    <div style="background: #0f0f1a; padding: 20px; text-align: center;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #1a1a2e; margin-top: 0;">%s</h2>
                        <p style="color: #333;">%s</p>
                        <div style="background: #1a1a2e; color: white; padding: 20px; border-radius: 8px; margin: 20px 0;">
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

    private String buildEventConfirmedBody(String lang, User user, Event event) {
        String dates = event.getStartDate().format(DATE_FORMAT);
        if (!event.getStartDate().equals(event.getEndDate())) {
            dates += " - " + event.getEndDate().format(DATE_FORMAT);
        }
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #0f0f1a; padding: 20px; text-align: center;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #1a1a2e; margin-top: 0;">%s</h2>
                        <p style="color: #333;">%s</p>
                        <div style="background: #1a1a2e; color: white; padding: 20px; border-radius: 8px;">
                            <p style="margin: 0 0 8px 0;"><strong>%s</strong></p>
                            <p style="margin: 0;"><strong>%s</strong> %s</p>
                        </div>
                        <p style="margin-top: 20px; color: #333;">%s</p>
                        <p style="color: #666; font-size: 14px;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            msg.getForLang("email.waitlist.confirmed.greeting", lang, user.getFirstName()),
            msg.getForLang("email.event.waitlist.confirmed.body", lang),
            event.getTitle(),
            msg.getForLang("email.reservation.date", lang), dates,
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
                    <div style="background: #0f0f1a; padding: 20px; text-align: center;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #1a1a2e; margin-top: 0;">%s</h2>
                        <p style="color: #333;">%s</p>
                        <div style="background: #1a1a2e; color: white; padding: 20px; border-radius: 8px;">
                            <p style="margin: 0 0 8px 0;"><strong>%s</strong> %s</p>
                            <p style="margin: 0;"><strong>%s</strong> %s - %s</p>
                        </div>
                        <p style="margin-top: 20px; color: #666; font-size: 14px;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
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
                    <div style="background: #0f0f1a; padding: 20px; text-align: center;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #1a1a2e; margin-top: 0;">%s</h2>
                        <p style="color: #333;">%s</p>
                        <div style="background: #1a1a2e; color: white; padding: 20px; border-radius: 8px;">
                            <p style="margin: 0 0 8px 0;"><strong>%s</strong></p>
                            <p style="margin: 0;"><strong>%s</strong> %s</p>
                        </div>
                        <p style="margin-top: 20px; color: #666; font-size: 14px;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            msg.getForLang("email.event.waitlist.joined.greeting", lang, user.getFirstName()),
            msg.getForLang("email.event.waitlist.joined.body", lang),
            event.getTitle(),
            msg.getForLang("email.reservation.date", lang), dates,
            msg.getForLang("email.reservation.team", lang)
        );
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            helper.setFrom(appConfig.getMail().getFrom());

            var logoResource = new org.springframework.core.io.ClassPathResource("static/logo/logo-black.png");
            helper.addInline("logo", logoResource, "image/png");

            mailSender.send(message);
            log.info("Waitlist email sent to: {}", to);
        } catch (MailException | jakarta.mail.MessagingException e) {
            log.error("Failed to send waitlist email to: {}", to, e);
        }
    }
}
