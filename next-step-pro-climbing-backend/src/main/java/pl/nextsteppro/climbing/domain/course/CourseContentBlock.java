package pl.nextsteppro.climbing.domain.course;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course_content_blocks")
public class CourseContentBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", nullable = false, length = 10)
    private CourseBlockType blockType;

    @Column(columnDefinition = "TEXT")
    @Nullable
    private String content;

    @Column(name = "image_filename", length = 500)
    @Nullable
    private String imageFilename;

    @Column(columnDefinition = "TEXT")
    @Nullable
    private String caption;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourseContentBlock() {}

    public CourseContentBlock(Course course, CourseBlockType blockType) {
        this.course = course;
        this.blockType = blockType;
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

    public Course getCourse() {
        return course;
    }

    public CourseBlockType getBlockType() {
        return blockType;
    }

    @Nullable
    public String getContent() {
        return content;
    }

    @Nullable
    public String getImageFilename() {
        return imageFilename;
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
    public void setContent(@Nullable String content) {
        this.content = content;
    }

    public void setImageFilename(@Nullable String imageFilename) {
        this.imageFilename = imageFilename;
    }

    public void setCaption(@Nullable String caption) {
        this.caption = caption;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
