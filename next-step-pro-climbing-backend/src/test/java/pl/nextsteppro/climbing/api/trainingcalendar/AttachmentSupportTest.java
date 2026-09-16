package pl.nextsteppro.climbing.api.trainingcalendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.nextsteppro.climbing.domain.personaltraining.AttachmentKind;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTraining;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingAttachment;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingAttachmentRepository;
import pl.nextsteppro.climbing.domain.trainingtemplate.TrainingTemplate;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for the abandoned-upload sweep (grace window + reference counting). */
@ExtendWith(MockitoExtension.class)
class AttachmentSupportTest {

    @Mock private TrainingAttachmentRepository attachmentRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private MessageService msg;

    private AttachmentSupport support;

    @BeforeEach
    void setUp() {
        support = new AttachmentSupport(attachmentRepository, fileStorageService, msg);
    }

    /**
     * Erasure has to reach the disk: the attachment rows leave with the cascade, so after the
     * account is gone nothing in the database can name these files. They used to wait for the
     * six-hourly orphan sweep, which is the wrong answer to a deletion request.
     */
    @Test
    void shouldUnlinkTheMaterialsOfADepartingAccount() throws Exception {
        String file = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.pdf";
        UUID userId = UUID.randomUUID();
        when(attachmentRepository.findByAthleteId(userId)).thenReturn(List.of(fileAttachment(file)));
        when(attachmentRepository.countByFilename(file)).thenReturn(0L);

        support.purgeForUser(userId);

        verify(attachmentRepository).deleteByAthleteId(userId);
        verify(fileStorageService).delete(file, "training");
    }

    /**
     * ...but reference-counted, unlike the comment attachments. Duplicate, paste and "use template"
     * all point several rows at ONE file on purpose, so deleting the bytes with the account would
     * blank a picture in the coach's library.
     */
    @Test
    void shouldKeepAMaterialTheCoachLibraryStillPointsAt() throws Exception {
        String shared = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.jpg";
        UUID userId = UUID.randomUUID();
        when(attachmentRepository.findByAthleteId(userId)).thenReturn(List.of(fileAttachment(shared)));
        when(attachmentRepository.countByFilename(shared)).thenReturn(1L);

        support.purgeForUser(userId);

        verify(attachmentRepository).deleteByAthleteId(userId);
        verify(fileStorageService, never()).delete(eq(shared), anyString());
    }

    /** A LINK has no bytes of ours; asking storage to remove one would be a malformed-name error. */
    @Test
    void shouldIgnoreLinksWhenPurgingAnAccount() throws Exception {
        UUID userId = UUID.randomUUID();
        when(attachmentRepository.findByAthleteId(userId))
            .thenReturn(List.of(TrainingAttachment.link(mock(PersonalTraining.class), "https://x.pl", null, 0)));

        support.purgeForUser(userId);

        verify(fileStorageService, never()).delete(anyString(), anyString());
    }

    private static TrainingAttachment fileAttachment(String filename) {
        return TrainingAttachment.file(
            mock(PersonalTraining.class), filename, "plan.pdf", "application/pdf", 1024L, null, 0,
            Instant.now().plus(TrainingAttachment.RETENTION));
    }

    @Test
    void shouldDeleteUnreferencedFileOlderThanGrace() throws Exception {
        String file = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.pdf";
        when(fileStorageService.listFilenames("training")).thenReturn(List.of(file));
        when(attachmentRepository.countByFilename(file)).thenReturn(0L);
        when(fileStorageService.getLastModifiedMillis(file, "training"))
            .thenReturn(Instant.now().minus(Duration.ofHours(48)).toEpochMilli());

        int deleted = support.sweepOrphanUploads(Duration.ofHours(24));

        assertEquals(1, deleted);
        verify(fileStorageService).delete(file, "training");
    }

    @Test
    void shouldKeepReferencedFile() throws Exception {
        String file = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.pdf";
        when(fileStorageService.listFilenames("training")).thenReturn(List.of(file));
        when(attachmentRepository.countByFilename(file)).thenReturn(1L);

        int deleted = support.sweepOrphanUploads(Duration.ofHours(24));

        assertEquals(0, deleted);
        verify(fileStorageService, never()).delete(anyString(), anyString());
    }

