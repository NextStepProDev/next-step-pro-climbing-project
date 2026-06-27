package pl.nextsteppro.climbing.api.settings;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class SiteSettingsDtos {

    public record HeroImageDto(
            @Nullable String imageUrl,
            @Nullable Float focalPointX,
            @Nullable Float focalPointY
    ) {}

    public record SlotTemplateDto(String name, int maxParticipants) {}

    public record BadgeImageDto(@Nullable String imageUrl, @Nullable String linkUrl) {}

    /**
     * Treść sekcji "Gdzie teraz szkolę" dla jednego języka.
     * Tytuł NIE jest tu trzymany — jest stały i tłumaczony we froncie (i18n).
     * Edytowalne: badge (np. "Obecnie w Andaluzji..."), podtytuł i lista miejsc.
     */
    public record LocationContentDto(
            String badge,
            String subtitle,
            List<String> locations
    ) {}

    /** Aktywna (wyświetlana) konfiguracja sekcji: widoczność + treść per język (pl/en/es). */
    public record LocationSectionDto(
            boolean enabled,
            Map<String, LocationContentDto> translations
    ) {}

    /** Zapisany szablon całej konfiguracji (wszystkie języki), do ponownego użycia. */
    public record LocationPresetDto(
            @Nullable String id,
            String name,
            Map<String, LocationContentDto> translations
    ) {}

    /** Który szablon jest aktualnie na stronie (null = sekcja niepokazywana). */
    public record LocationActiveStateDto(
            @Nullable String activePresetId
    ) {}

    /**
     * Treść promocji nad kalendarzem dla jednego języka.
     * Obowiązkowe: title + description. Opcjonalne: badge (plakietka u góry)
     * oraz przycisk CTA (ctaLabel + ctaUrl) — gdy oba puste, przycisku nie ma.
     */
    public record CalendarPromoContentDto(
            String badge,
            String title,
            String description,
            String ctaLabel,
            String ctaUrl
    ) {}

    /** Aktywna (wyświetlana) promocja kalendarza: widoczność + treść per język. */
    public record CalendarPromoSectionDto(
            boolean enabled,
            Map<String, CalendarPromoContentDto> translations
    ) {}

    /** Zapisany szablon promocji kalendarza (wszystkie języki), do ponownego użycia. */
    public record CalendarPromoPresetDto(
            @Nullable String id,
            String name,
            Map<String, CalendarPromoContentDto> translations
    ) {}

    public record HomeSettingsDto(
            HeroImageDto hero,
            HeroImageDto heroMobile,
            BadgeImageDto badge,
            BadgeImageDto badgeLeft,
            LocationSectionDto location
    ) {}
}
