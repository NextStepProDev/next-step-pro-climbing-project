package pl.nextsteppro.climbing.api.admin.news;

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
import pl.nextsteppro.climbing.api.admin.news.AdminNewsDtos.*;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/news")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - News", description = "Zarządzanie aktualnościami (tylko admin)")
public class AdminNewsController {

    private final AdminNewsService adminNewsService;

    public AdminNewsController(AdminNewsService adminNewsService) {
        this.adminNewsService = adminNewsService;
    }

    // ==================== Artykuły ====================

    @Operation(summary = "Pobierz wszystkie aktualności (drafty + opublikowane)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista aktualności"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping
    public ResponseEntity<AdminNewsPageDto> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminNewsService.getAllNews(page, size));
    }

    @Operation(summary = "Pobierz szczegóły aktualności z blokami")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Szczegóły aktualności"),
        @ApiResponse(responseCode = "400", description = "Aktualność nie znaleziona"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping("/{id}")
    public ResponseEntity<NewsDetailAdminDto> getById(
            @Parameter(description = "ID aktualności") @PathVariable UUID id) {
        return ResponseEntity.ok(adminNewsService.getNews(id));
    }

    @Operation(summary = "Utwórz nową aktualność (draft)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Aktualność utworzona"),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe dane"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PostMapping
    public ResponseEntity<NewsAdminDto> create(
            @Valid @RequestBody CreateNewsRequest request) {
        return ResponseEntity.ok(adminNewsService.createNews(request));
    }

    @Operation(summary = "Aktualizuj tytuł i excerpt aktualności")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Metadane zaktualizowane"),
        @ApiResponse(responseCode = "400", description = "Aktualność nie znaleziona lub nieprawidłowe dane"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PutMapping("/{id}/meta")
    public ResponseEntity<NewsAdminDto> updateMeta(
            @Parameter(description = "ID aktualności") @PathVariable UUID id,
            @Valid @RequestBody UpdateNewsMetaRequest request) {
        return ResponseEntity.ok(adminNewsService.updateNewsMeta(id, request));
    }

    @Operation(summary = "Opublikuj aktualność")
    @PostMapping("/{id}/publish")
    public ResponseEntity<NewsAdminDto> publish(
            @Parameter(description = "ID aktualności") @PathVariable UUID id) {
        return ResponseEntity.ok(adminNewsService.setPublished(id, true));
    }

    @Operation(summary = "Cofnij publikację aktualności")
    @PostMapping("/{id}/unpublish")
    public ResponseEntity<NewsAdminDto> unpublish(
            @Parameter(description = "ID aktualności") @PathVariable UUID id) {
        return ResponseEntity.ok(adminNewsService.setPublished(id, false));
    }

    @Operation(summary = "Usuń aktualność wraz z plikami")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Aktualność usunięta"),
        @ApiResponse(responseCode = "400", description = "Aktualność nie znaleziona"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID aktualności") @PathVariable UUID id) {
        adminNewsService.deleteNews(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Miniaturka ====================

    @Operation(summary = "Prześlij miniaturkę aktualności")
    @PostMapping("/{id}/thumbnail")
    public ResponseEntity<NewsDetailAdminDto> uploadThumbnail(
            @Parameter(description = "ID aktualności") @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(adminNewsService.uploadThumbnail(id, file));
    }

    @Operation(summary = "Usuń miniaturkę aktualności")
    @DeleteMapping("/{id}/thumbnail")
    public ResponseEntity<Void> deleteThumbnail(
            @Parameter(description = "ID aktualności") @PathVariable UUID id) throws IOException {
        adminNewsService.deleteThumbnail(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Bloki treści ====================

    @Operation(summary = "Dodaj blok tekstowy")
    @PostMapping("/{id}/blocks/text")
    public ResponseEntity<ContentBlockAdminDto> addTextBlock(
            @Parameter(description = "ID aktualności") @PathVariable UUID id,
            @Valid @RequestBody AddTextBlockRequest request) {
        return ResponseEntity.ok(adminNewsService.addTextBlock(id, request));
    }

    @Operation(summary = "Dodaj blok obrazkowy")
    @PostMapping("/{id}/blocks/image")
    public ResponseEntity<UploadBlockImageResponse> addImageBlock(
            @Parameter(description = "ID aktualności") @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) @Nullable String caption) throws IOException {
        return ResponseEntity.ok(adminNewsService.addImageBlock(id, file, caption));
    }

    @Operation(summary = "Edytuj treść bloku tekstowego")
    @PutMapping("/blocks/{blockId}/text")
    public ResponseEntity<Void> updateTextBlock(
            @Parameter(description = "ID bloku") @PathVariable UUID blockId,
            @Valid @RequestBody UpdateTextBlockRequest request) {
        adminNewsService.updateTextBlock(blockId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Edytuj caption bloku obrazkowego")
    @PutMapping("/blocks/{blockId}/image")
    public ResponseEntity<Void> updateImageBlock(
            @Parameter(description = "ID bloku") @PathVariable UUID blockId,
            @RequestBody UpdateImageBlockRequest request) {
        adminNewsService.updateImageBlock(blockId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Przesuń blok w górę lub dół")
    @PostMapping("/blocks/{blockId}/move")
    public ResponseEntity<Void> moveBlock(
            @Parameter(description = "ID bloku") @PathVariable UUID blockId,
            @RequestBody MoveBlockRequest request) {
        adminNewsService.moveBlock(blockId, request.direction());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Usuń blok treści")
    @DeleteMapping("/blocks/{blockId}")
    public ResponseEntity<Void> deleteBlock(
            @Parameter(description = "ID bloku") @PathVariable UUID blockId) {
        adminNewsService.deleteBlock(blockId);
        return ResponseEntity.noContent().build();
    }
}
