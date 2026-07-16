package pl.nextsteppro.climbing.domain.personaltraining;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TrainingDeletionRepository extends JpaRepository<TrainingDeletion, UUID> {

    /** Athlete's unread counter: future trainings the coach removed after the athlete's seen marker. */
    @Query("""
        SELECT COUNT(d) FROM TrainingDeletion d
        WHERE d.athlete.id = :athleteId AND d.deletedByAdmin = true AND d.createdAt > :since
        """)
    long countAdminDeletionsSince(UUID athleteId, Instant since);

    /** Coach-side per-athlete counter: future trainings athletes removed after this admin's seen marker. */
    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.personaltraining.AthleteActivityCount(d.athlete.id, COUNT(d))
        FROM TrainingDeletion d
        LEFT JOIN TrainingCalendarRead r ON r.userId = :adminId AND r.athleteId = d.athlete.id
        WHERE d.deletedByAdmin = false AND (r.seenAt IS NULL OR d.createdAt > r.seenAt)
        GROUP BY d.athlete.id
        """)
    List<AthleteActivityCount> countNewAthleteDeletionsPerAthlete(UUID adminId);

    /** "Deleted trainings" strip: unseen deletions made by the OTHER side, newest first. */
    @Query("""
        SELECT d FROM TrainingDeletion d
        WHERE d.athlete.id = :athleteId AND d.deletedByAdmin = :byAdmin AND d.createdAt > :since
        ORDER BY d.createdAt DESC
        """)
    List<TrainingDeletion> findUnseen(UUID athleteId, boolean byAdmin, Instant since);

    /** Housekeeping on insert: the log only needs to survive until it is read (60 days is plenty). */
    @Modifying
    @Query("DELETE FROM TrainingDeletion d WHERE d.athlete.id = :athleteId AND d.createdAt < :before")
    void pruneOldForAthlete(UUID athleteId, Instant before);
}
