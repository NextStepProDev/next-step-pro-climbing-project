package pl.nextsteppro.climbing.api.news;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Publiczne DTOs dla aktualności
 */
public class NewsDtos {

    public record NewsSummaryDto(
            UUID id,
            String title,
            @Nullable String excerpt,
            @Nullable String thumbnailUrl,
            @Nullable Float thumbnailFocalPointX,
            @Nullable Float thumbnailFocalPointY,
            Instant publishedAt,
            @Nullable Boolean starred
    ) {}

    public record NewsDetailDto(
            UUID id,
            String title,
            @Nullable String excerpt,
            @Nullable String thumbnailUrl,
            @Nullable Float thumbnailFocalPointX,
            @Nullable Float thumbnailFocalPointY,
            List<ContentBlockDto> blocks,
            Instant publishedAt,
            @Nullable Boolean starred
    ) {}

    public record ContentBlockDto(
            UUID id,
            String blockType,
            @Nullable String content,
            @Nullable String imageUrl,
            @Nullable String caption,
            int displayOrder
    ) {}

    public record NewsPageDto(
            List<NewsSummaryDto> content,
            int page,
            int size,
            long totalElements,
            boolean hasNext
    ) {}
}
