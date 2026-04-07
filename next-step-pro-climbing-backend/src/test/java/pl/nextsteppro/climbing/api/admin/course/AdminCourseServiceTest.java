package pl.nextsteppro.climbing.api.admin.course;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.admin.course.AdminCourseDtos.*;
import pl.nextsteppro.climbing.domain.course.*;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for AdminCourseService - manages course CRUD, file operations, and block management.
 */
@ExtendWith(MockitoExtension.class)
class AdminCourseServiceTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseContentBlockRepository blockRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private MultipartFile mockFile;

    private AdminCourseService adminCourseService;
    private Course testCourse;
    private UUID courseId;
    private static final String BASE_URL = "https://nextsteppro.pl";

    @BeforeEach
    void setUp() {
        adminCourseService = new AdminCourseService(
                courseRepository,
                blockRepository,
                fileStorageService,
                BASE_URL
        );

        courseId = UUID.randomUUID();
        testCourse = new Course("Kurs wspinaczki dla początkujących");
        setCourseIdViaReflection(testCourse, courseId);
    }

    // ========== CREATE ==========

    @Test
    void shouldCreateCourseSuccessfully() {
        // Given
        CreateCourseRequest request = new CreateCourseRequest("Nowy kurs", "Zajawka kursu");

        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> {
            Course c = inv.getArgument(0);
            setCourseIdViaReflection(c, UUID.randomUUID());
            return c;
        });

        // When
        CourseAdminDto result = adminCourseService.createCourse(request);

        // Then
        assertNotNull(result);
        assertEquals("Nowy kurs", result.title());
        assertEquals("Zajawka kursu", result.excerpt());
        assertFalse(result.published());

        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository).save(captor.capture());
        assertEquals("Nowy kurs", captor.getValue().getTitle());
        assertEquals("Zajawka kursu", captor.getValue().getExcerpt());
    }

    // ========== UPDATE META ==========

    @Test
    void shouldUpdateCourseMetaSuccessfully() {
        // Given
        UpdateCourseMetaRequest request = new UpdateCourseMetaRequest("Zaktualizowany tytuł", "Nowa zajawka");
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(testCourse));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        CourseAdminDto result = adminCourseService.updateCourseMeta(courseId, request);

        // Then
        assertEquals("Zaktualizowany tytuł", result.title());
        assertEquals("Nowa zajawka", result.excerpt());
        verify(courseRepository).save(testCourse);
    }

    @Test
    void shouldNotUpdateFieldsWhenRequestHasNullValues() {
        // Given
        UpdateCourseMetaRequest request = new UpdateCourseMetaRequest(null, null);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(testCourse));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        CourseAdminDto result = adminCourseService.updateCourseMeta(courseId, request);

        // Then
        assertEquals("Kurs wspinaczki dla początkujących", result.title());
    }

    // ========== PUBLISH / UNPUBLISH ==========

    @Test
    void shouldSetPublishedAtWhenFirstPublish() {
        // Given
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(testCourse));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        CourseAdminDto result = adminCourseService.setPublished(courseId, true);

        // Then
        assertTrue(result.published());
        assertNotNull(testCourse.getPublishedAt());
    }

    @Test
    void shouldNotOverwritePublishedAtOnRepublish() {
        // Given
        Instant originalPublishedAt = Instant.parse("2024-01-01T10:00:00Z");
        try {
            setField(testCourse, "publishedAt", originalPublishedAt);
            setField(testCourse, "published", true);
        } catch (Exception e) { throw new RuntimeException(e); }

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(testCourse));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        adminCourseService.setPublished(courseId, true);

        // Then
        assertEquals(originalPublishedAt, testCourse.getPublishedAt());
    }

    @Test
    void shouldUnpublishCourse() {
        // Given
        try {
            setField(testCourse, "published", true);
            setField(testCourse, "publishedAt", Instant.now());
        } catch (Exception e) { throw new RuntimeException(e); }

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(testCourse));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        CourseAdminDto result = adminCourseService.setPublished(courseId, false);

        // Then
        assertFalse(result.published());
        verify(courseRepository).save(testCourse);
    }

    // ========== DELETE ==========

    @Test
    void shouldDeleteCourseWithAllFilesCleanup() throws IOException {
        // Given
        testCourse.setThumbnailFilename("thumb.jpg");

        CourseContentBlock imageBlock = buildBlock(CourseBlockType.IMAGE, "img.jpg", null, 0);
        CourseContentBlock textBlock = buildBlock(CourseBlockType.TEXT, null, "Tekst", 1);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(testCourse));
        when(blockRepository.findByCourseIdOrderByDisplayOrderAsc(courseId))
                .thenReturn(List.of(imageBlock, textBlock));

        // When
        adminCourseService.deleteCourse(courseId);

        // Then
        verify(fileStorageService).delete("img.jpg", "courses");
        verify(fileStorageService).delete("thumb.jpg", "courses");
        verify(courseRepository).delete(testCourse);
    }

    @Test
    void shouldDeleteCourseEvenWhenFileDeletionFails() throws IOException {
        // Given
        testCourse.setThumbnailFilename("thumb.jpg");
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(testCourse));
        when(blockRepository.findByCourseIdOrderByDisplayOrderAsc(courseId)).thenReturn(List.of());
        doThrow(new IOException("File not found")).when(fileStorageService).delete("thumb.jpg", "courses");

        // When
        adminCourseService.deleteCourse(courseId);

        // Then
        verify(fileStorageService).delete("thumb.jpg", "courses");
        verify(courseRepository).delete(testCourse); // Still deletes course
    }

    @Test
    void shouldThrowWhenDeletingNonExistentCourse() {
        // Given
        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> adminCourseService.deleteCourse(courseId)
        );
        assertEquals("Course not found", ex.getMessage());
    }

    // ========== THUMBNAIL ==========

    @Test
    void shouldUploadThumbnailAndDeleteOldOne() throws IOException {
        // Given
        testCourse.setThumbnailFilename("old-thumb.jpg");
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(testCourse));
        when(fileStorageService.store(mockFile, "courses")).thenReturn("new-thumb.jpg");
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));
        when(blockRepository.findByCourseIdOrderByDisplayOrderAsc(courseId)).thenReturn(List.of());

        // When
        adminCourseService.uploadThumbnail(courseId, mockFile);

        // Then
        verify(fileStorageService).delete("old-thumb.jpg", "courses");
        verify(fileStorageService).store(mockFile, "courses");
        assertEquals("new-thumb.jpg", testCourse.getThumbnailFilename());
    }

    @Test
    void shouldUploadThumbnailWhenNoneExists() throws IOException {
        // Given
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(testCourse));
        when(fileStorageService.store(mockFile, "courses")).thenReturn("new-thumb.jpg");
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));
        when(blockRepository.findByCourseIdOrderByDisplayOrderAsc(courseId)).thenReturn(List.of());

        // When
        adminCourseService.uploadThumbnail(courseId, mockFile);

        // Then
        verify(fileStorageService, never()).delete(any(), any());
        assertEquals("new-thumb.jpg", testCourse.getThumbnailFilename());
    }

    @Test
    void shouldDeleteThumbnailSuccessfully() throws IOException {
        // Given
        testCourse.setThumbnailFilename("thumb.jpg");
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(testCourse));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        adminCourseService.deleteThumbnail(courseId);

        // Then
        verify(fileStorageService).delete("thumb.jpg", "courses");
        assertNull(testCourse.getThumbnailFilename());
    }

    @Test
    void shouldThrowWhenDeletingThumbnailThatDoesNotExist() {
        // Given
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(testCourse));

        // When & Then
        assertThrows(IllegalStateException.class, () -> adminCourseService.deleteThumbnail(courseId));
    }

    // ========== TEXT BLOCK ==========

    @Test
    void shouldAddTextBlockWithCorrectOrder() {
        // Given
        AddTextBlockRequest request = new AddTextBlockRequest("Treść akapitu");
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(testCourse));
        when(blockRepository.findMaxDisplayOrder(courseId)).thenReturn(2);
        when(blockRepository.save(any(CourseContentBlock.class))).thenAnswer(inv -> {
            CourseContentBlock b = inv.getArgument(0);
            setBlockIdViaReflection(b);
            return b;
        });

        // When
        ContentBlockAdminDto result = adminCourseService.addTextBlock(courseId, request);

        // Then
        assertNotNull(result);
        assertEquals("TEXT", result.blockType());
        assertEquals("Treść akapitu", result.content());
        assertEquals(3, result.displayOrder());
    }

    @Test
    void shouldUpdateTextBlockContent() {
        // Given
        UUID blockId = UUID.randomUUID();
        CourseContentBlock block = buildBlock(CourseBlockType.TEXT, null, "Stary tekst", 0);
        setBlockIdViaReflection(block, blockId);

        when(blockRepository.findById(blockId)).thenReturn(Optional.of(block));
        when(blockRepository.save(any(CourseContentBlock.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        adminCourseService.updateTextBlock(blockId, new UpdateTextBlockRequest("Nowy tekst"));

        // Then
        assertEquals("Nowy tekst", block.getContent());
        verify(blockRepository).save(block);
    }

    @Test
    void shouldThrowWhenUpdatingTextBlockOnImageBlock() {
        // Given
        UUID blockId = UUID.randomUUID();
        CourseContentBlock block = buildBlock(CourseBlockType.IMAGE, "img.jpg", null, 0);
        setBlockIdViaReflection(block, blockId);
        when(blockRepository.findById(blockId)).thenReturn(Optional.of(block));

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> adminCourseService.updateTextBlock(blockId, new UpdateTextBlockRequest("Tekst")));
    }

    // ========== IMAGE BLOCK ==========

    @Test
    void shouldAddImageBlockSuccessfully() throws IOException {
        // Given
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(testCourse));
        when(blockRepository.findMaxDisplayOrder(courseId)).thenReturn(0);
        when(fileStorageService.store(mockFile, "courses")).thenReturn("img.jpg");
        when(blockRepository.save(any(CourseContentBlock.class))).thenAnswer(inv -> {
            CourseContentBlock b = inv.getArgument(0);
            setBlockIdViaReflection(b);
            return b;
        });

        // When
        UploadBlockImageResponse result = adminCourseService.addImageBlock(courseId, mockFile, "Podpis");

        // Then
        assertNotNull(result);
        assertEquals("img.jpg", result.imageFilename());
        assertTrue(result.imageUrl().contains("img.jpg"));
        assertEquals(1, result.displayOrder());
    }

    @Test
    void shouldUpdateImageBlockCaption() {
        // Given
        UUID blockId = UUID.randomUUID();
        CourseContentBlock block = buildBlock(CourseBlockType.IMAGE, "img.jpg", null, 0);
        setBlockIdViaReflection(block, blockId);

        when(blockRepository.findById(blockId)).thenReturn(Optional.of(block));
        when(blockRepository.save(any(CourseContentBlock.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        adminCourseService.updateImageBlock(blockId, new UpdateImageBlockRequest("Nowy podpis"));

        // Then
        assertEquals("Nowy podpis", block.getCaption());
    }

    // ========== DELETE BLOCK ==========

    @Test
    void shouldDeleteTextBlockWithoutFileDeletion() throws IOException {
        // Given
        UUID blockId = UUID.randomUUID();
        CourseContentBlock block = buildBlock(CourseBlockType.TEXT, null, "Tekst", 0);
        setBlockIdViaReflection(block, blockId);
        when(blockRepository.findById(blockId)).thenReturn(Optional.of(block));

        // When
        adminCourseService.deleteBlock(blockId);

        // Then
        verify(fileStorageService, never()).delete(any(), any());
        verify(blockRepository).delete(block);
    }

    @Test
    void shouldDeleteImageBlockAndCleanupFile() throws IOException {
        // Given
        UUID blockId = UUID.randomUUID();
        CourseContentBlock block = buildBlock(CourseBlockType.IMAGE, "img.jpg", null, 0);
        setBlockIdViaReflection(block, blockId);
        when(blockRepository.findById(blockId)).thenReturn(Optional.of(block));

        // When
        adminCourseService.deleteBlock(blockId);

        // Then
        verify(fileStorageService).delete("img.jpg", "courses");
        verify(blockRepository).delete(block);
    }

    // ========== MOVE BLOCK ==========

    @Test
    void shouldMoveBlockUp() {
        // Given
        UUID blockId = UUID.randomUUID();
        CourseContentBlock block1 = buildBlock(CourseBlockType.TEXT, null, "A", 0);
        CourseContentBlock block2 = buildBlock(CourseBlockType.TEXT, null, "B", 1);
        setBlockIdViaReflection(block1, UUID.randomUUID());
        setBlockIdViaReflection(block2, blockId);

        when(blockRepository.findById(blockId)).thenReturn(Optional.of(block2));
        when(blockRepository.findByCourseIdOrderByDisplayOrderAsc(courseId)).thenReturn(List.of(block1, block2));

        // When
        adminCourseService.moveBlock(blockId, "UP");

        // Then
        assertEquals(0, block2.getDisplayOrder());
        assertEquals(1, block1.getDisplayOrder());
        verify(blockRepository).save(block2);
        verify(blockRepository).save(block1);
    }

    @Test
    void shouldMoveBlockDown() {
        // Given
        UUID blockId = UUID.randomUUID();
        CourseContentBlock block1 = buildBlock(CourseBlockType.TEXT, null, "A", 0);
        CourseContentBlock block2 = buildBlock(CourseBlockType.TEXT, null, "B", 1);
        setBlockIdViaReflection(block1, blockId);
        setBlockIdViaReflection(block2, UUID.randomUUID());

        when(blockRepository.findById(blockId)).thenReturn(Optional.of(block1));
        when(blockRepository.findByCourseIdOrderByDisplayOrderAsc(courseId)).thenReturn(List.of(block1, block2));

        // When
        adminCourseService.moveBlock(blockId, "DOWN");

        // Then
        assertEquals(1, block1.getDisplayOrder());
        assertEquals(0, block2.getDisplayOrder());
    }

    @Test
    void shouldNotMoveFirstBlockUp() {
        // Given
        UUID blockId = UUID.randomUUID();
        CourseContentBlock block = buildBlock(CourseBlockType.TEXT, null, "A", 0);
        setBlockIdViaReflection(block, blockId);

        when(blockRepository.findById(blockId)).thenReturn(Optional.of(block));
        when(blockRepository.findByCourseIdOrderByDisplayOrderAsc(courseId)).thenReturn(List.of(block));

        // When
        adminCourseService.moveBlock(blockId, "UP");

        // Then
        verify(blockRepository, never()).save(any());
    }

    @Test
    void shouldNotMoveLastBlockDown() {
        // Given
        UUID blockId = UUID.randomUUID();
        CourseContentBlock block = buildBlock(CourseBlockType.TEXT, null, "A", 0);
        setBlockIdViaReflection(block, blockId);

        when(blockRepository.findById(blockId)).thenReturn(Optional.of(block));
        when(blockRepository.findByCourseIdOrderByDisplayOrderAsc(courseId)).thenReturn(List.of(block));

        // When
        adminCourseService.moveBlock(blockId, "DOWN");

        // Then
        verify(blockRepository, never()).save(any());
    }

    // ========== Helpers ==========

    private CourseContentBlock buildBlock(CourseBlockType blockType, String imageFilename, String content, int displayOrder) {
        CourseContentBlock block = new CourseContentBlock(testCourse, blockType);
        block.setImageFilename(imageFilename);
        block.setContent(content);
        block.setDisplayOrder(displayOrder);
        return block;
    }

    private void setCourseIdViaReflection(Course course, UUID id) {
        try {
            setField(course, "id", id);
            setField(course, "createdAt", Instant.now());
            setField(course, "updatedAt", Instant.now());
        } catch (Exception e) {
            throw new RuntimeException("Failed to set course fields", e);
        }
    }

    private void setBlockIdViaReflection(CourseContentBlock block) {
        setBlockIdViaReflection(block, UUID.randomUUID());
    }

    private void setBlockIdViaReflection(CourseContentBlock block, UUID id) {
        try {
            setField(block, "id", id);
            setField(block, "createdAt", Instant.now());
            setField(block, "updatedAt", Instant.now());
        } catch (Exception e) {
            throw new RuntimeException("Failed to set block fields", e);
        }
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
