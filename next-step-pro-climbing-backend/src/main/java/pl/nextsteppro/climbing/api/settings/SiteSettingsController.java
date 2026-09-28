package pl.nextsteppro.climbing.api.settings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.nextsteppro.climbing.api.admin.settings.AdminSiteSettingsService;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.CalendarPromoSectionDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.HomeSettingsDto;

@RestController
@RequestMapping("/api/settings")
@Tag(name = "Site Settings", description = "Public site settings")
public class SiteSettingsController {

    private final AdminSiteSettingsService adminSiteSettingsService;

    public SiteSettingsController(AdminSiteSettingsService adminSiteSettingsService) {
        this.adminSiteSettingsService = adminSiteSettingsService;
    }

    @GetMapping("/home")
    @Operation(summary = "Get all homepage settings (hero + badges) in one request")
    @ApiResponse(responseCode = "200", description = "Hero image + badges")
    public ResponseEntity<HomeSettingsDto> getHomeSettings() {
        return ResponseEntity.ok(new HomeSettingsDto(
                adminSiteSettingsService.getHeroImage(),
                adminSiteSettingsService.getMobileHeroImage(),
                adminSiteSettingsService.getBadgeImage(),
                adminSiteSettingsService.getBadgeLeftImage(),
                adminSiteSettingsService.getLocationSection()
        ));
    }

    @GetMapping("/calendar-promo")
    @Operation(summary = "Get the active calendar promo (enabled=false if none)")
    @ApiResponse(responseCode = "200", description = "Promo content per language or enabled=false")
    public ResponseEntity<CalendarPromoSectionDto> getCalendarPromo() {
        return ResponseEntity.ok(adminSiteSettingsService.getCalendarPromoSection());
    }
}
