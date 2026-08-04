package pl.nextsteppro.climbing.domain.personaltraining;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Lightweight projection for athlete statistics: one personal training reduced to the fields the stats need. */
public record TrainingStatsRow(
    // Carried so one query still serves both: tasks are counted apart from every training number,
    // and a second query for them would double the cost of a page that deliberately has no cache.
    TrainingKind kind,
    LocalDate date,
    // Null for untimed ("all-day") trainings — missed derivation then uses end-of-day.
    @Nullable LocalTime endTime,
    @Nullable Instant completedAt,
    @Nullable Integer rpe
) {
    public boolean isCompleted() {
        return completedAt != null;
    }
}
