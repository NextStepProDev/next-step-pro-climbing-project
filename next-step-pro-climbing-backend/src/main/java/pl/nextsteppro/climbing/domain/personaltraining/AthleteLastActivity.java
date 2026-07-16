package pl.nextsteppro.climbing.domain.personaltraining;

import java.time.Instant;
import java.util.UUID;

/** JPQL constructor projection: per-athlete timestamp of the latest calendar activity. */
public record AthleteLastActivity(UUID athleteId, Instant lastActivityAt) {}
