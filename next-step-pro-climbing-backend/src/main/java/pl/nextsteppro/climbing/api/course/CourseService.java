package pl.nextsteppro.climbing.api.course;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.course.CourseDtos.*;
import pl.nextsteppro.climbing.domain.course.CourseContentBlock;
import pl.nextsteppro.climbing.domain.course.CourseContentBlockRepository;
import pl.nextsteppro.climbing.domain.course.CourseRepository;
import pl.nextsteppro.climbing.domain.course.CourseSummaryProjection;
import pl.nextsteppro.climbing.infrastructure.storage.FileUrls;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseContentBlockRepository blockRepository;
    private final String baseUrl;

    public CourseService(CourseRepository courseRepository,
                         CourseContentBlockRepository blockRepository,
                         @Value("${app.base-url}") String baseUrl) {
        this.courseRepository = courseRepository;
        this.blockRepository = blockRepository;
        this.baseUrl = baseUrl;
    }

    @Cacheable(value = "courseList", key = "#language")
    public List<CourseSummaryDto> getAllPublished(String language) {
        return courseRepository.findAllPublishedSummariesByLanguage(language)
                .stream()
                .map(this::toSummaryDto)
                .toList();
    }

    @Cacheable(value = "courseDetail", key = "#id")
    public CourseDetailDto getPublishedById(UUID id) {
        var course = courseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));

        if (!course.isPublished()) {
            throw new IllegalArgumentException("Course not found");
        }

        List<CourseContentBlock> blocks = blockRepository.findByCourseIdOrderByDisplayOrderAsc(id);

        return new CourseDetailDto(
                course.getId(),
                course.getTitle(),
                course.getPrice(),
                imageUrl(course.getThumbnailUrl(), course.getThumbnailFilename()),
                course.getThumbnailFocalPointX(),
                course.getThumbnailFocalPointY(),
                course.getLanguage(),
                course.getTranslationGroupId(),
                blocks.stream().map(this::toBlockDto).toList(),
                course.getPublishedAt()
        );
    }

    public List<CourseTranslationDto> getAvailableTranslations(UUID translationGroupId) {
        return courseRepository.findByTranslationGroupId(translationGroupId).stream()
                .filter(c -> c.isPublished())
                .map(c -> new CourseTranslationDto(c.getId(), c.getLanguage()))
                .toList();
    }

    private CourseSummaryDto toSummaryDto(CourseSummaryProjection projection) {
        return new CourseSummaryDto(
                projection.getId(),
                projection.getTitle(),
                projection.getPrice(),
                imageUrl(projection.getThumbnailUrl(), projection.getThumbnailFilename()),
                projection.getThumbnailFocalPointX(),
                projection.getThumbnailFocalPointY(),
                projection.getLanguage(),
                projection.getTranslationGroupId(),
                projection.getPublishedAt()
        );
    }

    private ContentBlockDto toBlockDto(CourseContentBlock block) {
        return new ContentBlockDto(
                block.getId(),
                block.getBlockType().name(),
                block.getContent(),
                imageUrl(block.getImageUrl(), block.getImageFilename()),
                block.getCaption(),
                block.getDisplayOrder()
        );
    }

    private @Nullable String imageUrl(@Nullable String externalUrl, @Nullable String filename) {
        return FileUrls.preferExternal(externalUrl, baseUrl, "courses", filename);
    }
}
