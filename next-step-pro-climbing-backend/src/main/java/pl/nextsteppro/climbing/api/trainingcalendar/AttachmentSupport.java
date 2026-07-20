package pl.nextsteppro.climbing.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTraining;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingAttachment;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingAttachmentRepository;
import pl.nextsteppro.climbing.domain.trainingtemplate.TrainingTemplate;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.media.VideoEmbedUrls;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Shared handling of training materials (links + uploaded files), used by both trainings
 * and templates. Centralises validation, persistence (per owner), DTO mapping, upload, and
 * reference-counted disk cleanup so a physical file is removed only once no attachment
 * (of any owner) points at it.
 */
@Component
public class AttachmentSupport {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentSupport.class);

    /** Uploaded materials live here (see FileController /training/{filename}). */
    static final String FOLDER = "training";

    private final TrainingAttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;
    private final MessageService msg;

    AttachmentSupport(TrainingAttachmentRepository attachmentRepository,
                      FileStorageService fileStorageService,
                      MessageService msg) {
        this.attachmentRepository = attachmentRepository;
        this.fileStorageService = fileStorageService;
        this.msg = msg;
    }

    // ---------- validation ----------

    void validate(@Nullable List<AttachmentRequest> requests) {
        if (requests == null) return;
        if (requests.size() > TrainingAttachment.MAX_PER_TRAINING) {
            throw new IllegalArgumentException(msg.get("training.attachment.too.many"));
        }
        for (AttachmentRequest req : requests) {
            if (req.isFile()) {
                validateFile(req.filename());
            } else {
                validateLinkUrl(req.url());
            }
        }
    }

    private void validateLinkUrl(@Nullable String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException(msg.get("training.attachment.url.invalid"));
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException(msg.get("training.attachment.url.invalid"));
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(msg.get("training.attachment.url.invalid"));
        }
    }

    /** A FILE attachment must reference a file already uploaded to the training folder. */
    private void validateFile(@Nullable String filename) {
        boolean ok;
        try {
            ok = filename != null && fileStorageService.exists(filename, FOLDER);
        } catch (IllegalArgumentException e) {
            ok = false; // malformed filename (path-traversal guard) → treat as missing
        }
        if (!ok) {
            throw new IllegalArgumentException(msg.get("training.attachment.file.invalid"));
        }
    }

    // ---------- persistence ----------

    void persistForTraining(PersonalTraining training, List<AttachmentRequest> requests) {
        int position = 0;
        for (AttachmentRequest req : requests) {
            String label = TrainingAttachment.sanitizeLabel(req.label());
            attachmentRepository.save(req.isFile()
                ? TrainingAttachment.file(training, req.filename(), sanitizeName(req.originalName()),
                    req.mimeType(), req.sizeBytes(), label, position++)
                : TrainingAttachment.link(training, req.url().trim(), label, position++));
        }
    }

    void persistForTemplate(TrainingTemplate template, List<AttachmentRequest> requests) {
        int position = 0;
        for (AttachmentRequest req : requests) {
            String label = TrainingAttachment.sanitizeLabel(req.label());
            attachmentRepository.save(req.isFile()
                ? TrainingAttachment.file(template, req.filename(), sanitizeName(req.originalName()),
                    req.mimeType(), req.sizeBytes(), label, position++)
                : TrainingAttachment.link(template, req.url().trim(), label, position++));
        }
    }

    /** Replace-all for a training: wipe old rows, persist new, drop now-unreferenced files. */
    void replaceForTraining(PersonalTraining training, List<AttachmentRequest> requests) {
        List<String> oldFiles = fileFilenames(attachmentRepository.findByTrainingIdOrderByPositionAsc(training.getId()));
        attachmentRepository.deleteByTrainingId(training.getId());
        persistForTraining(training, requests);
        deleteFilesIfUnreferenced(oldFiles);
    }

    void replaceForTemplate(TrainingTemplate template, List<AttachmentRequest> requests) {
        List<String> oldFiles = fileFilenames(attachmentRepository.findByTemplateIdOrderByPositionAsc(template.getId()));
        attachmentRepository.deleteByTemplateId(template.getId());
        persistForTemplate(template, requests);
        deleteFilesIfUnreferenced(oldFiles);
    }

    // ---------- reads ----------

    List<TrainingAttachmentDto> dtosForTraining(UUID trainingId) {
        return attachmentRepository.findByTrainingIdOrderByPositionAsc(trainingId).stream()
            .map(AttachmentSupport::toDto).toList();
    }

    /** Batch DTO map for the calendar range (avoids N+1 across many trainings). */
    java.util.Map<UUID, List<TrainingAttachmentDto>> dtosForTrainings(List<UUID> trainingIds) {
        java.util.Map<UUID, List<TrainingAttachmentDto>> map = new java.util.HashMap<>();
        if (trainingIds.isEmpty()) return map;
        for (TrainingAttachment a : attachmentRepository.findByTrainingIdInOrderByPositionAsc(trainingIds)) {
            map.computeIfAbsent(a.trainingId(), k -> new java.util.ArrayList<>()).add(toDto(a));
        }
        return map;
    }

    List<TrainingAttachmentDto> dtosForTemplate(UUID templateId) {
        return attachmentRepository.findByTemplateIdOrderByPositionAsc(templateId).stream()
            .map(AttachmentSupport::toDto).toList();
    }

    static TrainingAttachmentDto toDto(TrainingAttachment a) {
        if (a.getKind() == pl.nextsteppro.climbing.domain.personaltraining.AttachmentKind.FILE) {
            String serveUrl = "/api/files/" + FOLDER + "/" + a.getFilename();
            return new TrainingAttachmentDto(a.getId(), "FILE", serveUrl, a.getLabel(),
                null, a.getFilename(), a.getOriginalName(), a.getMimeType(), a.getSizeBytes());
        }
        String url = a.getUrl();
        return new TrainingAttachmentDto(a.getId(), "LINK", url, a.getLabel(),
            url != null ? VideoEmbedUrls.toEmbedUrlOrNull(url) : null, null, null, null, null);
    }

    // ---------- upload + cleanup ----------

    // ---------- admin materials management (central cleanup view) ----------

    List<MaterialDto> listMaterials() {
        var fmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");
        return attachmentRepository.findAllFilesWithOwner().stream().map(a -> {
            String ownerType;
            String ownerLabel;
            if (a.getTraining() != null) {
                ownerType = "TRAINING";
                ownerLabel = a.getTraining().getTrainingDate().format(fmt) + " — " + a.getTraining().getTitle();
            } else if (a.getTemplate() != null) {
                ownerType = "TEMPLATE";
                ownerLabel = a.getTemplate().getTitle();
            } else {
                ownerType = "TRAINING";
                ownerLabel = "";
            }
            return new MaterialDto(a.getId(), a.getOriginalName(), a.getMimeType(), a.getSizeBytes(),
                "/api/files/" + FOLDER + "/" + a.getFilename(), ownerType, ownerLabel, a.getCreatedAt());
        }).toList();
    }

    void deleteMaterial(UUID attachmentId) {
        TrainingAttachment a = attachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("training.attachment.not.found")));
        String filename = a.getFilename();
        attachmentRepository.delete(a);
        attachmentRepository.flush(); // ensure the row is gone before the reference count
        if (filename != null) {
            deleteFilesIfUnreferenced(List.of(filename));
        }
    }

    AttachmentUploadResponse upload(MultipartFile file) {
        try {
            String filename = fileStorageService.storeDocument(file, FOLDER);
            return new AttachmentUploadResponse(
                filename, sanitizeName(file.getOriginalFilename()), file.getContentType(),
                file.getSize(), "/api/files/" + FOLDER + "/" + filename);
        } catch (IOException e) {
            throw new IllegalStateException(msg.get("training.attachment.upload.failed"));
        }
    }

    List<String> fileFilenames(List<TrainingAttachment> attachments) {
        return attachments.stream()
            .filter(a -> a.getKind() == pl.nextsteppro.climbing.domain.personaltraining.AttachmentKind.FILE)
            .map(TrainingAttachment::getFilename)
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    /**
     * Remove a training's attachment rows AND any now-unreferenced files. Call BEFORE deleting the
     * training: the explicit bulk delete flushes immediately, so the reference count is accurate
     * (relying on the DB cascade alone would leave the rows unflushed when we count).
     */
    void purgeTrainingAttachments(UUID trainingId) {
        List<String> files = fileFilenames(attachmentRepository.findByTrainingIdOrderByPositionAsc(trainingId));
        attachmentRepository.deleteByTrainingId(trainingId);
        deleteFilesIfUnreferenced(files);
    }

    void purgeTemplateAttachments(UUID templateId) {
        List<String> files = fileFilenames(attachmentRepository.findByTemplateIdOrderByPositionAsc(templateId));
        attachmentRepository.deleteByTemplateId(templateId);
        deleteFilesIfUnreferenced(files);
    }

    /**
     * Sweep abandoned uploads: files in the training folder that no attachment references and that
     * are older than {@code olderThan}. The grace window protects an upload that was stored but not
     * yet saved onto a training/template (two-phase upload). Returns how many files were deleted.
     */
    public int sweepOrphanUploads(java.time.Duration olderThan) {
        java.time.Instant cutoff = java.time.Instant.now().minus(olderThan);
        int deleted = 0;
        for (String filename : fileStorageService.listFilenames(FOLDER)) {
            try {
                if (attachmentRepository.countByFilename(filename) > 0) continue;
                long modified = fileStorageService.getLastModifiedMillis(filename, FOLDER);
                if (modified < 0 || java.time.Instant.ofEpochMilli(modified).isAfter(cutoff)) continue;
                fileStorageService.delete(filename, FOLDER);
                deleted++;
            } catch (Exception e) {
                // Malformed name or IO error — skip this file, keep sweeping
                logger.warn("Skipping orphan-sweep of {}: {}", filename, e.getMessage());
            }
        }
        return deleted;
    }

    /** Delete each file from disk only if NO attachment (any owner) still references it. */
    void deleteFilesIfUnreferenced(List<String> filenames) {
        for (String filename : filenames) {
            if (filename == null) continue;
            if (attachmentRepository.countByFilename(filename) > 0) continue;
            try {
                fileStorageService.delete(filename, FOLDER);
            } catch (Exception e) {
                logger.warn("Failed to delete orphaned training attachment file {}", filename, e);
            }
        }
    }

    @Nullable
    static String sanitizeName(@Nullable String name) {
        if (name == null || name.isBlank()) return null;
        String escaped = HtmlUtils.htmlEscape(name.trim(), StandardCharsets.UTF_8.name());
        return escaped.length() > 255 ? escaped.substring(0, 255) : escaped;
    }
}
