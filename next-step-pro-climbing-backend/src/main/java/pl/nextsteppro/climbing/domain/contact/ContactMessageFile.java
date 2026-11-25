package pl.nextsteppro.climbing.domain.contact;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.StoredText;

import java.time.Instant;
import java.util.UUID;

/**
 * A photo or a PDF attached to one chat message (V111). Stored in the private {@code contactfiles/}
 * folder, which {@link pl.nextsteppro.climbing.api.file.FileController} never maps: these are read
 * only through the chat's own endpoints, which check the conversation's owner.
 *
 * <p>The twin of {@code TrainingCommentFile}, kept separate on purpose: those hang off the
 * coach/athlete training threads and are swept, exported and erased along those paths. One row is
 * exactly one file on disk (unique {@code filename}), so deleting the row deletes the file.
 */
@Entity
@Table(name = "contact_message_files")
public class ContactMessageFile {

    public static final int MAX_PER_MESSAGE = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ContactMessage message;

    @Column(nullable = false, length = 64)
    private String filename;

    @Column(name = "original_name")
    @Nullable
    private String originalName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Both set or both null (CHECK) - a PDF has no dimensions. Reserves the image's space in the chat. */
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

    protected ContactMessageFile() {}

    public ContactMessageFile(ContactMessage message, String filename, @Nullable String originalName,
                              String mimeType, long sizeBytes, @Nullable Integer width, @Nullable Integer height,
                              int position, Instant expiresAt) {
        this.message = message;
        this.filename = filename;
        this.originalName = originalName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        // Both or neither, and never a value the SMALLINT would wrap into a negative size: a bare
        // shortValue() of a 40000 px panorama is negative, and the chat reserves that much space.
        boolean usable = fitsColumn(width) && fitsColumn(height) && width != null && height != null;
        this.width = usable ? width.shortValue() : null;
        this.height = usable ? height.shortValue() : null;
        this.position = (short) position;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    private static boolean fitsColumn(@Nullable Integer value) {
        return value == null || (value >= 0 && value <= Short.MAX_VALUE);
    }

    /** The only client-supplied string that survives to display; the name on disk is a UUID. */
    public static @Nullable String sanitizeName(@Nullable String name) {
        return StoredText.escapeAndCap(name, 255);
    }

    public UUID getId() {
        return id;
    }

    public ContactMessage getMessage() {
        return message;
    }

    public String getFilename() {
        return filename;
    }

    public @Nullable String getOriginalName() {
        return originalName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public @Nullable Short getWidth() {
        return width;
    }

    public @Nullable Short getHeight() {
        return height;
    }

    public short getPosition() {
        return position;
    }

    /** When the retention sweep removes the file - shown next to it, so it is never a surprise. */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
