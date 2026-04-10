package pl.nextsteppro.climbing.domain.course;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 255)
    @Nullable
    private String price;

    @Column(name = "thumbnail_filename", length = 500)
    @Nullable
    private String thumbnailFilename;

    @Column(name = "thumbnail_url", length = 2048)
    @Nullable
    private String thumbnailUrl;

    @Column(name = "thumbnail_focal_point_x")
    @Nullable
    private Float thumbnailFocalPointX;

    @Column(name = "thumbnail_focal_point_y")
    @Nullable
    private Float thumbnailFocalPointY;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "is_published", nullable = false)
    private boolean published = false;

    @Column(name = "published_at")
    @Nullable
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Course() {}

    public Course(String title) {
        this.title = title;
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

    public String getTitle() {
        return title;
    }

    @Nullable
    public String getPrice() {
        return price;
    }

    @Nullable
    public String getThumbnailFilename() {
        return thumbnailFilename;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isPublished() {
        return published;
    }

    @Nullable
    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Nullable
    public Float getThumbnailFocalPointX() {
        return thumbnailFocalPointX;
    }

    @Nullable
    public Float getThumbnailFocalPointY() {
        return thumbnailFocalPointY;
    }

    // Setters
    public void setTitle(String title) {
        this.title = title;
    }

    public void setPrice(@Nullable String price) {
        this.price = price;
    }

    public void setThumbnailFilename(@Nullable String thumbnailFilename) {
        this.thumbnailFilename = thumbnailFilename;
    }

    @Nullable
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(@Nullable String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public void setPublishedAt(@Nullable Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public void setThumbnailFocalPointX(@Nullable Float thumbnailFocalPointX) {
        this.thumbnailFocalPointX = thumbnailFocalPointX;
    }

    public void setThumbnailFocalPointY(@Nullable Float thumbnailFocalPointY) {
        this.thumbnailFocalPointY = thumbnailFocalPointY;
    }
}
