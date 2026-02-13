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
import pl.nextsteppro.climbing.infrastructure.ical.ICalService;

import java.time.format.DateTimeFormatter;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final JavaMailSender mailSender;
    private final AppConfig appConfig;
    private final ICalService iCalService;

    public MailService(JavaMailSender mailSender, AppConfig appConfig, ICalService iCalService) {
        this.mailSender = mailSender;
        this.appConfig = appConfig;
        this.iCalService = iCalService;
    }

    @Async
    public void sendReservationConfirmation(Reservation reservation) {
        User user = reservation.getUser();
        if (!user.isEmailNotificationsEnabled()) return;

        TimeSlot slot = reservation.getTimeSlot();

        String subject = "Potwierdzenie rezerwacji - Next Step Pro Climbing";
        String body = buildReservationConfirmationBody(user, slot);

        sendEmail(user.getEmail(), subject, body, null);
    }

    @Async
    public void sendAdminNotification(Reservation reservation) {
        User user = reservation.getUser();
        TimeSlot slot = reservation.getTimeSlot();

        String subject = "Nowa rezerwacja: " + user.getFullName();
        String body = buildAdminNotificationBody(user, slot);
        byte[] icsAttachment = iCalService.generateReservationIcs(reservation);

        sendEmail(appConfig.getAdmin().getEmail(), subject, body, icsAttachment);
    }

    @Async
    public void sendCancellationConfirmation(Reservation reservation) {
        User user = reservation.getUser();
        if (!user.isEmailNotificationsEnabled()) return;

        TimeSlot slot = reservation.getTimeSlot();

        String subject = "Anulowanie rezerwacji - Next Step Pro Climbing";
        String body = buildCancellationBody(user, slot);

        sendEmail(user.getEmail(), subject, body, null);
    }

    @Async
    public void sendEventReservationConfirmation(User user, Event event) {
        if (!user.isEmailNotificationsEnabled()) return;

        String subject = "Potwierdzenie zapisu na wydarzenie - Next Step Pro Climbing";
        String body = buildEventReservationConfirmationBody(user, event);

        sendEmail(user.getEmail(), subject, body, null);
    }

    @Async
    public void sendEventAdminNotification(User user, Event event, int participants) {
        String subject = "Nowy zapis na wydarzenie: " + user.getFullName();
        String body = buildEventAdminNotificationBody(user, event, participants);
        byte[] icsAttachment = iCalService.generateEventIcs(event);

        sendEmail(appConfig.getAdmin().getEmail(), subject, body, icsAttachment);
    }

    @Async
    public void sendAdminCancellationNotification(Reservation reservation) {
        User user = reservation.getUser();
        if (!user.isEmailNotificationsEnabled()) return;

        TimeSlot slot = reservation.getTimeSlot();

        String subject = "Trening odwołany - Next Step Pro Climbing";
        String body = buildAdminCancellationBody(user, slot);

        sendEmail(user.getEmail(), subject, body, null);
    }

    @Async
    public void sendAdminEventCancellationNotification(User user, Event event) {
        if (!user.isEmailNotificationsEnabled()) return;

        String subject = "Wydarzenie odwołane - Next Step Pro Climbing";
        String body = buildAdminEventCancellationBody(user, event);

        sendEmail(user.getEmail(), subject, body, null);
    }

    @Async
    public void sendEventCancellationConfirmation(User user, Event event) {
        if (!user.isEmailNotificationsEnabled()) return;

        String subject = "Anulowanie zapisu na wydarzenie - Next Step Pro Climbing";
        String body = buildEventCancellationBody(user, event);

        sendEmail(user.getEmail(), subject, body, null);
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

    private String buildReservationConfirmationBody(User user, TimeSlot slot) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #0f0f1a; padding: 20px; text-align: center;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #1a1a2e; margin-top: 0;">Cześć %s!</h2>
                        <p style="color: #333;">Twoja rezerwacja została potwierdzona.</p>
                        <div style="background: #1a1a2e; color: white; padding: 20px; border-radius: 8px;">
                            <p><strong>Data:</strong> %s</p>
                            <p><strong>Godzina:</strong> %s - %s</p>
                        </div>
                        <p style="margin-top: 20px; color: #333;">Do zobaczenia na ścianie!</p>
                        <p style="color: #666; font-size: 14px;">Zespół Next Step Pro Climbing</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            user.getFirstName(),
            slot.getDate().format(DATE_FORMAT),
            slot.getStartTime().format(TIME_FORMAT),
            slot.getEndTime().format(TIME_FORMAT)
        );
    }

    private String buildAdminNotificationBody(User user, TimeSlot slot) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #0f0f1a; padding: 20px; text-align: center;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #1a1a2e; margin-top: 0;">Nowa rezerwacja</h2>
                        <div style="background: #f5f5f5; padding: 20px; border-radius: 8px;">
                            <p><strong>Klient:</strong> %s</p>
                            <p><strong>Email:</strong> %s</p>
                            <p><strong>Telefon:</strong> %s</p>
                            <p><strong>Data:</strong> %s</p>
                            <p><strong>Godzina:</strong> %s - %s</p>
                        </div>
                        <p style="margin-top: 20px; color: #666; font-size: 14px;">Plik .ics w załączniku.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            user.getFullName(),
            user.getEmail(),
            user.getPhone(),
            slot.getDate().format(DATE_FORMAT),
            slot.getStartTime().format(TIME_FORMAT),
            slot.getEndTime().format(TIME_FORMAT)
        );
    }

    private String buildCancellationBody(User user, TimeSlot slot) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif;">
                <h2>Cześć %s!</h2>
                <p>Twoja rezerwacja została anulowana.</p>
                <div style="background: #1a1a2e; color: white; padding: 20px; border-radius: 8px;">
                    <p><strong>Data:</strong> %s</p>
                    <p><strong>Godzina:</strong> %s - %s</p>
                </div>
                <p style="margin-top: 20px;">Mamy nadzieję, że wkrótce Cię zobaczymy!</p>
                <p>Zespół Next Step Pro Climbing</p>
            </body>
            </html>
            """.formatted(
            user.getFirstName(),
            slot.getDate().format(DATE_FORMAT),
            slot.getStartTime().format(TIME_FORMAT),
            slot.getEndTime().format(TIME_FORMAT)
        );
    }

    private String buildEventReservationConfirmationBody(User user, Event event) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #0f0f1a; padding: 20px; text-align: center;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #1a1a2e; margin-top: 0;">Cześć %s!</h2>
                        <p style="color: #333;">Twoja rezerwacja na wydarzenie została potwierdzona.</p>
                        <div style="background: #1a1a2e; color: white; padding: 20px; border-radius: 8px;">
                            <p><strong>Wydarzenie:</strong> %s</p>
                            <p><strong>Termin:</strong> %s - %s</p>
                        </div>
                        <p style="margin-top: 20px; color: #333;">Do zobaczenia!</p>
                        <p style="color: #666; font-size: 14px;">Zespół Next Step Pro Climbing</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            user.getFirstName(),
            event.getTitle(),
            event.getStartDate().format(DATE_FORMAT),
            event.getEndDate().format(DATE_FORMAT)
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
                        <h2 style="color: #1a1a2e; margin-top: 0;">Nowy zapis na wydarzenie</h2>
                        <div style="background: #f5f5f5; padding: 20px; border-radius: 8px;">
                            <p><strong>Klient:</strong> %s</p>
                            <p><strong>Email:</strong> %s</p>
                            <p><strong>Telefon:</strong> %s</p>
                            <p><strong>Wydarzenie:</strong> %s</p>
                            <p><strong>Termin:</strong> %s - %s</p>
                            <p><strong>Liczba osób:</strong> %d</p>
                        </div>
                        <p style="margin-top: 20px; color: #666; font-size: 14px;">Plik .ics w załączniku.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            user.getFullName(),
            user.getEmail(),
            user.getPhone(),
            event.getTitle(),
            event.getStartDate().format(DATE_FORMAT),
            event.getEndDate().format(DATE_FORMAT),
            participants
        );
    }

    private String buildAdminCancellationBody(User user, TimeSlot slot) {
        String titleLine = slot.getDisplayTitle() != null
            ? "<p style=\"margin: 0 0 8px 0;\"><strong>Termin:</strong> %s</p>".formatted(slot.getDisplayTitle())
            : "";
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #0f0f1a; padding: 20px; text-align: center;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #1a1a2e; margin-top: 0;">Cześć %s!</h2>
                        <p style="color: #333;">Niestety, Twój trening został <strong style="color: #e11d48;">odwołany przez instruktora</strong>.</p>
                        <div style="background: #fef2f2; border: 1px solid #fecaca; color: #991b1b; padding: 20px; border-radius: 8px;">
                            %s
                            <p style="margin: 0 0 8px 0;"><strong>Data:</strong> %s</p>
                            <p style="margin: 0;"><strong>Godzina:</strong> %s - %s</p>
                        </div>
                        <p style="margin-top: 20px; color: #333;">Przepraszamy za utrudnienia. Zapraszamy do rezerwacji innego terminu!</p>
                        <p style="color: #666; font-size: 14px;">Zespół Next Step Pro Climbing</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            user.getFirstName(),
            titleLine,
            slot.getDate().format(DATE_FORMAT),
            slot.getStartTime().format(TIME_FORMAT),
            slot.getEndTime().format(TIME_FORMAT)
        );
    }

    private String buildAdminEventCancellationBody(User user, Event event) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden;">
                    <div style="background: #0f0f1a; padding: 20px; text-align: center;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
                    </div>
                    <div style="padding: 30px;">
                        <h2 style="color: #1a1a2e; margin-top: 0;">Cześć %s!</h2>
                        <p style="color: #333;">Niestety, Twoje wydarzenie zostało <strong style="color: #e11d48;">odwołane przez instruktora</strong>.</p>
                        <div style="background: #fef2f2; border: 1px solid #fecaca; color: #991b1b; padding: 20px; border-radius: 8px;">
                            <p style="margin: 0 0 8px 0;"><strong>Wydarzenie:</strong> %s</p>
                            <p style="margin: 0;"><strong>Termin:</strong> %s - %s</p>
                        </div>
                        <p style="margin-top: 20px; color: #333;">Przepraszamy za utrudnienia. Zapraszamy do rezerwacji innego terminu!</p>
                        <p style="color: #666; font-size: 14px;">Zespół Next Step Pro Climbing</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            user.getFirstName(),
            event.getTitle(),
            event.getStartDate().format(DATE_FORMAT),
            event.getEndDate().format(DATE_FORMAT)
        );
    }

    private String buildEventCancellationBody(User user, Event event) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif;">
                <h2>Cześć %s!</h2>
                <p>Twój zapis na wydarzenie został anulowany.</p>
                <div style="background: #1a1a2e; color: white; padding: 20px; border-radius: 8px;">
                    <p><strong>Wydarzenie:</strong> %s</p>
                    <p><strong>Termin:</strong> %s - %s</p>
                </div>
                <p style="margin-top: 20px;">Mamy nadzieję, że wkrótce Cię zobaczymy!</p>
                <p>Zespół Next Step Pro Climbing</p>
            </body>
            </html>
            """.formatted(
            user.getFirstName(),
            event.getTitle(),
            event.getStartDate().format(DATE_FORMAT),
            event.getEndDate().format(DATE_FORMAT)
        );
    }
}
