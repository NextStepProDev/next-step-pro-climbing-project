package pl.nextsteppro.climbing.api.ascent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.nextsteppro.climbing.domain.climbingascent.AscentDiscipline;
import pl.nextsteppro.climbing.domain.climbingascent.AscentStatsRow;
import pl.nextsteppro.climbing.domain.climbingascent.AscentStyle;
import pl.nextsteppro.climbing.domain.climbingascent.ClimbingGrade;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The statistics are pure given the rows, so this drives {@code buildStats} directly with a
 * hand-built logbook — no database, no clock, no mocks.
 */
class AscentStatsServiceTest {

    private final AscentStatsService service = new AscentStatsService(null, null);

    private static AscentStatsRow row(LocalDate day, AscentDiscipline discipline,
                                      ClimbingGrade grade, AscentStyle style) {
        return row(day, discipline, grade, style, "Jura Północna", "jura polnocna", "Droga", null);
    }

    private static AscentStatsRow row(LocalDate day, AscentDiscipline discipline, ClimbingGrade grade,
                                      AscentStyle style, String area, String areaKey,
                                      String routeName, Integer stars) {
        return new AscentStatsRow(discipline, grade, style, day, areaKey, area, "skala", "Skała",
            routeName, null, stars, null, null, null, null, null, null);
    }

    private static AscentStatsRow withAttempts(AscentStyle style, Integer attempts) {
        return new AscentStatsRow(AscentDiscipline.SPORT, ClimbingGrade.FR_7A, style,
            LocalDate.of(2026, 5, 1), "jura", "Jura", "skala", "Skała", "Droga", attempts, null,
            null, null, null, null, null, null);
    }

    private static AscentStatsRow tradWithAttempts(AscentStyle style, Integer attempts) {
        return new AscentStatsRow(AscentDiscipline.TRAD, ClimbingGrade.FR_7A, style,
            LocalDate.of(2026, 5, 1), "jura", "Jura", "skala", "Skała", "Droga", attempts, null,
            null, null, null, null, null, null);
    }

    private static AscentStatsRow atPlace(String areaKey, String cragKey) {
        return new AscentStatsRow(AscentDiscipline.SPORT, ClimbingGrade.FR_7A, AscentStyle.RP,
            LocalDate.of(2026, 5, 1), areaKey, areaKey, cragKey, cragKey, "Droga", null, null,
            null, null, null, null, null, null);
    }

    private static Optional<AscentDisciplineStatsDto> block(AscentStatsDto stats, AscentDiscipline d) {
        return stats.disciplines().stream().filter(b -> b.discipline().equals(d.name())).findFirst();
    }

    @Test
    @DisplayName("a boulder never lands in the route pyramid, however close the labels look")
    void shouldKeepTheScalesApart() {
        List<AscentStatsRow> rows = List.of(
            row(LocalDate.of(2026, 5, 1), AscentDiscipline.SPORT, ClimbingGrade.FR_7A, AscentStyle.RP),
            row(LocalDate.of(2026, 5, 2), AscentDiscipline.BOULDER, ClimbingGrade.FB_7A, AscentStyle.RP));

        AscentStatsDto stats = service.buildStats(rows, 2026);

        AscentDisciplineStatsDto sport = block(stats, AscentDiscipline.SPORT).orElseThrow();
        AscentDisciplineStatsDto boulder = block(stats, AscentDiscipline.BOULDER).orElseThrow();
        assertThat(sport.ascentCount()).isEqualTo(1);
        assertThat(boulder.ascentCount()).isEqualTo(1);
        assertThat(sport.pyramid()).singleElement()
            .satisfies(rung -> assertThat(rung.grade()).isEqualTo("FR_7A"));
        assertThat(boulder.pyramid()).singleElement()
            .satisfies(rung -> assertThat(rung.grade()).isEqualTo("FB_7A"));
        assertThat(sport.gradeScale()).isNotEqualTo(boulder.gradeScale());
    }

