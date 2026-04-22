package pl.nextsteppro.climbing.api.admin.settings;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.HeroImageDto;
import pl.nextsteppro.climbing.domain.settings.SiteSetting;
import pl.nextsteppro.climbing.domain.settings.SiteSettingsRepository;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.io.IOException;

@Service
@Transactional
public class AdminSiteSettingsService {

    private static final Logger logger = LoggerFactory.getLogger(AdminSiteSettingsService.class);
    private static final String FOLDER = "site";
    private static final String KEY_IMAGE_URL = "hero_image_url";
    private static final String KEY_IMAGE_FILENAME = "hero_image_filename";
    private static final String KEY_FOCAL_POINT_X = "hero_focal_point_x";
    private static final String KEY_FOCAL_POINT_Y = "hero_focal_point_y";

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

    @Cacheable("siteSettings")
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
