package pl.nextsteppro.climbing.api.ascent;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.climbingascent.AscentDiscipline;
import pl.nextsteppro.climbing.domain.climbingascent.AscentStyle;
import pl.nextsteppro.climbing.domain.climbingascent.AscentTerrain;
import pl.nextsteppro.climbing.domain.climbingascent.ClimbingAscent;
import pl.nextsteppro.climbing.domain.climbingascent.ClimbingGrade;
import pl.nextsteppro.climbing.domain.climbingascent.GradeScale;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * DTOs for the climbing logbook. A separate file from {@code TrainingCalendarDtos} but the same
 * package, so the records stay package-private without pushing that file past 700 lines.
 */
final class AscentDtos {
    private AscentDtos() {}
}

@Schema(description = "One completed ascent to record. The terrain decides which fields apply: "
        + "rock entries carry a discipline, attempts and a star rating; mountain entries carry a "
        + "season, length, pitches, duration, partners and what the author led. Sending a field "
        + "belonging to the other terrain is a 400 rather than a silently ignored value.")
record SaveAscentRequest(
        @Schema(description = "ROCK (default when omitted, for older clients) or MOUNTAIN")
        @Nullable AscentTerrain terrain,
        @NotNull LocalDate climbedOn,
        @Schema(description = "Rock only — the mountains use the season instead.")
        @Nullable AscentDiscipline discipline,
        @Schema(description = "Unified grade. French everywhere except bouldering, mountains included.")
        @NotNull ClimbingGrade grade,
        @NotNull AscentStyle style,
        @NotBlank @Size(max = ClimbingAscent.MAX_PLACE_LENGTH) String area,
        @Schema(description = "The crag on rock, the summit in the mountains — the same slot either way.")
        @NotBlank @Size(max = ClimbingAscent.MAX_PLACE_LENGTH) String crag,
        @NotBlank @Size(max = ClimbingAscent.MAX_ROUTE_NAME_LENGTH) String routeName,
        @Schema(description = "Attempts including the successful one. Forced to 1 for OS and FLASH. Rock only.")
        @Nullable @Min(ClimbingAscent.MIN_ATTEMPTS) @Max(ClimbingAscent.MAX_ATTEMPTS) Integer attempts,
        @Schema(description = "Rock only.")
        @Nullable @Min(ClimbingAscent.MIN_STARS) @Max(ClimbingAscent.MAX_STARS) Integer qualityStars,
        @Nullable @Size(max = ClimbingAscent.MAX_COMMENT_LENGTH) String comment,
        // ---- mountain only ----
        @Schema(description = "Mountains: true = winter ascent. Required for MOUNTAIN, refused for ROCK.")
        @Nullable Boolean winter,
        @Schema(description = "The guidebook's own grade as free text: V, UIAA VI, WI4.")
        @Nullable @Size(max = ClimbingAscent.MAX_ORIGINAL_GRADE_LENGTH) String originalGrade,
        @Nullable @Min(1) @Max(ClimbingAscent.MAX_LENGTH_METERS) Integer lengthMeters,
        @Nullable @Min(1) @Max(ClimbingAscent.MAX_PITCHES) Integer pitches,
        @Schema(description = "Time on the route, in minutes.")
        @Nullable @Min(1) @Max(ClimbingAscent.MAX_DURATION_MINUTES) Integer durationMinutes,
        @Schema(description = "Hardest pitch the author led — same French axis as the route.")
        @Nullable ClimbingGrade ledGrade,
        @Nullable @Min(0) @Max(ClimbingAscent.MAX_PITCHES) Integer ledPitches,
        @Nullable @Size(max = ClimbingAscent.MAX_PARTNERS_LENGTH) String partners) {

    /** Older clients (and the rock form) may omit the terrain entirely. */
    AscentTerrain terrainOrRock() {
        return terrain != null ? terrain : AscentTerrain.ROCK;
    }
}