    @Test
    void shouldKeepRecentUnreferencedFileWithinGrace() throws Exception {
        // Protects a just-uploaded file that is not attached yet (form still open)
        String file = "cccccccc-cccc-cccc-cccc-cccccccccccc.pdf";
        when(fileStorageService.listFilenames("training")).thenReturn(List.of(file));
        when(attachmentRepository.countByFilename(file)).thenReturn(0L);
        when(fileStorageService.getLastModifiedMillis(file, "training"))
            .thenReturn(Instant.now().minus(Duration.ofMinutes(10)).toEpochMilli());

        int deleted = support.sweepOrphanUploads(Duration.ofHours(24));

        assertEquals(0, deleted);
        verify(fileStorageService, never()).delete(anyString(), anyString());
    }

    @Test
    void persistForTrainingDerivesFileMetadataServerSideIgnoringClientClaims() {
        PersonalTraining training = mock(PersonalTraining.class);
        String filename = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee.pdf";
        when(fileStorageService.getFileSize(filename, "training")).thenReturn(4242L);
        // Client echoes back a bogus size (999999) and a mismatched type (image/png) for a .pdf
        AttachmentRequest req = new AttachmentRequest(
            AttachmentKind.FILE, null, filename, "plan.pdf", "image/png", 999_999L, "Plan");

        support.persistForTraining(training, List.of(req));

        ArgumentCaptor<TrainingAttachment> captor = ArgumentCaptor.forClass(TrainingAttachment.class);
        verify(attachmentRepository).save(captor.capture());
        TrainingAttachment saved = captor.getValue();
        assertEquals("application/pdf", saved.getMimeType(), "type derived from filename, not client");
        assertEquals(4242L, saved.getSizeBytes(), "real disk size, not client-claimed");
        assertEquals("plan.pdf", saved.getOriginalName(), "display name kept from client");
    }

    // ---------- retention (a file on a training lives a year; see V99) ----------

    @Test
    void shouldStampARetentionDateOnAFileAttachedToATraining() {
        PersonalTraining training = mock(PersonalTraining.class);
        String filename = "11111111-1111-1111-1111-111111111111.jpg";
        when(fileStorageService.getFileSize(filename, "training")).thenReturn(10L);

        support.persistForTraining(training, List.of(fileRequest(filename)));

        Instant expected = Instant.now().plus(TrainingAttachment.RETENTION);
        Instant actual = savedAttachment().getExpiresAt();
        assertNotNull(actual);
        assertTrue(Math.abs(Duration.between(expected, actual).toSeconds()) < 5,
            "expected roughly " + expected + " but was " + actual);
    }

    /** A link holds none of our bytes, so there is nothing for the sweep to reclaim. */
    @Test
    void shouldLeaveALinkWithoutARetentionDate() {
        support.persistForTraining(mock(PersonalTraining.class),
            List.of(new AttachmentRequest("https://youtu.be/x", "Wideo")));

        assertNull(savedAttachment().getExpiresAt());
    }

    /**
     * A template is a library handed out for years. A PDF expiring underneath it would leave the
     * template silently incomplete, and the coach would find out while giving it to an athlete.
     */
    @Test
    void shouldLeaveATemplateMaterialWithoutARetentionDate() {
        String filename = "22222222-2222-2222-2222-222222222222.pdf";
        when(fileStorageService.getFileSize(filename, "training")).thenReturn(10L);

        support.persistForTemplate(mock(TrainingTemplate.class), List.of(fileRequest(filename)));

        assertNull(savedAttachment().getExpiresAt());
    }

