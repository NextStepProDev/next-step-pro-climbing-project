package pl.nextsteppro.climbing.api.trainingcalendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentFileRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.infrastructure.storage.TestImages;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Attachments on messages in a training thread, over real PostgreSQL (V80).
 *
 * <p>Lives in this package rather than {@code integration/} because the DTO records are
 * package-private.
 */
class CommentAttachmentIntegrationTest extends BaseIntegrationTest {

    @Autowired private TrainingCalendarService trainingCalendarService;
    @Autowired private AdminTrainingCalendarService adminTrainingCalendarService;
    @Autowired private PersonalTrainingRepository personalTrainingRepository;
    @Autowired private TrainingCommentRepository trainingCommentRepository;
    @Autowired private TrainingCommentFileRepository commentFileRepository;
    @Autowired private pl.nextsteppro.climbing.infrastructure.storage.FileStorageService fileStorageService;

    private User athlete;
    private User otherAthlete;
    private User coach;
    private UUID trainingId;

    @BeforeEach
    void setUp() {
        commentFileRepository.deleteAll();
        trainingCommentRepository.deleteAll();
        personalTrainingRepository.deleteAll();
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        eventRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        athlete = saveAthlete("athlete@example.com", "Anna", true);
        otherAthlete = saveAthlete("other@example.com", "Ola", true);

        coach = new User("coach@example.com", "Trener", "Główny", "+48111111111", "coach");
        coach.setRole(UserRole.ADMIN);
        coach.setEmailVerified(true);
        coach = userRepository.save(coach);

        trainingId = trainingCalendarService.createMy(athlete.getId(), new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(1), LocalTime.of(17, 0), LocalTime.of(19, 0),
            "Sesja bulderowa", null, null)).id();
    }

    private User saveAthlete(String email, String firstName, boolean consent) {
        User user = new User(email, firstName, "Wspinaczka", "+48123456789", "hash");
        user.setRole(UserRole.USER);
        user.setEmailVerified(true);
        user.setAthlete(true);
        if (consent) user.grantTrainingConsent();
        return userRepository.save(user);
    }

    private static MultipartFile jpeg(String name) {
        return new MockMultipartFile("files", name, "image/jpeg", TestImages.jpeg(400, 300));
    }

    private static MultipartFile pdf(String name) {
        return new MockMultipartFile("files", name, "application/pdf", TestImages.pdf());
    }

    // ---------- uploading ----------

    @Test
    void shouldAttachAPhotoAndRecordItsRealDimensionsAndExpiry() {
        TrainingCommentDto comment = trainingCalendarService.addMyCommentWithFiles(
            athlete.getId(), trainingId, "Tak wyszła ta sekwencja", List.of(jpeg("droga.jpg")));

        assertEquals(1, comment.files().size());
        TrainingCommentFileDto file = comment.files().getFirst();
        assertEquals("image/jpeg", file.mimeType());
        // Read back from the stored bytes, not echoed from the request
        assertEquals(400, file.width());
        assertEquals(300, file.height());
        assertTrue(file.url().startsWith("/api/training-calendar/comment-files/"));
        assertTrue(file.canDelete());

        long days = ChronoUnit.DAYS.between(Instant.now(), file.expiresAt());
        assertTrue(days > 360 && days <= 365, "Expected roughly a year of retention, got " + days + " days");
    }

    @Test
    void shouldAcceptAMessageThatIsNothingButAFile() {
        // A photo of a route is a whole message; body became nullable in V80 for exactly this.
        TrainingCommentDto comment = trainingCalendarService.addMyCommentWithFiles(
            athlete.getId(), trainingId, null, List.of(jpeg("droga.jpg")));

        assertNull(comment.body());
        assertEquals(1, comment.files().size());
    }

    @Test
    void shouldRefuseAMessageWithNeitherTextNorFile() {
        assertThrows(IllegalArgumentException.class, () -> trainingCalendarService.addMyCommentWithFiles(
            athlete.getId(), trainingId, "   ", List.of()));
    }

    @Test
    void shouldRefuseAFourthFileOnOneMessage() {
        assertThrows(IllegalArgumentException.class, () -> trainingCalendarService.addMyCommentWithFiles(
            athlete.getId(), trainingId, null,
            List.of(jpeg("a.jpg"), jpeg("b.jpg"), jpeg("c.jpg"), jpeg("d.jpg"))));
    }

    @Test
    void shouldStorePdfAsIsAndLeaveItWithoutDimensions() {
        TrainingCommentDto comment = trainingCalendarService.addMyCommentWithFiles(
            athlete.getId(), trainingId, null, List.of(pdf("rozpiska.pdf")));

        TrainingCommentFileDto file = comment.files().getFirst();
        assertEquals("application/pdf", file.mimeType());
        assertNull(file.width());
        assertNull(file.height());
    }

    // ---------- what the bytes actually are ----------

    @Test
    void shouldStripExifFromAnUploadedPhoto() throws Exception {
        // A phone photo carries the GPS coordinates of wherever it was taken. Nobody attaching a
        // picture of a route means to publish that, so the re-encode has to remove it.
        byte[] withExif = jpegWithExifMarker();
        assertTrue(containsAscii(withExif, "Exif"), "fixture should carry an EXIF segment to begin with");

        trainingCalendarService.addMyCommentWithFiles(athlete.getId(), trainingId, null,
            List.of(new MockMultipartFile("files", "photo.jpg", "image/jpeg", withExif)));

        var file = commentFileRepository.findAll().getFirst();
        byte[] stored;
        try (var in = fileStorageService.getInputStream(file.getFilename(), CommentFileSupport.FOLDER)) {
            stored = in.readAllBytes();
        }
        assertFalse(containsAscii(stored, "Exif"), "Stored photo still carries an EXIF segment");
    }

    @Test
    void shouldRefuseAPayloadThatIsNotWhatItClaims() {
        // The declared type and the extension are both written by the client; the bytes are not.
        MultipartFile disguisedScript = new MockMultipartFile(
            "files", "shell.jpg", "image/jpeg", "<?php system($_GET['c']); ?>".getBytes());
        assertThrows(IllegalArgumentException.class, () -> trainingCalendarService.addMyCommentWithFiles(
            athlete.getId(), trainingId, null, List.of(disguisedScript)));

        MultipartFile pngNamedJpg = new MockMultipartFile(
            "files", "photo.jpg", "image/jpeg", TestImages.png());
        assertThrows(IllegalArgumentException.class, () -> trainingCalendarService.addMyCommentWithFiles(
            athlete.getId(), trainingId, null, List.of(pngNamedJpg)));

        MultipartFile fakePdf = new MockMultipartFile(
            "files", "plan.pdf", "application/pdf", "not really a pdf".getBytes());
        assertThrows(IllegalArgumentException.class, () -> trainingCalendarService.addMyCommentWithFiles(
            athlete.getId(), trainingId, null, List.of(fakePdf)));
    }

    @Test
    void shouldRefuseWebpBecauseItCannotBeStrippedOrMeasured() {
        // This JVM ships no WebP reader, so accepting one would mean storing a photo exactly as it
        // arrived — metadata included — and with no dimensions for the UI to reserve space with.
        MultipartFile webp = new MockMultipartFile("files", "photo.webp", "image/webp", TestImages.webp());
        assertThrows(IllegalArgumentException.class, () -> trainingCalendarService.addMyCommentWithFiles(
            athlete.getId(), trainingId, null, List.of(webp)));
    }

    // ---------- who may see and remove ----------

    @Test
    void shouldHideAnAttachmentFromEveryoneOutsideTheThread() {
        UUID fileId = attachAsAthlete();

        assertThrows(IllegalArgumentException.class,
            () -> trainingCalendarService.openCommentFile(otherAthlete.getId(), false, fileId));
        assertThrows(IllegalArgumentException.class,
            () -> trainingCalendarService.deleteCommentFile(otherAthlete.getId(), false, fileId));
    }

    @Test
    void shouldAnswerAStrangerExactlyAsItAnswersAMadeUpId() {
        UUID realButForeign = attachAsAthlete();

        String forForeign = assertThrows(IllegalArgumentException.class,
            () -> trainingCalendarService.openCommentFile(otherAthlete.getId(), false, realButForeign))
            .getMessage();
        String forNonsense = assertThrows(IllegalArgumentException.class,
            () -> trainingCalendarService.openCommentFile(otherAthlete.getId(), false, UUID.randomUUID()))
            .getMessage();

        // Two different replies is all it takes to enumerate: a stranger could tell a real file id
        // from a guessed one without ever seeing a byte. The guards' own messages talk about the
        // TRAINING, which is exactly the tell.
        assertEquals(forNonsense, forForeign);
    }

    @Test
    void shouldRefuseAnAthleteWhoHasNotGivenConsent() {
        User unconsented = saveAthlete("noconsent@example.com", "Bez", false);
        UUID theirTraining = trainingCalendarService.createMy(athlete.getId(), new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(2), null, null, "Plan", null, null)).id();

        assertThrows(IllegalStateException.class, () -> trainingCalendarService.addMyCommentWithFiles(
            unconsented.getId(), theirTraining, null, List.of(jpeg("a.jpg"))));
    }

    @Test
    void shouldLetTheCoachReadWhatTheAthleteAttached() {
        UUID fileId = attachAsAthlete();

        CommentFileStream stream = trainingCalendarService.openCommentFile(coach.getId(), true, fileId);
        assertEquals("image/jpeg", stream.mimeType());
        assertTrue(stream.size() > 0);
    }

    @Test
    void shouldStopAnAthleteFromRemovingWhatTheCoachSent() {
        TrainingCommentDto coachComment = adminTrainingCalendarService.addCommentWithFiles(
            coach.getId(), trainingId, "Zobacz rozpiskę", List.of(pdf("plan.pdf")));
        UUID fileId = coachComment.files().getFirst().id();

        // Visible to the athlete, but not theirs to withdraw.
        assertThrows(IllegalStateException.class,
            () -> trainingCalendarService.deleteCommentFile(athlete.getId(), false, fileId));

        assertDoesNotThrow(() -> trainingCalendarService.deleteCommentFile(coach.getId(), true, fileId));
    }

    @Test
    void shouldMarkCoachFilesAsUndeletableForTheAthlete() {
        adminTrainingCalendarService.addCommentWithFiles(
            coach.getId(), trainingId, null, List.of(pdf("plan.pdf")));

        var asSeenByAthlete = trainingCalendarService.getMyComments(athlete.getId(), trainingId);
        assertFalse(asSeenByAthlete.getFirst().files().getFirst().canDelete());
    }

    // ---------- deleting ----------

    @Test
    void shouldKeepTheWordsWhenOnlyTheFileIsRemoved() {
        TrainingCommentDto comment = trainingCalendarService.addMyCommentWithFiles(
            athlete.getId(), trainingId, "Nogi dziś martwe", List.of(jpeg("a.jpg")));

        trainingCalendarService.deleteCommentFile(athlete.getId(), false, comment.files().getFirst().id());

        var thread = trainingCalendarService.getMyComments(athlete.getId(), trainingId);
        assertEquals(1, thread.size());
        assertEquals("Nogi dziś martwe", thread.getFirst().body());
        assertTrue(thread.getFirst().files().isEmpty());
    }

    @Test
    void shouldRemoveTheWholeMessageWhenTheFileWasAllOfIt() {
        TrainingCommentDto comment = trainingCalendarService.addMyCommentWithFiles(
            athlete.getId(), trainingId, null, List.of(jpeg("a.jpg")));

        trainingCalendarService.deleteCommentFile(athlete.getId(), false, comment.files().getFirst().id());

        // An empty bubble would sit in the thread forever; body is nullable, so nothing else stops it.
        assertTrue(trainingCalendarService.getMyComments(athlete.getId(), trainingId).isEmpty());
    }

    @Test
    void shouldUnlinkFilesFromDiskWhenTheTrainingIsDeleted() {
        trainingCalendarService.addMyCommentWithFiles(athlete.getId(), trainingId, null, List.of(jpeg("a.jpg")));
        String filename = commentFileRepository.findAll().getFirst().getFilename();
        assertTrue(fileStorageService.exists(filename, CommentFileSupport.FOLDER));

        // Comment rows vanish through the DB cascade without Hibernate loading them, so nothing
        // but an explicit purge can reach the files.
        trainingCalendarService.deleteMy(athlete.getId(), trainingId);

        assertFalse(fileStorageService.exists(filename, CommentFileSupport.FOLDER));
    }

    private UUID attachAsAthlete() {
        return trainingCalendarService.addMyCommentWithFiles(
            athlete.getId(), trainingId, null, List.of(jpeg("a.jpg"))).files().getFirst().id();
    }

    /** A real JPEG with an APP1 "Exif" segment spliced in right after the SOI marker. */
    private static byte[] jpegWithExifMarker() throws Exception {
        byte[] base = TestImages.jpeg(120, 90);
        byte[] payload = "Exif\0\0GPSLatitude 50.123 GPSLongitude 19.456".getBytes();
        int length = payload.length + 2;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(base, 0, 2); // SOI
        out.write(0xFF);
        out.write(0xE1); // APP1
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.write(payload);
        out.write(base, 2, base.length - 2);
        return out.toByteArray();
    }

    private static boolean containsAscii(byte[] haystack, String needle) {
        byte[] pattern = needle.getBytes();
        outer:
        for (int i = 0; i <= haystack.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (haystack[i + j] != pattern[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
