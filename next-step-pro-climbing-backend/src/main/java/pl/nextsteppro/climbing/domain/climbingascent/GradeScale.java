package pl.nextsteppro.climbing.domain.climbingascent;

/**
 * The axis a grade lives on. Two grades are only ever comparable within one scale.
 *
 * <p>The app deliberately speaks French and nothing else: {@code 6a+} for routes, {@code 6A+} for
 * boulders. Adding a third system (UIAA, V-scale, British E-grades) would mean either a conversion
 * table — which is guesswork dressed as data — or a third pyramid nobody fills.
 *
 * <p>The prefix of every {@link ClimbingGrade} constant encodes its scale, and the database CHECK
 * {@code chk_climbing_ascents_grade_scale} reads that prefix. Renaming the prefixes silently
 * breaks the constraint, which is why {@link #constantPrefix()} lives here rather than being
 * spelled out at each call site.
 */
public enum GradeScale {

    /** French sport grades, used for both sport and trad routes: 3 … 9c. */
    FRENCH_ROUTE("FR_"),

    /** Fontainebleau boulder grades: 3 … 9A. */
    FONT_BOULDER("FB_");

    private final String constantPrefix;

    GradeScale(String constantPrefix) {
        this.constantPrefix = constantPrefix;
    }

    /** Prefix every {@link ClimbingGrade} constant of this scale carries; mirrored by a DB CHECK. */
    public String constantPrefix() {
        return constantPrefix;
    }
}
