package pl.nextsteppro.climbing.domain.settlement;

import java.time.LocalDate;
import java.util.UUID;

/** A session marked as covered by somebody's subscription, reduced to what decides its month. */
public record SubscriberSession(UUID payoutId, LocalDate day) {

    public LocalDate month() {
        return day.withDayOfMonth(1);
    }
}
