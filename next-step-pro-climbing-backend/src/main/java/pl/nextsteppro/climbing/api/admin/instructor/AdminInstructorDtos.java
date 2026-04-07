package pl.nextsteppro.climbing.api.admin.instructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin instructor DTOs with full details
 */
public class AdminInstructorDtos {

    public record CreateInstructorRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Nullable String bio,
            @Nullable String certifications
    ) {}

    public record UpdateInstructorRequest(
            @Nullable @Size(max = 100) String firstName,
            @Nullable @Size(max = 100) String lastName,
            @Nullable String bio,
            @Nullable String certifications,
            @Nullable Boolean active,
            @Nullable Integer displayOrder,
            @Nullable Float focalPointX,
            @Nullable Float focalPointY
    ) {}

    public record SetBadgeRequest(@Nullable String badgeUrl) {}

    public record InstructorAdminDto(
            UUID id,
            String firstName,
            String lastName,
            @Nullable String photoFilename,
            @Nullable String photoUrl,
            @Nullable Float focalPointX,
            @Nullable Float focalPointY,
            @Nullable String bio,
            @Nullable String certifications,
            @Nullable String badgeUrl,
            int displayOrder,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
