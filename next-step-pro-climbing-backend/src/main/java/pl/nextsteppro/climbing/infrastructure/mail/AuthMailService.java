package pl.nextsteppro.climbing.infrastructure.mail;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pl.nextsteppro.climbing.config.AdminEmailConfig;
import pl.nextsteppro.climbing.config.AppConfig;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.util.List;

@Service
public class AuthMailService {

    private static final Logger log = LoggerFactory.getLogger(AuthMailService.class);
    private static final String ADMIN_LANG = "pl";

    private final JavaMailSender mailSender;
    private final AppConfig appConfig;
    private final AdminEmailConfig adminEmailConfig;
    private final MessageService msg;
    private final String siteUrl;

    public AuthMailService(JavaMailSender mailSender, AppConfig appConfig, AdminEmailConfig adminEmailConfig, MessageService msg) {
        this.mailSender = mailSender;
        this.appConfig = appConfig;
        this.adminEmailConfig = adminEmailConfig;
        this.msg = msg;
        this.siteUrl = appConfig.getSiteUrl();
    }

    @Async
    public void sendVerificationEmail(User user, String token) {
        String lang = user.getPreferredLanguage();
        String verificationUrl = buildVerificationUrl(token);
        String subject = msg.getForLang("email.verification.subject", lang);
        String body = buildVerificationEmailBody(lang, user.getFirstName(), verificationUrl);

        sendEmail(user.getEmail(), subject, body);
    }

    @Async
    public void sendWelcomeEmail(User user) {
        String lang = user.getPreferredLanguage();
        String subject = msg.getForLang("email.welcome.subject", lang);
        String body = buildWelcomeEmailBody(lang, user.getFirstName());

        sendEmail(user.getEmail(), subject, body);
    }

    @Async
    public void sendPasswordResetEmail(User user, String token) {
        String lang = user.getPreferredLanguage();
        String resetUrl = buildPasswordResetUrl(token);
        String subject = msg.getForLang("email.reset.subject", lang);
        String body = buildPasswordResetEmailBody(lang, user.getFirstName(), resetUrl);

        sendEmail(user.getEmail(), subject, body);
    }

    @Async
    public void sendAccountDeletedByAdminNotification(User user) {
        String lang = user.getPreferredLanguage();
        String subject = msg.getForLang("email.account.deleted.subject", lang);
        String body = buildAccountDeletedEmailBody(lang, user.getFirstName());
        sendEmail(user.getEmail(), subject, body);
    }

    // Powiadomienie do ADMINA, gdy użytkownik SAM usunął swoje konto.
    // affectedReservations = liczba anulowanych potwierdzonych rezerwacji (zwolnione miejsca).
    @Async
    public void sendAccountSelfDeletedAdminNotification(User user, int affectedReservations) {
        String subject = msg.getForLang("email.admin.account.deleted.subject", ADMIN_LANG, user.getFullName());
        String body = buildAccountSelfDeletedAdminBody(user, affectedReservations);
        sendToAdmins(subject, body);
    }

    @Async
    public void sendPasswordChangedNotification(User user) {
        String lang = user.getPreferredLanguage();
        String subject = msg.getForLang("email.password.changed.subject", lang);
        String body = buildPasswordChangedEmailBody(lang, user.getFirstName());

        sendEmail(user.getEmail(), subject, body);
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true);

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            helper.setFrom(appConfig.getMail().getFrom());

            var logoResource = new org.springframework.core.io.ClassPathResource("static/logo/logo-white.png");
            helper.addInline("logo", logoResource, "image/png");

