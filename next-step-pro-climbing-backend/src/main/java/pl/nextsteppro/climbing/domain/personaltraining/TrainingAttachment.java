package pl.nextsteppro.climbing.domain.personaltraining;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import org.springframework.web.util.HtmlUtils;
import pl.nextsteppro.climbing.domain.trainingtemplate.TrainingTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * A material attached to a training — either a pasted link ({@link AttachmentKind#LINK}) or an
 * uploaded file ({@link AttachmentKind#FILE}, PDF/image stored under the {@code training/} folder).
 * YouTube/Instagram links are rendered as an embedded player (the embed URL is computed in the DTO,
 * never stored).
 *
 * <p>Orchestrated by the service (no JPA collection on {@link PersonalTraining}), mirroring comments
 * and deletions. Files stored on disk are cleaned up by the service on delete/replace (the DB CASCADE
 * only removes the rows).
 */
@Entity
@Table(name = "training_attachments")
public class TrainingAttachment {

    public static final int MAX_LABEL_LENGTH = 120;
    public static final int MAX_URL_LENGTH = 2048;
    public static final int MAX_PER_TRAINING = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Exactly one owner is set (DB CHECK): a training or a template.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id")
    @Nullable
    private PersonalTraining training;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    @Nullable
    private TrainingTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AttachmentKind kind;

    // LINK: the pasted URL
    @Column(length = MAX_URL_LENGTH)
    @Nullable
    private String url;

    // FILE: stored filename (UUID.ext) + display metadata
    @Nullable
    private String filename;

    @Column(name = "original_name")
    @Nullable
    private String originalName;

    @Column(name = "mime_type")
    @Nullable
    private String mimeType;

    @Column(name = "size_bytes")
    @Nullable
    private Long sizeBytes;

    @Column(length = MAX_LABEL_LENGTH)
    @Nullable
    private String label;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TrainingAttachment() {}

    private TrainingAttachment(AttachmentKind kind, int position, @Nullable String label) {
        this.kind = kind;
        this.position = position;
        this.label = label;
    }

    private void asLink(String url) {
        this.url = url;
    }

    private void asFile(String filename, @Nullable String originalName, @Nullable String mimeType, @Nullable Long sizeBytes) {
        this.filename = filename;
        this.originalName = originalName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
    }

    public static TrainingAttachment link(PersonalTraining training, String url, @Nullable String label, int position) {
        TrainingAttachment a = new TrainingAttachment(AttachmentKind.LINK, position, label);
        a.training = training;
        a.asLink(url);
        return a;
    }

    public static TrainingAttachment file(PersonalTraining training, String filename, @Nullable String originalName,
                                          @Nullable String mimeType, @Nullable Long sizeBytes,
                                          @Nullable String label, int position) {
        TrainingAttachment a = new TrainingAttachment(AttachmentKind.FILE, position, label);
        a.training = training;
        a.asFile(filename, originalName, mimeType, sizeBytes);
        return a;
    }

    public static TrainingAttachment link(TrainingTemplate template, String url, @Nullable String label, int position) {
        TrainingAttachment a = new TrainingAttachment(AttachmentKind.LINK, position, label);
        a.template = template;
        a.asLink(url);
        return a;
    }

    public static TrainingAttachment file(TrainingTemplate template, String filename, @Nullable String originalName,
                                          @Nullable String mimeType, @Nullable Long sizeBytes,
                                          @Nullable String label, int position) {
        TrainingAttachment a = new TrainingAttachment(AttachmentKind.FILE, position, label);
        a.template = template;
        a.asFile(filename, originalName, mimeType, sizeBytes);
        return a;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /** Same UTF-8 HTML-escape as PersonalTraining.sanitizeText; null/blank label is allowed. */
    @Nullable
    public static String sanitizeLabel(@Nullable String label) {
        if (label == null || label.isBlank()) return null;
        String escaped = HtmlUtils.htmlEscape(label.trim(), java.nio.charset.StandardCharsets.UTF_8.name());
        return escaped.length() > MAX_LABEL_LENGTH ? escaped.substring(0, MAX_LABEL_LENGTH) : escaped;
    }

    public UUID getId() {
        return id;
    }

    /** FK access without initialising the lazy proxy. NOT named getTrainingId() on purpose —
     * a "trainingId" bean property makes Spring Data derive findByTrainingId as the invalid
     * path a.trainingId instead of a.training.id. Only valid for training-owned rows. */
    public UUID trainingId() {
        return java.util.Objects.requireNonNull(training).getId();
    }

    /** FK access for template-owned rows (see {@link #trainingId()} for the naming rationale). */
    public UUID templateId() {
        return java.util.Objects.requireNonNull(template).getId();
    }

    public AttachmentKind getKind() {
        return kind;
    }

    @Nullable
    public String getUrl() {
        return url;
    }

    @Nullable
    public String getFilename() {
        return filename;
    }

    @Nullable
    public String getOriginalName() {
        return originalName;
    }

    @Nullable
    public String getMimeType() {
        return mimeType;
    }

    @Nullable
    public Long getSizeBytes() {
        return sizeBytes;
    }

    @Nullable
    public String getLabel() {
        return label;
    }

    public int getPosition() {
        return position;
    }

    // Real owner accessors (safe as bean getters — the fake "trainingId" property was the problem)
    @Nullable
    public PersonalTraining getTraining() {
        return training;
    }

    @Nullable
    public TrainingTemplate getTemplate() {
        return template;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
