package pl.nextsteppro.climbing.api.trainingcalendar;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingKind;
import pl.nextsteppro.climbing.domain.trainingtemplate.TrainingTemplate;
import pl.nextsteppro.climbing.domain.trainingtemplate.TrainingTemplateRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.util.List;
import java.util.UUID;

/**
 * Coach's reusable entry-template library (shared across all athletes). Applying a template copies
 * its content into a new entry on the frontend, so later edits here never touch already-created
 * trainings. A template makes either a TRAINING or a TASK, and carries only the fields that kind
 * has. Materials reuse the training_attachments table (owned by the template).
 * Not activity-logged — this is library management, not an athlete-facing mutation.
 */
@Service
@Transactional
public class TrainingTemplateService {

    private final TrainingTemplateRepository templateRepository;
    private final AttachmentSupport attachments;
    private final MessageService msg;

    public TrainingTemplateService(TrainingTemplateRepository templateRepository,
                                   AttachmentSupport attachments,
                                   MessageService msg) {
        this.templateRepository = templateRepository;
        this.attachments = attachments;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public List<TrainingTemplateDto> list() {
        return templateRepository.findAllByOrderByTitleAsc().stream()
            .map(t -> toDto(t, attachments.dtosForTemplate(t.getId())))
            .toList();
    }

    public TrainingTemplateDto create(SaveTemplateRequest request) {
        attachments.validate(request.attachments());
        TrainingKind kind = kindOf(request);
        requireShapeMatchingKind(kind, request);
        TrainingTemplate template = new TrainingTemplate(
            kind,
            requireSanitizedTitle(request.title()),
            TrainingTemplate.sanitizeText(request.description(), TrainingTemplate.MAX_DESCRIPTION_LENGTH),
            request.defaultDurationMinutes(),
            request.targetCalories());
        templateRepository.save(template);
        if (request.attachments() != null) {
            attachments.persistForTemplate(template, request.attachments());
        }
        return toDto(template, attachments.dtosForTemplate(template.getId()));
    }

    public TrainingTemplateDto update(UUID templateId, SaveTemplateRequest request) {
        TrainingTemplate template = requireTemplate(templateId);
        attachments.validate(request.attachments());
        TrainingKind kind = kindOf(request);
        requireShapeMatchingKind(kind, request);
        template.update(
            kind,
            requireSanitizedTitle(request.title()),
            TrainingTemplate.sanitizeText(request.description(), TrainingTemplate.MAX_DESCRIPTION_LENGTH),
            request.defaultDurationMinutes(),
            request.targetCalories());
        if (request.attachments() != null) {
            attachments.replaceForTemplate(template, request.attachments());
        }
        return toDto(template, attachments.dtosForTemplate(template.getId()));
    }

    public void delete(UUID templateId) {
        TrainingTemplate template = requireTemplate(templateId);
        attachments.purgeTemplateAttachments(templateId);
        templateRepository.delete(template);
    }

    private TrainingTemplate requireTemplate(UUID templateId) {
        return templateRepository.findById(templateId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("training.template.not.found")));
    }

    private static TrainingKind kindOf(SaveTemplateRequest request) {
        return request.kind() != null ? request.kind() : TrainingKind.TRAINING;
    }

    /**
     * A template comes in exactly one of two shapes, mirroring the entry it creates: a TRAINING
     * brings a duration and no calorie target, a TASK brings the reverse. Enforced here rather than
     * left to the database CHECK so a mismatched request answers 400 with a sentence instead of a
     * constraint-violation 409.
     */
    private void requireShapeMatchingKind(TrainingKind kind, SaveTemplateRequest request) {
        if (kind == TrainingKind.TASK) {
            if (request.defaultDurationMinutes() != null) {
                throw new IllegalArgumentException(msg.get("training.template.task.duration"));
            }
            return;
        }
        if (request.defaultDurationMinutes() == null) {
            throw new IllegalArgumentException(msg.get("training.template.duration.required"));
        }
        if (request.targetCalories() != null) {
            throw new IllegalArgumentException(msg.get("training.template.calories.task.only"));
        }
    }

    private String requireSanitizedTitle(String title) {
        String sanitized = TrainingTemplate.sanitizeText(title, TrainingTemplate.MAX_TITLE_LENGTH);
        if (sanitized == null) {
            throw new IllegalArgumentException(msg.get("training.template.title.empty"));
        }
        return sanitized;
    }

    private static TrainingTemplateDto toDto(TrainingTemplate t, List<TrainingAttachmentDto> attachmentDtos) {
        return new TrainingTemplateDto(t.getId(), t.getKind(), t.getTitle(), t.getDescription(),
            t.getDefaultDurationMinutes(), t.getTargetCalories(), attachmentDtos, t.getUpdatedAt());
    }
}
