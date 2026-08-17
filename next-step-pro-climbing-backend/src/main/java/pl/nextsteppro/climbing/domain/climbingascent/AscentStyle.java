package pl.nextsteppro.climbing.domain.climbingascent;

/**
 * How the route went down, from least to most committing.
 *
 * <p>The shorthand names (OS, RP, TR, GU, HP) are NOT translated — they are the international
 * shorthand every climber already reads, and a Polish "na wzrok" would be a worse label than the
 * one on the topo. SOLO and FREE_SOLO are words rather than shorthand, so those two do get a
 * translation ("Solo", "Na żywca"); the UI goes through one dictionary for all of them so the
 * exception has a single home.
 *
 * <p>Pinkpoint was dropped: it is a distinction almost nobody logs, and every style in a dropdown
 * is a question the climber has to answer.
 *
 * <p><b>Trad speaks its own dialect.</b> The GU family and headpoint belong to trad and nowhere
 * else — see {@link AscentDiscipline#TRAD}. On gear the question is not "how much did you know"
 * but "where did you work it from", so sport's OS/FLASH/RP say the wrong thing there: a trad
 * onsight is ground up by definition, and a plain "RP" hides whether the route was rehearsed on
 * a rope from above. Sport, boulder and the mountains keep their own sets unchanged.
 *
 * <p>{@link #purity()} exists because "hardest ascent" is a meaningless number on its own: an
 * onsight and a redpoint of the same grade are different achievements, so the stats report the
 * hardest one <em>per style</em> rather than collapsing them.
 */
public enum AscentStyle {

    /**
     * A0 — the route went, but with pulls on gear: a sling, a bolt, a piece of protection.
     * The weakest style there is, and mountains only: on a crag the honest record of the same
     * day is a redpoint attempt, whereas an alpine route pulled through one hard move is still
     * an ascent of that route.
     */
    A0(0),

    /** Toprope. Sport and boulder only — no trad, and nothing to hang a rope from up a mountain. */
    TR(1),

    /**
     * HP — headpoint. Trad's answer to the redpoint: led clean, but rehearsed from above on a
     * rope first. Ranked below GU because the ground-up game is the harder one, never because
     * the ascent counts for less.
     */
    HP(2),

    /** Redpoint — led clean after rehearsal. */
    RP(3),

    /**
     * GU — ground up. Led clean with every attempt started from the ground: no rappelling in,
     * no toprope rehearsal, gear placed on lead. Attempts still count, which is the whole
     * difference between this and {@link #FLASH_GU}.
     */
    GU(4),

    /** First try, but with prior knowledge (beta, having watched someone). */
    FLASH(5),

    /** Flash on gear, ground up — first try with beta, nothing rehearsed from above. */
    FLASH_GU(6),

    /** First try, no prior knowledge. */
    OS(7),

    /** Onsight on gear. Ground up is implied — an onsight rehearsed from above is not one. */
    OS_GU(8),

    /**
     * Alone, but roped — self-belayed or with a fixed line. Ordered above the partnered styles
     * because it is a different commitment, not a cleaner one; the ordering only decides the
     * reading order of the lists, never a score.
     */
    SOLO(9),

    /** Alone and unroped. Polish calls it "na żywca". */
    FREE_SOLO(10);

    private final int purity;

    AscentStyle(int purity) {
        this.purity = purity;
    }

    /** Higher = cleaner. Only meaningful as an ordering, never as a score to average. */
    public int purity() {
        return purity;
    }

    /** OS and FLASH mean "first go" by definition, so attempts can only ever be 1. */
    public boolean isFirstTry() {
        return this == OS || this == FLASH || this == OS_GU || this == FLASH_GU;
    }

    /**
     * An onsight, whichever dialect it was logged in. The onsight rate is computed per discipline,
     * so trad — where the onsight is {@link #OS_GU} — has to answer the same question as sport
     * rather than reporting a permanent zero.
     */
    public boolean isOnsight() {
        return this == OS || this == OS_GU;
    }

    /**
     * Ascents where a count of attempts says something: the route was worked and then led clean.
     * OS and FLASH are one try by definition, and toprope or solo answer a different question,
     * so averaging attempts over those would dilute the figure into noise.
     */
    public boolean isWorkedSend() {
        return this == RP || this == GU || this == HP;
    }
}
