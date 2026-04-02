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
            Instant publishedAt
    ) {}

    public record NewsDetailDto(
            UUID id,
            String title,
            @Nullable String excerpt,
            @Nullable String thumbnailUrl,
            List<ContentBlockDto> blocks,
            Instant publishedAt
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
