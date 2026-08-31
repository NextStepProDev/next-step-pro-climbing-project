package pl.nextsteppro.climbing.api.admin.settlement;

import java.util.Locale;
import java.util.Optional;

/**
 * Which calendar entry a settlement is attached to. Travels as a path segment
 * ({@code /api/admin/settlements/slot/{id}/user/{id}}), never into the database — the table keeps
 * two separate foreign keys instead of a discriminator, so a settlement dies with the slot or event
 * it prices (see V92).
 *
 * <p>There is no {@code RESERVATION} member and there must never be one: a multi-day event books
 * one reservation row per day, so pricing per reservation would charge a three-day course three
 * times.
 */
public enum SettlementTarget {
    SLOT,
    EVENT;

    /**
     * Parses the lower-case path segment. Bound by hand rather than by declaring
     * {@code @PathVariable SettlementTarget}: Spring matches enum constants by exact name, so the
     * URL would have to shout {@code /settlements/SLOT/…}. Returns empty rather than throwing, so
     * the caller can raise a translated message — the raw segment comes from the client and does
     * not belong in the response.
     */
    public static Optional<SettlementTarget> tryFrom(String segment) {
        try {
            return Optional.of(valueOf(segment.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
