package pl.nextsteppro.climbing.domain.personaltraining;

import java.util.UUID;

/** JPQL constructor projection: per-athlete count of unread activity (coach-side badges). */
public record AthleteActivityCount(UUID athleteId, long count) {}
