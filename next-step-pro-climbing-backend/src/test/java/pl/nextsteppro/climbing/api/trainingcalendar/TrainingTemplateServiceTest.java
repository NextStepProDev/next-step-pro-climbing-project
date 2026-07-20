package pl.nextsteppro.climbing.api.trainingcalendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.nextsteppro.climbing.domain.personaltraining.AttachmentKind;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingAttachment;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingAttachmentRepository;
import pl.nextsteppro.climbing.domain.trainingtemplate.TrainingTemplate;
import pl.nextsteppro.climbing.domain.trainingtemplate.TrainingTemplateRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TrainingTemplateService (coach's reusable template library).
 * Uses a real AttachmentSupport over mocked repositories/storage to exercise the shared
 * attachment handling (validation, persistence, reference-counted file cleanup).
 */
@ExtendWith(MockitoExtension.class)
class TrainingTemplateServiceTest {

    @Mock private TrainingTemplateRepository templateRepository;
    @Mock private TrainingAttachmentRepository attachmentRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private MessageService msg;

    private TrainingTemplateService service;

    @BeforeEach
    void setUp() {
        AttachmentSupport attachments = new AttachmentSupport(attachmentRepository, fileStorageService, msg);
        service = new TrainingTemplateService(templateRepository, attachments, msg);
        lenient().when(msg.get(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shouldCreateTemplateWithAttachments() {
        // Given
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        SaveTemplateRequest request = new SaveTemplateRequest(
            "Rozgrzewka <b>palców</b>", "Opis", 90,
            List.of(new AttachmentRequest("https://youtu.be/dQw4w9WgXcQ", "Wideo")));

        // When
        TrainingTemplateDto dto = service.create(request);

        // Then
        assertEquals("Rozgrzewka &lt;b&gt;palców&lt;/b&gt;", dto.title());
        assertEquals(90, dto.defaultDurationMinutes());
        verify(attachmentRepository).save(argThat(a -> a.getKind() == AttachmentKind.LINK));
    }

    @Test
    void shouldRejectCreateWhenTitleBlank() {
        SaveTemplateRequest request = new SaveTemplateRequest("   ", null, 60, null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.create(request));
        assertEquals("training.template.title.empty", e.getMessage());
        verify(templateRepository, never()).save(any());
    }

    @Test
    void shouldRejectWhenMoreThanThreeAttachments() {
        SaveTemplateRequest request = new SaveTemplateRequest("T", null, 60, List.of(
            new AttachmentRequest("https://a.com", null), new AttachmentRequest("https://b.com", null),
            new AttachmentRequest("https://c.com", null), new AttachmentRequest("https://d.com", null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verify(templateRepository, never()).save(any());
    }

    @Test
    void shouldListTemplatesSortedByTitle() {
        TrainingTemplate a = new TrainingTemplate("A", null, 60);
        TrainingTemplate b = new TrainingTemplate("B", null, 90);
        when(templateRepository.findAllByOrderByTitleAsc()).thenReturn(List.of(a, b));

        List<TrainingTemplateDto> list = service.list();

        assertEquals(2, list.size());
        assertEquals("A", list.get(0).title());
    }

    @Test
    void shouldReplaceAttachmentsOnUpdate() {
        UUID templateId = UUID.randomUUID();
        TrainingTemplate template = new TrainingTemplate("T", null, 60);
        setField(template, "id", templateId);
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        SaveTemplateRequest request = new SaveTemplateRequest("T2", null, 75,
            List.of(new AttachmentRequest("https://a.com", null)));

        service.update(templateId, request);

        verify(attachmentRepository).deleteByTemplateId(templateId);
        verify(attachmentRepository).save(any());
    }

    @Test
    void shouldDeleteTemplateAndUnreferencedFiles() throws Exception {
        UUID templateId = UUID.randomUUID();
        TrainingTemplate template = new TrainingTemplate("T", null, 60);
        setField(template, "id", templateId);
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(attachmentRepository.findByTemplateIdOrderByPositionAsc(templateId)).thenReturn(List.of(
            TrainingAttachment.file(template, "11111111-1111-1111-1111-111111111111.pdf", "a.pdf", "application/pdf", 1L, null, 0)));
        // No other row references the file → count 0 → delete from disk
        when(attachmentRepository.countByFilename(anyString())).thenReturn(0L);

        service.delete(templateId);

        verify(templateRepository).delete(template);
        verify(fileStorageService).delete("11111111-1111-1111-1111-111111111111.pdf", "training");
    }

    @Test
    void shouldKeepFileOnDiskWhenStillReferencedElsewhere() throws Exception {
        UUID templateId = UUID.randomUUID();
        TrainingTemplate template = new TrainingTemplate("T", null, 60);
        setField(template, "id", templateId);
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(attachmentRepository.findByTemplateIdOrderByPositionAsc(templateId)).thenReturn(List.of(
            TrainingAttachment.file(template, "22222222-2222-2222-2222-222222222222.pdf", "a.pdf", "application/pdf", 1L, null, 0)));
        // A training still references the same physical file → must NOT be deleted
        when(attachmentRepository.countByFilename("22222222-2222-2222-2222-222222222222.pdf")).thenReturn(1L);

        service.delete(templateId);

        verify(fileStorageService, never()).delete(anyString(), anyString());
    }

    @Test
    void shouldRejectWhenTemplateNotFound() {
        UUID templateId = UUID.randomUUID();
        when(templateRepository.findById(templateId)).thenReturn(Optional.empty());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.delete(templateId));
        assertEquals("training.template.not.found", e.getMessage());
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
