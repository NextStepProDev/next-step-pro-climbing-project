package pl.nextsteppro.climbing.api.admin.instructor;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.admin.instructor.AdminInstructorDtos.*;
import pl.nextsteppro.climbing.config.ContentLanguages;
import pl.nextsteppro.climbing.domain.instructor.Instructor;
import pl.nextsteppro.climbing.domain.instructor.InstructorRepository;
import pl.nextsteppro.climbing.domain.instructor.InstructorType;
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

    @CacheEvict(value = "instructorList", allEntries = true)
    public InstructorAdminDto createInstructor(CreateInstructorRequest request) {
        Instructor instructor = new Instructor(request.firstName(), request.lastName());
        instructor.setBio(request.bio());
        instructor.setCertifications(request.certifications());
        instructor.setMemberType(request.memberType() != null ? request.memberType() : InstructorType.INSTRUCTOR);
        instructor.setProfile8aUrl(request.profile8aUrl());
        instructor.setLanguage(request.language() != null ? request.language() : "pl");

        instructor.setDisplayOrder(instructorRepository.findMinDisplayOrder().orElse(1) - 1);
        instructor = instructorRepository.save(instructor);

        for (String lang : ContentLanguages.ALL) {
            if (!lang.equals(instructor.getLanguage())) {
                Instructor copy = new Instructor(instructor.getFirstName(), instructor.getLastName());
                copy.setLanguage(lang);
                copy.setTranslationGroupId(instructor.getTranslationGroupId());
                copy.setBio(instructor.getBio());
                copy.setCertifications(instructor.getCertifications());
                copy.setMemberType(instructor.getMemberType());
                copy.setProfile8aUrl(instructor.getProfile8aUrl());
                copy.setDisplayOrder(instructor.getDisplayOrder());
                instructorRepository.save(copy);
            }
        }

        return toAdminDto(instructor);
    }

    @CacheEvict(value = "instructorList", allEntries = true)
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
            for (Instructor sibling : instructorRepository.findByTranslationGroupId(instructor.getTranslationGroupId())) {
                if (!sibling.getId().equals(instructor.getId()) && sibling.isActive() != request.active()) {
                    sibling.setActive(request.active());
                    instructorRepository.save(sibling);
                }
            }
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
        if (request.memberType() != null) {
            instructor.setMemberType(request.memberType());
        }
        if (request.profile8aUrl() != null) {
            instructor.setProfile8aUrl(request.profile8aUrl().isBlank() ? null : request.profile8aUrl());
        }

        instructor = instructorRepository.save(instructor);
        return toAdminDto(instructor);
    }

    @CacheEvict(value = "instructorList", allEntries = true)
    public void deleteInstructor(UUID id) {
        Instructor instructor = instructorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found"));

        if (instructor.getPhotoFilename() != null) {
            if (!instructorRepository.existsByPhotoFilenameAndIdNot(instructor.getPhotoFilename(), id)) {
                try {
                    fileStorageService.delete(instructor.getPhotoFilename(), "instructors");
                } catch (IOException e) {
                    logger.warn("Failed to delete instructor photo file: {}", e.getMessage());
                }
            }
        }

        instructorRepository.delete(instructor);
    }

    @CacheEvict(value = "instructorList", allEntries = true)
    public void uploadPhoto(UUID id, MultipartFile file) throws IOException {
        Instructor instructor = instructorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found"));

        if (instructor.getPhotoFilename() != null) {
            if (!instructorRepository.existsByPhotoFilenameAndIdNot(instructor.getPhotoFilename(), id)) {
                try {
                    fileStorageService.delete(instructor.getPhotoFilename(), "instructors");
                } catch (IOException e) {
                    logger.warn("Failed to delete old photo: {}", e.getMessage());
                }
            }
        }

        String filename = fileStorageService.store(file, "instructors");
        instructor.setPhotoFilename(filename);
        instructorRepository.save(instructor);
    }

    @CacheEvict(value = "instructorList", allEntries = true)
    public void deletePhoto(UUID id) throws IOException {
        Instructor instructor = instructorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found"));

        if (instructor.getPhotoFilename() == null) {
            throw new IllegalStateException("Instructor has no photo");
        }

        if (!instructorRepository.existsByPhotoFilenameAndIdNot(instructor.getPhotoFilename(), id)) {
            fileStorageService.delete(instructor.getPhotoFilename(), "instructors");
        }
        instructor.setPhotoFilename(null);
        instructorRepository.save(instructor);
    }

    @CacheEvict(value = "instructorList", allEntries = true)
    public InstructorAdminDto duplicateAsTranslation(UUID id, String targetLanguage) {
        Instructor source = instructorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found"));

        if (source.getLanguage().equals(targetLanguage)) {
            throw new IllegalArgumentException("Target language is the same as source language");
        }

        if (instructorRepository.existsByTranslationGroupIdAndLanguage(source.getTranslationGroupId(), targetLanguage)) {
            throw new IllegalArgumentException("Translation in this language already exists");
        }

        Instructor copy = new Instructor(source.getFirstName(), source.getLastName());
        copy.setLanguage(targetLanguage);
        copy.setTranslationGroupId(source.getTranslationGroupId());
        copy.setBio(source.getBio());
        copy.setCertifications(source.getCertifications());
        copy.setPhotoFilename(source.getPhotoFilename());
        copy.setPhotoExternalUrl(source.getPhotoExternalUrl());
        copy.setFocalPointX(source.getFocalPointX());
        copy.setFocalPointY(source.getFocalPointY());
        copy.setBadgeUrl(source.getBadgeUrl());
        copy.setProfile8aUrl(source.getProfile8aUrl());
        copy.setMemberType(source.getMemberType());
        copy.setActive(source.isActive());
        copy.setDisplayOrder(instructorRepository.findMinDisplayOrder().orElse(1) - 1);

        copy = instructorRepository.save(copy);
        return toAdminDto(copy);
    }

    @CacheEvict(value = "instructorList", allEntries = true)
    public List<InstructorAdminDto> moveUp(UUID id) {
        Instructor target = instructorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found"));
        List<Instructor> group = instructorRepository.findAllByOrderByDisplayOrderAscCreatedAtAsc()
                .stream()
                .filter(i -> i.getMemberType() == target.getMemberType() && i.getLanguage().equals(target.getLanguage()))
                .toList();
        int idx = indexOfId(group, id);
        if (idx > 0) {
            swap(group.get(idx), group.get(idx - 1));
            instructorRepository.saveAll(List.of(group.get(idx), group.get(idx - 1)));
            syncDisplayOrder(group.get(idx));
            syncDisplayOrder(group.get(idx - 1));
        }
        return instructorRepository.findAllByOrderByDisplayOrderAscCreatedAtAsc()
                .stream().map(this::toAdminDto).toList();
    }

    @CacheEvict(value = "instructorList", allEntries = true)
    public List<InstructorAdminDto> moveDown(UUID id) {
        Instructor target = instructorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found"));
        List<Instructor> group = instructorRepository.findAllByOrderByDisplayOrderAscCreatedAtAsc()
                .stream()
                .filter(i -> i.getMemberType() == target.getMemberType() && i.getLanguage().equals(target.getLanguage()))
                .toList();
        int idx = indexOfId(group, id);
        if (idx >= 0 && idx < group.size() - 1) {
            swap(group.get(idx), group.get(idx + 1));
            instructorRepository.saveAll(List.of(group.get(idx), group.get(idx + 1)));
            syncDisplayOrder(group.get(idx));
            syncDisplayOrder(group.get(idx + 1));
        }
        return instructorRepository.findAllByOrderByDisplayOrderAscCreatedAtAsc()
                .stream().map(this::toAdminDto).toList();
    }

    private void syncDisplayOrder(Instructor instructor) {
        for (Instructor sibling : instructorRepository.findByTranslationGroupId(instructor.getTranslationGroupId())) {
            if (!sibling.getId().equals(instructor.getId()) && sibling.getDisplayOrder() != instructor.getDisplayOrder()) {
                sibling.setDisplayOrder(instructor.getDisplayOrder());
                instructorRepository.save(sibling);
            }
        }
    }

    private int indexOfId(List<Instructor> list, UUID id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(id)) return i;
        }
        return -1;
    }

    private void swap(Instructor a, Instructor b) {
        int tmp = a.getDisplayOrder();
        a.setDisplayOrder(b.getDisplayOrder());
        b.setDisplayOrder(tmp);
    }

    @CacheEvict(value = "instructorList", allEntries = true)
    public AdminInstructorDtos.SyncMediaResultDto syncMediaToTranslations(UUID sourceId) {
        Instructor source = instructorRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found"));

        List<Instructor> siblings = instructorRepository.findByTranslationGroupId(source.getTranslationGroupId())
                .stream()
                .filter(s -> !s.getId().equals(sourceId))
                .toList();

        int updated = 0;
        for (Instructor sibling : siblings) {
            boolean changed = false;

            if (!java.util.Objects.equals(sibling.getPhotoFilename(), source.getPhotoFilename())) {
                sibling.setPhotoFilename(source.getPhotoFilename());
                changed = true;
            }
            if (!java.util.Objects.equals(sibling.getPhotoExternalUrl(), source.getPhotoExternalUrl())) {
                sibling.setPhotoExternalUrl(source.getPhotoExternalUrl());
                changed = true;
            }
            if (!java.util.Objects.equals(sibling.getFocalPointX(), source.getFocalPointX())) {
                sibling.setFocalPointX(source.getFocalPointX());
                changed = true;
            }
            if (!java.util.Objects.equals(sibling.getFocalPointY(), source.getFocalPointY())) {
                sibling.setFocalPointY(source.getFocalPointY());
                changed = true;
            }
            if (!java.util.Objects.equals(sibling.getBadgeUrl(), source.getBadgeUrl())) {
                sibling.setBadgeUrl(source.getBadgeUrl());
                changed = true;
            }

            if (changed) {
                instructorRepository.save(sibling);
                updated++;
            }
        }

        return new AdminInstructorDtos.SyncMediaResultDto(updated);
    }

    @CacheEvict(value = "instructorList", allEntries = true)
    public InstructorAdminDto setBadge(UUID id, @Nullable String badgeUrl) {
        Instructor instructor = instructorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found"));
        instructor.setBadgeUrl(badgeUrl);
        instructor = instructorRepository.save(instructor);
        return toAdminDto(instructor);
    }

    @CacheEvict(value = "instructorList", allEntries = true)
    public InstructorAdminDto setPhotoUrl(UUID id, @Nullable String url) {
        Instructor instructor = instructorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found"));
        instructor.setPhotoExternalUrl(url);
        instructor = instructorRepository.save(instructor);
        return toAdminDto(instructor);
    }

    private InstructorAdminDto toAdminDto(Instructor instructor) {
        return new InstructorAdminDto(
                instructor.getId(),
                instructor.getFirstName(),
                instructor.getLastName(),
                instructor.getPhotoFilename(),
                buildPhotoUrl(instructor),
                instructor.getFocalPointX(),
                instructor.getFocalPointY(),
                instructor.getBio(),
                instructor.getCertifications(),
                instructor.getBadgeUrl(),
                instructor.getMemberType(),
                instructor.getProfile8aUrl(),
                instructor.getDisplayOrder(),
                instructor.isActive(),
                instructor.getCreatedAt(),
                instructor.getUpdatedAt(),
                instructor.getLanguage(),
                instructor.getTranslationGroupId()
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
