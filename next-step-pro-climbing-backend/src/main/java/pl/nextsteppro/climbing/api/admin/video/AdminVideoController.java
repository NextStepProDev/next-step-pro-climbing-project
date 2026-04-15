package pl.nextsteppro.climbing.api.admin.video;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.api.admin.video.AdminVideoDtos.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/videos")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Videos", description = "Zarządzanie filmami (tylko admin)")
public class AdminVideoController {

    private final AdminVideoService adminVideoService;

    public AdminVideoController(AdminVideoService adminVideoService) {
        this.adminVideoService = adminVideoService;
    }

    @Operation(summary = "Pobierz wszystkie filmy (drafty + opublikowane)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista filmów"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping
    public ResponseEntity<List<VideoAdminDto>> getAll() {
        return ResponseEntity.ok(adminVideoService.getAllVideos());
    }

    @Operation(summary = "Pobierz szczegóły filmu")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Szczegóły filmu"),
        @ApiResponse(responseCode = "400", description = "Film nie znaleziony"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping("/{id}")
    public ResponseEntity<VideoAdminDto> getById(
            @Parameter(description = "ID filmu") @PathVariable UUID id) {
        return ResponseEntity.ok(adminVideoService.getVideo(id));
    }

    @Operation(summary = "Utwórz nowy film (draft)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Film utworzony"),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe dane"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PostMapping
    public ResponseEntity<VideoAdminDto> create(
            @Valid @RequestBody CreateVideoRequest request) {
        return ResponseEntity.ok(adminVideoService.createVideo(request));
    }

    @Operation(summary = "Aktualizuj film")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Film zaktualizowany"),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe dane lub film nie znaleziony"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PutMapping("/{id}")
    public ResponseEntity<VideoAdminDto> update(
            @Parameter(description = "ID filmu") @PathVariable UUID id,
            @Valid @RequestBody UpdateVideoRequest request) {
        return ResponseEntity.ok(adminVideoService.updateVideo(id, request));
    }

    @Operation(summary = "Usuń film")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Film usunięty"),
        @ApiResponse(responseCode = "400", description = "Film nie znaleziony"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID filmu") @PathVariable UUID id) {
        adminVideoService.deleteVideo(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Opublikuj film")
    @PostMapping("/{id}/publish")
    public ResponseEntity<VideoAdminDto> publish(
            @Parameter(description = "ID filmu") @PathVariable UUID id) {
        return ResponseEntity.ok(adminVideoService.setPublished(id, true));
    }

    @Operation(summary = "Cofnij publikację filmu")
    @PostMapping("/{id}/unpublish")
    public ResponseEntity<VideoAdminDto> unpublish(
            @Parameter(description = "ID filmu") @PathVariable UUID id) {
        return ResponseEntity.ok(adminVideoService.setPublished(id, false));
    }

    @Operation(summary = "Przesuń film w górę (mniejszy displayOrder)")
    @PostMapping("/{id}/move-up")
    public ResponseEntity<List<VideoAdminDto>> moveUp(
            @Parameter(description = "ID filmu") @PathVariable UUID id) {
        return ResponseEntity.ok(adminVideoService.moveUp(id));
    }

    @Operation(summary = "Przesuń film w dół (większy displayOrder)")
    @PostMapping("/{id}/move-down")
    public ResponseEntity<List<VideoAdminDto>> moveDown(
            @Parameter(description = "ID filmu") @PathVariable UUID id) {
        return ResponseEntity.ok(adminVideoService.moveDown(id));
    }
}
