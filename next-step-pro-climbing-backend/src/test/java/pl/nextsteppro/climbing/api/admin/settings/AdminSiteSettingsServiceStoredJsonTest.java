package pl.nextsteppro.climbing.api.admin.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.CalendarPromoContentDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.CalendarPromoPresetDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.LocationContentDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.LocationPresetDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.SlotTemplateDto;
import pl.nextsteppro.climbing.domain.settings.SiteSetting;
import pl.nextsteppro.climbing.domain.settings.SiteSettingsRepository;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Settings rows that already sit in production were written by Jackson 2. Since the move to
 * Jackson 3 (Spring Boot 4.2) the same service reads them with different defaults — properties
 * sorted alphabetically on write, nulls for primitives rejected on read, trailing tokens rejected.
 * readJson swallows a parse failure and returns the default, so a row the new mapper could not
 * read would not fail anything: the slot templates would just vanish from the admin form, and the
 * home page location and calendar promo would silently hide. These tests feed the service JSON in
 * the exact shape Jackson 2 stored (declaration order, explicit nulls) and expect the values back.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminSiteSettingsServiceStoredJsonTest {

    @Mock
    private SiteSettingsRepository siteSettingsRepository;
    @Mock
    private FileStorageService fileStorageService;

    private AdminSiteSettingsService service;
    private Map<String, SiteSetting> store;

    @BeforeEach
    void setUp() {
        store = new HashMap<>();
        when(siteSettingsRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.<String>getArgument(0))));
        service = new AdminSiteSettingsService(siteSettingsRepository, fileStorageService, "https://nextsteppro.pl");
    }

    private void stored(String key, String json) {
        store.put(key, new SiteSetting(key, json));
    }

    @Test
    void shouldReadSlotTemplatesStoredByJackson2() {
        // Given
        stored("slot_templates",
                "[{\"name\":\"Trening grupowy\",\"maxParticipants\":6},{\"name\":\"1:1\",\"maxParticipants\":1}]");

        // When
        List<SlotTemplateDto> templates = service.getSlotTemplates();

        // Then
        assertEquals(List.of(new SlotTemplateDto("Trening grupowy", 6), new SlotTemplateDto("1:1", 1)), templates);
    }

    @Test
    void shouldReadLocationPresetsAndActiveStateStoredByJackson2() {
        // Given
        stored("home_location_presets",
                "[{\"id\":\"p1\",\"name\":\"Andaluzja\",\"translations\":{\"pl\":"
                        + "{\"badge\":\"Obecnie w Andaluzji\",\"subtitle\":null,\"locations\":[\"El Chorro\",\"Granada\"]}}}]");
        stored("home_location_active", "{\"activePresetId\":\"p1\"}");

        // When
        List<LocationPresetDto> presets = service.getLocationPresets();

        // Then
        assertEquals(1, presets.size());
        LocationPresetDto preset = presets.getFirst();
        assertEquals("p1", preset.id());
        assertEquals("Andaluzja", preset.name());
        assertEquals(new LocationContentDto("Obecnie w Andaluzji", null, List.of("El Chorro", "Granada")),
                preset.translations().get("pl"));
        assertEquals("p1", service.getActiveState().activePresetId());
        assertTrue(service.getLocationSection().enabled());
    }

    @Test
    void shouldReadCalendarPromoPresetsStoredByJackson2() {
        // Given
        stored("calendar_promo_presets",
                "[{\"id\":\"c1\",\"name\":\"Zima\",\"translations\":{\"en\":{\"badge\":null,\"title\":\"Winter\","
                        + "\"description\":\"Book a session\",\"ctaLabel\":null,\"ctaUrl\":null}}}]");
        stored("calendar_promo_active", "{\"activePresetId\":null}");

        // When
        List<CalendarPromoPresetDto> presets = service.getCalendarPromoPresets();

        // Then
        assertEquals(1, presets.size());
        assertEquals(new CalendarPromoContentDto(null, "Winter", "Book a session", null, null),
                presets.getFirst().translations().get("en"));
        assertNull(service.getCalendarPromoActiveState().activePresetId());
        assertFalse(service.getCalendarPromoSection().enabled());
    }
}
