package pl.nextsteppro.climbing.infrastructure.mail;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pl.nextsteppro.climbing.config.AppConfig;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import jakarta.annotation.PostConstruct;
import java.time.format.DateTimeFormatter;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String ADMIN_LANG = "pl";

    private final JavaMailSender mailSender;
    private final AppConfig appConfig;
    private final MessageService msg;

    public MailService(JavaMailSender mailSender, AppConfig appConfig, MessageService msg) {
        this.mailSender = mailSender;
        this.appConfig = appConfig;
        this.msg = msg;
    }

    @PostConstruct
    void logMailConfiguration() {
        String adminEmail = appConfig.getAdmin().getEmail();
        String fromEmail = appConfig.getMail().getFrom();
        log.info("Mail configuration — admin notifications to: '{}', from: '{}'", adminEmail, fromEmail);
        if (adminEmail == null || adminEmail.isBlank()) {
            log.error("ADMIN_EMAIL is not configured! Admin notifications will NOT be sent.");
        }
    }

    @Async
    public void sendReservationConfirmation(Reservation reservation) {
        User user = reservation.getUser();
        if (!user.isEmailNotificationsEnabled()) return;

        String lang = user.getPreferredLanguage();
        TimeSlot slot = reservation.getTimeSlot();

        String subject = msg.get("email.reservation.subject", lang);
        String body = buildReservationConfirmationBody(lang, user, slot);

        sendEmail(user.getEmail(), subject, body, null);
    }

    @Async
    public void sendAdminNotification(Reservation reservation) {
        String adminEmail = resolveAdminEmail();
        if (adminEmail == null) return;

        log.info("Sending admin notification to: '{}' for reservation {}", adminEmail, reservation.getId());

        User user = reservation.getUser();
        TimeSlot slot = reservation.getTimeSlot();

        String subject = msg.get("email.admin.new.reservation.subject", ADMIN_LANG, user.getFullName());
        String body = buildAdminNotificationBody(user, slot, reservation.getComment(), reservation.getParticipants());

        sendEmail(adminEmail, subject, body, null);
    }

    @Async
    public void sendCancellationConfirmation(Reservation reservation) {
        User user = reservation.getUser();
        if (!user.isEmailNotificationsEnabled()) return;

        String lang = user.getPreferredLanguage();
        TimeSlot slot = reservation.getTimeSlot();

        String subject = msg.get("email.cancellation.subject", lang);
        String body = buildCancellationBody(lang, user, slot);

        sendEmail(user.getEmail(), subject, body, null);
    }

    @Async
    public void sendEventReservationConfirmation(User user, Event event) {
        if (!user.isEmailNotificationsEnabled()) return;

        String lang = user.getPreferredLanguage();
        String subject = msg.get("email.event.reservation.subject", lang);
        String body = buildEventReservationConfirmationBody(lang, user, event);

        sendEmail(user.getEmail(), subject, body, null);
    }

    @Async
    public void sendEventAdminNotification(User user, Event event, int participants) {
        String adminEmail = resolveAdminEmail();
        if (adminEmail == null) return;

        log.info("Sending event admin notification to: '{}' for event {}", adminEmail, event.getId());

        String subject = msg.get("email.admin.event.subject", ADMIN_LANG, user.getFullName());
        String body = buildEventAdminNotificationBody(user, event, participants);

        sendEmail(adminEmail, subject, body, null);
    }

    @Async
    public void sendAdminCancellationNotification(Reservation reservation) {
        User user = reservation.getUser();
        if (!user.isEmailNotificationsEnabled()) return;

        String lang = user.getPreferredLanguage();
        TimeSlot slot = reservation.getTimeSlot();

        String subject = msg.get("email.admin.cancel.subject", lang);
        String body = buildAdminCancellationBody(lang, user, slot);

        sendEmail(user.getEmail(), subject, body, null);
    }

    @Async
    public void sendAdminEventCancellationNotification(User user, Event event) {
        if (!user.isEmailNotificationsEnabled()) return;

        String lang = user.getPreferredLanguage();
        String subject = msg.get("email.admin.event.cancel.subject", lang);
        String body = buildAdminEventCancellationBody(lang, user, event);

        sendEmail(user.getEmail(), subject, body, null);
    }

    @Async
    public void sendUserCancellationAdminNotification(Reservation reservation) {
        String adminEmail = resolveAdminEmail();
        if (adminEmail == null) return;

        log.info("Sending user cancellation admin notification to: '{}'", adminEmail);

        User user = reservation.getUser();
        TimeSlot slot = reservation.getTimeSlot();

        String subject = msg.get("email.user.cancel.admin.subject", ADMIN_LANG,
            user.getFullName(),
            slot.getDate().format(DATE_FORMAT),
            slot.getStartTime().format(TIME_FORMAT));
        String body = buildUserCancellationAdminBody(user, slot);

        sendEmail(adminEmail, subject, body, null);
    }

    @Async
    public void sendUserEventCancellationAdminNotification(User user, Event event) {
        String adminEmail = resolveAdminEmail();
        if (adminEmail == null) return;

        log.info("Sending user event cancellation admin notification to: '{}'", adminEmail);

        String subject = msg.get("email.user.event.cancel.admin.subject", ADMIN_LANG,
            user.getFullName(), event.getTitle());
        String body = buildUserEventCancellationAdminBody(user, event);

        sendEmail(adminEmail, subject, body, null);
    }

    @Async
    public void sendEventCancellationConfirmation(User user, Event event) {
        if (!user.isEmailNotificationsEnabled()) return;

        String lang = user.getPreferredLanguage();
        String subject = msg.get("email.event.cancellation.subject", lang);
        String body = buildEventCancellationBody(lang, user, event);

        sendEmail(user.getEmail(), subject, body, null);
    }

    private @Nullable String resolveAdminEmail() {
        String email = appConfig.getAdmin().getEmail();
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        String fallback = appConfig.getMail().getFrom();
        if (fallback != null && !fallback.isBlank()) {
            log.warn("ADMIN_EMAIL is empty, falling back to MAIL_FROM: '{}'", fallback);
            return fallback.trim();
        }
        log.error("Cannot send admin notification: no admin email configured (ADMIN_EMAIL and MAIL_FROM are both empty)");
        return null;
    }

    private void sendEmail(String to, String subject, String body, @Nullable byte[] icsAttachment) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true);

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            helper.setFrom(appConfig.getMail().getFrom());

            var logoResource = new org.springframework.core.io.ClassPathResource("static/logo/logo-black.png");
            helper.addInline("logo", logoResource, "image/png");

            if (icsAttachment != null) {
                helper.addAttachment("reservation.ics", () -> new java.io.ByteArrayInputStream(icsAttachment), "text/calendar");
            }

            mailSender.send(message);
            log.info("Email sent to: {}", to);
        } catch (MailException | jakarta.mail.MessagingException e) {
            log.error("Failed to send email to: {}", to, e);
        }
    }

    private String buildReservationConfirmationBody(String lang, User user, TimeSlot slot) {
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
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s - %s</p>
                        </div>
                        <p style="margin-top: 20px; color: #333;">%s</p>
                        <p style="color: #666; font-size: 14px;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            msg.get("email.reservation.greeting", lang, user.getFirstName()),
            msg.get("email.reservation.body", lang),
            msg.get("email.reservation.date", lang),
            slot.getDate().format(DATE_FORMAT),
            msg.get("email.reservation.time", lang),
            slot.getStartTime().format(TIME_FORMAT),
            slot.getEndTime().format(TIME_FORMAT),
            msg.get("email.reservation.see.you", lang),
            msg.get("email.reservation.team", lang)
        );
    }

    private String buildAdminNotificationBody(User user, TimeSlot slot, @Nullable String comment, int participants) {
        String commentLine = (comment != null && !comment.isBlank())
            ? "<p><strong>%s</strong> %s</p>".formatted(msg.get("email.admin.comment", ADMIN_LANG), comment)
            : "";
        String participantsLine = participants > 1
            ? "<p><strong>%s</strong> %d</p>".formatted(msg.get("email.admin.participants", ADMIN_LANG), participants)
            : "";
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #0f0f1a; padding: 20px; text-align: center;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #1a1a2e; margin-top: 0;">%s</h2>
                        <div style="background: #f5f5f5; padding: 20px; border-radius: 8px;">
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s - %s</p>
                            %s
                            %s
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            msg.get("email.admin.new.reservation", ADMIN_LANG),
            msg.get("email.admin.client", ADMIN_LANG), user.getFullName(),
            msg.get("email.admin.email", ADMIN_LANG), user.getEmail(),
            msg.get("email.admin.phone", ADMIN_LANG), user.getPhone(),
            msg.get("email.admin.date", ADMIN_LANG), slot.getDate().format(DATE_FORMAT),
            msg.get("email.admin.time", ADMIN_LANG), slot.getStartTime().format(TIME_FORMAT), slot.getEndTime().format(TIME_FORMAT),
            participantsLine,
            commentLine
        );
    }

    private String buildCancellationBody(String lang, User user, TimeSlot slot) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif;">
                <h2>%s</h2>
                <p>%s</p>
                <div style="background: #1a1a2e; color: white; padding: 20px; border-radius: 8px;">
                    <p><strong>%s</strong> %s</p>
                    <p><strong>%s</strong> %s - %s</p>
                </div>
                <p style="margin-top: 20px;">%s</p>
                <p>%s</p>
            </body>
            </html>
            """.formatted(
            msg.get("email.cancellation.greeting", lang, user.getFirstName()),
            msg.get("email.cancellation.body", lang),
            msg.get("email.reservation.date", lang), slot.getDate().format(DATE_FORMAT),
            msg.get("email.reservation.time", lang), slot.getStartTime().format(TIME_FORMAT), slot.getEndTime().format(TIME_FORMAT),
            msg.get("email.cancellation.hope", lang),
            msg.get("email.reservation.team", lang)
        );
    }

    private String buildEventReservationConfirmationBody(String lang, User user, Event event) {
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
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s - %s</p>
                        </div>
                        <p style="margin-top: 20px; color: #333;">%s</p>
                        <p style="color: #666; font-size: 14px;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            msg.get("email.event.reservation.greeting", lang, user.getFirstName()),
            msg.get("email.event.reservation.body", lang),
            msg.get("email.event.reservation.event", lang), event.getTitle(),
            msg.get("email.event.reservation.dates", lang), event.getStartDate().format(DATE_FORMAT), event.getEndDate().format(DATE_FORMAT),
            msg.get("email.see.you", lang),
            msg.get("email.reservation.team", lang)
        );
    }

    private String buildEventAdminNotificationBody(User user, Event event, int participants) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #0f0f1a; padding: 20px; text-align: center;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #1a1a2e; margin-top: 0;">%s</h2>
                        <div style="background: #f5f5f5; padding: 20px; border-radius: 8px;">
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s - %s</p>
                            <p><strong>%s</strong> %d</p>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            msg.get("email.admin.event.title", ADMIN_LANG),
            msg.get("email.admin.client", ADMIN_LANG), user.getFullName(),
            msg.get("email.admin.email", ADMIN_LANG), user.getEmail(),
            msg.get("email.admin.phone", ADMIN_LANG), user.getPhone(),
            msg.get("email.admin.event.event", ADMIN_LANG), event.getTitle(),
            msg.get("email.admin.event.dates", ADMIN_LANG), event.getStartDate().format(DATE_FORMAT), event.getEndDate().format(DATE_FORMAT),
            msg.get("email.admin.participants", ADMIN_LANG), participants
        );
    }

    private String buildAdminCancellationBody(String lang, User user, TimeSlot slot) {
        String titleLine = slot.getDisplayTitle() != null
            ? "<p style=\"margin: 0 0 8px 0;\"><strong>%s</strong> %s</p>".formatted(
                msg.get("email.admin.cancel.slot.label", lang), slot.getDisplayTitle())
            : "";
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
                        <div style="background: #fef2f2; border: 1px solid #fecaca; color: #991b1b; padding: 20px; border-radius: 8px;">
                            %s
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
            msg.get("email.admin.cancel.greeting", lang, user.getFirstName()),
            msg.get("email.admin.cancel.body", lang),
            titleLine,
            msg.get("email.reservation.date", lang), slot.getDate().format(DATE_FORMAT),
            msg.get("email.reservation.time", lang), slot.getStartTime().format(TIME_FORMAT), slot.getEndTime().format(TIME_FORMAT),
            msg.get("email.admin.cancel.sorry", lang),
            msg.get("email.reservation.team", lang)
        );
    }

    private String buildAdminEventCancellationBody(String lang, User user, Event event) {
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
                        <div style="background: #fef2f2; border: 1px solid #fecaca; color: #991b1b; padding: 20px; border-radius: 8px;">
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
            msg.get("email.admin.event.cancel.greeting", lang, user.getFirstName()),
            msg.get("email.admin.event.cancel.body", lang),
            msg.get("email.event.reservation.event", lang), event.getTitle(),
            msg.get("email.event.reservation.dates", lang), event.getStartDate().format(DATE_FORMAT), event.getEndDate().format(DATE_FORMAT),
            msg.get("email.admin.cancel.sorry", lang),
            msg.get("email.reservation.team", lang)
        );
    }

    private String buildUserCancellationAdminBody(User user, TimeSlot slot) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #0f0f1a; padding: 20px; text-align: center;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #e11d48; margin-top: 0;">%s</h2>
                        <div style="background: #fef2f2; border: 1px solid #fecaca; color: #991b1b; padding: 20px; border-radius: 8px;">
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s - %s</p>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            msg.get("email.user.cancel.admin.title", ADMIN_LANG),
            msg.get("email.admin.client", ADMIN_LANG), user.getFullName(),
            msg.get("email.admin.email", ADMIN_LANG), user.getEmail(),
            msg.get("email.admin.phone", ADMIN_LANG), user.getPhone(),
            msg.get("email.admin.date", ADMIN_LANG), slot.getDate().format(DATE_FORMAT),
            msg.get("email.admin.time", ADMIN_LANG), slot.getStartTime().format(TIME_FORMAT), slot.getEndTime().format(TIME_FORMAT)
        );
    }

    private String buildUserEventCancellationAdminBody(User user, Event event) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #0f0f1a; padding: 20px; text-align: center;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #e11d48; margin-top: 0;">%s</h2>
                        <div style="background: #fef2f2; border: 1px solid #fecaca; color: #991b1b; padding: 20px; border-radius: 8px;">
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s - %s</p>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            msg.get("email.user.event.cancel.admin.title", ADMIN_LANG),
            msg.get("email.admin.client", ADMIN_LANG), user.getFullName(),
            msg.get("email.admin.email", ADMIN_LANG), user.getEmail(),
            msg.get("email.admin.phone", ADMIN_LANG), user.getPhone(),
            msg.get("email.admin.event.event", ADMIN_LANG), event.getTitle(),
            msg.get("email.admin.event.dates", ADMIN_LANG), event.getStartDate().format(DATE_FORMAT), event.getEndDate().format(DATE_FORMAT)
        );
    }

    private String buildEventCancellationBody(String lang, User user, Event event) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif;">
                <h2>%s</h2>
                <p>%s</p>
                <div style="background: #1a1a2e; color: white; padding: 20px; border-radius: 8px;">
                    <p><strong>%s</strong> %s</p>
                    <p><strong>%s</strong> %s - %s</p>
                </div>
                <p style="margin-top: 20px;">%s</p>
                <p>%s</p>
            </body>
            </html>
            """.formatted(
            msg.get("email.event.cancellation.greeting", lang, user.getFirstName()),
            msg.get("email.event.cancellation.body", lang),
            msg.get("email.event.reservation.event", lang), event.getTitle(),
            msg.get("email.event.reservation.dates", lang), event.getStartDate().format(DATE_FORMAT), event.getEndDate().format(DATE_FORMAT),
            msg.get("email.event.cancellation.hope", lang),
            msg.get("email.reservation.team", lang)
        );
    }
}