    @Test
    @DisplayName("trad gets its own block even though it shares sport's scale")
    void shouldGiveTradItsOwnPyramid() {
        List<AscentStatsRow> rows = List.of(
            row(LocalDate.of(2026, 5, 1), AscentDiscipline.SPORT, ClimbingGrade.FR_7A, AscentStyle.RP),
            row(LocalDate.of(2026, 5, 2), AscentDiscipline.TRAD, ClimbingGrade.FR_6A, AscentStyle.OS_GU));

        AscentStatsDto stats = service.buildStats(rows, 2026);

        assertThat(stats.disciplines()).hasSize(2);
        assertThat(block(stats, AscentDiscipline.TRAD).orElseThrow().ascentCount()).isEqualTo(1);
        assertThat(block(stats, AscentDiscipline.SPORT).orElseThrow().pyramid()).hasSize(1);
    }

    @Test
    void shouldNotEmitABlockForADisciplineWithNoAscents() {
        List<AscentStatsRow> rows = List.of(
            row(LocalDate.of(2026, 5, 1), AscentDiscipline.BOULDER, ClimbingGrade.FB_6A, AscentStyle.FLASH));

        AscentStatsDto stats = service.buildStats(rows, 2026);

        assertThat(stats.disciplines()).hasSize(1);
        assertThat(block(stats, AscentDiscipline.SPORT)).isEmpty();
    }

    @Test
    void shouldOrderThePyramidHardestFirstAndSplitItByStyle() {
        List<AscentStatsRow> rows = List.of(
            row(LocalDate.of(2026, 5, 1), AscentDiscipline.SPORT, ClimbingGrade.FR_6A, AscentStyle.OS),
            row(LocalDate.of(2026, 5, 2), AscentDiscipline.SPORT, ClimbingGrade.FR_6A, AscentStyle.RP),
            row(LocalDate.of(2026, 5, 3), AscentDiscipline.SPORT, ClimbingGrade.FR_7A, AscentStyle.RP));

        List<PyramidRowDto> pyramid = block(service.buildStats(rows, 2026), AscentDiscipline.SPORT)
            .orElseThrow().pyramid();

        assertThat(pyramid).extracting(PyramidRowDto::gradeLabel).containsExactly("7a", "6a");
        assertThat(pyramid.getFirst().total()).isEqualTo(1);
        assertThat(pyramid.get(1).total()).isEqualTo(2);
        assertThat(pyramid.get(1).byStyle()).containsEntry("OS", 1).containsEntry("RP", 1);
    }

    @Test
    @DisplayName("hardest is reported per style — an onsight and a redpoint are different achievements")
    void shouldReportTheHardestAscentPerStyleWithItsRoute() {
        List<AscentStatsRow> rows = List.of(
            row(LocalDate.of(2026, 5, 1), AscentDiscipline.SPORT, ClimbingGrade.FR_7C, AscentStyle.RP,
                "Jura", "jura", "Wielkie Ciśnienie", null),
            row(LocalDate.of(2026, 5, 2), AscentDiscipline.SPORT, ClimbingGrade.FR_6C, AscentStyle.OS,
                "Jura", "jura", "Pajęczyna", null));

        var hardest = block(service.buildStats(rows, 2026), AscentDiscipline.SPORT)
            .orElseThrow().hardestByStyle();

        assertThat(hardest.get("RP").gradeLabel()).isEqualTo("7c");
        assertThat(hardest.get("RP").routeName()).isEqualTo("Wielkie Ciśnienie");
        assertThat(hardest.get("OS").gradeLabel()).isEqualTo("6c");
        assertThat(hardest).doesNotContainKey("FLASH");
    }

    @Test
    @DisplayName("the progression is all-time even when one year is selected")
    void shouldKeepTheProgressionAllTimeRegardlessOfTheYearFilter() {
        List<AscentStatsRow> rows = List.of(
            row(LocalDate.of(2024, 5, 1), AscentDiscipline.SPORT, ClimbingGrade.FR_6A, AscentStyle.RP),
            row(LocalDate.of(2025, 5, 1), AscentDiscipline.SPORT, ClimbingGrade.FR_6C, AscentStyle.OS),
            row(LocalDate.of(2026, 5, 1), AscentDiscipline.SPORT, ClimbingGrade.FR_7A, AscentStyle.RP));

        AscentDisciplineStatsDto sport = block(service.buildStats(rows, 2026), AscentDiscipline.SPORT)
            .orElseThrow();

        assertThat(sport.ascentCount()).as("the pyramid is filtered").isEqualTo(1);
        assertThat(sport.progressionByYear()).as("the progression is not").hasSize(3);
        assertThat(sport.progressionByYear()).extracting(GradeProgressPointDto::year)
            .containsExactly(2024, 2025, 2026);
        assertThat(sport.progressionByYear().get(1).bestOnsightLabel()).isEqualTo("6c");
        assertThat(sport.progressionByYear().get(2).bestOnsightLabel())
            .as("no onsight that year").isNull();
    }

