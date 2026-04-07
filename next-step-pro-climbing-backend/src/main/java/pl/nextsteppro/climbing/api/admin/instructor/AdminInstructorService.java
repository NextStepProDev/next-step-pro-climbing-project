package pl.nextsteppro.climbing.api.admin.instructor;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.admin.instructor.AdminInstructorDtos.*;
import pl.nextsteppro.climbing.domain.instructor.Instructor;
import pl.nextsteppro.climbing.domain.instructor.InstructorRepository;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AdminInstructorService {

    private static final Logger logger = LoggerFactory.getLogger(AdminInstructorService.class);

    private final InstructorRepository instructorRepository;
    private final FileStorageService fileStorageService;
    private final String baseUrl;

    public AdminInstructorService(InstructorRepository instructorRepository,
                                  FileStorageService fileStorageService,
                                  @Value("${app.base-url}") String baseUrl) {
        this.instructorRepository = instructorRepository;
        this.fileStorageService = fileStorageService;
        this.baseUrl = baseUrl;
    }

    public List<InstructorAdminDto> getAllInstructors() {
        return instructorRepository.findAllByOrderByDisplayOrderAscCreatedAtAsc()
                .stream()
                .map(this::toAdminDto)
                .toList();
    }

    public InstructorAdminDto getInstructor(UUID id) {
        Instructor instructor = instructorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found"));
        return toAdminDto(instructor);
    }

    public InstructorAdminDto createInstructor(CreateInstructorRequest request) {
        Instructor instructor = new Instructor(request.firstName(), request.lastName());
        instructor.setBio(request.bio());
        instructor.setCertifications(request.certifications());

        instructor.setDisplayOrder(instructorRepository.findMinDisplayOrder().orElse(1) - 1);
        instructor = instructorRepository.save(instructor);
        return toAdminDto(instructor);
    }

    public InstructorAdminDto updateInstructor(UUID id, UpdateInstructorRequest request) {
        Instructor instructor = instructorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found"));

        if (request.firstName() != null) {
            instructor.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            instructor.setLastName(request.lastName());
        }
        if (request.bio() != null) {
            instructor.setBio(request.bio());
        }
        if (request.certifications() != null) {
            instructor.setCertifications(request.certifications());
        }
        if (request.active() != null) {
            instructor.setActive(request.active());
        }
        if (request.displayOrder() != null) {
            instructor.setDisplayOrder(request.displayOrder());
        }
        if (request.focalPointX() != null) {
            instructor.setFocalPointX(request.focalPointX());
        }
        if (request.focalPointY() != null) {
            instructor.setFocalPointY(request.focalPointY());
        }

        instructor = instructorRepository.save(instructor);
        return toAdminDto(instructor);
    }

    public void deleteInstructor(UUID id) {
        Instructor instructor = instructorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found"));

        // Delete photo file if exists
        if (instructor.getPhotoFilename() != null) {
            try {
                fileStorageService.delete(instructor.getPhotoFilename(), "instructors");
            } catch (IOException e) {
                // Log but don't fail the delete operation
                logger.warn("Failed to delete instructor photo file: {}", e.getMessage());
            }
        }

        instructorRepository.delete(instructor);
    }

    public void uploadPhoto(UUID id, MultipartFile file) throws IOException {
        Instructor instructor = instructorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found"));

        // Delete old photo if exists
        if (instructor.getPhotoFilename() != null) {
            try {
                fileStorageService.delete(instructor.getPhotoFilename(), "instructors");
            } catch (IOException e) {
                // Log but continue
                logger.warn("Failed to delete old photo: {}", e.getMessage());
            }
        }

        // Store new photo
        String filename = fileStorageService.store(file, "instructors");
        instructor.setPhotoFilename(filename);
        instructorRepository.save(instructor);
    }

    public void deletePhoto(UUID id) throws IOException {
        Instructor instructor = instructorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found"));

        if (instructor.getPhotoFilename() == null) {
            throw new IllegalStateException("Instructor has no photo");
        }

        fileStorageService.delete(instructor.getPhotoFilename(), "instructors");
        instructor.setPhotoFilename(null);
        instructorRepository.save(instructor);
    }

    public InstructorAdminDto setBadge(UUID id, @Nullable String badgeUrl) {
        Instructor instructor = instructorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found"));
        instructor.setBadgeUrl(badgeUrl);
        instructor = instructorRepository.save(instructor);
        return toAdminDto(instructor);
    }

    private InstructorAdminDto toAdminDto(Instructor instructor) {
        return new InstructorAdminDto(
                instructor.getId(),
                instructor.getFirstName(),
                instructor.getLastName(),
                instructor.getPhotoFilename(),
                buildPhotoUrl(instructor.getPhotoFilename()),
                instructor.getFocalPointX(),
                instructor.getFocalPointY(),
                instructor.getBio(),
                instructor.getCertifications(),
                instructor.getBadgeUrl(),
                instructor.getDisplayOrder(),
                instructor.isActive(),
                instructor.getCreatedAt(),
                instructor.getUpdatedAt()
        );
    }

    @Nullable
    private String buildPhotoUrl(@Nullable String filename) {
        if (filename == null) {
            return null;
        }
        return baseUrl + "/api/files/instructors/" + filename;
    }
}
