package pl.nextsteppro.climbing.domain.news;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "news")
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    @Nullable
    private String excerpt;

    @Column(name = "thumbnail_filename", length = 500)
    @Nullable
    private String thumbnailFilename;

    @Column(name = "is_published", nullable = false)
    private boolean published = false;

    @Column(name = "published_at")
    @Nullable
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected News() {}

    public News(String title) {
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
    public String getExcerpt() {
        return excerpt;
    }

    @Nullable
    public String getThumbnailFilename() {
        return thumbnailFilename;
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

    // Setters
    public void setTitle(String title) {
        this.title = title;
    }

    public void setExcerpt(@Nullable String excerpt) {
        this.excerpt = excerpt;
    }

    public void setThumbnailFilename(@Nullable String thumbnailFilename) {
        this.thumbnailFilename = thumbnailFilename;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public void setPublishedAt(@Nullable Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}