    /**
     * A save rewrites every row of a training, so a file that was already there has to carry its own
     * date across. Otherwise fixing a typo in the title would hand every photo another year, and
     * the promise would only ever bind trainings nobody edits.
     */
    @Test
    void shouldKeepTheOriginalDateWhenAnEditRewritesTheSameFile() {
        PersonalTraining training = mock(PersonalTraining.class);
        String filename = "33333333-3333-3333-3333-333333333333.jpg";
        Instant stampedLastYear = Instant.now().minus(Duration.ofDays(300));
        when(attachmentRepository.findByTrainingIdOrderByPositionAsc(training.getId()))
            .thenReturn(List.of(TrainingAttachment.file(training, filename, "a.jpg", "image/jpeg", 1L,
                null, 0, stampedLastYear)));
        when(fileStorageService.getFileSize(filename, "training")).thenReturn(10L);

        support.replaceForTraining(training, List.of(fileRequest(filename)));

        assertEquals(stampedLastYear, savedAttachment().getExpiresAt());
    }

    @Test
    void shouldRemoveAMaterialPastItsRetentionWindow() throws Exception {
        String file = "44444444-4444-4444-4444-444444444444.jpg";
        Instant now = Instant.now();
        TrainingAttachment expired = TrainingAttachment.file(mock(PersonalTraining.class), file,
            "a.jpg", "image/jpeg", 1L, null, 0, now.minus(Duration.ofDays(1)));
        when(attachmentRepository.findByExpiresAtLessThanEqual(now)).thenReturn(List.of(expired));
        when(attachmentRepository.countByFilename(file)).thenReturn(0L);

        assertEquals(1, support.deleteExpired(now));

        verify(attachmentRepository).deleteAllInBatch(List.of(expired));
        verify(fileStorageService).delete(file, "training");
    }

    /**
     * Copies share one file on purpose (duplicate, paste, "use template"), and each copy carries its
     * own date — so the row goes and the bytes stay until the last one expires.
     */
    @Test
    void shouldKeepTheBytesOfAnExpiredCopyAnotherRowStillPointsAt() throws Exception {
        String shared = "55555555-5555-5555-5555-555555555555.jpg";
        Instant now = Instant.now();
        TrainingAttachment expired = TrainingAttachment.file(mock(PersonalTraining.class), shared,
            "a.jpg", "image/jpeg", 1L, null, 0, now.minus(Duration.ofDays(1)));
        when(attachmentRepository.findByExpiresAtLessThanEqual(now)).thenReturn(List.of(expired));
        when(attachmentRepository.countByFilename(shared)).thenReturn(1L);

        assertEquals(1, support.deleteExpired(now));

        verify(attachmentRepository).deleteAllInBatch(List.of(expired));
        verify(fileStorageService, never()).delete(eq(shared), anyString());
    }

    @Test
    void shouldTouchNothingWhenNoMaterialHasExpired() {
        Instant now = Instant.now();
        when(attachmentRepository.findByExpiresAtLessThanEqual(now)).thenReturn(List.of());

        assertEquals(0, support.deleteExpired(now));

        verify(attachmentRepository, never()).deleteAllInBatch(anyList());
        verify(attachmentRepository, never()).flush();
    }

    private static AttachmentRequest fileRequest(String filename) {
        return new AttachmentRequest(AttachmentKind.FILE, null, filename, "a.jpg", "image/jpeg", 1L, null);
    }

    private TrainingAttachment savedAttachment() {
        ArgumentCaptor<TrainingAttachment> captor = ArgumentCaptor.forClass(TrainingAttachment.class);
        verify(attachmentRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void shouldSkipFileThatThrowsAndKeepSweeping() throws Exception {
        String bad = "not-a-uuid.txt";
        String good = "dddddddd-dddd-dddd-dddd-dddddddddddd.pdf";
        when(fileStorageService.listFilenames("training")).thenReturn(List.of(bad, good));
        // bad → count throws (malformed name guard); good → deletable
        when(attachmentRepository.countByFilename(bad)).thenThrow(new IllegalArgumentException("bad name"));
        when(attachmentRepository.countByFilename(good)).thenReturn(0L);
        when(fileStorageService.getLastModifiedMillis(good, "training"))
            .thenReturn(Instant.now().minus(Duration.ofHours(48)).toEpochMilli());

        int deleted = support.sweepOrphanUploads(Duration.ofHours(24));

        assertEquals(1, deleted);
        verify(fileStorageService).delete(good, "training");
        verify(fileStorageService, never()).delete(eq(bad), anyString());
    }
}
