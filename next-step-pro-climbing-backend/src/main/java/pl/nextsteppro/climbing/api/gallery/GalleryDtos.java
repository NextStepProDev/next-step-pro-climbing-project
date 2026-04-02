package pl.nextsteppro.climbing.api.gallery;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public gallery DTOs for client consumption
 */
public class GalleryDtos {

    public record AlbumSummaryDto(
            UUID id,
            String name,
            @Nullable String description,
            @Nullable String thumbnailUrl,
            long photoCount,
            Instant createdAt
    ) {}

    public record AlbumDetailDto(
            UUID id,
            String name,
            @Nullable String description,
            List<PhotoDto> photos,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record PhotoDto(
            UUID id,
            String url,
            @Nullable String caption,
            Instant createdAt
    ) {}
}