@Schema(description = "One logged ascent. Mountain-only fields are null on rock entries and vice versa.")
record AscentDto(
        UUID id,
        String terrain,
        LocalDate climbedOn,
        @Nullable String discipline,
        String gradeScale,
        @Schema(description = "Enum constant, e.g. FR_7A_PLUS — the value to send back on update.")
        String grade,
        @Schema(description = "What the climber reads on the topo, e.g. 7a+. Never derived on the client.")
        String gradeLabel,
        @Schema(description = "Ordering within gradeScale only. Sorting the table by difficulty uses this.")
        int gradeRank,
        String style,
        String area,
        String crag,
        String routeName,
        @Nullable Integer attempts,
        @Nullable Integer qualityStars,
        @Nullable String comment,
        @Nullable Boolean winter,
        @Nullable String originalGrade,
        @Nullable Integer lengthMeters,
        @Nullable Integer pitches,
        @Nullable Integer durationMinutes,
        @Nullable String ledGrade,
        @Nullable String ledGradeLabel,
        @Nullable Integer ledGradeRank,
        @Nullable Integer ledPitches,
        @Nullable String partners,
        Instant createdAt) {
}

@Schema(description = "One area the athlete has climbed in, with the crags they logged there.")
record PlaceSuggestionDto(String area, List<String> crags) {
}

@Schema(description = "A slice of the logbook plus everything the filters and the form need.")
record AscentLogDto(
        List<AscentDto> entries,
        @Schema(description = "Years the athlete has ascents in, newest first.")
        List<Integer> availableYears,
        @Schema(description = "Which year these entries cover; null means all years.")
        @Nullable Integer selectedYear,
        @Schema(description = "Ascents across every year — tells an empty year apart from an empty logbook.")
        long totalCount,
        @Schema(description = "Autocomplete source, computed across all years so an old crag still suggests.")
        List<PlaceSuggestionDto> places) {
}

@Schema(description = "One rung of the pyramid: how many ascents at this grade, split by style.")
record PyramidRowDto(String grade, String gradeLabel, int rank, Map<String, Integer> byStyle, int total) {
}

@Schema(description = "The hardest ascent in one style — with the route, because a grade alone is not a memory.")
record BestAscentDto(String grade, String gradeLabel, int rank, String routeName, String crag,
                     LocalDate climbedOn) {
}

@Schema(description = "Best grade of one year. Always all-time: a progression shown for a single year is not a progression.")
record GradeProgressPointDto(int year,
                             @Nullable String bestGradeLabel, @Nullable Integer bestRank,
                             @Nullable String bestOnsightLabel, @Nullable Integer bestOnsightRank) {
}

@Schema(description = "Statistics for one discipline. Grades are only ever compared inside this block.")
record AscentDisciplineStatsDto(
        String discipline,
        String gradeScale,
        int ascentCount,
        List<PyramidRowDto> pyramid,
        @Schema(description = "Style name -> the hardest ascent in it. Styles with no ascents are absent.")
        Map<String, BestAscentDto> hardestByStyle,
        Map<String, Integer> styleDistribution,
        @Nullable Double onsightRatePercent,
        List<GradeProgressPointDto> progressionByYear) {
}

record AreaCountDto(String area, int ascentCount) {
}

@Schema(description = "Figures that only mountain ascents have. Null on a rock logbook.")
record MountainStatsDto(
        int summerCount,
        int winterCount,
        @Schema(description = "Total metres of route climbed, over entries that carry a length.")
        int totalMeters,
        int entriesWithLength,
        @Schema(description = "Total pitches, over entries that carry a pitch count.")
        int totalPitches,
        int entriesWithPitches,
        @Schema(description = "Total time on route, in minutes, over entries that carry a duration.")
        int totalMinutes,
        int entriesWithDuration,
        @Schema(description = "Distinct summits reached, grouped on the normalized key.")
        int summitCount,
        @Schema(description = "Pyramid of the routes themselves — the mountains have no discipline "
                + "blocks, so without this there would be nowhere to see what level is being climbed.")
        List<PyramidRowDto> pyramid,
        Map<String, BestAscentDto> hardestByStyle,
        @Schema(description = "Pyramid of what the author LED — a different question from what the route was graded.")
        List<PyramidRowDto> leadPyramid,
        @Nullable BestAscentDto hardestLed,
        int ledPitchesTotal) {
}

