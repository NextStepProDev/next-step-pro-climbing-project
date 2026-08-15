package pl.nextsteppro.climbing.domain.climbingascent;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The closed catalogue of grades the logbook accepts, on two scales.
 *
 * <p><b>Why an enum and not free text.</b> The pyramid, "hardest onsight" and the progression
 * chart all group and order by grade, and a ranking keyed on free text already failed once in
 * this project — the "most frequent places" tile read {@code events.location} as a grouping key
 * and returned the same crag twice under two spellings. Here the cost would be higher still:
 * {@code 7a} and {@code 7A} are three grades apart and would land in one bar.
 *
 * <p><b>Why the rank lives here and not in a column.</b> The rank is a property of the list, not
 * of the row. Stored per ascent it would need a data migration the day {@code 9c+} gets pushed
 * onto the top of the scale, and nothing sorts by it in SQL anyway — the stats do one pass in
 * Java (the {@code TrainingStatsService} pattern) and the table sorts client-side. Same division
 * of labour as body weight: the database keeps what was measured, the app derives everything else.
 *
 * <p>Ranks are spaced by ten so a grade can be inserted between two existing ones without
 * renumbering. Their absolute values mean nothing; only the order does, and only within a scale.
 *
 * <p><b>The constant names are load-bearing.</b> {@code chk_climbing_ascents_grade_scale} (V82)
 * checks the {@code FR_}/{@code FB_} prefix against the discipline, so renaming a constant
 * without a data migration desynchronises the database from this file.
 */
public enum ClimbingGrade {

    // ---- French route scale (sport + trad) ----------------------------------------------
    FR_3(GradeScale.FRENCH_ROUTE, "3", 10),
    FR_4A(GradeScale.FRENCH_ROUTE, "4a", 20),
    FR_4B(GradeScale.FRENCH_ROUTE, "4b", 30),
    FR_4C(GradeScale.FRENCH_ROUTE, "4c", 40),
    FR_5A(GradeScale.FRENCH_ROUTE, "5a", 50),
    FR_5B(GradeScale.FRENCH_ROUTE, "5b", 60),
    FR_5C(GradeScale.FRENCH_ROUTE, "5c", 70),
    FR_6A(GradeScale.FRENCH_ROUTE, "6a", 80),
    FR_6A_PLUS(GradeScale.FRENCH_ROUTE, "6a+", 90),
    FR_6B(GradeScale.FRENCH_ROUTE, "6b", 100),
    FR_6B_PLUS(GradeScale.FRENCH_ROUTE, "6b+", 110),
    FR_6C(GradeScale.FRENCH_ROUTE, "6c", 120),
    FR_6C_PLUS(GradeScale.FRENCH_ROUTE, "6c+", 130),
    FR_7A(GradeScale.FRENCH_ROUTE, "7a", 140),
    FR_7A_PLUS(GradeScale.FRENCH_ROUTE, "7a+", 150),
    FR_7B(GradeScale.FRENCH_ROUTE, "7b", 160),
    FR_7B_PLUS(GradeScale.FRENCH_ROUTE, "7b+", 170),
    FR_7C(GradeScale.FRENCH_ROUTE, "7c", 180),
    FR_7C_PLUS(GradeScale.FRENCH_ROUTE, "7c+", 190),
    FR_8A(GradeScale.FRENCH_ROUTE, "8a", 200),
    FR_8A_PLUS(GradeScale.FRENCH_ROUTE, "8a+", 210),
    FR_8B(GradeScale.FRENCH_ROUTE, "8b", 220),
    FR_8B_PLUS(GradeScale.FRENCH_ROUTE, "8b+", 230),
    FR_8C(GradeScale.FRENCH_ROUTE, "8c", 240),
    FR_8C_PLUS(GradeScale.FRENCH_ROUTE, "8c+", 250),
    FR_9A(GradeScale.FRENCH_ROUTE, "9a", 260),
    FR_9A_PLUS(GradeScale.FRENCH_ROUTE, "9a+", 270),
    FR_9B(GradeScale.FRENCH_ROUTE, "9b", 280),
    FR_9B_PLUS(GradeScale.FRENCH_ROUTE, "9b+", 290),
    FR_9C(GradeScale.FRENCH_ROUTE, "9c", 300),

