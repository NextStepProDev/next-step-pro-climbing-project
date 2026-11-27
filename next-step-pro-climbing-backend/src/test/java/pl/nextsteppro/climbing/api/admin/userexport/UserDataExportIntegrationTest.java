package pl.nextsteppro.climbing.api.admin.userexport;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import pl.nextsteppro.climbing.domain.activitylog.ActivityActionType;
import pl.nextsteppro.climbing.domain.activitylog.ActivityLog;
import pl.nextsteppro.climbing.domain.activitylog.ActivityLogRepository;
import pl.nextsteppro.climbing.domain.adminnote.AdminPrivateNoteRepository;
import pl.nextsteppro.climbing.domain.athletegoal.AthleteGoal;
import pl.nextsteppro.climbing.domain.athletegoal.AthleteGoalRepository;
import pl.nextsteppro.climbing.domain.athletegoal.GoalHorizon;
import pl.nextsteppro.climbing.domain.athleteweight.AthleteWeight;
import pl.nextsteppro.climbing.domain.athleteweight.AthleteWeightRepository;
import pl.nextsteppro.climbing.domain.newsletter.NewsletterConsentLog;
import pl.nextsteppro.climbing.domain.newsletter.NewsletterConsentLogRepository;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTraining;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingComment;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRpe;
import pl.nextsteppro.climbing.domain.reservation.ReservationRpeRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.contact.ContactConversation;
import pl.nextsteppro.climbing.domain.contact.ContactConversationRepository;
import pl.nextsteppro.climbing.domain.contact.ContactMessage;
import pl.nextsteppro.climbing.domain.contact.ContactMessageFile;
import pl.nextsteppro.climbing.domain.contact.ContactMessageFileRepository;
import pl.nextsteppro.climbing.domain.contact.ContactMessageRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The personal-data export, over real PostgreSQL. Lives in this package because the export's
 * records are package-private.
 *
 * <p>The risk on this endpoint is two-sided, and so are the tests: a section that silently comes
 * back empty (an incomplete answer to a legal request that looks complete), and a field that rides
 * along into a file mailed outside the system. The second is asserted on the SERIALISED JSON, so a
 * field added to a record later is caught whether or not anybody remembers this test.
 */
class UserDataExportIntegrationTest extends BaseIntegrationTest {

    private static final String NOTE_PHRASE = "notatnik-trenera-nie-dla-klienta";

    @Autowired private UserDataExportService service;
    @Autowired private ActivityLogRepository activityLogRepository;
    @Autowired private ReservationRpeRepository rpeRepository;
    @Autowired private PersonalTrainingRepository trainingRepository;
    @Autowired private TrainingCommentRepository commentRepository;
    @Autowired private AthleteWeightRepository weightRepository;
    @Autowired private AthleteGoalRepository goalRepository;
    @Autowired private NewsletterConsentLogRepository consentLogRepository;
    @Autowired private AdminPrivateNoteRepository noteRepository;
    @Autowired private ContactConversationRepository contactConversationRepository;
    @Autowired private ContactMessageRepository contactMessageRepository;
    @Autowired private ContactMessageFileRepository contactFileRepository;

    private User user;
    private User admin;

    @BeforeEach
    void setUp() {
        noteRepository.deleteAll();
        commentRepository.deleteAll();
        trainingRepository.deleteAll();
        weightRepository.deleteAll();
        goalRepository.deleteAll();
        consentLogRepository.deleteAll();
        activityLogRepository.deleteAll();
        rpeRepository.deleteAll();
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        user = new User("eksport@example.com", "Ola", "Nowak", "+48500600700", "ola");
        user.setRole(UserRole.USER);
        user.setEmailVerified(true);
        user.setAthlete(true);
        user.grantTrainingConsent();
        user.setPasswordHash("$2a$12$notarealhashbutlongenoughtospot");
        user.setOauthId("google-oauth-subject-12345");
        user = userRepository.save(user);

        admin = new User("trener@example.com", "Trener", "Główny", "+48500000000", "trener");
        admin.setRole(UserRole.ADMIN);
        admin.setEmailVerified(true);
        admin = userRepository.save(admin);
    }

