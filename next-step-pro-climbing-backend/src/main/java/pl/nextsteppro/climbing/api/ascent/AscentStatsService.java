package pl.nextsteppro.climbing.api.ascent;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.trainingcalendar.TrainingCalendarService;
import pl.nextsteppro.climbing.domain.climbingascent.AscentDiscipline;
import pl.nextsteppro.climbing.domain.climbingascent.AscentStatsRow;
import pl.nextsteppro.climbing.domain.climbingascent.AscentStyle;
import pl.nextsteppro.climbing.domain.climbingascent.AscentTerrain;
import pl.nextsteppro.climbing.domain.climbingascent.ClimbingAscentRepository;
import pl.nextsteppro.climbing.domain.climbingascent.ClimbingGrade;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Statistics over the climbing logbook.
 *
 * <p><b>Deliberately uncached.</b> Correcting a grade or deleting an entry has to move the numbers
 * in the same render — same reason as {@code TrainingStatsService}, and the same shape: one query
 * for the whole logbook, one pass in Java.
 *
 * <p><b>Grade axes never merge.</b> {@code 7a} on the French route scale and {@code 7A} on the
 * Font scale are three grades apart, so every number derived from difficulty — the pyramid, the
 * hardest ascent per style, the onsight rate, the progression — is computed inside one discipline
 * block. Only the figures whose unit is a place or an attempt (areas, crags, attempts per
 * redpoint, ratings) are shared across the whole logbook, because those are comparable no matter
 * what was climbed.
 *
 * <p>Trad gets its own block rather than sharing sport's pyramid: it shares the scale but not the
 * game, and the athlete asked for it separately.
 *
 * <p><b>There is no "days on rock" figure, and that is a decision.</b> Only completed ascents are
 * logged, so a day of failed attempts on a project, a rained-off day or a day spent belaying
 * leaves no row behind. Counting distinct dates would therefore have measured how diligently the
 * logbook was filled in rather than how much was climbed — and it read as a real number, which
 * made it worse than no number at all. Reviving it needs a way to record a day without a send,
 * which is exactly what the "ascents only" rule rules out.
 */
@Service
@Transactional(readOnly = true)
public class AscentStatsService {

    private static final int TOP_AREAS = 5;

    private final ClimbingAscentRepository ascentRepository;
    private final AscentService ascentService;
    private final TrainingCalendarService calendarService;

    public AscentStatsService(ClimbingAscentRepository ascentRepository,
                              AscentService ascentService,
                              TrainingCalendarService calendarService) {
        this.ascentRepository = ascentRepository;
        this.ascentService = ascentService;
        this.calendarService = calendarService;
    }

    /** Own logbook, so being logged in is the whole gate — see {@link AscentService}. */
    public AscentStatsDto getMyStats(UUID userId, AscentTerrain terrain, @Nullable String year) {
        return buildStats(userId, terrain, year);
    }

    /** Coach path — designated athletes only, so a plain user's logbook stays private. */
    public AscentStatsDto getStatsForAthlete(UUID athleteId, AscentTerrain terrain, @Nullable String year) {
        calendarService.requireFlaggedAthlete(athleteId);
        return buildStats(athleteId, terrain, year);
    }

    private AscentStatsDto buildStats(UUID athleteId, AscentTerrain terrain, @Nullable String yearParam) {
        List<AscentStatsRow> all = ascentRepository.findStatsRowsByAthleteId(athleteId, terrain);
        Integer selectedYear = ascentService.resolveYear(yearsIn(all), yearParam);
        return buildStats(all, selectedYear, terrain);
    }

    /**
     * Package-private and taking the already-loaded rows so tests can pin the data without a
     * database. The year is applied here as a slice of {@code all} rather than as a second query:
     * the progression chart is all-time whatever year is selected, so both views are needed.
     */
    AscentStatsDto buildStats(List<AscentStatsRow> all, @Nullable Integer selectedYear) {
        return buildStats(all, selectedYear, AscentTerrain.ROCK);
    }

