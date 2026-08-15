package pl.nextsteppro.climbing.domain.climbingascent;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Everything the statistics need from one ascent, and nothing else.
 *
 * <p>Loaded as a projection rather than as entities: the stats read every ascent the athlete ever
 * logged, and dragging the comment column (up to 2 kB a row) through that for a number nobody
 * computes from it is pure weight. Same reason as {@code TrainingStatsRow}.
 *
 * <p>{@code routeName} and {@code crag} are here despite not being aggregated — "hardest onsight"
 * is answered with the route, because "8a" without a name is a number, not a memory.
 */
public record AscentStatsRow(
        AscentDiscipline discipline,
        ClimbingGrade grade,
        AscentStyle style,
        LocalDate climbedOn,
        String areaKey,
        String area,
        String cragKey,
        String crag,
        String routeName,
        @Nullable Integer attempts,
        @Nullable Integer qualityStars,
        // ---- mountain only ----
        @Nullable Boolean winter,
        @Nullable Integer lengthMeters,
        @Nullable Integer pitches,
        @Nullable Integer durationMinutes,
        @Nullable ClimbingGrade ledGrade,
        @Nullable Integer ledPitches) {
}