    private PersonalTraining seedEverything() {
        TimeSlot slot = timeSlotRepository.save(new TimeSlot(
            LocalDate.of(2026, 3, 2), LocalTime.of(18, 0), LocalTime.of(20, 0), 6));
        Reservation reservation = reservationRepository.save(new Reservation(user, slot));
        rpeRepository.save(new ReservationRpe(reservation, 7, "ciężko"));

        PersonalTraining training = trainingRepository.save(new PersonalTraining(user,
            LocalDate.of(2026, 3, 3), null, null, "Siła palców", "4x10 s", true));
        commentRepository.save(TrainingComment.onTraining(training, admin, true, "Jak poszło?"));
        commentRepository.save(TrainingComment.onTraining(training, user, false, "Dobrze"));
        commentRepository.save(TrainingComment.onSlot(user, slot, user, false, "Spóźnię się"));

        weightRepository.save(new AthleteWeight(user, LocalDate.of(2026, 3, 1), new BigDecimal("61.4")));
        goalRepository.save(new AthleteGoal(user, GoalHorizon.SHORT, "7a do lata", LocalDate.of(2026, 7, 1)));
        consentLogRepository.save(new NewsletterConsentLog(user, "SUBSCRIBE", "REGISTRATION"));

        noteRepository.upsertForTraining(admin.getId(), training.getId(), NOTE_PHRASE, Instant.now());
        return training;
    }

    private String exportJson() throws Exception {
        return new ObjectMapper()
            .writeValueAsString(service.export(user.getId(), admin.getId()).orElseThrow());
    }

    @Test
    void shouldExportWhatAConfirmedPersonWroteFromTheFormWhileSignedOut() {
        user.markEmailVerified();
        userRepository.save(user);
        ContactConversation own = contactConversationRepository.save(ContactConversation.forUser(user, Instant.now()));
        contactMessageRepository.save(ContactMessage.general(own, "Z konta", "pl", Instant.now().minusSeconds(60)));
        ContactConversation asGuest = contactConversationRepository.save(
            ContactConversation.forGuest(user.getEmail().toUpperCase(), "Ola", Instant.now()));
        contactMessageRepository.save(ContactMessage.general(asGuest, "Bez logowania", "pl", Instant.now()));
        contactMessageRepository.flush();

        UserDataExportDto export = service.export(user.getId(), admin.getId()).orElseThrow();

        assertEquals(List.of("Z konta", "Bez logowania"),
            export.contactMessages().stream().map(ExportContactMessageDto::body).toList());
    }

    // A section of the person's data left out is an incomplete answer to an access request, with no
    // symptom anywhere - so the chat's files (V111) are named in the export like the threads' files.
    @Test
    void shouldExportTheFilesOfTheChatAndAMessageThatIsOnlyAFile() {
        ContactConversation own = contactConversationRepository.save(ContactConversation.forUser(user, Instant.now()));
        ContactMessage onlyFile = contactMessageRepository.save(ContactMessage.general(own, null, "pl", Instant.now()));
        contactFileRepository.save(new ContactMessageFile(onlyFile, UUID.randomUUID() + ".jpg", "droga.jpg",
            "image/jpeg", 1234, 400, 300, 0, Instant.now().plusSeconds(3600)));
        contactFileRepository.flush();

        UserDataExportDto export = service.export(user.getId(), admin.getId()).orElseThrow();

        ExportContactMessageDto message = export.contactMessages().getFirst();
        assertNull(message.body());
        assertEquals(List.of("droga.jpg"), message.files().stream().map(ExportFileDto::name).toList());
        assertEquals("image/jpeg", message.files().getFirst().mimeType());
    }

    @Test
    void shouldNotExportAGuestConversationUnderTheAddressOfAnUnconfirmedAccount() {
        // Anyone can type an address into a sign-up form; until it is confirmed, mail sent from it
        // as a guest is not this account's data and must not be handed to whoever holds the account.
        ContactConversation asGuest = contactConversationRepository.save(
            ContactConversation.forGuest(user.getEmail(), "Ktoś", Instant.now()));
        contactMessageRepository.save(ContactMessage.general(asGuest, "Cudza wiadomość", "pl", Instant.now()));
        contactMessageRepository.flush();

        UserDataExportDto export = service.export(user.getId(), admin.getId()).orElseThrow();

        assertTrue(export.contactMessages().isEmpty());
    }

    @Test
    @DisplayName("shouldFillEverySectionTheUserHasDataIn")
    void shouldFillEverySectionTheUserHasDataIn() {
        seedEverything();

        UserDataExportDto export = service.export(user.getId(), admin.getId()).orElseThrow();

        assertEquals(UserDataExportService.SCHEMA_VERSION, export.schemaVersion());
        assertEquals("eksport@example.com", export.account().email());
        assertEquals(1, export.reservations().size());
        assertEquals(7, export.reservations().getFirst().rpe());
        assertEquals(1, export.trainingPlan().size());
        assertEquals(3, export.messages().size());
        assertEquals(1, export.weights().size());
        assertEquals(1, export.goals().size());
        assertEquals(1, export.newsletterConsentHistory().size());
    }

