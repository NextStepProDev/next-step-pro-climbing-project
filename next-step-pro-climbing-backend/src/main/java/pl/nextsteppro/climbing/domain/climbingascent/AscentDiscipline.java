package pl.nextsteppro.climbing.domain.climbingascent;

import java.util.Set;

/**
 * What kind of climbing the entry records. Decides two things the athlete must not have to
 * remember: which grade scale applies, and which styles even exist.
 *
 * <p>This enum is the single source of truth for both. The database CHECKs
 * ({@code chk_climbing_ascents_grade_scale}, {@code chk_climbing_ascents_boulder_style}) mirror
 * it, and the {@code /ascents/options} endpoint serves it to the form — so the front never
 * hardcodes a list. That is the lesson from {@code SlotKindPicker}: a form offering a value the
 * backend refuses is a 400 for something that looked available.
 *
 * <p>Statistics are computed per discipline, so trad gets its own pyramid even though it shares
 * the French route scale with sport.
 */
public enum AscentDiscipline {

    SPORT(GradeScale.FRENCH_ROUTE, Set.of(AscentStyle.OS, AscentStyle.FLASH, AscentStyle.RP,
            AscentStyle.TR, AscentStyle.SOLO, AscentStyle.FREE_SOLO)),

    TRAD(GradeScale.FRENCH_ROUTE, Set.of(AscentStyle.OS, AscentStyle.FLASH, AscentStyle.RP,
            AscentStyle.TR, AscentStyle.SOLO, AscentStyle.FREE_SOLO)),

    /**
     * No rope to hang on, so toprope does not apply — and neither does free solo, since every
     * boulder problem is climbed unroped by definition. Calling that "free solo" would put a
     * label on the normal case.
     */
    BOULDER(GradeScale.FONT_BOULDER, Set.of(AscentStyle.OS, AscentStyle.FLASH, AscentStyle.RP));

    /**
     * What the mountains allow. Toprope is gone: nobody hangs a rope down an alpine route, so
     * offering it would be a question with no honest answer. A0 is here and nowhere else — an
     * alpine route pulled through one move is still an ascent of that route, while the same day
     * on a crag is honestly recorded as a redpoint attempt.
     */
    public static final Set<AscentStyle> MOUNTAIN_STYLES = Set.of(
            AscentStyle.OS, AscentStyle.FLASH, AscentStyle.RP, AscentStyle.A0,
            AscentStyle.SOLO, AscentStyle.FREE_SOLO);

    private final GradeScale scale;
    private final Set<AscentStyle> allowedStyles;

    AscentDiscipline(GradeScale scale, Set<AscentStyle> allowedStyles) {
        this.scale = scale;
        this.allowedStyles = allowedStyles;
    }

    public GradeScale scale() {
        return scale;
    }

    public Set<AscentStyle> allowedStyles() {
        return allowedStyles;
    }

    public boolean allows(AscentStyle style) {
        return allowedStyles.contains(style);
    }

    public boolean allows(ClimbingGrade grade) {
        return grade.scale() == scale;
    }
}
