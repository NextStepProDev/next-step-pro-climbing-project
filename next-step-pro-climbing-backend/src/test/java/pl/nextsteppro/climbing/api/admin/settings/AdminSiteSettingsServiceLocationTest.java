package pl.nextsteppro.climbing.api.admin.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.LocationActiveStateDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.LocationContentDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.LocationPresetDto;
import pl.nextsteppro.climbing.api.settings.SiteSettingsDtos.LocationSectionDto;
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
 * Tests for the "Where I am now" (home location) template logic in
 * AdminSiteSettingsService: preset CRUD, active-preset state, and section
 * resolution.
 *
 * The SiteSettingsRepository is backed by an in-memory map so the JSON
 * round-trip (serialize on save, deserialize on read) is exercised for real.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminSiteSettingsServiceLocationTest {

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

    private static LocationPresetDto preset(String id, String name) {
        Map<String, LocationContentDto> translations = Map.of(
                "pl", new LocationContentDto("Obecnie w " + name, "Prowadzę zajęcia w:", List.of("El Chorro", "Granada"))
        );
        return new LocationPresetDto(id, name, translations);
    }

    @Test
    void shouldReturnDisabledSectionWhenNoActivePreset() {
        // When
        LocationSectionDto section = service.getLocationSection();

        // Then
        assertFalse(section.enabled());
        assertTrue(section.translations().isEmpty());
    }

    @Test
    void shouldReturnEmptyPresetsWhenNothingSaved() {
        assertTrue(service.getLocationPresets().isEmpty());
        assertNull(service.getActiveState().activePresetId());
    }

    @Test
    void shouldAssignIdWhenSavingNewPreset() {
        // When
        LocationPresetDto saved = service.saveLocationPreset(preset(null, "Andaluzja"));

        // Then
        assertNotNull(saved.id());
        assertFalse(saved.id().isBlank());
        List<LocationPresetDto> all = service.getLocationPresets();
        assertEquals(1, all.size());
        assertEquals("Andaluzja", all.getFirst().name());
    }

    @Test
    void shouldUpdateExistingPresetByIdWithoutDuplicating() {
        // Given
        LocationPresetDto saved = service.saveLocationPreset(preset(null, "Andaluzja"));

        // When — re-save same id with a new name
        service.saveLocationPreset(new LocationPresetDto(saved.id(), "Andaluzja-renamed", saved.translations()));

        // Then — still one preset, name updated
        List<LocationPresetDto> all = service.getLocationPresets();
        assertEquals(1, all.size());
        assertEquals("Andaluzja-renamed", all.getFirst().name());
        assertEquals(saved.id(), all.getFirst().id());
    }

    @Test
    void shouldExposeActivePresetContentInSection() {
        // Given
        LocationPresetDto saved = service.saveLocationPreset(preset(null, "Andaluzja"));

        // When
        service.setActivePreset(saved.id());
        LocationSectionDto section = service.getLocationSection();

        // Then
        assertTrue(section.enabled());
        assertEquals("Obecnie w Andaluzja", section.translations().get("pl").badge());
        assertEquals(List.of("El Chorro", "Granada"), section.translations().get("pl").locations());
    }

    @Test
    void shouldReturnDisabledWhenActiveIdMissingFromPresets() {
        // Given — active points at a non-existent preset
        service.setActivePreset("ghost-id");

        // When
        LocationSectionDto section = service.getLocationSection();

        // Then
        assertFalse(section.enabled());
        assertTrue(section.translations().isEmpty());
    }

    @Test
    void shouldClearActiveStateWhenDeletingActivePreset() {
        // Given
        LocationPresetDto saved = service.saveLocationPreset(preset(null, "Andaluzja"));
        service.setActivePreset(saved.id());

        // When
        service.deleteLocationPreset(saved.id());

        // Then — preset gone, active cleared, section disabled
        assertTrue(service.getLocationPresets().isEmpty());
        assertNull(service.getActiveState().activePresetId());
        assertFalse(service.getLocationSection().enabled());
    }

    @Test
    void shouldKeepActiveStateWhenDeletingDifferentPreset() {
        // Given — two presets, A is active
        LocationPresetDto a = service.saveLocationPreset(preset(null, "Andaluzja"));
        LocationPresetDto b = service.saveLocationPreset(preset(null, "Tatry"));
        service.setActivePreset(a.id());

        // When — delete the inactive one
        service.deleteLocationPreset(b.id());

        // Then — A still active and shown
        LocationActiveStateDto active = service.getActiveState();
        assertEquals(a.id(), active.activePresetId());
        assertTrue(service.getLocationSection().enabled());
        assertEquals(1, service.getLocationPresets().size());
    }
}
