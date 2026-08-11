package pl.nextsteppro.climbing.domain.personaltraining;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * A file attached to one message in the athlete &lt;-&gt; coach thread — a photo of a route, a
 * screenshot from a watch, a PDF plan. Stored under the {@code commentfiles/} folder, which is
 * deliberately absent from {@link pl.nextsteppro.climbing.api.file.FileController}: these are never
 * reachable without a session.
 *
 * <p>Deliberately NOT a {@link TrainingAttachment} with a third owner. Attachments carry the
 * <em>coach's</em> materials and are copied by "duplicate training" and "use template"; a photo
 * somebody sent in conversation must not travel with a copied plan. They also live forever, while
 * these expire — see {@link #getExpiresAt()}.
 *
 * <p>One row is exactly one file on disk (enforced by a unique index on {@code filename}), so
 * deleting the row means deleting the file, with no reference counting to get wrong.
 */
@Entity
@Table(name = "training_comment_files")
public class TrainingCommentFile {

    public static final int MAX_PER_COMMENT = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private TrainingComment comment;

    @Column(nullable = false, length = 64)
    private String filename;

    @Column(name = "original_name")
    @Nullable
    private String originalName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Both set or both null (DB CHECK) — a PDF has no dimensions. Reserves layout space. */
    @Nullable
    private Short width;

    @Nullable
    private Short height;

    @Column(nullable = false)
    private short position;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TrainingCommentFile() {}

    public TrainingCommentFile(TrainingComment comment, String filename, @Nullable String originalName,
                               String mimeType, long sizeBytes, @Nullable Integer width, @Nullable Integer height,
                               int position, Instant expiresAt) {
        this.comment = comment;
        this.filename = filename;
        this.originalName = originalName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.width = width == null ? null : width.shortValue();
        this.height = height == null ? null : height.shortValue();
        this.position = (short) position;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /**
     * The only client-supplied string that survives to display: the original filename cannot be
     * recovered from the UUID on disk. Escaped and capped, exactly like
     * {@link TrainingAttachment#sanitizeLabel}.
     */
    @Nullable
    public static String sanitizeName(@Nullable String name) {
        if (name == null || name.isBlank()) return null;
        String escaped = HtmlUtils.htmlEscape(name.trim(), StandardCharsets.UTF_8.name());
        return escaped.length() > 255 ? escaped.substring(0, 255) : escaped;
    }

    public UUID getId() {
        return id;
    }

    public TrainingComment getComment() {
        return comment;
    }

    public String getFilename() {
        return filename;
    }

    @Nullable
    public String getOriginalName() {
        return originalName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    @Nullable
    public Short getWidth() {
        return width;
    }

    @Nullable
    public Short getHeight() {
        return height;
    }

    public short getPosition() {
        return position;
    }

    /** When the retention sweep removes this file. Shown in the UI, so it is never a surprise. */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
