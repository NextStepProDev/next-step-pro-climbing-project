package pl.nextsteppro.climbing.api.settings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.nextsteppro.climbing.api.admin.settings.AdminSiteSettingsService;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.HeroImageDto;

@RestController
@RequestMapping("/api/settings")
@Tag(name = "Site Settings", description = "Publiczne ustawienia witryny")
public class SiteSettingsController {

    private final AdminSiteSettingsService adminSiteSettingsService;

    public SiteSettingsController(AdminSiteSettingsService adminSiteSettingsService) {
        this.adminSiteSettingsService = adminSiteSettingsService;
    }

    @GetMapping("/hero")
    @Operation(summary = "Pobierz zdjęcie tła strony głównej")
    @ApiResponse(responseCode = "200", description = "URL zdjęcia hero lub null gdy brak")
    public ResponseEntity<HeroImageDto> getHeroImage() {
        return ResponseEntity.ok(adminSiteSettingsService.getHeroImage());
    }
}
