package pl.nextsteppro.climbing.api.course;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Publiczne DTOs dla kursów
 */
public class CourseDtos {

    public record CourseSummaryDto(
            UUID id,
            String title,
            @Nullable String excerpt,
            @Nullable String thumbnailUrl,
            @Nullable Float thumbnailFocalPointX,
            @Nullable Float thumbnailFocalPointY,
            @Nullable Instant publishedAt
    ) {}

    public record CourseDetailDto(
            UUID id,
            String title,
            @Nullable String excerpt,
            @Nullable String thumbnailUrl,
            @Nullable Float thumbnailFocalPointX,
            @Nullable Float thumbnailFocalPointY,
            List<ContentBlockDto> blocks,
            @Nullable Instant publishedAt
    ) {}

    public record ContentBlockDto(
            UUID id,
            String blockType,
            @Nullable String content,
            @Nullable String imageUrl,
            @Nullable String caption,
            int displayOrder
    ) {}
}
