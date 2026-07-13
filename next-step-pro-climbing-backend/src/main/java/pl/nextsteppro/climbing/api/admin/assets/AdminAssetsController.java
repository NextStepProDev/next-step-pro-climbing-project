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
@Tag(name = "Admin - Assets", description = "Media library")
public class AdminAssetsController {

    private final AdminAssetsService adminAssetsService;

    public AdminAssetsController(AdminAssetsService adminAssetsService) {
        this.adminAssetsService = adminAssetsService;
    }

    @GetMapping
    @Operation(summary = "Get asset list", description = "Returns all files from the media library")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of assets",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AssetDto.class)))),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    public ResponseEntity<List<AssetDto>> list() {
        return ResponseEntity.ok(adminAssetsService.list());
    }

    @PostMapping
    @Operation(summary = "Upload asset", description = "Uploads a file to the media library (max 10MB, JPEG/PNG/WebP)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Asset saved",
            content = @Content(schema = @Schema(implementation = AssetDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid file (type or size)"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    public ResponseEntity<AssetDto> upload(
            @Parameter(description = "File to upload") @RequestParam("file") MultipartFile file) throws IOException {
        AssetDto dto = adminAssetsService.upload(file);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete asset", description = "Permanently deletes the file from the media library and disk")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Asset deleted"),
        @ApiResponse(responseCode = "404", description = "Asset not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "UUID assetu") @PathVariable UUID id) throws IOException {
        adminAssetsService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
