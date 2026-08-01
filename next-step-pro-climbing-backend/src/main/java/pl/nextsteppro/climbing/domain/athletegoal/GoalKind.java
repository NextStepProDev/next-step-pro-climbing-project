package pl.nextsteppro.climbing.domain.athletegoal;

/**
 * What kind of goal this is. Declaration order is the order of the rows in the banner above
 * the training calendar: training goals first, weight goals below.
 *
 * <p>The active-goal slot is unique per {@code (athlete, kind, horizon)} (V75), so an athlete
 * may chase a technique goal and a weight goal on the same horizon at once.
 */
public enum GoalKind {

    /** Free-text training goal. Closed by hand by the coach; the closure is permanent. */
    GENERAL,

    /**
     * Target body weight. Closes ITSELF once the 7-day trend reaches the target — but only
     * when that trend rests on enough readings to be trusted (see WeightTrendCalculator).
     * Because a machine closed it, the coach may reopen it after a mistyped weigh-in.
     */
    WEIGHT
}
