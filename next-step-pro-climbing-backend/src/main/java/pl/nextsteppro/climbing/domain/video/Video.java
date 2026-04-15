package pl.nextsteppro.climbing.domain.video;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "videos")
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    @Nullable
    private String excerpt;

    @Column(columnDefinition = "TEXT")
    @Nullable
    private String content;

    @Column(name = "youtube_url", nullable = false, length = 2048)
    private String youtubeUrl;

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

    protected Video() {}

    public Video(String title, String youtubeUrl) {
        this.title = title;
        this.youtubeUrl = youtubeUrl;
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
    public UUID getId() { return id; }
    public String getTitle() { return title; }
    @Nullable public String getExcerpt() { return excerpt; }
    @Nullable public String getContent() { return content; }
    public String getYoutubeUrl() { return youtubeUrl; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isPublished() { return published; }
    @Nullable public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Setters
    public void setTitle(String title) { this.title = title; }
    public void setExcerpt(@Nullable String excerpt) { this.excerpt = excerpt; }
    public void setContent(@Nullable String content) { this.content = content; }
    public void setYoutubeUrl(String youtubeUrl) { this.youtubeUrl = youtubeUrl; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public void setPublished(boolean published) { this.published = published; }
    public void setPublishedAt(@Nullable Instant publishedAt) { this.publishedAt = publishedAt; }
}
