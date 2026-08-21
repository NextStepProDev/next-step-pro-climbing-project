package pl.nextsteppro.climbing.api.trainingcalendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.user.UserService;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentFileRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;
import pl.nextsteppro.climbing.infrastructure.storage.TestImages;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The retention promise: attachments disappear a year after they were sent, and the folder is
 * reconciled against the rows so a silently failed unlink is not a permanent leak.
 */
class CommentFileRetentionIntegrationTest extends BaseIntegrationTest {

    @Autowired private TrainingCalendarService trainingCalendarService;
    @Autowired private CommentFileRetentionService retention;
    @Autowired private PersonalTrainingRepository personalTrainingRepository;
    @Autowired private TrainingCommentRepository trainingCommentRepository;
    @Autowired private TrainingCommentFileRepository commentFileRepository;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private UserService userService;
    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * The scratch folder the test profile points storage at. Same value as
     * {@code app.storage.root} in application-test.yml, plus the attachments subfolder.
     */
    private static final Path FILES_FOLDER = Path.of(
        System.getProperty("java.io.tmpdir"), "nsp-climbing-test-uploads", CommentFileSupport.FOLDER);

    private User athlete;
    private UUID trainingId;

    @BeforeEach
    void setUp() throws IOException {
        // Three tests below assert an EXACT sweep count, which is only meaningful against a known
        // folder. Uploads are not transactional: every test that attaches a file leaves it on disk
        // while its rows roll back, so the folder accumulates across runs. Once those leftovers age
        // past the sweeper's grace window they get counted too — "expected 1 but was 47".
        //
        // The failure hides itself, which is why it read as flaky for so long: the run that fails
        // also deletes the backlog, so the next run is green and the cycle restarts only after
        // files have sat unused long enough to age. CI never sees it at all — a fresh container
        // starts with an empty tmpdir.
        emptyTheAttachmentFolder();

        commentFileRepository.deleteAll();
        trainingCommentRepository.deleteAll();
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

        trainingId = trainingCalendarService.createMy(athlete.getId(), new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(1), null, null, "Sesja", null, null)).id();
    }

    private static MultipartFile jpeg() {
        return new MockMultipartFile("files", "a.jpg", "image/jpeg", TestImages.jpeg(200, 150));
    }

    private void expire(UUID fileId) {
        // Faster than waiting a year, and it exercises the query rather than the clock.
        jdbcTemplate.update("UPDATE training_comment_files SET expires_at = ? WHERE id = ?",
            java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(1))), fileId);
    }

    @Test
    void shouldDeleteExpiredFilesAndKeepTheWords() {
        TrainingCommentDto withText = trainingCalendarService.addMyCommentWithFiles(
            athlete.getId(), trainingId, "Nogi dziś martwe", List.of(jpeg()));
        TrainingCommentDto fileOnly = trainingCalendarService.addMyCommentWithFiles(
            athlete.getId(), trainingId, null, List.of(jpeg()));

        String keptText = withText.body();
        String survivingFilename = fileOnly.files().getFirst().id().toString();
        assertNotNull(survivingFilename);

        expire(withText.files().getFirst().id());
        expire(fileOnly.files().getFirst().id());

        assertEquals(2, retention.deleteExpired());

        var thread = trainingCalendarService.getMyComments(athlete.getId(), trainingId);
        // The words survive and only the picture goes; a message that was nothing but the file
        // goes with it, because an empty bubble would sit in the thread forever.
        assertEquals(1, thread.size());
        assertEquals(keptText, thread.getFirst().body());
        assertTrue(thread.getFirst().files().isEmpty());
        assertEquals(0, commentFileRepository.count());
    }

    @Test
    void shouldRemoveTheFileFromDiskWhenItExpires() {
        TrainingCommentDto comment = trainingCalendarService.addMyCommentWithFiles(
            athlete.getId(), trainingId, "x", List.of(jpeg()));
        String filename = commentFileRepository.findAll().getFirst().getFilename();
        assertTrue(fileStorageService.exists(filename, CommentFileSupport.FOLDER));

        expire(comment.files().getFirst().id());
        retention.deleteExpired();

        assertFalse(fileStorageService.exists(filename, CommentFileSupport.FOLDER));
    }

    @Test
    void shouldSweepAFileNoRowClaims() throws IOException {
        // Every explicit unlink sits in a transaction that can roll back, and the storage layer
        // logs its failures rather than raising them. Without this pass, one silent failure is a
        // permanent leak of somebody's data.
        Path orphan = writeStrayFile();
        backdate(orphan, Duration.ofHours(12));

        assertEquals(1, retention.deleteOrphans());
        assertFalse(Files.exists(orphan));
    }

    @Test
    void shouldNotSweepAFileThatWasJustWritten() throws IOException {
        // The known filenames are read before the folder is listed, so an upload landing between
        // the two looks orphaned — deleting what somebody just sent is far worse than waiting.
        Path fresh = writeStrayFile();

        assertEquals(0, retention.deleteOrphans());
        assertTrue(Files.exists(fresh));
        Files.deleteIfExists(fresh);
    }

    @Test
    void shouldLeaveClaimedFilesAlone() {
        trainingCalendarService.addMyCommentWithFiles(athlete.getId(), trainingId, "x", List.of(jpeg()));
        String filename = commentFileRepository.findAll().getFirst().getFilename();

        assertEquals(0, retention.deleteOrphans());
        assertTrue(fileStorageService.exists(filename, CommentFileSupport.FOLDER));
    }

    @Test
    void shouldUnlinkFilesWhenTheAccountIsDeleted() {
        trainingCalendarService.addMyCommentWithFiles(athlete.getId(), trainingId, "x", List.of(jpeg()));
        String filename = commentFileRepository.findAll().getFirst().getFilename();
        assertTrue(fileStorageService.exists(filename, CommentFileSupport.FOLDER),
            "guard against a vacuous pass: the file must exist before the account is deleted");

        // The comment rows go with the DB cascade; the files need an explicit unlink, from both
        // deletion paths. The orphan sweep would eventually catch a miss — but "eventually" is the
        // wrong answer to an erasure request.
        userService.deleteAccount(athlete.getId(), "hash");

        assertFalse(fileStorageService.exists(filename, CommentFileSupport.FOLDER));
    }

    /** Leaves the folder present but empty, so an exact sweep count means what it says. */
    private static void emptyTheAttachmentFolder() throws IOException {
        Files.createDirectories(FILES_FOLDER);
        try (var entries = Files.list(FILES_FOLDER)) {
            for (Path leftover : entries.toList()) {
                Files.deleteIfExists(leftover);
            }
        }
    }

    private Path writeStrayFile() throws IOException {
        Files.createDirectories(FILES_FOLDER);
        Path stray = FILES_FOLDER.resolve(UUID.randomUUID() + ".jpg");
        Files.write(stray, TestImages.jpeg());
        return stray;
    }

    private static void backdate(Path file, Duration age) throws IOException {
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(age)));
    }
}
