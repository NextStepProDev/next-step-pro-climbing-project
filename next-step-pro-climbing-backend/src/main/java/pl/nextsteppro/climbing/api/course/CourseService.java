package pl.nextsteppro.climbing.api.course;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.course.CourseDtos.*;
import pl.nextsteppro.climbing.domain.course.CourseContentBlock;
import pl.nextsteppro.climbing.domain.course.CourseContentBlockRepository;
import pl.nextsteppro.climbing.domain.course.CourseRepository;
import pl.nextsteppro.climbing.domain.course.CourseSummaryProjection;

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

    @Cacheable("courseList")
    public List<CourseSummaryDto> getAllPublished() {
        return courseRepository.findAllPublishedSummaries()
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
                course.getExcerpt(),
                course.getThumbnailFilename() != null ? buildFileUrl(course.getThumbnailFilename()) : null,
                course.getThumbnailFocalPointX(),
                course.getThumbnailFocalPointY(),
                blocks.stream().map(this::toBlockDto).toList(),
                course.getPublishedAt()
        );
    }

    private CourseSummaryDto toSummaryDto(CourseSummaryProjection projection) {
        return new CourseSummaryDto(
                projection.getId(),
                projection.getTitle(),
                projection.getExcerpt(),
                projection.getThumbnailFilename() != null ? buildFileUrl(projection.getThumbnailFilename()) : null,
                projection.getThumbnailFocalPointX(),
                projection.getThumbnailFocalPointY(),
                projection.getPublishedAt()
        );
    }

    private ContentBlockDto toBlockDto(CourseContentBlock block) {
        return new ContentBlockDto(
                block.getId(),
                block.getBlockType().name(),
                block.getContent(),
                block.getImageFilename() != null ? buildFileUrl(block.getImageFilename()) : null,
                block.getCaption(),
                block.getDisplayOrder()
        );
    }

    private String buildFileUrl(String filename) {
        return baseUrl + "/api/files/courses/" + filename;
    }
}
