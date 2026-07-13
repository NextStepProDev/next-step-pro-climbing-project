package pl.nextsteppro.climbing.api.video;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Public DTOs for videos
 */
public class VideoDtos {

    public record VideoDto(
            UUID id,
            String title,
            @Nullable String excerpt,
            @Nullable String content,
            String youtubeUrl,
            int displayOrder,
            Instant publishedAt
    ) {}
}