    AscentStatsDto buildStats(List<AscentStatsRow> all, @Nullable Integer selectedYear,
                              AscentTerrain terrain) {
        List<AscentStatsRow> selected = selectedYear == null
                ? all
                : all.stream().filter(row -> row.climbedOn().getYear() == selectedYear).toList();

        // Mountains have no discipline, so the per-discipline blocks stay empty on purpose and
        // the terrain gets its own set of figures instead — metres, pitches, time and what was led
        List<AscentDisciplineStatsDto> disciplines = new ArrayList<>();
        for (AscentDiscipline discipline : terrain == AscentTerrain.MOUNTAIN
                ? new AscentDiscipline[0] : AscentDiscipline.values()) {
            List<AscentStatsRow> inScope = selected.stream()
                    .filter(row -> row.discipline() == discipline).toList();
            if (inScope.isEmpty()) {
                continue;
            }
            List<AscentStatsRow> allTime = all.stream()
                    .filter(row -> row.discipline() == discipline).toList();
            disciplines.add(disciplineStats(discipline, inScope, allTime));
        }
        // Busiest first: a boulderer opening the panel should not have to scroll past two
        // near-empty blocks to reach their own
        disciplines.sort(Comparator.comparingInt(AscentDisciplineStatsDto::ascentCount).reversed());

        // Deliberately NOT a "days on rock" count. Only ascents are logged, so a day of failed
        // attempts, a rained-off day or a day spent belaying leaves no row — the figure would
        // have measured how diligently the logbook was filled in, not how much was climbed.
        // Worked sends across every dialect — RP on bolts, GU and HP on gear. Pinning this to RP
        // alone would leave a trad-only logbook with a permanent dash next to a field its owner
        // fills in on every entry
        List<AscentStatsRow> countedRedpoints = selected.stream()
                .filter(row -> row.style().isWorkedSend() && row.attempts() != null)
                .toList();

        return new AscentStatsDto(
                selectedYear,
                selected.size(),
                selected.stream().map(AscentStatsRow::climbedOn).min(LocalDate::compareTo).orElse(null),
                (int) selected.stream().map(AscentStatsRow::areaKey).distinct().count(),
                (int) selected.stream().map(AscentStatsRow::cragKey).distinct().count(),
                countedRedpoints.isEmpty() ? null : round1(countedRedpoints.stream()
                        .mapToInt(row -> row.attempts() == null ? 0 : row.attempts()).average().orElse(0)),
                countedRedpoints.size(),
                averageStars(selected),
                topAreas(selected),
                disciplines,
                terrain == AscentTerrain.MOUNTAIN ? mountainStats(selected) : null);
    }

