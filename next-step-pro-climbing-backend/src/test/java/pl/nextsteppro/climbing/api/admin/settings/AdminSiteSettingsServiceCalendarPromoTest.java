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
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.CalendarPromoSectionDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.LocationActiveStateDto;
import pl.nextsteppro.climbing.domain.settings.SiteSetting;
import pl.nextsteppro.climbing.domain.settings.SiteSettingsRepository;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for the calendar-promo template logic in AdminSiteSettingsService:
 * preset CRUD, active-preset state, and section resolution.
 *
 * The SiteSettingsRepository is backed by an in-memory map so the JSON
 * round-trip (serialize on save, deserialize on read) is exercised for real.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminSiteSettingsServiceCalendarPromoTest {

    @Mock
    private SiteSettingsRepository siteSettingsRepository;
    @Mock
    private FileStorageService fileStorageService;

    private AdminSiteSettingsService service;
    private Map<String, SiteSetting> store;

    private static final String BASE_URL = "https://nextsteppro.pl";

    @BeforeEach
    void setUp() {
        store = new HashMap<>();
        when(siteSettingsRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.<String>getArgument(0))));
        when(siteSettingsRepository.save(any(SiteSetting.class)))
                .thenAnswer(inv -> {
                    SiteSetting s = inv.getArgument(0);
                    store.put(s.getKey(), s);
                    return s;
                });
        doAnswer(inv -> {
            store.remove(inv.<String>getArgument(0));
            return null;
        }).when(siteSettingsRepository).deleteById(anyString());

        service = new AdminSiteSettingsService(siteSettingsRepository, fileStorageService, BASE_URL);
    }

    private static CalendarPromoPresetDto preset(String id, String name) {
        Map<String, CalendarPromoContentDto> translations = Map.of(
                "pl", new CalendarPromoContentDto("🔥 PROMOCJA", "Lato " + name, "Opis " + name, "Zapisz się", "/kursy")
        );
        return new CalendarPromoPresetDto(id, name, translations);
    }

    @Test
    void shouldReturnDisabledSectionWhenNoActivePreset() {
        // When
        CalendarPromoSectionDto section = service.getCalendarPromoSection();

        // Then
        assertFalse(section.enabled());
        assertTrue(section.translations().isEmpty());
    }

    @Test
    void shouldReturnEmptyPresetsWhenNothingSaved() {
        assertTrue(service.getCalendarPromoPresets().isEmpty());
        assertNull(service.getCalendarPromoActiveState().activePresetId());
    }

    @Test
    void shouldAssignIdWhenSavingNewPreset() {
        // When
        CalendarPromoPresetDto saved = service.saveCalendarPromoPreset(preset(null, "A"));

        // Then
        assertNotNull(saved.id());
        assertFalse(saved.id().isBlank());
        List<CalendarPromoPresetDto> all = service.getCalendarPromoPresets();
        assertEquals(1, all.size());
        assertEquals("A", all.getFirst().name());
    }

    @Test
    void shouldUpdateExistingPresetByIdWithoutDuplicating() {
        // Given
        CalendarPromoPresetDto saved = service.saveCalendarPromoPreset(preset(null, "A"));

        // When — re-save same id with a new name
        service.saveCalendarPromoPreset(new CalendarPromoPresetDto(saved.id(), "A-renamed", saved.translations()));

        // Then — still one preset, name updated
        List<CalendarPromoPresetDto> all = service.getCalendarPromoPresets();
        assertEquals(1, all.size());
        assertEquals("A-renamed", all.getFirst().name());
        assertEquals(saved.id(), all.getFirst().id());
    }

    @Test
    void shouldExposeActivePresetContentInSection() {
        // Given
        CalendarPromoPresetDto saved = service.saveCalendarPromoPreset(preset(null, "Lato"));

        // When
        service.setCalendarPromoActivePreset(saved.id());
        CalendarPromoSectionDto section = service.getCalendarPromoSection();

        // Then
        assertTrue(section.enabled());
        assertEquals("Lato Lato", section.translations().get("pl").title());
    }

    @Test
    void shouldReturnDisabledWhenActiveIdMissingFromPresets() {
        // Given — active points at a non-existent preset
        service.setCalendarPromoActivePreset("ghost-id");

        // When
        CalendarPromoSectionDto section = service.getCalendarPromoSection();

        // Then
        assertFalse(section.enabled());
        assertTrue(section.translations().isEmpty());
    }

    @Test
    void shouldClearActiveStateWhenDeletingActivePreset() {
        // Given
        CalendarPromoPresetDto saved = service.saveCalendarPromoPreset(preset(null, "A"));
        service.setCalendarPromoActivePreset(saved.id());

        // When
        service.deleteCalendarPromoPreset(saved.id());

        // Then — preset gone, active cleared, section disabled
        assertTrue(service.getCalendarPromoPresets().isEmpty());
        assertNull(service.getCalendarPromoActiveState().activePresetId());
        assertFalse(service.getCalendarPromoSection().enabled());
    }

    @Test
    void shouldKeepActiveStateWhenDeletingDifferentPreset() {
        // Given — two presets, A is active
        CalendarPromoPresetDto a = service.saveCalendarPromoPreset(preset(null, "A"));
        CalendarPromoPresetDto b = service.saveCalendarPromoPreset(preset(null, "B"));
        service.setCalendarPromoActivePreset(a.id());

        // When — delete the inactive one
        service.deleteCalendarPromoPreset(b.id());

        // Then — A still active and shown
        LocationActiveStateDto active = service.getCalendarPromoActiveState();
        assertEquals(a.id(), active.activePresetId());
        assertTrue(service.getCalendarPromoSection().enabled());
        assertEquals(1, service.getCalendarPromoPresets().size());
    }
}
