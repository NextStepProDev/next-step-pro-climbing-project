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

@Service
public class AuthMailService {

    private static final Logger log = LoggerFactory.getLogger(AuthMailService.class);

    private final JavaMailSender mailSender;
    private final AppConfig appConfig;

    public AuthMailService(JavaMailSender mailSender, AppConfig appConfig) {
        this.mailSender = mailSender;
        this.appConfig = appConfig;
    }

    @Async
    public void sendVerificationEmail(User user, String token) {
        String verificationUrl = buildVerificationUrl(token);
        String subject = "Potwierdź swój adres email - Next Step Pro Climbing";
        String body = buildVerificationEmailBody(user.getFirstName(), verificationUrl);

        sendEmail(user.getEmail(), subject, body);
    }

    @Async
    public void sendPasswordResetEmail(User user, String token) {
        String resetUrl = buildPasswordResetUrl(token);
        String subject = "Reset hasła - Next Step Pro Climbing";
        String body = buildPasswordResetEmailBody(user.getFirstName(), resetUrl);

        sendEmail(user.getEmail(), subject, body);
    }

    @Async
    public void sendPasswordChangedNotification(User user) {
        String subject = "Twoje hasło zostało zmienione - Next Step Pro Climbing";
        String body = buildPasswordChangedEmailBody(user.getFirstName());

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
        return appConfig.getCors().getAllowedOrigins() + "/verify-email?token=" + token;
    }

    private String buildPasswordResetUrl(String token) {
        return appConfig.getCors().getAllowedOrigins() + "/reset-password?token=" + token;
    }

    private String buildVerificationEmailBody(String firstName, String verificationUrl) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #0f0f1a; color: #e0e0e0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #1a1a2e; border-radius: 12px; overflow: hidden;">
                    <div style="text-align: center; padding: 24px 30px 0;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
                    </div>
                    <div style="padding: 20px 30px 30px;">
                    <h1 style="color: #3b82f6; margin-bottom: 20px;">Witaj, %s!</h1>
                    <p style="font-size: 16px; line-height: 1.6;">
                        Dziękujemy za rejestrację w Next Step Pro Climbing.
                    </p>
                    <p style="font-size: 16px; line-height: 1.6;">
                        Kliknij poniższy przycisk, aby potwierdzić swój adres email:
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
                            Potwierdź email
                        </a>
                    </div>
                    <p style="font-size: 14px; color: #9ca3af;">
                        Link jest ważny przez 15 minut.
                    </p>
                    <p style="font-size: 14px; color: #9ca3af;">
                        Jeśli nie rejestrowałeś/aś się w naszym serwisie, zignoruj tę wiadomość.
                    </p>
                    <hr style="border: none; border-top: 1px solid #2d2d44; margin: 30px 0;">
                    <p style="font-size: 12px; color: #6b7280; text-align: center;">
                        Next Step Pro Climbing<br>
                        Wspinaj się z nami!
                    </p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(firstName, verificationUrl);
    }

    private String buildPasswordResetEmailBody(String firstName, String resetUrl) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #0f0f1a; color: #e0e0e0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #1a1a2e; border-radius: 12px; overflow: hidden;">
                    <div style="text-align: center; padding: 24px 30px 0;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
                    </div>
                    <div style="padding: 20px 30px 30px;">
                    <h1 style="color: #3b82f6; margin-bottom: 20px;">Cześć, %s!</h1>
                    <p style="font-size: 16px; line-height: 1.6;">
                        Otrzymaliśmy prośbę o reset hasła do Twojego konta.
                    </p>
                    <p style="font-size: 16px; line-height: 1.6;">
                        Kliknij poniższy przycisk, aby ustawić nowe hasło:
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
                            Ustaw nowe hasło
                        </a>
                    </div>
                    <p style="font-size: 14px; color: #9ca3af;">
                        Link jest ważny przez 1 godzinę.
                    </p>
                    <p style="font-size: 14px; color: #9ca3af;">
                        Jeśli nie prosiłeś/aś o reset hasła, zignoruj tę wiadomość. Twoje konto jest bezpieczne.
                    </p>
                    <hr style="border: none; border-top: 1px solid #2d2d44; margin: 30px 0;">
                    <p style="font-size: 12px; color: #6b7280; text-align: center;">
                        Next Step Pro Climbing<br>
                        Wspinaj się z nami!
                    </p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(firstName, resetUrl);
    }

    private String buildPasswordChangedEmailBody(String firstName) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #0f0f1a; color: #e0e0e0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #1a1a2e; border-radius: 12px; overflow: hidden;">
                    <div style="text-align: center; padding: 24px 30px 0;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
                    </div>
                    <div style="padding: 20px 30px 30px;">
                    <h1 style="color: #3b82f6; margin-bottom: 20px;">Cześć, %s!</h1>
                    <p style="font-size: 16px; line-height: 1.6;">
                        Twoje hasło zostało pomyślnie zmienione.
                    </p>
                    <p style="font-size: 16px; line-height: 1.6;">
                        Jeśli to nie Ty zmieniłeś/aś hasło, skontaktuj się z nami natychmiast.
                    </p>
                    <hr style="border: none; border-top: 1px solid #2d2d44; margin: 30px 0;">
                    <p style="font-size: 12px; color: #6b7280; text-align: center;">
                        Next Step Pro Climbing<br>
                        Wspinaj się z nami!
                    </p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(firstName);
    }
}
