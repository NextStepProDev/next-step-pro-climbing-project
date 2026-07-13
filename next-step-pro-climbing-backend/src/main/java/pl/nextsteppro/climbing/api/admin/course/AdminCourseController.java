package pl.nextsteppro.climbing.api.admin.course;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.admin.course.AdminCourseDtos.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/courses")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Courses", description = "Course management (admin only)")
public class AdminCourseController {

    private final AdminCourseService adminCourseService;

    public AdminCourseController(AdminCourseService adminCourseService) {
        this.adminCourseService = adminCourseService;
    }

    // ==================== Courses ====================

    @Operation(summary = "Get all courses (drafts + published)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of courses"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping
    public ResponseEntity<List<CourseAdminDto>> getAll() {
        return ResponseEntity.ok(adminCourseService.getAllCourses());
    }

    @Operation(summary = "Get course details with blocks")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Course details"),
        @ApiResponse(responseCode = "400", description = "Course not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CourseDetailAdminDto> getById(
            @Parameter(description = "ID kursu") @PathVariable UUID id) {
        return ResponseEntity.ok(adminCourseService.getCourse(id));
    }

    @Operation(summary = "Create new course (draft)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Course created"),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping
    public ResponseEntity<CourseAdminDto> create(
            @Valid @RequestBody CreateCourseRequest request) {
        return ResponseEntity.ok(adminCourseService.createCourse(request));
    }

    @Operation(summary = "Update course title and price")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Metadata updated"),
        @ApiResponse(responseCode = "400", description = "Course not found or invalid data"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PutMapping("/{id}/meta")
    public ResponseEntity<CourseAdminDto> updateMeta(
            @Parameter(description = "ID kursu") @PathVariable UUID id,
            @Valid @RequestBody UpdateCourseMetaRequest request) {
        return ResponseEntity.ok(adminCourseService.updateCourseMeta(id, request));
    }

    @Operation(summary = "Publish course")
    @PostMapping("/{id}/publish")
    public ResponseEntity<CourseAdminDto> publish(
            @Parameter(description = "ID kursu") @PathVariable UUID id) {
        return ResponseEntity.ok(adminCourseService.setPublished(id, true));
    }

    @Operation(summary = "Unpublish course")
    @PostMapping("/{id}/unpublish")
    public ResponseEntity<CourseAdminDto> unpublish(
            @Parameter(description = "ID kursu") @PathVariable UUID id) {
        return ResponseEntity.ok(adminCourseService.setPublished(id, false));
    }

    @Operation(summary = "Reorder courses")
    @PutMapping("/reorder")
    public ResponseEntity<Void> reorder(@RequestBody ReorderCoursesRequest request) {
        adminCourseService.reorderCourses(request.orderedIds());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Duplicate course as translation")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Course duplicated as translation"),
        @ApiResponse(responseCode = "400", description = "Course not found or translation already exists"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/{id}/duplicate-translation")
    public ResponseEntity<CourseDetailAdminDto> duplicateAsTranslation(
            @Parameter(description = "Source course ID") @PathVariable UUID id,
            @Valid @RequestBody AdminCourseDtos.DuplicateAsTranslationRequest request) {
        return ResponseEntity.ok(adminCourseService.duplicateAsTranslation(id, request.targetLanguage()));
    }

    @Operation(summary = "Sync media blocks (IMAGE) to all course translations")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Media synchronized"),
        @ApiResponse(responseCode = "400", description = "Course not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/{id}/sync-media-to-translations")
    public ResponseEntity<AdminCourseDtos.SyncMediaResultDto> syncMediaToTranslations(
            @Parameter(description = "Source course ID") @PathVariable UUID id) {
        return ResponseEntity.ok(adminCourseService.syncMediaToTranslations(id));
    }

    @Operation(summary = "Delete course along with its files")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Course deleted"),
        @ApiResponse(responseCode = "400", description = "Course not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID kursu") @PathVariable UUID id) {
        adminCourseService.deleteCourse(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Thumbnail ====================

    @Operation(summary = "Upload course thumbnail")
    @PostMapping("/{id}/thumbnail")
    public ResponseEntity<CourseDetailAdminDto> uploadThumbnail(
            @Parameter(description = "ID kursu") @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(adminCourseService.uploadThumbnail(id, file));
    }

    @Operation(summary = "Set course thumbnail from the media library (URL)")
    @PutMapping("/{id}/thumbnail-url")
    public ResponseEntity<Void> setThumbnailUrl(
            @Parameter(description = "ID kursu") @PathVariable UUID id,
            @RequestBody AdminCourseDtos.SetThumbnailUrlRequest request) {
        adminCourseService.setThumbnailUrl(id, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete course thumbnail")
    @DeleteMapping("/{id}/thumbnail")
    public ResponseEntity<Void> deleteThumbnail(
            @Parameter(description = "ID kursu") @PathVariable UUID id) throws IOException {
        adminCourseService.deleteThumbnail(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Set course thumbnail focal point")
    @PutMapping("/{id}/thumbnail-focal-point")
    public ResponseEntity<Void> updateThumbnailFocalPoint(
            @Parameter(description = "ID kursu") @PathVariable UUID id,
            @RequestBody UpdateThumbnailFocalPointRequest request) {
        adminCourseService.updateThumbnailFocalPoint(id, request);
        return ResponseEntity.noContent().build();
    }

    // ==================== Content blocks ====================

    @Operation(summary = "Add text block")
    @PostMapping("/{id}/blocks/text")
    public ResponseEntity<ContentBlockAdminDto> addTextBlock(
            @Parameter(description = "ID kursu") @PathVariable UUID id,
            @Valid @RequestBody AddTextBlockRequest request) {
        return ResponseEntity.ok(adminCourseService.addTextBlock(id, request));
    }

    @Operation(summary = "Add image block")
    @PostMapping("/{id}/blocks/image")
    public ResponseEntity<UploadBlockImageResponse> addImageBlock(
            @Parameter(description = "ID kursu") @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) @Nullable String caption) throws IOException {
        return ResponseEntity.ok(adminCourseService.addImageBlock(id, file, caption));
    }

    @Operation(summary = "Add image block from the media library (URL)")
    @PostMapping("/{id}/blocks/image-from-url")
    public ResponseEntity<ContentBlockAdminDto> addImageBlockFromUrl(
            @Parameter(description = "ID kursu") @PathVariable UUID id,
            @RequestBody AdminCourseDtos.AddImageBlockFromUrlRequest request) {
        return ResponseEntity.ok(adminCourseService.addImageBlockFromUrl(id, request));
    }

    @Operation(summary = "Edit text block content")
    @PutMapping("/blocks/{blockId}/text")
    public ResponseEntity<Void> updateTextBlock(
            @Parameter(description = "ID bloku") @PathVariable UUID blockId,
            @Valid @RequestBody UpdateTextBlockRequest request) {
        adminCourseService.updateTextBlock(blockId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Edit image block caption")
    @PutMapping("/blocks/{blockId}/image")
    public ResponseEntity<Void> updateImageBlock(
            @Parameter(description = "ID bloku") @PathVariable UUID blockId,
            @RequestBody UpdateImageBlockRequest request) {
        adminCourseService.updateImageBlock(blockId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Move block up or down")
    @PostMapping("/blocks/{blockId}/move")
    public ResponseEntity<Void> moveBlock(
            @Parameter(description = "ID bloku") @PathVariable UUID blockId,
            @RequestBody MoveBlockRequest request) {
        adminCourseService.moveBlock(blockId, request.direction());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete content block")
    @DeleteMapping("/blocks/{blockId}")
    public ResponseEntity<Void> deleteBlock(
            @Parameter(description = "ID bloku") @PathVariable UUID blockId) {
        adminCourseService.deleteBlock(blockId);
        return ResponseEntity.noContent().build();
    }
}
