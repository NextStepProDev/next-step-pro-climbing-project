package pl.nextsteppro.climbing.domain.gallery;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "photos")
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id", nullable = false)
    private Album album;

    @Column(nullable = false, length = 500)
    private String filename;

    @Column(columnDefinition = "TEXT")
    @Nullable
    private String caption;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "focal_point_x")
    @Nullable
    private Float focalPointX;

    @Column(name = "focal_point_y")
    @Nullable
    private Float focalPointY;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Photo() {}

    public Photo(Album album, String filename) {
        this.album = album;
        this.filename = filename;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public Album getAlbum() {
        return album;
    }

    public String getFilename() {
        return filename;
    }

    @Nullable
    public String getCaption() {
        return caption;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // Setters
    public void setAlbum(Album album) {
        this.album = album;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setCaption(@Nullable String caption) {
        this.caption = caption;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    @Nullable
    public Float getFocalPointX() {
        return focalPointX;
    }

    public void setFocalPointX(@Nullable Float focalPointX) {
        this.focalPointX = focalPointX;
    }

    @Nullable
    public Float getFocalPointY() {
        return focalPointY;
    }

    public void setFocalPointY(@Nullable Float focalPointY) {
        this.focalPointY = focalPointY;
    }
}
