package pl.nextsteppro.climbing.api.admin.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin DTOs dla zarządzania kursami
 */
public class AdminCourseDtos {

    public record CourseAdminDto(
            UUID id,
            String title,
            @Nullable String excerpt,
            @Nullable String thumbnailUrl,
            int displayOrder,
            boolean published,
            @Nullable Instant publishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CourseDetailAdminDto(
            UUID id,
            String title,
            @Nullable String excerpt,
            @Nullable String thumbnailFilename,
            @Nullable String thumbnailUrl,
            @Nullable Float thumbnailFocalPointX,
            @Nullable Float thumbnailFocalPointY,
            boolean published,
            @Nullable Instant publishedAt,
            List<ContentBlockAdminDto> blocks,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record ContentBlockAdminDto(
            UUID id,
            String blockType,
            @Nullable String content,
            @Nullable String imageFilename,
            @Nullable String imageUrl,
            @Nullable String caption,
            int displayOrder
    ) {}

    // --- Requesty ---

    public record CreateCourseRequest(
            @NotBlank @Size(max = 500) String title,
            @Nullable @Size(max = 1000) String excerpt
    ) {}

    public record UpdateCourseMetaRequest(
            @Nullable @Size(max = 500) String title,
            @Nullable @Size(max = 1000) String excerpt
    ) {}

    public record AddTextBlockRequest(
            @NotBlank String content
    ) {}

    public record UpdateTextBlockRequest(
            @NotBlank String content
    ) {}

    public record UpdateImageBlockRequest(
            @Nullable String caption
    ) {}

    public record MoveBlockRequest(
            String direction
    ) {}

    public record UpdateThumbnailFocalPointRequest(
            @Nullable Float focalPointX,
            @Nullable Float focalPointY
    ) {}

    public record ReorderCoursesRequest(
            List<UUID> orderedIds
    ) {}

    public record UploadBlockImageResponse(
            UUID blockId,
            String imageFilename,
            String imageUrl,
            int displayOrder
    ) {}

    public record UploadThumbnailResponse(
            String filename,
            String url
    ) {}
}