@Schema(description = "Logbook statistics. Per-discipline blocks never share a grade axis; the "
        + "totals above them count places and attempts, which are comparable across all of them.")
record AscentStatsDto(
        @Nullable Integer selectedYear,
        int totalAscents,
        @Nullable LocalDate firstAscentDate,
        @Schema(description = "Distinct areas, grouped on the normalized key so one crag spelled two ways counts once.")
        int areaCount,
        @Schema(description = "Distinct crags, grouped the same way. Counted globally, not per area.")
        int cragCount,
        @Schema(description = "Mean attempts over redpoints that carry a count; null when none do.")
        @Nullable Double avgAttemptsToRedpoint,
        @Schema(description = "How many redpoints that mean is built from — the field is optional, "
                + "so the figure is meaningless without its denominator.")
        int redpointsWithAttempts,
        @Nullable Double avgQualityStars,
        List<AreaCountDto> topAreas,
        @Schema(description = "Only disciplines with at least one ascent, busiest first. Empty for mountains.")
        List<AscentDisciplineStatsDto> disciplines,
        @Schema(description = "Present only on a mountain logbook.")
        @Nullable MountainStatsDto mountain) {
}

@Schema(description = "One entry of the public recent-ascents list. Only climbers who have not "
        + "switched off public visibility appear here, and only what is on this record is shown — "
        + "no comment, no attempts, no rating.")
record PublicAscentDto(
        UUID id,
        @Schema(description = "Full name of the climber, as shown on the site.")
        String climberName,
        LocalDate climbedOn,
        @Schema(description = "ROCK or MOUNTAIN — the list mixes both and labels each entry.")
        String terrain,
        @Nullable String discipline,
        String gradeScale,
        String gradeLabel,
        int gradeRank,
        String style,
        String area,
        String crag,
        String routeName) {
}

record GradeOptionDto(String value, String label, int rank) {
}

record DisciplineOptionDto(String value, String gradeScale, List<String> styles) {
}

@Schema(description = "The catalogue the form renders: which scale and styles each discipline "
        + "allows, plus the styles the mountains allow. Served so the front never hardcodes a list.")
record AscentOptionsDto(
        List<DisciplineOptionDto> disciplines,
        @Schema(description = "Styles for mountain entries — no toprope, and no discipline to hang them off.")
        List<String> mountainStyles,
        Map<String, List<GradeOptionDto>> gradesByScale) {

    static AscentOptionsDto current() {
        List<DisciplineOptionDto> disciplines = Arrays.stream(AscentDiscipline.values())
                .map(discipline -> new DisciplineOptionDto(
                        discipline.name(),
                        discipline.scale().name(),
                        discipline.allowedStyles().stream()
                                // Cleanest first: the form should read OS, FLASH, RP... not enum order
                                .sorted(Comparator.comparingInt(AscentStyle::purity).reversed())
                                .map(AscentStyle::name)
                                .toList()))
                .toList();

        List<String> mountainStyles = AscentDiscipline.MOUNTAIN_STYLES.stream()
                .sorted(Comparator.comparingInt(AscentStyle::purity).reversed())
                .map(AscentStyle::name)
                .toList();

        Map<String, List<GradeOptionDto>> grades = Arrays.stream(GradeScale.values())
                .collect(Collectors.toMap(
                        Enum::name,
                        scale -> ClimbingGrade.of(scale).stream()
                                .map(grade -> new GradeOptionDto(grade.name(), grade.label(), grade.rank()))
                                .toList()));

        return new AscentOptionsDto(disciplines, mountainStyles, grades);
    }
}