    @Test
    void shouldReportTheFirstAscentDateAndTheTotal() {
        LocalDate day = LocalDate.of(2026, 5, 1);
        List<AscentStatsRow> rows = List.of(
            row(day.plusDays(1), AscentDiscipline.SPORT, ClimbingGrade.FR_6B, AscentStyle.RP),
            row(day, AscentDiscipline.SPORT, ClimbingGrade.FR_6A, AscentStyle.RP),
            row(day.plusDays(3), AscentDiscipline.BOULDER, ClimbingGrade.FB_6A, AscentStyle.FLASH));

        AscentStatsDto stats = service.buildStats(rows, 2026);

        assertThat(stats.totalAscents()).isEqualTo(3);
        assertThat(stats.firstAscentDate()).as("earliest, not first in the list").isEqualTo(day);
    }

    @Test
    @DisplayName("places are counted on the normalized key, and crags globally rather than per area")
    void shouldCountDistinctAreasAndCrags() {
        List<AscentStatsRow> rows = List.of(
            atPlace("jura polnocna", "koloczek"),
            atPlace("jura polnocna", "koloczek"),
            atPlace("jura polnocna", "rzedkowice"),
            atPlace("sokoliki", "sukiennice"));

        AscentStatsDto stats = service.buildStats(rows, 2026);

        assertThat(stats.areaCount()).isEqualTo(2);
        assertThat(stats.cragCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("attempts average over redpoints only — OS and FLASH are 1 by definition")
    void shouldAverageAttemptsOverCountedRedpointsOnly() {
        List<AscentStatsRow> rows = List.of(
            withAttempts(AscentStyle.RP, 2),
            withAttempts(AscentStyle.RP, 5),
            withAttempts(AscentStyle.RP, null),
            withAttempts(AscentStyle.OS, 1),
            withAttempts(AscentStyle.TR, 3));

        AscentStatsDto stats = service.buildStats(rows, 2026);

        assertThat(stats.avgAttemptsToRedpoint()).isEqualTo(3.5);
        assertThat(stats.redpointsWithAttempts()).as("the denominator the tile shows").isEqualTo(2);
    }

    @Test
    void shouldLeaveTheAttemptsAverageNullWhenNoRedpointCarriesACount() {
        List<AscentStatsRow> rows = List.of(
            withAttempts(AscentStyle.RP, null),
            withAttempts(AscentStyle.OS, 1));

        AscentStatsDto stats = service.buildStats(rows, 2026);

        assertThat(stats.avgAttemptsToRedpoint()).isNull();
        assertThat(stats.redpointsWithAttempts()).isZero();
    }

    @Test
    @DisplayName("trad's worked sends count towards the attempts average — it has no RP to offer")
    void shouldAverageAttemptsOverTheTradDialectToo() {
        List<AscentStatsRow> rows = List.of(
            tradWithAttempts(AscentStyle.GU, 4),
            tradWithAttempts(AscentStyle.HP, 6),
            tradWithAttempts(AscentStyle.OS_GU, 1));

        AscentStatsDto stats = service.buildStats(rows, 2026);

        assertThat(stats.avgAttemptsToRedpoint()).isEqualTo(5.0);
        assertThat(stats.redpointsWithAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("a ground-up onsight is an onsight — trad would otherwise read a permanent 0%")
    void shouldCountGroundUpOnsightsInTheOnsightRate() {
        List<AscentStatsRow> rows = List.of(
            row(LocalDate.of(2026, 5, 1), AscentDiscipline.TRAD, ClimbingGrade.FR_6A, AscentStyle.OS_GU),
            row(LocalDate.of(2026, 5, 2), AscentDiscipline.TRAD, ClimbingGrade.FR_6A, AscentStyle.GU));

        assertThat(block(service.buildStats(rows, 2026), AscentDiscipline.TRAD)
            .orElseThrow().onsightRatePercent()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("two spellings of one crag are one crag")
    void shouldGroupAreasOnTheNormalizedKey() {
        List<AscentStatsRow> rows = List.of(
            row(LocalDate.of(2026, 5, 1), AscentDiscipline.SPORT, ClimbingGrade.FR_6A, AscentStyle.RP,
                "Jura Północna", "jura polnocna", "A", null),
            row(LocalDate.of(2026, 5, 2), AscentDiscipline.SPORT, ClimbingGrade.FR_6A, AscentStyle.RP,
                "jura polnocna", "jura polnocna", "B", null),
            row(LocalDate.of(2026, 5, 3), AscentDiscipline.SPORT, ClimbingGrade.FR_6A, AscentStyle.RP,
                "Sokoliki", "sokoliki", "C", null));

        List<AreaCountDto> areas = service.buildStats(rows, 2026).topAreas();

        assertThat(areas).hasSize(2);
        assertThat(areas.getFirst().ascentCount()).isEqualTo(2);
        assertThat(areas.getFirst().area()).as("the most recent spelling labels the group")
            .isEqualTo("jura polnocna");
    }

    @Test
    void shouldComputeTheOnsightRateWithinTheDisciplineOnly() {
        List<AscentStatsRow> rows = List.of(
            row(LocalDate.of(2026, 5, 1), AscentDiscipline.SPORT, ClimbingGrade.FR_6A, AscentStyle.OS),
            row(LocalDate.of(2026, 5, 2), AscentDiscipline.SPORT, ClimbingGrade.FR_6A, AscentStyle.RP),
            row(LocalDate.of(2026, 5, 3), AscentDiscipline.BOULDER, ClimbingGrade.FB_6A, AscentStyle.RP));

        assertThat(block(service.buildStats(rows, 2026), AscentDiscipline.SPORT)
            .orElseThrow().onsightRatePercent()).isEqualTo(50.0);
        assertThat(block(service.buildStats(rows, 2026), AscentDiscipline.BOULDER)
            .orElseThrow().onsightRatePercent()).isEqualTo(0.0);
    }

    @Test
    void shouldAverageOnlyTheRatedAscents() {
        List<AscentStatsRow> rows = List.of(
            row(LocalDate.of(2026, 5, 1), AscentDiscipline.SPORT, ClimbingGrade.FR_6A, AscentStyle.RP,
                "Jura", "jura", "A", 5),
            row(LocalDate.of(2026, 5, 2), AscentDiscipline.SPORT, ClimbingGrade.FR_6A, AscentStyle.RP,
                "Jura", "jura", "B", 2),
            row(LocalDate.of(2026, 5, 3), AscentDiscipline.SPORT, ClimbingGrade.FR_6A, AscentStyle.RP,
                "Jura", "jura", "C", null));

        assertThat(service.buildStats(rows, 2026).avgQualityStars()).isEqualTo(3.5);
    }

    @Test
    void shouldReturnAnEmptyShapeForAnEmptyLogbook() {
        AscentStatsDto stats = service.buildStats(List.of(), null);

        assertThat(stats.totalAscents()).isZero();
        assertThat(stats.areaCount()).isZero();
        assertThat(stats.cragCount()).isZero();
        assertThat(stats.disciplines()).isEmpty();
        assertThat(stats.firstAscentDate()).isNull();
        assertThat(stats.avgQualityStars()).isNull();
        assertThat(stats.avgAttemptsToRedpoint()).isNull();
        assertThat(stats.redpointsWithAttempts()).isZero();
    }

    @Test
    @DisplayName("the busiest discipline comes first, so a boulderer is not scrolling past sport")
    void shouldOrderBlocksByAscentCount() {
        List<AscentStatsRow> rows = List.of(
            row(LocalDate.of(2026, 5, 1), AscentDiscipline.SPORT, ClimbingGrade.FR_6A, AscentStyle.RP),
            row(LocalDate.of(2026, 5, 2), AscentDiscipline.BOULDER, ClimbingGrade.FB_6A, AscentStyle.RP),
            row(LocalDate.of(2026, 5, 3), AscentDiscipline.BOULDER, ClimbingGrade.FB_6B, AscentStyle.RP));

        assertThat(service.buildStats(rows, 2026).disciplines())
            .extracting(AscentDisciplineStatsDto::discipline)
            .containsExactly("BOULDER", "SPORT");
    }
}
