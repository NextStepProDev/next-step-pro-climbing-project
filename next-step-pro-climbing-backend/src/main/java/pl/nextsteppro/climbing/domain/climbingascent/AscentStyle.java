package pl.nextsteppro.climbing.domain.climbingascent;

/**
 * How the route went down, from least to most committing.
 *
 * <p>The two-letter names (OS, RP, TR) are NOT translated — they are the international shorthand
 * every climber already reads, and a Polish "na wzrok" would be a worse label than the one on the
 * topo. SOLO and FREE_SOLO are words rather than shorthand, so those two do get a translation
 * ("Solo", "Na żywca"); the UI goes through one dictionary for all of them so the exception has
 * a single home.
 *
 * <p>Pinkpoint was dropped: it is a distinction almost nobody logs, and every style in a dropdown
 * is a question the climber has to answer.
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

    /** Toprope. Rock only — hanging a rope down a mountain route is not a thing. */
    TR(1),

    /** Redpoint — led clean after rehearsal. */
    RP(2),

    /** First try, but with prior knowledge (beta, having watched someone). */
    FLASH(3),

    /** First try, no prior knowledge. */
    OS(4),

    /**
     * Alone, but roped — self-belayed or with a fixed line. Ordered above the partnered styles
     * because it is a different commitment, not a cleaner one; the ordering only decides the
     * reading order of the lists, never a score.
     */
    SOLO(5),

    /** Alone and unroped. Polish calls it "na żywca". */
    FREE_SOLO(6);

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
        return this == OS || this == FLASH;
    }
}
