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
                course.getPrice(),
                buildThumbnailUrl(course.getThumbnailUrl(), course.getThumbnailFilename()),
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
                projection.getPrice(),
                buildThumbnailUrl(projection.getThumbnailUrl(), projection.getThumbnailFilename()),
                projection.getThumbnailFocalPointX(),
                projection.getThumbnailFocalPointY(),
                projection.getPublishedAt()
        );
    }

    private ContentBlockDto toBlockDto(CourseContentBlock block) {
        String imageUrl = block.getImageUrl() != null
                ? block.getImageUrl()
                : (block.getImageFilename() != null ? buildFileUrl(block.getImageFilename()) : null);
        return new ContentBlockDto(
                block.getId(),
                block.getBlockType().name(),
                block.getContent(),
                imageUrl,
                block.getCaption(),
                block.getDisplayOrder()
        );
    }

    private String buildThumbnailUrl(String thumbnailUrl, String thumbnailFilename) {
        if (thumbnailUrl != null) return thumbnailUrl;
        if (thumbnailFilename != null) return buildFileUrl(thumbnailFilename);
        return null;
    }

    private String buildFileUrl(String filename) {
        return baseUrl + "/api/files/courses/" + filename;
    }
}
