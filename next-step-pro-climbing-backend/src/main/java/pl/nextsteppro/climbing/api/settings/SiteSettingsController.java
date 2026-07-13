package pl.nextsteppro.climbing.api.settings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.nextsteppro.climbing.api.admin.settings.AdminSiteSettingsService;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.BadgeImageDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.CalendarPromoSectionDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.HeroImageDto;
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

    @GetMapping("/hero")
    @Operation(summary = "Get the homepage background image (desktop)")
    @ApiResponse(responseCode = "200", description = "Hero image URL or null if none")
    public ResponseEntity<HeroImageDto> getHeroImage() {
        return ResponseEntity.ok(adminSiteSettingsService.getHeroImage());
    }

    @GetMapping("/hero-mobile")
    @Operation(summary = "Get the homepage background image (mobile)")
    @ApiResponse(responseCode = "200", description = "Mobile hero image URL or null if none")
    public ResponseEntity<HeroImageDto> getMobileHeroImage() {
        return ResponseEntity.ok(adminSiteSettingsService.getMobileHeroImage());
    }

    @GetMapping("/badge")
    @Operation(summary = "Get the right homepage badge/logo")
    @ApiResponse(responseCode = "200", description = "Badge URL or null if none")
    public ResponseEntity<BadgeImageDto> getBadgeImage() {
        return ResponseEntity.ok(adminSiteSettingsService.getBadgeImage());
    }

    @GetMapping("/badge-left")
    @Operation(summary = "Get the left homepage badge/logo")
    @ApiResponse(responseCode = "200", description = "Badge URL or null if none")
    public ResponseEntity<BadgeImageDto> getBadgeLeftImage() {
        return ResponseEntity.ok(adminSiteSettingsService.getBadgeLeftImage());
    }

    @GetMapping("/calendar-promo")
    @Operation(summary = "Get the active calendar promo (enabled=false if none)")
    @ApiResponse(responseCode = "200", description = "Promo content per language or enabled=false")
    public ResponseEntity<CalendarPromoSectionDto> getCalendarPromo() {
        return ResponseEntity.ok(adminSiteSettingsService.getCalendarPromoSection());
    }
}
