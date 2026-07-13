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
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.BadgeImageDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.CalendarPromoPresetDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.HeroImageDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.LocationActiveStateDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.LocationPresetDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.SlotTemplateDto;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Site Settings", description = "Site settings")
public class AdminSiteSettingsController {

    private final AdminSiteSettingsService adminSiteSettingsService;

    public AdminSiteSettingsController(AdminSiteSettingsService adminSiteSettingsService) {
        this.adminSiteSettingsService = adminSiteSettingsService;
    }

    @GetMapping("/hero")
    @Operation(summary = "Get the current homepage hero image")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Image URL or null"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    public ResponseEntity<HeroImageDto> getHeroImage() {
        return ResponseEntity.ok(adminSiteSettingsService.getHeroImage());
    }

    @PostMapping("/hero")
    @Operation(summary = "Upload hero image from disk", description = "Uploads a file as the homepage background (max 10MB, JPEG/PNG/WebP)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Photo uploaded"),
        @ApiResponse(responseCode = "400", description = "Invalid file"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    public ResponseEntity<HeroImageDto> uploadHeroImage(
            @Parameter(description = "Image file") @RequestParam("file") MultipartFile file,
            @RequestParam(value = "focalPointX", required = false) @Nullable Float focalPointX,
            @RequestParam(value = "focalPointY", required = false) @Nullable Float focalPointY) throws IOException {
        return ResponseEntity.ok(adminSiteSettingsService.uploadHeroImage(file, focalPointX, focalPointY));
    }

    @PutMapping("/hero/url")
    @Operation(summary = "Set the hero image from the media library or gallery")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "URL saved"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    public ResponseEntity<HeroImageDto> setHeroImageUrl(@RequestBody SetHeroUrlRequest request) {
        return ResponseEntity.ok(adminSiteSettingsService.setHeroImageUrl(request.url(), request.focalPointX(), request.focalPointY()));
    }

    @PutMapping("/hero/focal-point")
    @Operation(summary = "Update hero image focal point")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Focal point saved"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    public ResponseEntity<HeroImageDto> setFocalPoint(@RequestBody SetFocalPointRequest request) {
        return ResponseEntity.ok(adminSiteSettingsService.setFocalPoint(request.x(), request.y()));
    }

    @DeleteMapping("/hero")
    @Operation(summary = "Delete the homepage hero image")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Photo deleted"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    public ResponseEntity<Void> deleteHeroImage() throws IOException {
        adminSiteSettingsService.deleteHeroImage();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/hero-mobile")
    @Operation(summary = "Get the current homepage hero image (mobile)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Image URL or null"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    public ResponseEntity<HeroImageDto> getMobileHeroImage() {
        return ResponseEntity.ok(adminSiteSettingsService.getMobileHeroImage());
    }

    @PostMapping("/hero-mobile")
    @Operation(summary = "Upload hero image (mobile) from disk", description = "Uploads a vertical background for phones (max 10MB, JPEG/PNG/WebP)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Photo uploaded"),
        @ApiResponse(responseCode = "400", description = "Invalid file"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    public ResponseEntity<HeroImageDto> uploadMobileHeroImage(
            @Parameter(description = "Image file") @RequestParam("file") MultipartFile file,
            @RequestParam(value = "focalPointX", required = false) @Nullable Float focalPointX,
            @RequestParam(value = "focalPointY", required = false) @Nullable Float focalPointY) throws IOException {
        return ResponseEntity.ok(adminSiteSettingsService.uploadMobileHeroImage(file, focalPointX, focalPointY));
    }

    @PutMapping("/hero-mobile/url")
    @Operation(summary = "Set the hero image (mobile) from the media library or gallery")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "URL saved"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    public ResponseEntity<HeroImageDto> setMobileHeroImageUrl(@RequestBody SetHeroUrlRequest request) {
        return ResponseEntity.ok(adminSiteSettingsService.setMobileHeroImageUrl(request.url(), request.focalPointX(), request.focalPointY()));
    }

    @PutMapping("/hero-mobile/focal-point")
    @Operation(summary = "Update hero image focal point (mobile)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Focal point saved"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    public ResponseEntity<HeroImageDto> setMobileFocalPoint(@RequestBody SetFocalPointRequest request) {
        return ResponseEntity.ok(adminSiteSettingsService.setMobileFocalPoint(request.x(), request.y()));
    }

    @DeleteMapping("/hero-mobile")
    @Operation(summary = "Delete the homepage hero image (mobile)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Photo deleted"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    public ResponseEntity<Void> deleteMobileHeroImage() throws IOException {
        adminSiteSettingsService.deleteMobileHeroImage();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/badge")
    @Operation(summary = "Get the current homepage logo/badge")
    public ResponseEntity<BadgeImageDto> getBadgeImage() {
        return ResponseEntity.ok(adminSiteSettingsService.getBadgeImage());
    }

    @PutMapping("/badge/url")
    @Operation(summary = "Set badge from the media library")
    public ResponseEntity<BadgeImageDto> setBadgeImageUrl(@RequestBody SetBadgeUrlRequest request) {
        return ResponseEntity.ok(adminSiteSettingsService.setBadgeImageUrl(request.url(), request.linkUrl()));
    }

    @DeleteMapping("/badge")
    @Operation(summary = "Remove badge from the homepage")
    public ResponseEntity<Void> deleteBadgeImage() {
        adminSiteSettingsService.deleteBadgeImage();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/badge-left")
    @Operation(summary = "Get the left homepage logo/badge")
    public ResponseEntity<BadgeImageDto> getBadgeLeftImage() {
        return ResponseEntity.ok(adminSiteSettingsService.getBadgeLeftImage());
    }

    @PutMapping("/badge-left/url")
    @Operation(summary = "Set left badge from the media library")
    public ResponseEntity<BadgeImageDto> setBadgeLeftImageUrl(@RequestBody SetBadgeUrlRequest request) {
        return ResponseEntity.ok(adminSiteSettingsService.setBadgeLeftImageUrl(request.url(), request.linkUrl()));
    }

    @DeleteMapping("/badge-left")
    @Operation(summary = "Remove left badge from the homepage")
    public ResponseEntity<Void> deleteBadgeLeftImage() {
        adminSiteSettingsService.deleteBadgeLeftImage();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/slot-templates")
    @Operation(summary = "Get predefined slot templates")
    public ResponseEntity<List<SlotTemplateDto>> getSlotTemplates() {
        return ResponseEntity.ok(adminSiteSettingsService.getSlotTemplates());
    }

    @PutMapping("/slot-templates")
    @Operation(summary = "Save predefined slot templates")
    public ResponseEntity<List<SlotTemplateDto>> saveSlotTemplates(@RequestBody List<SlotTemplateDto> templates) {
        return ResponseEntity.ok(adminSiteSettingsService.saveSlotTemplates(templates));
    }

    @GetMapping("/home-location")
    @Operation(summary = "Get which template is currently live on the page (or null)")
    public ResponseEntity<LocationActiveStateDto> getActiveState() {
        return ResponseEntity.ok(adminSiteSettingsService.getActiveState());
    }

    @PutMapping("/home-location")
    @Operation(summary = "Set the live template (activePresetId = null removes the section)")
    public ResponseEntity<LocationActiveStateDto> setActivePreset(@RequestBody LocationActiveStateDto state) {
        return ResponseEntity.ok(adminSiteSettingsService.setActivePreset(state.activePresetId()));
    }

    @GetMapping("/home-location/presets")
    @Operation(summary = "Get saved location section templates")
    public ResponseEntity<List<LocationPresetDto>> getLocationPresets() {
        return ResponseEntity.ok(adminSiteSettingsService.getLocationPresets());
    }

    @PostMapping("/home-location/presets")
    @Operation(summary = "Create or update a location section preset")
    public ResponseEntity<LocationPresetDto> saveLocationPreset(@RequestBody LocationPresetDto preset) {
        return ResponseEntity.ok(adminSiteSettingsService.saveLocationPreset(preset));
    }

    @DeleteMapping("/home-location/presets/{id}")
    @Operation(summary = "Delete location section preset")
    public ResponseEntity<Void> deleteLocationPreset(@PathVariable String id) {
        adminSiteSettingsService.deleteLocationPreset(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/calendar-promo")
    @Operation(summary = "Get which promo template is currently above the calendar (or null)")
    public ResponseEntity<LocationActiveStateDto> getCalendarPromoActiveState() {
        return ResponseEntity.ok(adminSiteSettingsService.getCalendarPromoActiveState());
    }

    @PutMapping("/calendar-promo")
    @Operation(summary = "Set the calendar promo template (activePresetId = null removes the promo)")
    public ResponseEntity<LocationActiveStateDto> setCalendarPromoActivePreset(@RequestBody LocationActiveStateDto state) {
        return ResponseEntity.ok(adminSiteSettingsService.setCalendarPromoActivePreset(state.activePresetId()));
    }

    @GetMapping("/calendar-promo/presets")
    @Operation(summary = "Get saved calendar promo templates")
    public ResponseEntity<List<CalendarPromoPresetDto>> getCalendarPromoPresets() {
        return ResponseEntity.ok(adminSiteSettingsService.getCalendarPromoPresets());
    }

    @PostMapping("/calendar-promo/presets")
    @Operation(summary = "Create or update a calendar promo template")
    public ResponseEntity<CalendarPromoPresetDto> saveCalendarPromoPreset(@RequestBody CalendarPromoPresetDto preset) {
        return ResponseEntity.ok(adminSiteSettingsService.saveCalendarPromoPreset(preset));
    }

    @DeleteMapping("/calendar-promo/presets/{id}")
    @Operation(summary = "Delete calendar promo template")
    public ResponseEntity<Void> deleteCalendarPromoPreset(@PathVariable String id) {
        adminSiteSettingsService.deleteCalendarPromoPreset(id);
        return ResponseEntity.noContent().build();
    }

    public record SetHeroUrlRequest(String url, @Nullable Float focalPointX, @Nullable Float focalPointY) {}

    public record SetFocalPointRequest(float x, float y) {}

    public record SetBadgeUrlRequest(String url, @Nullable String linkUrl) {}
}
