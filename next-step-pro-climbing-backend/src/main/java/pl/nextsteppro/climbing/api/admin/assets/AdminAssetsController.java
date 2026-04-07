package pl.nextsteppro.climbing.api.admin.assets;

import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "Pobierz listę assetów")
    public ResponseEntity<List<AssetDto>> list() {
        return ResponseEntity.ok(adminAssetsService.list());
    }

    @PostMapping
    @Operation(summary = "Uploaduj asset")
    public ResponseEntity<AssetDto> upload(
            @RequestParam("file") MultipartFile file) throws IOException {
        AssetDto dto = adminAssetsService.upload(file);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Usuń asset")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id) throws IOException {
        adminAssetsService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
