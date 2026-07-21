package pl.nextsteppro.climbing.domain.reservation;

import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.event.EventType;

import java.time.LocalDate;

/** Lightweight projection for athlete statistics: one attended reservation reduced to the fields the stats need.
 * {@code eventType}/{@code location} are null for standalone slots (no event behind them);
 * {@code rpe} is null when the athlete hasn't rated this reservation. */
public record ReservationStatsRow(
    LocalDate date,
    @Nullable EventType eventType,
    @Nullable String location,
    @Nullable Integer rpe
) {}
