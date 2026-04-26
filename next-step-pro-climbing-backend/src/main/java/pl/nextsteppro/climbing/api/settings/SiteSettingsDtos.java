package pl.nextsteppro.climbing.api.settings;

import org.jspecify.annotations.Nullable;

public class SiteSettingsDtos {

    public record HeroImageDto(
            @Nullable String imageUrl,
            @Nullable Float focalPointX,
            @Nullable Float focalPointY
    ) {}

    public record SlotTemplateDto(String name, int maxParticipants) {}

    public record BadgeImageDto(@Nullable String imageUrl, @Nullable String linkUrl) {}
}
