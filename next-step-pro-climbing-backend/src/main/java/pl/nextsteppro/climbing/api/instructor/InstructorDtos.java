package pl.nextsteppro.climbing.api.instructor;

import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.instructor.InstructorType;

import java.time.Instant;
import java.util.UUID;

/**
 * Public instructor DTOs for client consumption
 */
public class InstructorDtos {

    public record InstructorPublicDto(
            UUID id,
            String firstName,
            String lastName,
            @Nullable String photoUrl,
            @Nullable Float focalPointX,
            @Nullable Float focalPointY,
            @Nullable String bio,
            @Nullable String certifications,
            @Nullable String badgeUrl,
            InstructorType memberType,
            @Nullable String profile8aUrl,
            Instant createdAt
    ) {}
}
