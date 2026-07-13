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
     * Content of the "Where I teach now" section for a single language.
     * The title is NOT stored here — it is fixed and translated on the frontend (i18n).
     * Editable: badge (e.g. "Currently in Andalusia..."), subtitle and place list.
     */
    public record LocationContentDto(
            String badge,
            String subtitle,
            List<String> locations
    ) {}

    /** Active (displayed) section configuration: visibility + content per language (pl/en/es). */
    public record LocationSectionDto(
            boolean enabled,
            Map<String, LocationContentDto> translations
    ) {}

    /** Saved template of the whole configuration (all languages), for reuse. */
    public record LocationPresetDto(
            @Nullable String id,
            String name,
            Map<String, LocationContentDto> translations
    ) {}

    /** Which template is currently live on the page (null = section hidden). */
    public record LocationActiveStateDto(
            @Nullable String activePresetId
    ) {}

    /**
     * Calendar promo content for a single language.
     * Required: title + description. Optional: badge (small tag at the top)
     * and a CTA button (ctaLabel + ctaUrl) — when both are empty, there is no button.
     */
    public record CalendarPromoContentDto(
            String badge,
            String title,
            String description,
            String ctaLabel,
            String ctaUrl
    ) {}

    /** Active (displayed) calendar promo: visibility + content per language. */
    public record CalendarPromoSectionDto(
            boolean enabled,
            Map<String, CalendarPromoContentDto> translations
    ) {}

    /** Saved calendar promo template (all languages), for reuse. */
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
