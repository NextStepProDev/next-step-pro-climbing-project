package pl.nextsteppro.climbing.api.instructor;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.instructor.InstructorDtos.InstructorPublicDto;
import pl.nextsteppro.climbing.domain.instructor.Instructor;
import pl.nextsteppro.climbing.domain.instructor.InstructorRepository;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InstructorService {

    private final InstructorRepository instructorRepository;
    private final String baseUrl;

    public InstructorService(InstructorRepository instructorRepository,
                             @Value("${app.base-url}") String baseUrl) {
        this.instructorRepository = instructorRepository;
        this.baseUrl = baseUrl;
    }

    public List<InstructorPublicDto> getAllActiveInstructors() {
        return instructorRepository.findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc()
                .stream()
                .map(this::toPublicDto)
                .toList();
    }

    public InstructorPublicDto getInstructor(UUID id) {
        Instructor instructor = instructorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found"));

        if (!instructor.isActive()) {
            throw new IllegalArgumentException("Instructor not found");
        }

        return toPublicDto(instructor);
    }

    private InstructorPublicDto toPublicDto(Instructor instructor) {
        return new InstructorPublicDto(
                instructor.getId(),
                instructor.getFirstName(),
                instructor.getLastName(),
                buildPhotoUrl(instructor),
                instructor.getFocalPointX(),
                instructor.getFocalPointY(),
                instructor.getBio(),
                instructor.getCertifications(),
                instructor.getBadgeUrl(),
                instructor.getMemberType(),
                instructor.getProfile8aUrl(),
                instructor.getCreatedAt()
        );
    }

    @Nullable
    private String buildPhotoUrl(Instructor instructor) {
        if (instructor.getPhotoExternalUrl() != null) {
            return instructor.getPhotoExternalUrl();
        }
        if (instructor.getPhotoFilename() == null) {
            return null;
        }
        return baseUrl + "/api/files/instructors/" + instructor.getPhotoFilename();
    }
}
