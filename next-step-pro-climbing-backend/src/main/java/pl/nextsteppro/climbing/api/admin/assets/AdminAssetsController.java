package pl.nextsteppro.climbing.api.admin.assets;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.admin.assets.AdminAssetsDtos.AssetDto;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/assets")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Assets", description = "Biblioteka mediów")
public class AdminAssetsController {

    private final AdminAssetsService adminAssetsService;

    public AdminAssetsController(AdminAssetsService adminAssetsService) {
        this.adminAssetsService = adminAssetsService;
    }

    @GetMapping
    @Operation(summary = "Pobierz listę assetów", description = "Zwraca wszystkie pliki z biblioteki mediów")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista assetów",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AssetDto.class)))),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień admina")
    })
    public ResponseEntity<List<AssetDto>> list() {
        return ResponseEntity.ok(adminAssetsService.list());
    }

    @PostMapping
    @Operation(summary = "Uploaduj asset", description = "Wgrywa plik do biblioteki mediów (max 10MB, JPEG/PNG/WebP)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Asset zapisany",
            content = @Content(schema = @Schema(implementation = AssetDto.class))),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowy plik (typ lub rozmiar)"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień admina")
    })
    public ResponseEntity<AssetDto> upload(
            @Parameter(description = "Plik do wgrania") @RequestParam("file") MultipartFile file) throws IOException {
        AssetDto dto = adminAssetsService.upload(file);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Usuń asset", description = "Trwale usuwa plik z biblioteki mediów i dysku")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Asset usunięty"),
        @ApiResponse(responseCode = "404", description = "Asset nie istnieje"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień admina")
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "UUID assetu") @PathVariable UUID id) throws IOException {
        adminAssetsService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
