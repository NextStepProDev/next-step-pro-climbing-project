package pl.nextsteppro.climbing.api.admin.instructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.instructor.InstructorType;

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
            @Nullable String certifications,
            @Nullable InstructorType memberType,
            @Nullable String profile8aUrl
    ) {}

    public record UpdateInstructorRequest(
            @Nullable @Size(max = 100) String firstName,
            @Nullable @Size(max = 100) String lastName,
            @Nullable String bio,
            @Nullable String certifications,
            @Nullable Boolean active,
            @Nullable Integer displayOrder,
            @Nullable Float focalPointX,
            @Nullable Float focalPointY,
            @Nullable InstructorType memberType,
            @Nullable String profile8aUrl
    ) {}

    public record SetBadgeRequest(@Nullable String badgeUrl) {}

    public record SetPhotoUrlRequest(@Nullable String photoUrl) {}

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
            InstructorType memberType,
            @Nullable String profile8aUrl,
            int displayOrder,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
