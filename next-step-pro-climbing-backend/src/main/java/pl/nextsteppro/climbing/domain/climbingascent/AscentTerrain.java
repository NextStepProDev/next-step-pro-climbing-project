package pl.nextsteppro.climbing.domain.climbingascent;

/**
 * Where the ascent happened — and therefore which shape the entry takes.
 *
 * <p>Not a discipline: {@link AscentDiscipline} answers "sport, boulder or trad", which is a
 * question about a crag. This answers "crag or mountain", which decides what an entry even has
 * to say. A rock entry carries a discipline, attempts and a route rating; a mountain entry
 * carries a season, a length, pitches, a time and who led what.
 *
 * <p>Both share one grade axis: the unified grade is French in the mountains too (the convention
 * the PZA logbook uses), so pyramids, "hardest ascent" and the public feed need no second scale.
 */
public enum AscentTerrain {

    /** Crags and boulders: has a discipline, may carry attempts and a star rating. */
    ROCK,

    /** Mountains: has a season, and may carry length, pitches, duration, partners and led pitches. */
    MOUNTAIN
}