    /**
     * The mountain half. Every total ships with the number of entries it was built from: length,
     * pitches and duration are all optional, so "4200 m" alone would not say whether that is the
     * whole season or the two entries somebody bothered to measure.
     */
    private static MountainStatsDto mountainStats(List<AscentStatsRow> rows) {
        List<AscentStatsRow> withLength = rows.stream().filter(r -> r.lengthMeters() != null).toList();
        List<AscentStatsRow> withPitches = rows.stream().filter(r -> r.pitches() != null).toList();
        List<AscentStatsRow> withDuration = rows.stream().filter(r -> r.durationMinutes() != null).toList();
        List<AscentStatsRow> led = rows.stream().filter(r -> r.ledGrade() != null).toList();

        // The routes themselves. Mountains get no discipline block, so this pyramid is the only
        // place the level being climbed is visible at all
        Map<ClimbingGrade, Map<AscentStyle, Integer>> pyramid = new TreeMap<>(
                Comparator.comparingInt(ClimbingGrade::rank).reversed());
        Map<AscentStyle, AscentStatsRow> hardest = new EnumMap<>(AscentStyle.class);
        for (AscentStatsRow row : rows) {
            pyramid.computeIfAbsent(row.grade(), key -> new EnumMap<>(AscentStyle.class))
                    .merge(row.style(), 1, Integer::sum);
            AscentStatsRow best = hardest.get(row.style());
            if (best == null || row.grade().rank() > best.grade().rank()) {
                hardest.put(row.style(), row);
            }
        }
        Map<String, BestAscentDto> hardestByStyle = new LinkedHashMap<>();
        hardest.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<AscentStyle, AscentStatsRow> e)
                        -> e.getKey().purity()).reversed())
                .forEach(entry -> hardestByStyle.put(entry.getKey().name(), toBest(entry.getValue())));

        Map<ClimbingGrade, Map<AscentStyle, Integer>> leadPyramid = new TreeMap<>(
                Comparator.comparingInt(ClimbingGrade::rank).reversed());
        AscentStatsRow hardestLed = null;
        for (AscentStatsRow row : led) {
            ClimbingGrade grade = Objects.requireNonNull(row.ledGrade());
            leadPyramid.computeIfAbsent(grade, key -> new EnumMap<>(AscentStyle.class))
                    .merge(row.style(), 1, Integer::sum);
            if (hardestLed == null
                    || grade.rank() > Objects.requireNonNull(hardestLed.ledGrade()).rank()) {
                hardestLed = row;
            }
        }

        return new MountainStatsDto(
                (int) rows.stream().filter(r -> Boolean.FALSE.equals(r.winter())).count(),
                (int) rows.stream().filter(r -> Boolean.TRUE.equals(r.winter())).count(),
                withLength.stream().mapToInt(r -> Objects.requireNonNull(r.lengthMeters())).sum(),
                withLength.size(),
                withPitches.stream().mapToInt(r -> Objects.requireNonNull(r.pitches())).sum(),
                withPitches.size(),
                withDuration.stream().mapToInt(r -> Objects.requireNonNull(r.durationMinutes())).sum(),
                withDuration.size(),
                (int) rows.stream().map(AscentStatsRow::cragKey).distinct().count(),
                toPyramidRows(pyramid),
                hardestByStyle,
                toPyramidRows(leadPyramid),
                hardestLed == null ? null : new BestAscentDto(
                        Objects.requireNonNull(hardestLed.ledGrade()).name(),
                        Objects.requireNonNull(hardestLed.ledGrade()).label(),
                        Objects.requireNonNull(hardestLed.ledGrade()).rank(),
                        hardestLed.routeName(), hardestLed.crag(), hardestLed.climbedOn()),
                rows.stream().mapToInt(r -> r.ledPitches() == null ? 0 : r.ledPitches()).sum());
    }

    private static AscentDisciplineStatsDto disciplineStats(AscentDiscipline discipline,
                                                            List<AscentStatsRow> inScope,
                                                            List<AscentStatsRow> allTime) {
        Map<ClimbingGrade, Map<AscentStyle, Integer>> pyramid = new TreeMap<>(
                Comparator.comparingInt(ClimbingGrade::rank).reversed());
        Map<AscentStyle, Integer> styleCounts = new EnumMap<>(AscentStyle.class);
        Map<AscentStyle, AscentStatsRow> hardest = new EnumMap<>(AscentStyle.class);

        for (AscentStatsRow row : inScope) {
            pyramid.computeIfAbsent(row.grade(), key -> new EnumMap<>(AscentStyle.class))
                    .merge(row.style(), 1, Integer::sum);
            styleCounts.merge(row.style(), 1, Integer::sum);
            AscentStatsRow best = hardest.get(row.style());
            if (best == null || row.grade().rank() > best.grade().rank()) {
                hardest.put(row.style(), row);
            }
        }

        List<PyramidRowDto> rungs = toPyramidRows(pyramid);

        Map<String, BestAscentDto> hardestByStyle = new LinkedHashMap<>();
        hardest.entrySet().stream()
                // Cleanest style first, so the card reads OS, FLASH, RP... like the form does
                .sorted(Comparator.comparingInt((Map.Entry<AscentStyle, AscentStatsRow> e)
                        -> e.getKey().purity()).reversed())
                .forEach(entry -> hardestByStyle.put(entry.getKey().name(), toBest(entry.getValue())));

        // inScope is never empty here (the caller skips empty disciplines), so a zero rate is a
        // real answer — "no onsights this year" — rather than a missing one
        // Counted through isOnsight() rather than off the OS key: trad logs its onsights as
        // OS GU, and a block that always reported 0% would read as "never onsighted anything"
        int onsights = styleCounts.entrySet().stream()
                .filter(entry -> entry.getKey().isOnsight())
                .mapToInt(Map.Entry::getValue)
                .sum();
        double onsightRate = round1(100.0 * onsights / inScope.size());

        return new AscentDisciplineStatsDto(
                discipline.name(),
                discipline.scale().name(),
                inScope.size(),
                rungs,
                hardestByStyle,
                toNameKeys(styleCounts),
                onsightRate,
                progressionByYear(allTime));
    }

    /**
     * Best grade per calendar year, oldest first — computed over the discipline's whole history
     * regardless of the selected year, because a progression cropped to one year is a single
     * point pretending to be a trend.
     */
    private static List<GradeProgressPointDto> progressionByYear(List<AscentStatsRow> allTime) {
        Map<Integer, AscentStatsRow> bestOverall = new TreeMap<>();
        Map<Integer, AscentStatsRow> bestOnsight = new TreeMap<>();

        for (AscentStatsRow row : allTime) {
            int year = row.climbedOn().getYear();
            keepHarder(bestOverall, year, row);
            if (row.style().isOnsight()) {
                keepHarder(bestOnsight, year, row);
            }
        }

        return bestOverall.entrySet().stream()
                .map(entry -> {
                    AscentStatsRow onsight = bestOnsight.get(entry.getKey());
                    return new GradeProgressPointDto(
                            entry.getKey(),
                            entry.getValue().grade().label(),
                            entry.getValue().grade().rank(),
                            onsight != null ? onsight.grade().label() : null,
                            onsight != null ? onsight.grade().rank() : null);
                })
                .toList();
    }

    private static void keepHarder(Map<Integer, AscentStatsRow> best, int year, AscentStatsRow row) {
        AscentStatsRow current = best.get(year);
        if (current == null || row.grade().rank() > current.grade().rank()) {
            best.put(year, row);
        }
    }

    /**
     * Areas by ascent count, grouped on the normalized key so one crag spelled two ways stays
     * one crag. The label shown is the most recent spelling — the grouping already happened.
     */
    private static List<AreaCountDto> topAreas(List<AscentStatsRow> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, LocalDate> latest = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();

        for (AscentStatsRow row : rows) {
            counts.merge(row.areaKey(), 1, Integer::sum);
            LocalDate seen = latest.get(row.areaKey());
            if (seen == null || row.climbedOn().isAfter(seen)) {
                latest.put(row.areaKey(), row.climbedOn());
                labels.put(row.areaKey(), row.area());
            }
        }

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP_AREAS)
                .map(entry -> new AreaCountDto(labels.get(entry.getKey()), entry.getValue()))
                .toList();
    }

    private static @Nullable Double averageStars(List<AscentStatsRow> rows) {
        List<Integer> rated = rows.stream().map(AscentStatsRow::qualityStars).filter(Objects::nonNull).toList();
        if (rated.isEmpty()) {
            return null;
        }
        return round1(rated.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private static List<Integer> yearsIn(List<AscentStatsRow> rows) {
        return rows.stream()
                .map(row -> row.climbedOn().getYear())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private static BestAscentDto toBest(AscentStatsRow row) {
        return new BestAscentDto(row.grade().name(), row.grade().label(), row.grade().rank(),
                row.routeName(), row.crag(), row.climbedOn());
    }

    private static List<PyramidRowDto> toPyramidRows(Map<ClimbingGrade, Map<AscentStyle, Integer>> pyramid) {
        return pyramid.entrySet().stream()
                .map(entry -> new PyramidRowDto(
                        entry.getKey().name(),
                        entry.getKey().label(),
                        entry.getKey().rank(),
                        toNameKeys(entry.getValue()),
                        entry.getValue().values().stream().mapToInt(Integer::intValue).sum()))
                .toList();
    }

    private static Map<String, Integer> toNameKeys(Map<AscentStyle, Integer> counts) {
        Map<String, Integer> named = new LinkedHashMap<>();
        counts.forEach((style, count) -> named.put(style.name(), count));
        return named;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
