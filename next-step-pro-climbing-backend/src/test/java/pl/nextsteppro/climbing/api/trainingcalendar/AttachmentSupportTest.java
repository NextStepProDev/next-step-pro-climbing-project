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
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
