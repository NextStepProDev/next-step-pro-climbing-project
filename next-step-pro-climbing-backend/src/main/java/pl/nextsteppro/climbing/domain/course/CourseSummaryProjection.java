package pl.nextsteppro.climbing.domain.course;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public interface CourseSummaryProjection {

    UUID getId();

    String getTitle();

    @Nullable
    String getExcerpt();

    @Nullable
    String getThumbnailFilename();

    @Nullable
    Float getThumbnailFocalPointX();

    @Nullable
    Float getThumbnailFocalPointY();

    int getDisplayOrder();

    boolean isPublished();

    @Nullable
    Instant getPublishedAt();

    Instant getCreatedAt();

    Instant getUpdatedAt();
}
