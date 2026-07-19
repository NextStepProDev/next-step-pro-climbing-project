package pl.nextsteppro.climbing.domain.athletegoal;

/**
 * Time horizon of an athlete's goal. Exactly one ACTIVE goal per horizon is allowed
 * (partial unique index in V67), so the banner above the calendar always renders
 * at most three cards: short → medium → long. The horizon also picks the trophy
 * size in the trophy chest (small/medium/large cup).
 */
public enum GoalHorizon {
    SHORT,
    MEDIUM,
    LONG
}
