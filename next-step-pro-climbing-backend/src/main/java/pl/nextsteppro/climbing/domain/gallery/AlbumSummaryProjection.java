package pl.nextsteppro.climbing.domain.gallery;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Projection for album summary data with aggregated photo information.
 * Used to avoid N+1 query problem when fetching all albums.
 */
public interface AlbumSummaryProjection {
    UUID getId();
    String getName();
    @Nullable String getDescription();
    Instant getCreatedAt();
    Instant getUpdatedAt();
    @Nullable String getFirstPhotoFilename();
    Long getPhotoCount();
}
