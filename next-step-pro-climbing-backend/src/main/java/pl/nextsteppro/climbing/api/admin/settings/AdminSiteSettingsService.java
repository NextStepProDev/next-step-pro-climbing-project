package pl.nextsteppro.climbing.api.admin.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.BadgeImageDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.CalendarPromoPresetDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.CalendarPromoSectionDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.HeroImageDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.LocationActiveStateDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.LocationPresetDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.LocationSectionDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.SlotTemplateDto;
import pl.nextsteppro.climbing.domain.settings.SiteSetting;
import pl.nextsteppro.climbing.domain.settings.SiteSettingsRepository;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class AdminSiteSettingsService {

    private static final Logger logger = LoggerFactory.getLogger(AdminSiteSettingsService.class);
    private static final String FOLDER = "site";
    private static final String KEY_IMAGE_URL = "hero_image_url";
    private static final String KEY_IMAGE_FILENAME = "hero_image_filename";
    private static final String KEY_FOCAL_POINT_X = "hero_focal_point_x";
    private static final String KEY_FOCAL_POINT_Y = "hero_focal_point_y";
    private static final String KEY_MOBILE_IMAGE_URL = "hero_mobile_image_url";
    private static final String KEY_MOBILE_IMAGE_FILENAME = "hero_mobile_image_filename";
    private static final String KEY_MOBILE_FOCAL_POINT_X = "hero_mobile_focal_point_x";
    private static final String KEY_MOBILE_FOCAL_POINT_Y = "hero_mobile_focal_point_y";
    private static final String KEY_BADGE_IMAGE_URL = "badge_image_url";
    private static final String KEY_BADGE_LINK_URL = "badge_link_url";
    private static final String KEY_BADGE_LEFT_IMAGE_URL = "badge_left_image_url";
    private static final String KEY_BADGE_LEFT_LINK_URL = "badge_left_link_url";
    private static final String KEY_SLOT_TEMPLATES = "slot_templates";
    private static final String KEY_LOCATION_ACTIVE = "home_location_active";
    private static final String KEY_LOCATION_PRESETS = "home_location_presets";
    private static final String KEY_CALENDAR_PROMO_ACTIVE = "calendar_promo_active";
    private static final String KEY_CALENDAR_PROMO_PRESETS = "calendar_promo_presets";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SiteSettingsRepository siteSettingsRepository;
    private final FileStorageService fileStorageService;
    private final String baseUrl;

    public AdminSiteSettingsService(SiteSettingsRepository siteSettingsRepository,
                                    FileStorageService fileStorageService,
                                    @Value("${app.base-url}") String baseUrl) {
        this.siteSettingsRepository = siteSettingsRepository;
        this.fileStorageService = fileStorageService;
        this.baseUrl = baseUrl;
    }

    @Cacheable(value = "siteSettings", key = "'hero'")
    @Transactional(readOnly = true)
    public HeroImageDto getHeroImage() {
        String imageUrl = siteSettingsRepository.findById(KEY_IMAGE_URL)
                .map(SiteSetting::getValue)
                .orElse(null);
        Float focalPointX = parseFloat(siteSettingsRepository.findById(KEY_FOCAL_POINT_X)
                .map(SiteSetting::getValue)
                .orElse(null));
        Float focalPointY = parseFloat(siteSettingsRepository.findById(KEY_FOCAL_POINT_Y)
                .map(SiteSetting::getValue)
                .orElse(null));
        return new HeroImageDto(imageUrl, focalPointX, focalPointY);
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public HeroImageDto uploadHeroImage(MultipartFile file, @Nullable Float focalPointX, @Nullable Float focalPointY) throws IOException {
        return uploadHero(file, focalPointX, focalPointY, KEY_IMAGE_URL, KEY_IMAGE_FILENAME, KEY_FOCAL_POINT_X, KEY_FOCAL_POINT_Y);
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public HeroImageDto setHeroImageUrl(String url, @Nullable Float focalPointX, @Nullable Float focalPointY) {
        return setHeroUrl(url, focalPointX, focalPointY, KEY_IMAGE_URL, KEY_IMAGE_FILENAME, KEY_FOCAL_POINT_X, KEY_FOCAL_POINT_Y);
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public HeroImageDto setFocalPoint(float x, float y) {
        return setHeroFocalPoint(x, y, KEY_IMAGE_URL, KEY_FOCAL_POINT_X, KEY_FOCAL_POINT_Y);
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public void deleteHeroImage() throws IOException {
        deleteHero(KEY_IMAGE_URL, KEY_IMAGE_FILENAME, KEY_FOCAL_POINT_X, KEY_FOCAL_POINT_Y);
    }

    // === Hero MOBILE — separate vertical image for phones (same operations, own keys) ===

    @Cacheable(value = "siteSettings", key = "'heroMobile'")
    @Transactional(readOnly = true)
    public HeroImageDto getMobileHeroImage() {
        String imageUrl = siteSettingsRepository.findById(KEY_MOBILE_IMAGE_URL)
                .map(SiteSetting::getValue)
                .orElse(null);
        Float focalPointX = parseFloat(siteSettingsRepository.findById(KEY_MOBILE_FOCAL_POINT_X)
                .map(SiteSetting::getValue)
                .orElse(null));
        Float focalPointY = parseFloat(siteSettingsRepository.findById(KEY_MOBILE_FOCAL_POINT_Y)
                .map(SiteSetting::getValue)
                .orElse(null));
        return new HeroImageDto(imageUrl, focalPointX, focalPointY);
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public HeroImageDto uploadMobileHeroImage(MultipartFile file, @Nullable Float focalPointX, @Nullable Float focalPointY) throws IOException {
        return uploadHero(file, focalPointX, focalPointY, KEY_MOBILE_IMAGE_URL, KEY_MOBILE_IMAGE_FILENAME, KEY_MOBILE_FOCAL_POINT_X, KEY_MOBILE_FOCAL_POINT_Y);
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public HeroImageDto setMobileHeroImageUrl(String url, @Nullable Float focalPointX, @Nullable Float focalPointY) {
        return setHeroUrl(url, focalPointX, focalPointY, KEY_MOBILE_IMAGE_URL, KEY_MOBILE_IMAGE_FILENAME, KEY_MOBILE_FOCAL_POINT_X, KEY_MOBILE_FOCAL_POINT_Y);
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public HeroImageDto setMobileFocalPoint(float x, float y) {
        return setHeroFocalPoint(x, y, KEY_MOBILE_IMAGE_URL, KEY_MOBILE_FOCAL_POINT_X, KEY_MOBILE_FOCAL_POINT_Y);
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public void deleteMobileHeroImage() throws IOException {
        deleteHero(KEY_MOBILE_IMAGE_URL, KEY_MOBILE_IMAGE_FILENAME, KEY_MOBILE_FOCAL_POINT_X, KEY_MOBILE_FOCAL_POINT_Y);
    }

    // === Shared hero logic (desktop + mobile), parameterized by keys ===

    private HeroImageDto uploadHero(MultipartFile file, @Nullable Float focalPointX, @Nullable Float focalPointY,
                                    String urlKey, String filenameKey, String focalXKey, String focalYKey) throws IOException {
        deleteExistingFileIfPresent(filenameKey);

        String filename = fileStorageService.store(file, FOLDER);
        String imageUrl = baseUrl + "/api/files/" + FOLDER + "/" + filename;

        save(urlKey, imageUrl);
        save(filenameKey, filename);
        saveFocalPoint(focalPointX, focalPointY, focalXKey, focalYKey);

        return new HeroImageDto(imageUrl, focalPointX, focalPointY);
    }

    private HeroImageDto setHeroUrl(String url, @Nullable Float focalPointX, @Nullable Float focalPointY,
                                   String urlKey, String filenameKey, String focalXKey, String focalYKey) {
        deleteExistingFileIfPresent(filenameKey);

        save(urlKey, url);
        siteSettingsRepository.deleteById(filenameKey);
        saveFocalPoint(focalPointX, focalPointY, focalXKey, focalYKey);

        return new HeroImageDto(url, focalPointX, focalPointY);
    }

    private HeroImageDto setHeroFocalPoint(float x, float y, String urlKey, String focalXKey, String focalYKey) {
        save(focalXKey, String.valueOf(x));
        save(focalYKey, String.valueOf(y));

        String imageUrl = siteSettingsRepository.findById(urlKey)
                .map(SiteSetting::getValue)
                .orElse(null);
        return new HeroImageDto(imageUrl, x, y);
    }

    private void deleteHero(String urlKey, String filenameKey, String focalXKey, String focalYKey) throws IOException {
        deleteExistingFileIfPresent(filenameKey);

        siteSettingsRepository.deleteById(urlKey);
        siteSettingsRepository.deleteById(filenameKey);
        siteSettingsRepository.deleteById(focalXKey);
        siteSettingsRepository.deleteById(focalYKey);
    }

    @Cacheable(value = "siteSettings", key = "'badge'")
    @Transactional(readOnly = true)
    public BadgeImageDto getBadgeImage() {
        String imageUrl = siteSettingsRepository.findById(KEY_BADGE_IMAGE_URL)
                .map(SiteSetting::getValue).orElse(null);
        String linkUrl = siteSettingsRepository.findById(KEY_BADGE_LINK_URL)
                .map(SiteSetting::getValue).orElse(null);
        return new BadgeImageDto(imageUrl, linkUrl);
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public BadgeImageDto setBadgeImageUrl(String url, @Nullable String linkUrl) {
        save(KEY_BADGE_IMAGE_URL, url);
        if (linkUrl != null && !linkUrl.isBlank()) {
            save(KEY_BADGE_LINK_URL, linkUrl);
        } else {
            siteSettingsRepository.deleteById(KEY_BADGE_LINK_URL);
        }
        return new BadgeImageDto(url, linkUrl);
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public void deleteBadgeImage() {
        siteSettingsRepository.deleteById(KEY_BADGE_IMAGE_URL);
        siteSettingsRepository.deleteById(KEY_BADGE_LINK_URL);
    }

    @Cacheable(value = "siteSettings", key = "'badgeLeft'")
    @Transactional(readOnly = true)
    public BadgeImageDto getBadgeLeftImage() {
        String imageUrl = siteSettingsRepository.findById(KEY_BADGE_LEFT_IMAGE_URL)
                .map(SiteSetting::getValue).orElse(null);
        String linkUrl = siteSettingsRepository.findById(KEY_BADGE_LEFT_LINK_URL)
                .map(SiteSetting::getValue).orElse(null);
        return new BadgeImageDto(imageUrl, linkUrl);
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public BadgeImageDto setBadgeLeftImageUrl(String url, @Nullable String linkUrl) {
        save(KEY_BADGE_LEFT_IMAGE_URL, url);
        if (linkUrl != null && !linkUrl.isBlank()) {
            save(KEY_BADGE_LEFT_LINK_URL, linkUrl);
        } else {
            siteSettingsRepository.deleteById(KEY_BADGE_LEFT_LINK_URL);
        }
        return new BadgeImageDto(url, linkUrl);
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public void deleteBadgeLeftImage() {
        siteSettingsRepository.deleteById(KEY_BADGE_LEFT_IMAGE_URL);
        siteSettingsRepository.deleteById(KEY_BADGE_LEFT_LINK_URL);
    }

    @Transactional(readOnly = true)
    public List<SlotTemplateDto> getSlotTemplates() {
        return readJson(KEY_SLOT_TEMPLATES, new TypeReference<List<SlotTemplateDto>>() {}, List.of());
    }

    public List<SlotTemplateDto> saveSlotTemplates(List<SlotTemplateDto> templates) {
        writeJson(KEY_SLOT_TEMPLATES, templates);
        return templates;
    }

    // === "Where I teach now" section — active template (referenced by ID) ===

    @Cacheable(value = "siteSettings", key = "'homeLocation'")
    @Transactional(readOnly = true)
    public LocationSectionDto getLocationSection() {
        // The section shows ONLY when an existing template is selected; otherwise enabled=false (no section).
        String activeId = getActiveState().activePresetId();
        if (activeId == null) return new LocationSectionDto(false, Map.of());
        return getLocationPresets().stream()
                .filter(p -> activeId.equals(p.id()))
                .findFirst()
                .map(p -> new LocationSectionDto(true, p.translations()))
                .orElse(new LocationSectionDto(false, Map.of()));
    }

    @Transactional(readOnly = true)
    public LocationActiveStateDto getActiveState() {
        return readJson(KEY_LOCATION_ACTIVE, new TypeReference<LocationActiveStateDto>() {},
                new LocationActiveStateDto(null));
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public LocationActiveStateDto setActivePreset(@Nullable String presetId) {
        LocationActiveStateDto state = new LocationActiveStateDto(presetId);
        writeJson(KEY_LOCATION_ACTIVE, state);
        return state;
    }

    // === Section templates (CRUD) ===

    @Transactional(readOnly = true)
    public List<LocationPresetDto> getLocationPresets() {
        return readJson(KEY_LOCATION_PRESETS, new TypeReference<List<LocationPresetDto>>() {}, List.of());
    }

    // Saving a template can change live content (when editing the template that is on the page) → evict cache.
    @CacheEvict(value = "siteSettings", allEntries = true)
    public LocationPresetDto saveLocationPreset(LocationPresetDto preset) {
        List<LocationPresetDto> presets = new ArrayList<>(getLocationPresets());
        LocationPresetDto stored;
        if (preset.id() == null || preset.id().isBlank()) {
            stored = new LocationPresetDto(UUID.randomUUID().toString(), preset.name(), preset.translations());
            presets.add(stored);
        } else {
            stored = preset;
            int idx = indexOfPreset(presets, preset.id());
            if (idx >= 0) {
                presets.set(idx, stored);
            } else {
                presets.add(stored);
            }
        }
        persistPresets(presets);
        return stored;
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public void deleteLocationPreset(String id) {
        List<LocationPresetDto> presets = new ArrayList<>(getLocationPresets());
        presets.removeIf(p -> id.equals(p.id()));
        persistPresets(presets);
        // If the deleted template was live on the page — take it down (the section disappears).
        if (id.equals(getActiveState().activePresetId())) {
            writeJson(KEY_LOCATION_ACTIVE, new LocationActiveStateDto(null));
        }
    }

    private void persistPresets(List<LocationPresetDto> presets) {
        writeJson(KEY_LOCATION_PRESETS, presets);
    }

    // === Calendar promo — active template (referenced by ID) ===

    @Cacheable(value = "siteSettings", key = "'calendarPromo'")
    @Transactional(readOnly = true)
    public CalendarPromoSectionDto getCalendarPromoSection() {
        // The promo shows ONLY when an existing template is selected; otherwise enabled=false.
        String activeId = getCalendarPromoActiveState().activePresetId();
        if (activeId == null) return new CalendarPromoSectionDto(false, Map.of());
        return getCalendarPromoPresets().stream()
                .filter(p -> activeId.equals(p.id()))
                .findFirst()
                .map(p -> new CalendarPromoSectionDto(true, p.translations()))
                .orElse(new CalendarPromoSectionDto(false, Map.of()));
    }

    @Transactional(readOnly = true)
    public LocationActiveStateDto getCalendarPromoActiveState() {
        return readJson(KEY_CALENDAR_PROMO_ACTIVE, new TypeReference<LocationActiveStateDto>() {},
                new LocationActiveStateDto(null));
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public LocationActiveStateDto setCalendarPromoActivePreset(@Nullable String presetId) {
        LocationActiveStateDto state = new LocationActiveStateDto(presetId);
        writeJson(KEY_CALENDAR_PROMO_ACTIVE, state);
        return state;
    }

    @Transactional(readOnly = true)
    public List<CalendarPromoPresetDto> getCalendarPromoPresets() {
        return readJson(KEY_CALENDAR_PROMO_PRESETS, new TypeReference<List<CalendarPromoPresetDto>>() {}, List.of());
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public CalendarPromoPresetDto saveCalendarPromoPreset(CalendarPromoPresetDto preset) {
        List<CalendarPromoPresetDto> presets = new ArrayList<>(getCalendarPromoPresets());
        CalendarPromoPresetDto stored;
        if (preset.id() == null || preset.id().isBlank()) {
            stored = new CalendarPromoPresetDto(UUID.randomUUID().toString(), preset.name(), preset.translations());
            presets.add(stored);
        } else {
            stored = preset;
            int idx = indexOfPromoPreset(presets, preset.id());
            if (idx >= 0) {
                presets.set(idx, stored);
            } else {
                presets.add(stored);
            }
        }
        writeJson(KEY_CALENDAR_PROMO_PRESETS, presets);
        return stored;
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public void deleteCalendarPromoPreset(String id) {
        List<CalendarPromoPresetDto> presets = new ArrayList<>(getCalendarPromoPresets());
        presets.removeIf(p -> id.equals(p.id()));
        writeJson(KEY_CALENDAR_PROMO_PRESETS, presets);
        // If the deleted template was live on the page — take it down (the promo disappears).
        if (id.equals(getCalendarPromoActiveState().activePresetId())) {
            writeJson(KEY_CALENDAR_PROMO_ACTIVE, new LocationActiveStateDto(null));
        }
    }

    private static int indexOfPromoPreset(List<CalendarPromoPresetDto> presets, String id) {
        for (int i = 0; i < presets.size(); i++) {
            if (id.equals(presets.get(i).id())) return i;
        }
        return -1;
    }

    // Shared read/write of settings stored as JSON in site_settings.
    private <T> T readJson(String key, TypeReference<T> typeRef, T defaultValue) {
        String json = siteSettingsRepository.findById(key)
                .map(SiteSetting::getValue)
                .orElse(null);
        if (json == null) return defaultValue;
        try {
            return OBJECT_MAPPER.readValue(json, typeRef);
        } catch (Exception e) {
            logger.warn("Failed to parse setting {}: {}", key, e.getMessage());
            return defaultValue;
        }
    }

    private void writeJson(String key, Object value) {
        try {
            save(key, OBJECT_MAPPER.writeValueAsString(value));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize setting " + key, e);
        }
    }

    private static int indexOfPreset(List<LocationPresetDto> presets, String id) {
        for (int i = 0; i < presets.size(); i++) {
            if (id.equals(presets.get(i).id())) return i;
        }
        return -1;
    }

    private void saveFocalPoint(@Nullable Float x, @Nullable Float y, String focalXKey, String focalYKey) {
        if (x != null && y != null) {
            save(focalXKey, String.valueOf(x));
            save(focalYKey, String.valueOf(y));
        } else {
            siteSettingsRepository.deleteById(focalXKey);
            siteSettingsRepository.deleteById(focalYKey);
        }
    }

    @Nullable
    private static Float parseFloat(@Nullable String value) {
        if (value == null) return null;
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void deleteExistingFileIfPresent(String filenameKey) {
        siteSettingsRepository.findById(filenameKey)
                .map(SiteSetting::getValue)
                .ifPresent(filename -> {
                    try {
                        fileStorageService.delete(filename, FOLDER);
                    } catch (IOException e) {
                        logger.warn("Failed to delete hero image file: {}", e.getMessage());
                    }
                });
    }

    private void save(String key, String value) {
        SiteSetting setting = siteSettingsRepository.findById(key)
                .orElse(new SiteSetting(key, null));
        setting.setValue(value);
        siteSettingsRepository.save(setting);
    }
}
