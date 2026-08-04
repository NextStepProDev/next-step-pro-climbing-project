package pl.nextsteppro.climbing.domain.reservation;

import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.event.EventType;

import java.time.LocalDate;
import java.util.UUID;

/** Lightweight projection for athlete statistics: one attended reservation reduced to the fields the stats need.
 * {@code eventId}/{@code eventType}/{@code location} are null for standalone slots (no event behind them);
 * {@code rpe} is null when the athlete hasn't rated this reservation.
 *
 * <p>A multi-day event books one reservation PER DAY, so {@code eventId} is what tells "three days of
 * one trip" apart from "three trips" — see the location ranking in TrainingStatsService. */
public record ReservationStatsRow(
    LocalDate date,
    @Nullable UUID eventId,
    @Nullable EventType eventType,
    @Nullable String location,
    @Nullable Integer rpe
) {}
