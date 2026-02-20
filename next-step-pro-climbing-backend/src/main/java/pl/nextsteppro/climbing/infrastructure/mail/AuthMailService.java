package pl.nextsteppro.climbing.infrastructure.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pl.nextsteppro.climbing.config.AppConfig;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

@Service
public class AuthMailService {

    private static final Logger log = LoggerFactory.getLogger(AuthMailService.class);

    private final JavaMailSender mailSender;
    private final AppConfig appConfig;
    private final MessageService msg;

    public AuthMailService(JavaMailSender mailSender, AppConfig appConfig, MessageService msg) {
        this.mailSender = mailSender;
        this.appConfig = appConfig;
        this.msg = msg;
    }

    @Async
    public void sendVerificationEmail(User user, String token) {
        String lang = user.getPreferredLanguage();
        String verificationUrl = buildVerificationUrl(token);
        String subject = msg.get("email.verification.subject", lang);
        String body = buildVerificationEmailBody(lang, user.getFirstName(), verificationUrl);

        sendEmail(user.getEmail(), subject, body);
    }

    @Async
    public void sendPasswordResetEmail(User user, String token) {
        String lang = user.getPreferredLanguage();
        String resetUrl = buildPasswordResetUrl(token);
        String subject = msg.get("email.reset.subject", lang);
        String body = buildPasswordResetEmailBody(lang, user.getFirstName(), resetUrl);

        sendEmail(user.getEmail(), subject, body);
    }

    @Async
    public void sendPasswordChangedNotification(User user) {
        String lang = user.getPreferredLanguage();
        String subject = msg.get("email.password.changed.subject", lang);
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

    private String buildVerificationUrl(String token) {
        return appConfig.getBaseUrl() + "/verify-email?token=" + token;
    }

    private String buildPasswordResetUrl(String token) {
        return appConfig.getBaseUrl() + "/reset-password?token=" + token;
    }

    private String buildVerificationEmailBody(String lang, String firstName, String verificationUrl) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #0f0f1a; color: #e0e0e0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #1a1a2e; border-radius: 12px; overflow: hidden;">
                    <div style="text-align: center; padding: 24px 30px 0;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
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
            msg.get("email.verification.greeting", lang, firstName),
            msg.get("email.verification.body", lang),
            msg.get("email.verification.action", lang),
            verificationUrl,
            msg.get("email.verification.button", lang),
            msg.get("email.verification.expiry", lang),
            msg.get("email.verification.ignore", lang),
            msg.get("email.footer", lang),
            msg.get("email.footer.slogan", lang)
        );
    }

    private String buildPasswordResetEmailBody(String lang, String firstName, String resetUrl) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #0f0f1a; color: #e0e0e0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #1a1a2e; border-radius: 12px; overflow: hidden;">
                    <div style="text-align: center; padding: 24px 30px 0;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
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
            msg.get("email.reset.greeting", lang, firstName),
            msg.get("email.reset.body", lang),
            msg.get("email.reset.action", lang),
            resetUrl,
            msg.get("email.reset.button", lang),
            msg.get("email.reset.expiry", lang),
            msg.get("email.reset.ignore", lang),
            msg.get("email.footer", lang),
            msg.get("email.footer.slogan", lang)
        );
    }

    private String buildPasswordChangedEmailBody(String lang, String firstName) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #0f0f1a; color: #e0e0e0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #1a1a2e; border-radius: 12px; overflow: hidden;">
                    <div style="text-align: center; padding: 24px 30px 0;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
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
            msg.get("email.password.changed.greeting", lang, firstName),
            msg.get("email.password.changed.body", lang),
            msg.get("email.password.changed.warning", lang),
            msg.get("email.footer", lang),
            msg.get("email.footer.slogan", lang)
        );
    }
}
