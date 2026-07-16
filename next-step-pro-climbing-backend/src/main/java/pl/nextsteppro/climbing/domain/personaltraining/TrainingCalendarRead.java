package pl.nextsteppro.climbing.domain.personaltraining;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * "Seen" marker for a training calendar, per (viewer, calendar) pair:
 * the athlete holds one row (userId = athleteId = own id), the coach one row per athlete
 * (multiple admins keep independent counters). Unread counters are plain COUNTs of
 * trainings/comments with timestamps after {@code seenAt}; a missing row means
 * "count everything" (new calendars start empty anyway). Upserted on mark-seen.
 *
 * <p>Plain UUID columns (no associations) on purpose — the counter queries LEFT JOIN this
 * entity ON arbitrary conditions, which needs directly addressable key fields.
 */
@Entity
@Table(name = "training_calendar_reads")
@IdClass(TrainingCalendarRead.Key.class)
public class TrainingCalendarRead {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Column(name = "seen_at", nullable = false)
    private Instant seenAt;

    protected TrainingCalendarRead() {}

    public TrainingCalendarRead(UUID userId, UUID athleteId) {
        this.userId = userId;
        this.athleteId = athleteId;
        this.seenAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getAthleteId() {
        return athleteId;
    }

    public Instant getSeenAt() {
        return seenAt;
    }

    /** Composite key: (viewer, athlete calendar). */
    public static class Key implements java.io.Serializable {
        private UUID userId;
        private UUID athleteId;

        public Key() {}

        public Key(UUID userId, UUID athleteId) {
            this.userId = userId;
            this.athleteId = athleteId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return userId.equals(key.userId) && athleteId.equals(key.athleteId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(userId, athleteId);
        }
    }
}
