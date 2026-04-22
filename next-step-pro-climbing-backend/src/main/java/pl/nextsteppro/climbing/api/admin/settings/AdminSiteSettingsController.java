package pl.nextsteppro.climbing.api.admin.settings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.HeroImageDto;

import java.io.IOException;

@RestController
@RequestMapping("/api/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Site Settings", description = "Ustawienia witryny")
public class AdminSiteSettingsController {

    private final AdminSiteSettingsService adminSiteSettingsService;

    public AdminSiteSettingsController(AdminSiteSettingsService adminSiteSettingsService) {
        this.adminSiteSettingsService = adminSiteSettingsService;
    }

    @GetMapping("/hero")
    @Operation(summary = "Pobierz aktualne zdjęcie hero strony głównej")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "URL zdjęcia lub null"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień admina")
    })
    public ResponseEntity<HeroImageDto> getHeroImage() {
        return ResponseEntity.ok(adminSiteSettingsService.getHeroImage());
    }

    @PostMapping("/hero")
    @Operation(summary = "Wgraj zdjęcie hero z dysku", description = "Upload pliku jako tło strony głównej (max 10MB, JPEG/PNG/WebP)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Zdjęcie wgrane"),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowy plik"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień admina")
    })
    public ResponseEntity<HeroImageDto> uploadHeroImage(
            @Parameter(description = "Plik zdjęcia") @RequestParam("file") MultipartFile file,
            @RequestParam(value = "focalPointX", required = false) @Nullable Float focalPointX,
            @RequestParam(value = "focalPointY", required = false) @Nullable Float focalPointY) throws IOException {
        return ResponseEntity.ok(adminSiteSettingsService.uploadHeroImage(file, focalPointX, focalPointY));
    }

    @PutMapping("/hero/url")
    @Operation(summary = "Ustaw zdjęcie hero z biblioteki mediów lub galerii")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "URL zapisany"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień admina")
    })
    public ResponseEntity<HeroImageDto> setHeroImageUrl(@RequestBody SetHeroUrlRequest request) {
        return ResponseEntity.ok(adminSiteSettingsService.setHeroImageUrl(request.url(), request.focalPointX(), request.focalPointY()));
    }

    @PutMapping("/hero/focal-point")
    @Operation(summary = "Zaktualizuj focal point zdjęcia hero")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Focal point zapisany"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień admina")
    })
    public ResponseEntity<HeroImageDto> setFocalPoint(@RequestBody SetFocalPointRequest request) {
        return ResponseEntity.ok(adminSiteSettingsService.setFocalPoint(request.x(), request.y()));
    }

    @DeleteMapping("/hero")
    @Operation(summary = "Usuń zdjęcie hero strony głównej")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Zdjęcie usunięte"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień admina")
    })
    public ResponseEntity<Void> deleteHeroImage() throws IOException {
        adminSiteSettingsService.deleteHeroImage();
        return ResponseEntity.noContent().build();
    }

    public record SetHeroUrlRequest(String url, @Nullable Float focalPointX, @Nullable Float focalPointY) {}

    public record SetFocalPointRequest(float x, float y) {}
}
