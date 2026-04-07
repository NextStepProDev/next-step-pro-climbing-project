package pl.nextsteppro.climbing.api.course;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.nextsteppro.climbing.api.course.CourseDtos.*;
import pl.nextsteppro.climbing.domain.course.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for CourseService - public read-only access to published courses.
 */
@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseContentBlockRepository blockRepository;

    private CourseService courseService;

    private static final String BASE_URL = "https://nextsteppro.pl";

    @BeforeEach
    void setUp() {
        courseService = new CourseService(courseRepository, blockRepository, BASE_URL);
    }

    // ========== getAllPublished ==========

    @Test
    void shouldReturnEmptyListWhenNoPublishedCourses() {
        // Given
        when(courseRepository.findAllPublishedSummaries()).thenReturn(List.of());

        // When
        List<CourseSummaryDto> result = courseService.getAllPublished();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(courseRepository).findAllPublishedSummaries();
    }

    @Test
    void shouldReturnCourseSummariesWhenPublishedCoursesExist() {
        // Given
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Instant now = Instant.now();

        when(courseRepository.findAllPublishedSummaries()).thenReturn(List.of(
                buildProjection(id1, "Kurs dla początkujących", "Opis kursu", null, now),
                buildProjection(id2, "Kurs zaawansowany", null, "thumb.jpg", now)
        ));

        // When
        List<CourseSummaryDto> result = courseService.getAllPublished();

        // Then
        assertEquals(2, result.size());
        assertEquals(id1, result.get(0).id());
        assertEquals("Kurs dla początkujących", result.get(0).title());
        assertEquals("Opis kursu", result.get(0).excerpt());
        assertNull(result.get(0).thumbnailUrl());
        assertEquals(id2, result.get(1).id());
        assertNull(result.get(1).excerpt());
        assertNotNull(result.get(1).thumbnailUrl());
    }

    @Test
    void shouldBuildCorrectThumbnailUrlWhenThumbnailFilenamePresent() {
        // Given
        String filename = "abc123.jpg";
        when(courseRepository.findAllPublishedSummaries()).thenReturn(List.of(
                buildProjection(UUID.randomUUID(), "Kurs", null, filename, Instant.now())
        ));

        // When
        List<CourseSummaryDto> result = courseService.getAllPublished();

        // Then
        assertEquals(BASE_URL + "/api/files/courses/" + filename, result.get(0).thumbnailUrl());
    }

    @Test
    void shouldReturnNullThumbnailUrlWhenNoThumbnail() {
        // Given
        when(courseRepository.findAllPublishedSummaries()).thenReturn(List.of(
                buildProjection(UUID.randomUUID(), "Kurs", null, null, Instant.now())
        ));

        // When
        List<CourseSummaryDto> result = courseService.getAllPublished();

        // Then
        assertNull(result.get(0).thumbnailUrl());
    }

    // ========== getPublishedById ==========

    @Test
    void shouldGetPublishedCourseByIdSuccessfully() {
        // Given
        UUID courseId = UUID.randomUUID();
        Course course = buildCourse(courseId, "Kurs wspinaczki", "Zajawka", null, true);

        CourseContentBlock textBlock = buildBlock(courseId, CourseBlockType.TEXT, "Treść bloku", null, null, 0);
        CourseContentBlock imageBlock = buildBlock(courseId, CourseBlockType.IMAGE, null, "img.jpg", "Podpis", 1);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(blockRepository.findByCourseIdOrderByDisplayOrderAsc(courseId))
                .thenReturn(List.of(textBlock, imageBlock));

        // When
        CourseDetailDto result = courseService.getPublishedById(courseId);

        // Then
        assertNotNull(result);
        assertEquals(courseId, result.id());
        assertEquals("Kurs wspinaczki", result.title());
        assertEquals("Zajawka", result.excerpt());
        assertEquals(2, result.blocks().size());
    }

    @Test
    void shouldThrowWhenCourseNotFoundById() {
        // Given
        UUID courseId = UUID.randomUUID();
        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> courseService.getPublishedById(courseId)
        );
        assertEquals("Course not found", ex.getMessage());
    }

    @Test
    void shouldThrowWhenCourseIsNotPublished() {
        // Given
        UUID courseId = UUID.randomUUID();
        Course course = buildCourse(courseId, "Draft kurs", null, null, false);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        // When & Then
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> courseService.getPublishedById(courseId)
        );
        assertEquals("Course not found", ex.getMessage());
        verify(blockRepository, never()).findByCourseIdOrderByDisplayOrderAsc(any());
    }

    @Test
    void shouldMapContentBlocksToDto() {
        // Given
        UUID courseId = UUID.randomUUID();
        Course course = buildCourse(courseId, "Kurs", null, null, true);

        CourseContentBlock textBlock = buildBlock(courseId, CourseBlockType.TEXT, "Tekst akapitu", null, null, 0);
        CourseContentBlock imageBlock = buildBlock(courseId, CourseBlockType.IMAGE, null, "photo.png", "Podpis zdjęcia", 1);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(blockRepository.findByCourseIdOrderByDisplayOrderAsc(courseId))
                .thenReturn(List.of(textBlock, imageBlock));

        // When
        CourseDetailDto result = courseService.getPublishedById(courseId);

        // Then
        ContentBlockDto text = result.blocks().get(0);
        assertEquals("TEXT", text.blockType());
        assertEquals("Tekst akapitu", text.content());
        assertNull(text.imageUrl());
        assertNull(text.caption());

        ContentBlockDto image = result.blocks().get(1);
        assertEquals("IMAGE", image.blockType());
        assertNull(image.content());
        assertEquals(BASE_URL + "/api/files/courses/photo.png", image.imageUrl());
        assertEquals("Podpis zdjęcia", image.caption());
    }

    // ========== Helpers ==========

    private CourseSummaryProjection buildProjection(UUID id, String title, String excerpt, String thumbnailFilename, Instant publishedAt) {
        return new CourseSummaryProjection() {
            @Override public UUID getId() { return id; }
            @Override public String getTitle() { return title; }
            @Override public String getExcerpt() { return excerpt; }
            @Override public String getThumbnailFilename() { return thumbnailFilename; }
            @Override public String getThumbnailUrl() { return null; }
            @Override public Float getThumbnailFocalPointX() { return null; }
            @Override public Float getThumbnailFocalPointY() { return null; }
            @Override public int getDisplayOrder() { return 0; }
            @Override public boolean isPublished() { return true; }
            @Override public Instant getPublishedAt() { return publishedAt; }
            @Override public Instant getCreatedAt() { return Instant.now(); }
            @Override public Instant getUpdatedAt() { return Instant.now(); }
        };
    }

    private Course buildCourse(UUID id, String title, String excerpt, String thumbnailFilename, boolean published) {
        Course course = new Course(title);
        try {
            setField(course, "id", id);
            setField(course, "excerpt", excerpt);
            setField(course, "thumbnailFilename", thumbnailFilename);
            setField(course, "published", published);
            setField(course, "publishedAt", published ? Instant.now() : null);
            setField(course, "createdAt", Instant.now());
            setField(course, "updatedAt", Instant.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return course;
    }

    private CourseContentBlock buildBlock(UUID courseId, CourseBlockType blockType, String content, String imageFilename, String caption, int displayOrder) {
        Course course = new Course("dummy");
        try { setField(course, "id", courseId); } catch (Exception e) { throw new RuntimeException(e); }
        CourseContentBlock block = new CourseContentBlock(course, blockType);
        try {
            setField(block, "id", UUID.randomUUID());
            setField(block, "content", content);
            setField(block, "imageFilename", imageFilename);
            setField(block, "caption", caption);
            setField(block, "displayOrder", displayOrder);
            setField(block, "createdAt", Instant.now());
            setField(block, "updatedAt", Instant.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return block;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        var field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), name);
            }
            throw e;
        }
    }
}