            mailSender.send(message);
            log.info("Auth email sent to: {}", to);
        } catch (MailException | jakarta.mail.MessagingException e) {
            log.error("Failed to send auth email to: {}", to, e);
        }
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

    private static String esc(@Nullable String value) {
        return value == null ? "" : org.springframework.web.util.HtmlUtils.htmlEscape(value);
    }

    private String buildAccountSelfDeletedAdminBody(User user, int affectedReservations) {
        String reservationsLine = affectedReservations > 0
            ? msg.getForLang("email.admin.account.deleted.reservations", ADMIN_LANG, affectedReservations)
            : msg.getForLang("email.admin.account.deleted.reservations.none", ADMIN_LANG);
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #1a1816; padding: 20px; text-align: center;">
                        <a href="%s" style="display: inline-block; text-decoration: none; cursor: pointer; line-height: 0; font-size: 0;"><img src="cid:logo" alt="Next Step Pro Climbing" style="height: 100px; display: block; border: 0;" /></a>
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #e11d48; margin-top: 0;">%s</h2>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <div style="background: #fef2f2; border: 1px solid #fecaca; color: #991b1b; padding: 20px; border-radius: 8px;">
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p><strong>%s</strong> %s</p>
                            <p style="margin-bottom: 0;">%s</p>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            siteUrl,
            msg.getForLang("email.admin.account.deleted.title", ADMIN_LANG),
            msg.getForLang("email.admin.account.deleted.body", ADMIN_LANG),
            msg.getForLang("email.admin.client", ADMIN_LANG), esc(user.getFullName()),
            msg.getForLang("email.admin.email", ADMIN_LANG), esc(user.getEmail()),
            msg.getForLang("email.admin.phone", ADMIN_LANG), esc(user.getPhone()),
            reservationsLine
        );
    }

    private String buildWelcomeEmailBody(String lang, String firstName) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #1a1816; color: #e0e0e0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #312e2b; border-radius: 12px; overflow: hidden;">
                    <div style="text-align: center; padding: 24px 30px 0;">
                        <a href="%s" style="display: inline-block; text-decoration: none;"><img src="cid:logo" alt="Next Step Pro Climbing" style="height: 80px; display: block;" /></a>
                    </div>
                    <div style="padding: 20px 30px 30px;">
                    <h1 style="color: #3b82f6; margin-bottom: 20px;">%s</h1>
                    <p style="font-size: 16px; line-height: 1.6;">
                        %s
                    </p>
                    <p style="font-size: 16px; line-height: 1.6;">
                        %s
                    </p>
                    <hr style="border: none; border-top: 1px solid #2d2d44; margin: 30px 0;">
                    <p style="font-size: 12px; color: #6b7280; text-align: center;">
                        %s<br>
                        %s
                    </p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            siteUrl,
            msg.getForLang("email.welcome.greeting", lang, firstName),
            msg.getForLang("email.welcome.body", lang),
            msg.getForLang("email.welcome.see.you", lang),
            msg.getForLang("email.footer", lang),
            msg.getForLang("email.footer.slogan", lang)
        );
    }

    private String buildAccountDeletedEmailBody(String lang, String firstName) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #1a1816; color: #e0e0e0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #312e2b; border-radius: 12px; overflow: hidden;">
                    <div style="text-align: center; padding: 24px 30px 0;">
                        <a href="%s" style="display: inline-block; text-decoration: none;"><img src="cid:logo" alt="Next Step Pro Climbing" style="height: 80px; display: block;" /></a>
                    </div>
                    <div style="padding: 20px 30px 30px;">
                    <h1 style="color: #3b82f6; margin-bottom: 20px;">%s</h1>
                    <p style="font-size: 16px; line-height: 1.6;">
                        %s
                    </p>
                    <p style="font-size: 16px; line-height: 1.6; color: #f87171;">
                        %s
                    </p>
                    <hr style="border: none; border-top: 1px solid #2d2d44; margin: 30px 0;">
                    <p style="font-size: 12px; color: #6b7280; text-align: center;">
                        %s<br>
                        %s
                    </p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            siteUrl,
            msg.getForLang("email.account.deleted.greeting", lang, firstName),
            msg.getForLang("email.account.deleted.body", lang),
            msg.getForLang("email.account.deleted.contact", lang),
            msg.getForLang("email.footer", lang),
            msg.getForLang("email.footer.slogan", lang)
        );
    }

    private String buildVerificationUrl(String token) {
        return appConfig.getBaseUrl() + "/verify-email?token=" + token;
    }

    private String buildPasswordResetUrl(String token) {
        return appConfig.getBaseUrl() + "/reset-password?token=" + token;
    }

    private String buildVerificationEmailBody(String lang, String firstName, String verificationUrl) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #1a1816; color: #e0e0e0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #312e2b; border-radius: 12px; overflow: hidden;">
                    <div style="text-align: center; padding: 24px 30px 0;">
                        <a href="%s" style="display: inline-block; text-decoration: none;"><img src="cid:logo" alt="Next Step Pro Climbing" style="height: 80px; display: block;" /></a>
                    </div>
                    <div style="padding: 20px 30px 30px;">
                    <h1 style="color: #3b82f6; margin-bottom: 20px;">%s</h1>
                    <p style="font-size: 16px; line-height: 1.6;">
                        %s
                    </p>
                    <p style="font-size: 16px; line-height: 1.6;">
                        %s
                    </p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s"
                           style="background: linear-gradient(135deg, #3b82f6, #1d4ed8);
                                  color: white;
                                  padding: 14px 32px;
                                  text-decoration: none;
                                  border-radius: 8px;
                                  font-weight: bold;
                                  font-size: 16px;
                                  display: inline-block;">
                            %s
                        </a>
                    </div>
                    <p style="font-size: 14px; color: #9ca3af;">
                        %s
                    </p>
                    <p style="font-size: 14px; color: #9ca3af;">
                        %s
                    </p>
                    <hr style="border: none; border-top: 1px solid #2d2d44; margin: 30px 0;">
                    <p style="font-size: 12px; color: #6b7280; text-align: center;">
                        %s<br>
                        %s
                    </p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            siteUrl,
            msg.getForLang("email.verification.greeting", lang, firstName),
            msg.getForLang("email.verification.body", lang),
            msg.getForLang("email.verification.action", lang),
            verificationUrl,
            msg.getForLang("email.verification.button", lang),
            msg.getForLang("email.verification.expiry", lang),
            msg.getForLang("email.verification.ignore", lang),
            msg.getForLang("email.footer", lang),
            msg.getForLang("email.footer.slogan", lang)
        );
    }

    private String buildPasswordResetEmailBody(String lang, String firstName, String resetUrl) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #1a1816; color: #e0e0e0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #312e2b; border-radius: 12px; overflow: hidden;">
                    <div style="text-align: center; padding: 24px 30px 0;">
                        <a href="%s" style="display: inline-block; text-decoration: none;"><img src="cid:logo" alt="Next Step Pro Climbing" style="height: 80px; display: block;" /></a>
                    </div>
                    <div style="padding: 20px 30px 30px;">
                    <h1 style="color: #3b82f6; margin-bottom: 20px;">%s</h1>
                    <p style="font-size: 16px; line-height: 1.6;">
                        %s
                    </p>
                    <p style="font-size: 16px; line-height: 1.6;">
                        %s
                    </p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s"
                           style="background: linear-gradient(135deg, #3b82f6, #1d4ed8);
                                  color: white;
                                  padding: 14px 32px;
                                  text-decoration: none;
                                  border-radius: 8px;
                                  font-weight: bold;
                                  font-size: 16px;
                                  display: inline-block;">
                            %s
                        </a>
                    </div>
                    <p style="font-size: 14px; color: #9ca3af;">
                        %s
                    </p>
                    <p style="font-size: 14px; color: #9ca3af;">
                        %s
                    </p>
                    <hr style="border: none; border-top: 1px solid #2d2d44; margin: 30px 0;">
                    <p style="font-size: 12px; color: #6b7280; text-align: center;">
                        %s<br>
                        %s
                    </p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            siteUrl,
            msg.getForLang("email.reset.greeting", lang, firstName),
            msg.getForLang("email.reset.body", lang),
            msg.getForLang("email.reset.action", lang),
            resetUrl,
            msg.getForLang("email.reset.button", lang),
            msg.getForLang("email.reset.expiry", lang),
            msg.getForLang("email.reset.ignore", lang),
            msg.getForLang("email.footer", lang),
            msg.getForLang("email.footer.slogan", lang)
        );
    }

    private String buildPasswordChangedEmailBody(String lang, String firstName) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #1a1816; color: #e0e0e0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #312e2b; border-radius: 12px; overflow: hidden;">
                    <div style="text-align: center; padding: 24px 30px 0;">
                        <a href="%s" style="display: inline-block; text-decoration: none;"><img src="cid:logo" alt="Next Step Pro Climbing" style="height: 80px; display: block;" /></a>
                    </div>
                    <div style="padding: 20px 30px 30px;">
                    <h1 style="color: #3b82f6; margin-bottom: 20px;">%s</h1>
                    <p style="font-size: 16px; line-height: 1.6;">
                        %s
                    </p>
                    <p style="font-size: 16px; line-height: 1.6;">
                        %s
                    </p>
                    <hr style="border: none; border-top: 1px solid #2d2d44; margin: 30px 0;">
                    <p style="font-size: 12px; color: #6b7280; text-align: center;">
                        %s<br>
                        %s
                    </p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            siteUrl,
            msg.getForLang("email.password.changed.greeting", lang, firstName),
            msg.getForLang("email.password.changed.body", lang),
            msg.getForLang("email.password.changed.warning", lang),
            msg.getForLang("email.footer", lang),
            msg.getForLang("email.footer.slogan", lang)
        );
    }
}
