package pl.nextsteppro.climbing.api.admin.video;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin DTOs dla zarządzania filmami
 */
public class AdminVideoDtos {

    public record VideoAdminDto(
            UUID id,
            String title,
            @Nullable String excerpt,
            @Nullable String content,
            String youtubeUrl,
            int displayOrder,
            boolean published,
            @Nullable Instant publishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CreateVideoRequest(
            @NotBlank @Size(max = 500) String title,
            @Nullable String excerpt,
            @Nullable String content,
            @NotBlank @Size(max = 2048) String youtubeUrl
    ) {}

    public record UpdateVideoRequest(
            @Nullable @Size(max = 500) String title,
            @Nullable String excerpt,
            @Nullable String content,
            @Nullable @Size(max = 2048) String youtubeUrl
    ) {}
}
