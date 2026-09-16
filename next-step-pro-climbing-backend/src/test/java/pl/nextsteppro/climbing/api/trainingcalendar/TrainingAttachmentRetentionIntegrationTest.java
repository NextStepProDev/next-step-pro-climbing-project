package pl.nextsteppro.climbing.api.trainingcalendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingAttachmentRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingKind;
import pl.nextsteppro.climbing.domain.trainingtemplate.TrainingTemplateRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;
import pl.nextsteppro.climbing.infrastructure.storage.TestImages;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The retention promise for a coach's materials (V99): a FILE attached to a training goes a year
 * after it was attached, and the bytes go with it once nothing else points there.
 *
 * <p>Twin of {@link CommentFileRetentionIntegrationTest}, and deliberately separate: the two
 * windows answer to different things, and a single test asserting both would make them look like
 * one rule the day somebody reconsiders either.
 */
class TrainingAttachmentRetentionIntegrationTest extends BaseIntegrationTest {

    @Autowired private TrainingCalendarService trainingCalendarService;
    @Autowired private TrainingTemplateService templateService;
    @Autowired private AttachmentSupport attachments;
    @Autowired private TrainingAttachmentRepository attachmentRepository;
    @Autowired private PersonalTrainingRepository personalTrainingRepository;
    @Autowired private TrainingTemplateRepository templateRepository;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User athlete;

    @BeforeEach
    void setUp() {
        attachmentRepository.deleteAll();
        templateRepository.deleteAll();
        personalTrainingRepository.deleteAll();
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        eventRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        athlete = new User("athlete@example.com", "Anna", "Wspinaczka", "+48123456789", "hash");
        athlete.setRole(UserRole.USER);
        athlete.setEmailVerified(true);
        athlete.setAthlete(true);
        athlete.grantTrainingConsent();
        athlete = userRepository.save(athlete);
    }

    private static MultipartFile jpeg() {
        return new MockMultipartFile("file", "plan.jpg", "image/jpeg", TestImages.jpeg(200, 150));
    }

    /** Uploads a file and hangs it on a new training; returns the stored filename. */
    private String trainingWithFile(String title) {
        String filename = trainingCalendarService.uploadMyAttachment(athlete.getId(), jpeg()).filename();
        trainingCalendarService.createMy(athlete.getId(), new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(1), null, null, title, null,
            List.of(fileRequest(filename))));
        return filename;
    }

    private static AttachmentRequest fileRequest(String filename) {
        return new AttachmentRequest(
            pl.nextsteppro.climbing.domain.personaltraining.AttachmentKind.FILE,
            null, filename, "plan.jpg", "image/jpeg", 1L, null);
    }

    /** Faster than waiting a year, and it exercises the query rather than the clock. */
    private void expireAll() {
        jdbcTemplate.update("UPDATE training_attachments SET expires_at = ? WHERE expires_at IS NOT NULL",
            java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(1))));
    }

    @Test
    void shouldRemoveTheRowAndTheFileOnceTheYearIsUp() {
        String filename = trainingWithFile("Sesja");
        assertTrue(fileStorageService.exists(filename, AttachmentSupport.FOLDER));

        expireAll();

        assertEquals(1, attachments.deleteExpired());
        assertEquals(0, attachmentRepository.count());
        assertFalse(fileStorageService.exists(filename, AttachmentSupport.FOLDER));
    }

    @Test
    void shouldLeaveAMaterialWhoseYearHasNotPassed() {
        String filename = trainingWithFile("Sesja");

        assertEquals(0, attachments.deleteExpired());
        assertEquals(1, attachmentRepository.count());
        assertTrue(fileStorageService.exists(filename, AttachmentSupport.FOLDER));
    }

    /**
     * Duplicate, paste and "use template" deliberately point several rows at one file. Each row
     * carries its own date, so the expiring one must take its row and leave the bytes — otherwise
     * a sweep on one athlete's plan blanks a picture on somebody else's.
     */
    @Test
    void shouldKeepTheBytesWhileAnotherTrainingStillPointsAtThem() {
        String filename = trainingWithFile("Sesja");
        // Second training on the same physical file, attached today
        trainingCalendarService.createMy(athlete.getId(), new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(2), null, null, "Kopia", null, List.of(fileRequest(filename))));

        jdbcTemplate.update("""
            UPDATE training_attachments SET expires_at = ?
             WHERE training_id = (SELECT id FROM personal_trainings WHERE title = 'Sesja')
            """, java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(1))));

        assertEquals(1, attachments.deleteExpired());
        assertEquals(1, attachmentRepository.count());
        assertTrue(fileStorageService.exists(filename, AttachmentSupport.FOLDER),
            "the copy that has not expired still needs these bytes");
    }

    /**
     * A template is a library handed out for years; a PDF expiring underneath it would leave the
     * template silently incomplete. The DB CHECK keeps a date off these rows, so the sweep cannot
     * reach them even if somebody later forgets the rule in Java.
     */
    @Test
    void shouldNeverExpireAMaterialOwnedByATemplate() {
        String filename = trainingCalendarService.uploadMyAttachment(athlete.getId(), jpeg()).filename();
        templateService.create(new SaveTemplateRequest(
            TrainingKind.TRAINING, "Rozgrzewka", null, 60, null, List.of(fileRequest(filename))));

        expireAll();

        assertEquals(0, attachments.deleteExpired());
        assertEquals(1, attachmentRepository.count());
        assertTrue(fileStorageService.exists(filename, AttachmentSupport.FOLDER));
    }

    @Test
    void shouldStampAYearOnAFileAndNothingOnALink() {
        String filename = trainingCalendarService.uploadMyAttachment(athlete.getId(), jpeg()).filename();
        UUID trainingId = trainingCalendarService.createMy(athlete.getId(), new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(1), null, null, "Sesja", null,
            List.of(fileRequest(filename), new AttachmentRequest("https://youtu.be/x", "Wideo")))).id();
        assertNotNull(trainingId);

        var rows = attachmentRepository.findAll();
        var file = rows.stream().filter(a -> a.getFilename() != null).findFirst().orElseThrow();
        var link = rows.stream().filter(a -> a.getUrl() != null).findFirst().orElseThrow();

        assertNull(link.getExpiresAt(), "a link holds none of our bytes");
        assertNotNull(file.getExpiresAt());
        long days = Duration.between(Instant.now(), file.getExpiresAt()).toDays();
        assertTrue(days >= 364 && days <= 365, "expected about a year, got " + days + " days");
    }
}
