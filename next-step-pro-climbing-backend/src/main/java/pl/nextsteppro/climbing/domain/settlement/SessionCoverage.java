package pl.nextsteppro.climbing.domain.settlement;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Who settles a session in bulk, if anybody: an institution paying a lump for a month, a client
 * whose standing subscription covers it — or nobody, because the session is free. All three take it
 * out of per-participant pricing.
 *
 * <p>One projection rather than two lookups, so a caller cannot ask about a source, get nothing, and
 * conclude the session is unmarked while a subscription covers it.
 */
public record SessionCoverage(@Nullable UUID sourceId, @Nullable UUID userId, boolean free) {

    public boolean bySubscription() {
        return userId != null;
    }
}
