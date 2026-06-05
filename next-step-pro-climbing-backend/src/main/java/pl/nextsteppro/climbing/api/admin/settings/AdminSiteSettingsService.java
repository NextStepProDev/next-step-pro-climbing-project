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
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.HeroImageDto;
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
    private static final String KEY_BADGE_IMAGE_URL = "badge_image_url";
    private static final String KEY_BADGE_LINK_URL = "badge_link_url";
    private static final String KEY_BADGE_LEFT_IMAGE_URL = "badge_left_image_url";
    private static final String KEY_BADGE_LEFT_LINK_URL = "badge_left_link_url";
    private static final String KEY_SLOT_TEMPLATES = "slot_templates";
    private static final String KEY_LOCATION_ACTIVE = "home_location_active";
    private static final String KEY_LOCATION_PRESETS = "home_location_presets";
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
        deleteExistingFileIfPresent();

        String filename = fileStorageService.store(file, FOLDER);
        String imageUrl = baseUrl + "/api/files/" + FOLDER + "/" + filename;

        save(KEY_IMAGE_URL, imageUrl);
        save(KEY_IMAGE_FILENAME, filename);
        saveFocalPoint(focalPointX, focalPointY);

        return new HeroImageDto(imageUrl, focalPointX, focalPointY);
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public HeroImageDto setHeroImageUrl(String url, @Nullable Float focalPointX, @Nullable Float focalPointY) {
        deleteExistingFileIfPresent();

        save(KEY_IMAGE_URL, url);
        siteSettingsRepository.deleteById(KEY_IMAGE_FILENAME);
        saveFocalPoint(focalPointX, focalPointY);

        return new HeroImageDto(url, focalPointX, focalPointY);
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public HeroImageDto setFocalPoint(float x, float y) {
        save(KEY_FOCAL_POINT_X, String.valueOf(x));
        save(KEY_FOCAL_POINT_Y, String.valueOf(y));

        String imageUrl = siteSettingsRepository.findById(KEY_IMAGE_URL)
                .map(SiteSetting::getValue)
                .orElse(null);
        return new HeroImageDto(imageUrl, x, y);
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public void deleteHeroImage() throws IOException {
        deleteExistingFileIfPresent();

        siteSettingsRepository.deleteById(KEY_IMAGE_URL);
        siteSettingsRepository.deleteById(KEY_IMAGE_FILENAME);
        siteSettingsRepository.deleteById(KEY_FOCAL_POINT_X);
        siteSettingsRepository.deleteById(KEY_FOCAL_POINT_Y);
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

    // === Sekcja "Gdzie teraz szkolę" (aktywna treść) ===

    @Cacheable(value = "siteSettings", key = "'homeLocation'")
    @Transactional(readOnly = true)
    public LocationSectionDto getLocationSection() {
        // Brak konfiguracji: sekcja widoczna, pusta mapa = front zrobi fallback do i18n.
        return readJson(KEY_LOCATION_ACTIVE, new TypeReference<LocationSectionDto>() {},
                new LocationSectionDto(true, Map.of()));
    }

    @CacheEvict(value = "siteSettings", allEntries = true)
    public LocationSectionDto saveLocationSection(LocationSectionDto section) {
        writeJson(KEY_LOCATION_ACTIVE, section);
        return section;
    }

    // === Presety sekcji (lista zapisanych wariantów, CRUD) ===

    @Transactional(readOnly = true)
    public List<LocationPresetDto> getLocationPresets() {
        return readJson(KEY_LOCATION_PRESETS, new TypeReference<List<LocationPresetDto>>() {}, List.of());
    }

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

    public void deleteLocationPreset(String id) {
        List<LocationPresetDto> presets = new ArrayList<>(getLocationPresets());
        presets.removeIf(p -> id.equals(p.id()));
        persistPresets(presets);
    }

    private void persistPresets(List<LocationPresetDto> presets) {
        writeJson(KEY_LOCATION_PRESETS, presets);
    }

    // Wspólne odczyt/zapis ustawień przechowywanych jako JSON w site_settings.
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

    private void saveFocalPoint(@Nullable Float x, @Nullable Float y) {
        if (x != null && y != null) {
            save(KEY_FOCAL_POINT_X, String.valueOf(x));
            save(KEY_FOCAL_POINT_Y, String.valueOf(y));
        } else {
            siteSettingsRepository.deleteById(KEY_FOCAL_POINT_X);
            siteSettingsRepository.deleteById(KEY_FOCAL_POINT_Y);
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

    private void deleteExistingFileIfPresent() {
        siteSettingsRepository.findById(KEY_IMAGE_FILENAME)
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