    // ---- Fontainebleau boulder scale ----------------------------------------------------
    FB_3(GradeScale.FONT_BOULDER, "3", 10),
    FB_4(GradeScale.FONT_BOULDER, "4", 20),
    FB_4_PLUS(GradeScale.FONT_BOULDER, "4+", 30),
    FB_5(GradeScale.FONT_BOULDER, "5", 40),
    FB_5_PLUS(GradeScale.FONT_BOULDER, "5+", 50),
    FB_6A(GradeScale.FONT_BOULDER, "6A", 60),
    FB_6A_PLUS(GradeScale.FONT_BOULDER, "6A+", 70),
    FB_6B(GradeScale.FONT_BOULDER, "6B", 80),
    FB_6B_PLUS(GradeScale.FONT_BOULDER, "6B+", 90),
    FB_6C(GradeScale.FONT_BOULDER, "6C", 100),
    FB_6C_PLUS(GradeScale.FONT_BOULDER, "6C+", 110),
    FB_7A(GradeScale.FONT_BOULDER, "7A", 120),
    FB_7A_PLUS(GradeScale.FONT_BOULDER, "7A+", 130),
    FB_7B(GradeScale.FONT_BOULDER, "7B", 140),
    FB_7B_PLUS(GradeScale.FONT_BOULDER, "7B+", 150),
    FB_7C(GradeScale.FONT_BOULDER, "7C", 160),
    FB_7C_PLUS(GradeScale.FONT_BOULDER, "7C+", 170),
    FB_8A(GradeScale.FONT_BOULDER, "8A", 180),
    FB_8A_PLUS(GradeScale.FONT_BOULDER, "8A+", 190),
    FB_8B(GradeScale.FONT_BOULDER, "8B", 200),
    FB_8B_PLUS(GradeScale.FONT_BOULDER, "8B+", 210),
    FB_8C(GradeScale.FONT_BOULDER, "8C", 220),
    FB_8C_PLUS(GradeScale.FONT_BOULDER, "8C+", 230),
    FB_9A(GradeScale.FONT_BOULDER, "9A", 240);

    private static final Map<GradeScale, List<ClimbingGrade>> BY_SCALE = new EnumMap<>(GradeScale.class);

    static {
        for (GradeScale scale : GradeScale.values()) {
            BY_SCALE.put(scale, Arrays.stream(values())
                    .filter(grade -> grade.scale == scale)
                    .sorted(Comparator.comparingInt(ClimbingGrade::rank))
                    .toList());
        }
    }

    private final GradeScale scale;
    private final String label;
    private final int rank;

    ClimbingGrade(GradeScale scale, String label, int rank) {
        this.scale = scale;
        this.label = label;
        this.rank = rank;
    }

    public GradeScale scale() {
        return scale;
    }

    /** What the climber reads on the topo. Never translated, never derived on the front. */
    public String label() {
        return label;
    }

    /** Ordering within {@link #scale()} only. Comparing ranks across scales is meaningless. */
    public int rank() {
        return rank;
    }

    /** Every grade of one scale, easiest first. Feeds the form's dropdown and the pyramid axis. */
    public static List<ClimbingGrade> of(GradeScale scale) {
        return BY_SCALE.get(scale);
    }

    /**
     * The hardest of the given grades, or {@code null} if there are none.
     *
     * @throws IllegalArgumentException if the grades span more than one scale — {@code 7a} and
     *         {@code 7A} have no common ordering, so the honest answer to "which is harder" is
     *         a refusal rather than whichever happens to carry the bigger rank
     */
    public static @Nullable ClimbingGrade hardest(Collection<ClimbingGrade> grades) {
        ClimbingGrade hardest = null;
        for (ClimbingGrade grade : grades) {
            if (hardest == null) {
                hardest = grade;
            } else {
                if (grade.scale != hardest.scale) {
                    throw new IllegalArgumentException(
                            "Cannot compare grades across scales: " + hardest + " vs " + grade);
                }
                if (grade.rank > hardest.rank) {
                    hardest = grade;
                }
            }
        }
        return hardest;
    }
}
