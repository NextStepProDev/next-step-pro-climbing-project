package pl.nextsteppro.climbing.domain.personaltraining;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Lightweight projection for athlete statistics: one personal training reduced to the fields the stats need. */
public record TrainingStatsRow(
    LocalDate date,
    LocalTime endTime,
    @Nullable Instant completedAt,
    @Nullable Integer rpe
) {
    public boolean isCompleted() {
        return completedAt != null;
    }
}
