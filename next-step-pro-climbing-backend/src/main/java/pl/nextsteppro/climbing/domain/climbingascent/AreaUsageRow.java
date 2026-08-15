package pl.nextsteppro.climbing.domain.climbingascent;

import java.time.LocalDate;

/**
 * One (area, crag) pair the athlete has used before, with how often and how recently.
 *
 * <p>Feeds the form's autocomplete. Grouped by the normalized keys but carrying the spelling as
 * typed, so the suggestion list can show "Jura Północna" while still counting it together with
 * "jura polnocna"; when the same key has several spellings, the most recent one wins.
 */
public record AreaUsageRow(
        String areaKey,
        String area,
        String cragKey,
        String crag,
        long usageCount,
        LocalDate lastUsedOn) {
}
