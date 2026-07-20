package pl.nextsteppro.climbing.domain.personaltraining;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import org.springframework.web.util.HtmlUtils;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_id", nullable = false)
    private PersonalTraining training;

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

    private TrainingAttachment(PersonalTraining training, AttachmentKind kind, int position, @Nullable String label) {
        this.training = training;
        this.kind = kind;
        this.position = position;
        this.label = label;
    }

    public static TrainingAttachment link(PersonalTraining training, String url, @Nullable String label, int position) {
        TrainingAttachment a = new TrainingAttachment(training, AttachmentKind.LINK, position, label);
        a.url = url;
        return a;
    }

    public static TrainingAttachment file(PersonalTraining training, String filename, @Nullable String originalName,
                                          @Nullable String mimeType, @Nullable Long sizeBytes,
                                          @Nullable String label, int position) {
        TrainingAttachment a = new TrainingAttachment(training, AttachmentKind.FILE, position, label);
        a.filename = filename;
        a.originalName = originalName;
        a.mimeType = mimeType;
        a.sizeBytes = sizeBytes;
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
     * path a.trainingId instead of a.training.id. */
    public UUID trainingId() {
        return training.getId();
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
}
