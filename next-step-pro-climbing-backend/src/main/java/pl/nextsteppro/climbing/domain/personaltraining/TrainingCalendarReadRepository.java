package pl.nextsteppro.climbing.domain.personaltraining;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TrainingCalendarReadRepository extends JpaRepository<TrainingCalendarRead, TrainingCalendarRead.Key> {

    Optional<TrainingCalendarRead> findByUserIdAndAthleteId(UUID userId, UUID athleteId);

    /**
     * Race-free mark-seen: two concurrent calls (double click, two tabs) must not blow up on the PK.
     * seenAt comes from the JVM clock, NOT SQL now() — entity timestamps (createdAt/updatedAt/
     * completedAt) use Instant.now(), and Postgres now() is the TRANSACTION start time, which
     * could predate activity written moments earlier and leave counters stuck.
     * clearAutomatically: a native update bypasses the persistence context — without the clear,
     * a TrainingCalendarRead entity already loaded in the same session would keep serving the
     * STALE seenAt (Hibernate's identity map wins over fresh column values on re-query).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO training_calendar_reads (user_id, athlete_id, seen_at)
        VALUES (:userId, :athleteId, :seenAt)
        ON CONFLICT (user_id, athlete_id) DO UPDATE SET seen_at = :seenAt
        """, nativeQuery = true)
    void upsertSeen(UUID userId, UUID athleteId, Instant seenAt);
}
