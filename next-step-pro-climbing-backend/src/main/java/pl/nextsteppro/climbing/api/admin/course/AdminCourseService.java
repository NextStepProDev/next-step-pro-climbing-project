package pl.nextsteppro.climbing.api.admin.course;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.admin.course.AdminCourseDtos.*;
import pl.nextsteppro.climbing.domain.course.*;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminCourseService {

    private static final Logger logger = LoggerFactory.getLogger(AdminCourseService.class);

    private final CourseRepository courseRepository;
    private final CourseContentBlockRepository blockRepository;
    private final FileStorageService fileStorageService;
    private final String baseUrl;

    public AdminCourseService(CourseRepository courseRepository,
                               CourseContentBlockRepository blockRepository,
                               FileStorageService fileStorageService,
                               @Value("${app.base-url}") String baseUrl) {
        this.courseRepository = courseRepository;
        this.blockRepository = blockRepository;
        this.fileStorageService = fileStorageService;
        this.baseUrl = baseUrl;
    }

    // --- Kursy ---

    @Transactional(readOnly = true)
    public List<CourseAdminDto> getAllCourses() {
        return courseRepository.findAllSummaries().stream().map(this::toAdminDto).toList();
    }

    @Transactional(readOnly = true)
    public CourseDetailAdminDto getCourse(UUID id) {
        Course course = findCourse(id);
        List<CourseContentBlock> blocks = blockRepository.findByCourseIdOrderByDisplayOrderAsc(id);
        return toDetailAdminDto(course, blocks);
    }

    @CacheEvict(value = {"courseList", "courseDetail"}, allEntries = true)
    public CourseAdminDto createCourse(CreateCourseRequest request) {
        Course course = new Course(request.title());
        course.setExcerpt(request.excerpt());
        course.setDisplayOrder(courseRepository.findMaxDisplayOrder() + 1);
        course = courseRepository.save(course);
        return toAdminDto(course);
    }

    @CacheEvict(value = {"courseList", "courseDetail"}, allEntries = true)
    public void reorderCourses(List<UUID> orderedIds) {
        List<Course> courses = courseRepository.findAllById(orderedIds);
        if (courses.size() != orderedIds.size()) {
            throw new IllegalArgumentException("One or more course IDs not found");
        }

        Map<UUID, Course> courseMap = courses.stream()
                .collect(Collectors.toMap(Course::getId, c -> c));

        for (int i = 0; i < orderedIds.size(); i++) {
            courseMap.get(orderedIds.get(i)).setDisplayOrder(i);
        }

        courseRepository.saveAll(courses);
    }

    @Caching(evict = {
        @CacheEvict(value = {"courseList", "courseDetail"}, allEntries = true),
        @CacheEvict(value = {"calendarMonth", "calendarWeek"}, allEntries = true)
    })
    public CourseAdminDto updateCourseMeta(UUID id, UpdateCourseMetaRequest request) {
        Course course = findCourse(id);

        if (request.title() != null) {
            course.setTitle(request.title());
        }
        if (request.excerpt() != null) {
            course.setExcerpt(request.excerpt());
        }

        course = courseRepository.save(course);
        return toAdminDto(course);
    }

    @CacheEvict(value = {"courseList", "courseDetail"}, allEntries = true)
    public CourseAdminDto setPublished(UUID id, boolean publish) {
        Course course = findCourse(id);

        if (publish && course.getPublishedAt() == null) {
            course.setPublishedAt(Instant.now());
        }
        course.setPublished(publish);

        course = courseRepository.save(course);
        return toAdminDto(course);
    }

    @CacheEvict(value = {"courseList", "courseDetail"}, allEntries = true)
    public void deleteCourse(UUID id) {
        Course course = findCourse(id);

        // Usuń pliki bloków IMAGE
        List<CourseContentBlock> blocks = blockRepository.findByCourseIdOrderByDisplayOrderAsc(id);
        for (CourseContentBlock block : blocks) {
            if (block.getBlockType() == CourseBlockType.IMAGE && block.getImageFilename() != null) {
                try {
                    fileStorageService.delete(block.getImageFilename(), "courses");
                } catch (IOException e) {
                    logger.warn("Failed to delete block image file: {} - {}", block.getImageFilename(), e.getMessage());
                }
            }
        }

        // Usuń miniaturkę
        if (course.getThumbnailFilename() != null) {
            try {
                fileStorageService.delete(course.getThumbnailFilename(), "courses");
            } catch (IOException e) {
                logger.warn("Failed to delete thumbnail file: {} - {}", course.getThumbnailFilename(), e.getMessage());
            }
        }

        courseRepository.delete(course);
    }

    // --- Miniaturka ---

    @CacheEvict(value = {"courseList", "courseDetail"}, allEntries = true)
    public CourseDetailAdminDto uploadThumbnail(UUID id, MultipartFile file) throws IOException {
        Course course = findCourse(id);

        if (course.getThumbnailFilename() != null) {
            try {
                fileStorageService.delete(course.getThumbnailFilename(), "courses");
            } catch (IOException e) {
                logger.warn("Failed to delete old thumbnail: {} - {}", course.getThumbnailFilename(), e.getMessage());
            }
        }

        String filename = fileStorageService.store(file, "courses");
        course.setThumbnailFilename(filename);
        courseRepository.save(course);

        List<CourseContentBlock> blocks = blockRepository.findByCourseIdOrderByDisplayOrderAsc(id);
        return toDetailAdminDto(course, blocks);
    }

    @CacheEvict(value = {"courseList", "courseDetail"}, allEntries = true)
    public void updateThumbnailFocalPoint(UUID id, AdminCourseDtos.UpdateThumbnailFocalPointRequest req) {
        Course course = findCourse(id);
        course.setThumbnailFocalPointX(req.focalPointX());
        course.setThumbnailFocalPointY(req.focalPointY());
        courseRepository.save(course);
    }

    @CacheEvict(value = {"courseList", "courseDetail"}, allEntries = true)
    public void deleteThumbnail(UUID id) throws IOException {
        Course course = findCourse(id);

        if (course.getThumbnailFilename() == null && course.getThumbnailUrl() == null) {
            throw new IllegalStateException("No thumbnail to delete");
        }

        if (course.getThumbnailFilename() != null) {
            fileStorageService.delete(course.getThumbnailFilename(), "courses");
            course.setThumbnailFilename(null);
        }
        course.setThumbnailUrl(null);
        courseRepository.save(course);
    }

    @CacheEvict(value = {"courseList", "courseDetail"}, allEntries = true)
    public void setThumbnailUrl(UUID id, SetThumbnailUrlRequest request) {
        Course course = findCourse(id);

        if (course.getThumbnailFilename() != null) {
            try {
                fileStorageService.delete(course.getThumbnailFilename(), "courses");
            } catch (IOException e) {
                logger.warn("Failed to delete old thumbnail when setting URL: {}", e.getMessage());
            }
            course.setThumbnailFilename(null);
        }

        course.setThumbnailUrl(request.thumbnailUrl());
        courseRepository.save(course);
    }

    // --- Bloki treści ---

    @CacheEvict(value = "courseDetail", allEntries = true)
    public ContentBlockAdminDto addTextBlock(UUID courseId, AddTextBlockRequest request) {
        Course course = findCourse(courseId);
        int order = blockRepository.findMaxDisplayOrder(courseId) + 1;

        CourseContentBlock block = new CourseContentBlock(course, CourseBlockType.TEXT);
        block.setContent(request.content());
        block.setDisplayOrder(order);

        block = blockRepository.save(block);
        return toBlockAdminDto(block);
    }

    @CacheEvict(value = "courseDetail", allEntries = true)
    public UploadBlockImageResponse addImageBlock(UUID courseId, MultipartFile file, @Nullable String caption) throws IOException {
        Course course = findCourse(courseId);
        int order = blockRepository.findMaxDisplayOrder(courseId) + 1;

        String filename = fileStorageService.store(file, "courses");

        CourseContentBlock block = new CourseContentBlock(course, CourseBlockType.IMAGE);
        block.setImageFilename(filename);
        block.setCaption(caption);
        block.setDisplayOrder(order);

        block = blockRepository.save(block);

        return new UploadBlockImageResponse(
                block.getId(),
                filename,
                buildFileUrl(filename),
                block.getDisplayOrder()
        );
    }

    @CacheEvict(value = "courseDetail", allEntries = true)
    public ContentBlockAdminDto addImageBlockFromUrl(UUID courseId, AddImageBlockFromUrlRequest request) {
        Course course = findCourse(courseId);
        int order = blockRepository.findMaxDisplayOrder(courseId) + 1;

        CourseContentBlock block = new CourseContentBlock(course, CourseBlockType.IMAGE);
        block.setImageUrl(request.imageUrl());
        block.setCaption(request.caption());
        block.setDisplayOrder(order);

        block = blockRepository.save(block);
        return toBlockAdminDto(block);
    }

    @CacheEvict(value = "courseDetail", allEntries = true)
    public void updateTextBlock(UUID blockId, UpdateTextBlockRequest request) {
        CourseContentBlock block = findBlock(blockId);

        if (block.getBlockType() != CourseBlockType.TEXT) {
            throw new IllegalArgumentException("Block is not a TEXT block");
        }

        block.setContent(request.content());
        blockRepository.save(block);
    }

    @CacheEvict(value = "courseDetail", allEntries = true)
    public void updateImageBlock(UUID blockId, UpdateImageBlockRequest request) {
        CourseContentBlock block = findBlock(blockId);

        if (block.getBlockType() != CourseBlockType.IMAGE) {
            throw new IllegalArgumentException("Block is not an IMAGE block");
        }

        block.setCaption(request.caption());
        blockRepository.save(block);
    }

    @CacheEvict(value = "courseDetail", allEntries = true)
    public void deleteBlock(UUID blockId) {
        CourseContentBlock block = findBlock(blockId);

        if (block.getBlockType() == CourseBlockType.IMAGE && block.getImageFilename() != null) {
            try {
                fileStorageService.delete(block.getImageFilename(), "courses");
            } catch (IOException e) {
                logger.warn("Failed to delete block image file: {} - {}", block.getImageFilename(), e.getMessage());
            }
        }

        blockRepository.delete(block);
    }

    @CacheEvict(value = "courseDetail", allEntries = true)
    public void moveBlock(UUID blockId, String direction) {
        CourseContentBlock block = findBlock(blockId);
        UUID courseId = block.getCourse().getId();

        List<CourseContentBlock> blocks = blockRepository.findByCourseIdOrderByDisplayOrderAsc(courseId);

        int position = -1;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).getId().equals(blockId)) {
                position = i;
                break;
            }
        }

        if (position < 0) {
            return;
        }

        if ("UP".equals(direction) && position > 0) {
            CourseContentBlock other = blocks.get(position - 1);
            int tmpOrder = block.getDisplayOrder();
            block.setDisplayOrder(other.getDisplayOrder());
            other.setDisplayOrder(tmpOrder);
            blockRepository.save(block);
            blockRepository.save(other);
        } else if ("DOWN".equals(direction) && position < blocks.size() - 1) {
            CourseContentBlock other = blocks.get(position + 1);
            int tmpOrder = block.getDisplayOrder();
            block.setDisplayOrder(other.getDisplayOrder());
            other.setDisplayOrder(tmpOrder);
            blockRepository.save(block);
            blockRepository.save(other);
        }
    }

    // --- Helpery ---

    private Course findCourse(UUID id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));
    }

    private CourseContentBlock findBlock(UUID id) {
        return blockRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Block not found"));
    }

    private CourseAdminDto toAdminDto(CourseSummaryProjection projection) {
        return new CourseAdminDto(
                projection.getId(),
                projection.getTitle(),
                projection.getExcerpt(),
                buildThumbnailUrl(projection.getThumbnailUrl(), projection.getThumbnailFilename()),
                projection.getDisplayOrder(),
                projection.isPublished(),
                projection.getPublishedAt(),
                projection.getCreatedAt(),
                projection.getUpdatedAt()
        );
    }

    private CourseAdminDto toAdminDto(Course course) {
        return new CourseAdminDto(
                course.getId(),
                course.getTitle(),
                course.getExcerpt(),
                buildThumbnailUrl(course.getThumbnailUrl(), course.getThumbnailFilename()),
                course.getDisplayOrder(),
                course.isPublished(),
                course.getPublishedAt(),
                course.getCreatedAt(),
                course.getUpdatedAt()
        );
    }

    private CourseDetailAdminDto toDetailAdminDto(Course course, List<CourseContentBlock> blocks) {
        return new CourseDetailAdminDto(
                course.getId(),
                course.getTitle(),
                course.getExcerpt(),
                course.getThumbnailFilename(),
                buildThumbnailUrl(course.getThumbnailUrl(), course.getThumbnailFilename()),
                course.getThumbnailFocalPointX(),
                course.getThumbnailFocalPointY(),
                course.isPublished(),
                course.getPublishedAt(),
                blocks.stream().map(this::toBlockAdminDto).toList(),
                course.getCreatedAt(),
                course.getUpdatedAt()
        );
    }

    private ContentBlockAdminDto toBlockAdminDto(CourseContentBlock block) {
        String resolvedImageUrl = block.getImageUrl() != null
                ? block.getImageUrl()
                : (block.getImageFilename() != null ? buildFileUrl(block.getImageFilename()) : null);
        return new ContentBlockAdminDto(
                block.getId(),
                block.getBlockType().name(),
                block.getContent(),
                block.getImageFilename(),
                resolvedImageUrl,
                block.getCaption(),
                block.getDisplayOrder()
        );
    }

    @Nullable
    private String buildThumbnailUrl(@Nullable String thumbnailUrl, @Nullable String thumbnailFilename) {
        if (thumbnailUrl != null) return thumbnailUrl;
        if (thumbnailFilename != null) return buildFileUrl(thumbnailFilename);
        return null;
    }

    private String buildFileUrl(String filename) {
        return baseUrl + "/api/files/courses/" + filename;
    }
}
