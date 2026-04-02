package pl.nextsteppro.climbing.api.admin.gallery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin gallery DTOs with full details
 */
public class AdminGalleryDtos {

    // Album DTOs
    public record CreateAlbumRequest(
            @NotBlank @Size(max = 255) String name,
            @Nullable String description
    ) {}

    public record UpdateAlbumRequest(
            @Nullable @Size(max = 255) String name,
            @Nullable String description
    ) {}

    public record AlbumAdminDto(
            UUID id,
            String name,
            @Nullable String description,
            @Nullable String thumbnailUrl,
            long photoCount,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record AlbumDetailAdminDto(
            UUID id,
            String name,
            @Nullable String description,
            List<PhotoAdminDto> photos,
            Instant createdAt,
            Instant updatedAt
    ) {}

    // Photo DTOs
    public record PhotoAdminDto(
            UUID id,
            String filename,
            String url,
            @Nullable String caption,
            int displayOrder,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record UpdatePhotoRequest(
            @Nullable String caption,
            @Nullable Integer displayOrder
    ) {}

    public record UploadPhotoResponse(
            UUID id,
            String filename,
            String url
    ) {}
}
