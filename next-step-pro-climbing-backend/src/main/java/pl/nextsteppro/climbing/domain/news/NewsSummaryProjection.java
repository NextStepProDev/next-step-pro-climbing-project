package pl.nextsteppro.climbing.domain.news;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public interface NewsSummaryProjection {

    UUID getId();

    String getTitle();

    @Nullable
    String getExcerpt();

    @Nullable
    String getThumbnailFilename();

    @Nullable
    String getThumbnailUrl();

    @Nullable
    Float getThumbnailFocalPointX();

    @Nullable
    Float getThumbnailFocalPointY();

    boolean isPublished();

    @Nullable
    Instant getPublishedAt();

    Instant getCreatedAt();

    Instant getUpdatedAt();
}
