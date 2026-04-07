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
@Tag(name = "Admin - Courses", description = "Zarządzanie kursami (tylko admin)")
public class AdminCourseController {

    private final AdminCourseService adminCourseService;

    public AdminCourseController(AdminCourseService adminCourseService) {
        this.adminCourseService = adminCourseService;
    }

    // ==================== Kursy ====================

    @Operation(summary = "Pobierz wszystkie kursy (drafty + opublikowane)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista kursów"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping
    public ResponseEntity<List<CourseAdminDto>> getAll() {
        return ResponseEntity.ok(adminCourseService.getAllCourses());
    }

    @Operation(summary = "Pobierz szczegóły kursu z blokami")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Szczegóły kursu"),
        @ApiResponse(responseCode = "400", description = "Kurs nie znaleziony"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CourseDetailAdminDto> getById(
            @Parameter(description = "ID kursu") @PathVariable UUID id) {
        return ResponseEntity.ok(adminCourseService.getCourse(id));
    }

    @Operation(summary = "Utwórz nowy kurs (draft)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Kurs utworzony"),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe dane"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PostMapping
    public ResponseEntity<CourseAdminDto> create(
            @Valid @RequestBody CreateCourseRequest request) {
        return ResponseEntity.ok(adminCourseService.createCourse(request));
    }

    @Operation(summary = "Aktualizuj tytuł i excerpt kursu")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Metadane zaktualizowane"),
        @ApiResponse(responseCode = "400", description = "Kurs nie znaleziony lub nieprawidłowe dane"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PutMapping("/{id}/meta")
    public ResponseEntity<CourseAdminDto> updateMeta(
            @Parameter(description = "ID kursu") @PathVariable UUID id,
            @Valid @RequestBody UpdateCourseMetaRequest request) {
        return ResponseEntity.ok(adminCourseService.updateCourseMeta(id, request));
    }

    @Operation(summary = "Opublikuj kurs")
    @PostMapping("/{id}/publish")
    public ResponseEntity<CourseAdminDto> publish(
            @Parameter(description = "ID kursu") @PathVariable UUID id) {
        return ResponseEntity.ok(adminCourseService.setPublished(id, true));
    }

    @Operation(summary = "Cofnij publikację kursu")
    @PostMapping("/{id}/unpublish")
    public ResponseEntity<CourseAdminDto> unpublish(
            @Parameter(description = "ID kursu") @PathVariable UUID id) {
        return ResponseEntity.ok(adminCourseService.setPublished(id, false));
    }

    @Operation(summary = "Zmień kolejność kursów")
    @PutMapping("/reorder")
    public ResponseEntity<Void> reorder(@RequestBody ReorderCoursesRequest request) {
        adminCourseService.reorderCourses(request.orderedIds());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Usuń kurs wraz z plikami")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Kurs usunięty"),
        @ApiResponse(responseCode = "400", description = "Kurs nie znaleziony"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID kursu") @PathVariable UUID id) {
        adminCourseService.deleteCourse(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Miniaturka ====================

    @Operation(summary = "Prześlij miniaturkę kursu")
    @PostMapping("/{id}/thumbnail")
    public ResponseEntity<CourseDetailAdminDto> uploadThumbnail(
            @Parameter(description = "ID kursu") @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(adminCourseService.uploadThumbnail(id, file));
    }

    @Operation(summary = "Ustaw miniaturkę kursu z biblioteki mediów (URL)")
    @PutMapping("/{id}/thumbnail-url")
    public ResponseEntity<Void> setThumbnailUrl(
            @Parameter(description = "ID kursu") @PathVariable UUID id,
            @RequestBody AdminCourseDtos.SetThumbnailUrlRequest request) {
        adminCourseService.setThumbnailUrl(id, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Usuń miniaturkę kursu")
    @DeleteMapping("/{id}/thumbnail")
    public ResponseEntity<Void> deleteThumbnail(
            @Parameter(description = "ID kursu") @PathVariable UUID id) throws IOException {
        adminCourseService.deleteThumbnail(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Ustaw focal point miniaturki kursu")
    @PutMapping("/{id}/thumbnail-focal-point")
    public ResponseEntity<Void> updateThumbnailFocalPoint(
            @Parameter(description = "ID kursu") @PathVariable UUID id,
            @RequestBody UpdateThumbnailFocalPointRequest request) {
        adminCourseService.updateThumbnailFocalPoint(id, request);
        return ResponseEntity.noContent().build();
    }

    // ==================== Bloki treści ====================

    @Operation(summary = "Dodaj blok tekstowy")
    @PostMapping("/{id}/blocks/text")
    public ResponseEntity<ContentBlockAdminDto> addTextBlock(
            @Parameter(description = "ID kursu") @PathVariable UUID id,
            @Valid @RequestBody AddTextBlockRequest request) {
        return ResponseEntity.ok(adminCourseService.addTextBlock(id, request));
    }

    @Operation(summary = "Dodaj blok obrazkowy")
    @PostMapping("/{id}/blocks/image")
    public ResponseEntity<UploadBlockImageResponse> addImageBlock(
            @Parameter(description = "ID kursu") @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) @Nullable String caption) throws IOException {
        return ResponseEntity.ok(adminCourseService.addImageBlock(id, file, caption));
    }

    @Operation(summary = "Dodaj blok obrazkowy z biblioteki mediów (URL)")
    @PostMapping("/{id}/blocks/image-from-url")
    public ResponseEntity<ContentBlockAdminDto> addImageBlockFromUrl(
            @Parameter(description = "ID kursu") @PathVariable UUID id,
            @RequestBody AdminCourseDtos.AddImageBlockFromUrlRequest request) {
        return ResponseEntity.ok(adminCourseService.addImageBlockFromUrl(id, request));
    }

    @Operation(summary = "Edytuj treść bloku tekstowego")
    @PutMapping("/blocks/{blockId}/text")
    public ResponseEntity<Void> updateTextBlock(
            @Parameter(description = "ID bloku") @PathVariable UUID blockId,
            @Valid @RequestBody UpdateTextBlockRequest request) {
        adminCourseService.updateTextBlock(blockId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Edytuj caption bloku obrazkowego")
    @PutMapping("/blocks/{blockId}/image")
    public ResponseEntity<Void> updateImageBlock(
            @Parameter(description = "ID bloku") @PathVariable UUID blockId,
            @RequestBody UpdateImageBlockRequest request) {
        adminCourseService.updateImageBlock(blockId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Przesuń blok w górę lub dół")
    @PostMapping("/blocks/{blockId}/move")
    public ResponseEntity<Void> moveBlock(
            @Parameter(description = "ID bloku") @PathVariable UUID blockId,
            @RequestBody MoveBlockRequest request) {
        adminCourseService.moveBlock(blockId, request.direction());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Usuń blok treści")
    @DeleteMapping("/blocks/{blockId}")
    public ResponseEntity<Void> deleteBlock(
            @Parameter(description = "ID bloku") @PathVariable UUID blockId) {
        adminCourseService.deleteBlock(blockId);
        return ResponseEntity.noContent().build();
    }
}
