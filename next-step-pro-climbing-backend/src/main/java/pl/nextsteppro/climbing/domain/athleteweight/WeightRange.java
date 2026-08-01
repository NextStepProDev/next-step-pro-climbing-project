package pl.nextsteppro.climbing.domain.athleteweight;

/**
 * How far back the weight chart reaches. A closed set of named ranges rather than a free
 * {@code days} number from the client: the server decides what each one costs, so there is no
 * request shape that can ask for an unbounded history.
 *
 * <p>{@link #ALL} is capped too. One row per athlete per day is the hard ceiling (unique
 * index in V74), so five years is every reading a real person will have — and a finite number
 * beats an honest-sounding "everything" that grows without limit.
 */
public enum WeightRange {

    /** Default. Kept at 120 days so nothing changes for anyone who never touches the picker. */
    RECENT(120),

    YEAR(365),

    /** Five years, i.e. at most ~1825 readings. */
    ALL(1825);

    public static final WeightRange DEFAULT = RECENT;

    private final int days;

    WeightRange(int days) {
        this.days = days;
    }

    public int days() {
        return days;
    }
}