    /** Stored HTML-escaped; the file is read in a text editor, not rendered. */
    @Test
    @DisplayName("shouldExportFreeTextAsTypedNotAsStoredEscaped")
    void shouldExportFreeTextAsTypedNotAsStoredEscaped() {
        PersonalTraining training = seedEverything();
        String typed = "Zrób \"4x8\" & <odpocznij>";
        commentRepository.save(TrainingComment.onTraining(training, user, false,
            TrainingComment.sanitizeBody(typed)));

        UserDataExportDto export = service.export(user.getId(), admin.getId()).orElseThrow();

        assertTrue(export.messages().stream().anyMatch(m -> typed.equals(m.body())),
            "an escaped body reached the file as stored, not as typed");
    }

    @Test
    @DisplayName("shouldNameMessageAuthorsByRoleNotByAccount")
    void shouldNameMessageAuthorsByRoleNotByAccount() throws Exception {
        PersonalTraining training = seedEverything();

        UserDataExportDto export = service.export(user.getId(), admin.getId()).orElseThrow();

        List<String> onTraining = export.messages().stream()
            .filter(m -> training.getId().equals(m.trainingId()))
            .map(ExportCommentDto::author)
            .toList();
        assertEquals(List.of("COACH", "ME"), onTraining);
        // The coach's account is not the subject of this file — no name, no address.
        String json = exportJson();
        assertFalse(json.contains("trener@example.com"));
    }

    @Test
    @DisplayName("shouldNotSerialiseSecretsOrTheCoachsPrivateNotes")
    void shouldNotSerialiseSecretsOrTheCoachsPrivateNotes() throws Exception {
        seedEverything();

        String json = exportJson();

        for (String forbidden : List.of(
                "passwordHash", "$2a$12$notarealhashbutlongenoughtospot",
                "oauthId", "google-oauth-subject-12345",
                "newsletterUnsubscribeToken",
                user.getNewsletterUnsubscribeToken().toString(),
                "failedLoginAttempts", "lockedUntil",
                NOTE_PHRASE)) {
            assertFalse(json.contains(forbidden),
                "The export serialised '" + forbidden + "' — it must never leave the system");
        }
        assertTrue(json.contains("\"hasPassword\":true"));
    }

    /** Revoking the flag clears the consent but keeps the plan rows — exactly the data somebody
     * most plausibly asks about afterwards. The right of access does not lapse with the flag. */
    @Test
    @DisplayName("shouldExportTrainingDataEvenAfterTheAthleteFlagWasRevoked")
    void shouldExportTrainingDataEvenAfterTheAthleteFlagWasRevoked() {
        seedEverything();
        user.setAthlete(false);
        user = userRepository.save(user);

        UserDataExportDto export = service.export(user.getId(), admin.getId()).orElseThrow();

        assertFalse(export.account().athlete());
        assertEquals(1, export.trainingPlan().size());
        assertEquals(1, export.weights().size());
    }

    @Test
    @DisplayName("shouldLogTheExportUnderTheAdminAndKeepItOutOfTheFile")
    void shouldLogTheExportUnderTheAdminAndKeepItOutOfTheFile() {
        UserDataExportDto export = service.export(user.getId(), admin.getId()).orElseThrow();

        List<ActivityLog> adminLog = activityLogRepository.findByUserIdWithDetails(admin.getId(), PageRequest.of(0, 10));
        assertEquals(1, adminLog.size());
        assertEquals(ActivityActionType.ADMIN_USER_DATA_EXPORTED, adminLog.getFirst().getActionType());
        // Whose data it was rides as the subject FK, so erasing that account erases it from here too
        assertEquals(user.getId(), adminLog.getFirst().getSubject().getId());
        assertNull(adminLog.getFirst().getDescription());
        assertTrue(export.activity().isEmpty());
    }

    /**
     * An admin's own log entries describe actions ON other people ("Ola Nowak (ola@…) -> athlete"),
     * so exporting an admin must not carry them — the file would hand third parties' data out.
     */
    @Test
    @DisplayName("shouldKeepOtherPeoplesDataOutWhenExportingAnAdmin")
    void shouldKeepOtherPeoplesDataOutWhenExportingAnAdmin() throws Exception {
        // the admin exports Ola first, which files an entry naming her under the admin
        service.export(user.getId(), admin.getId()).orElseThrow();

        String json = new ObjectMapper()
            .writeValueAsString(service.export(admin.getId(), admin.getId()).orElseThrow());

        assertFalse(json.contains("eksport@example.com"),
            "an admin's export carried another person's address from the admin's own log");
    }

    @Test
    @DisplayName("shouldReturnEmptyAndLogNothingWhenUserDoesNotExist")
    void shouldReturnEmptyAndLogNothingWhenUserDoesNotExist() {
        assertTrue(service.export(UUID.randomUUID(), admin.getId()).isEmpty());
        assertEquals(0, activityLogRepository.count());
    }
}
