package pl.nextsteppro.climbing.domain.personaltraining;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TrainingCommentRepository extends JpaRepository<TrainingComment, UUID> {

    /** Thread of one training, chronological; author fetched for name/avatar in the DTO. */
    @Query("""
        SELECT c FROM TrainingComment c
        JOIN FETCH c.author
        WHERE c.training.id = :trainingId
        ORDER BY c.createdAt ASC
        """)
    List<TrainingComment> findThread(UUID trainingId);

    /**
     * Athlete's unread counter: coach messages written — or corrected — after the athlete's marker.
     *
     * <p>The {@code editedAt} half is what stops an edit from being silent. Without it someone who
     * had already read "3x10" would never learn the message now says "4x8", and the author may edit
     * at any time. {@code COUNT} counts rows, so a message that is both new and freshly corrected
     * still counts once — fixing a typo right after sending does not ring twice.
     */
    @Query("""
        SELECT COUNT(c) FROM TrainingComment c
        WHERE c.training.athlete.id = :athleteId AND c.authorIsAdmin = true
          AND (c.createdAt > :since OR c.editedAt > :since)
        """)
    long countCoachCommentsSince(UUID athleteId, Instant since);

    /** Unread dots on calendar blocks: trainings in the athlete's calendar that have messages
     * from the other side written or corrected after the viewer's seen marker. */
    @Query("""
        SELECT DISTINCT c.training.id FROM TrainingComment c
        WHERE c.training.athlete.id = :athleteId AND c.authorIsAdmin = :fromAdmin
          AND (c.createdAt > :since OR c.editedAt > :since)
        """)
    List<UUID> findTrainingIdsWithNewComments(UUID athleteId, boolean fromAdmin, Instant since);

    /** Coach-side per-athlete counter: athlete messages after this admin's per-athlete seen marker. */
    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.personaltraining.AthleteActivityCount(c.training.athlete.id, COUNT(c))
        FROM TrainingComment c
        LEFT JOIN TrainingCalendarRead r ON r.userId = :adminId AND r.athleteId = c.training.athlete.id
        WHERE c.training.athlete.athlete = true
          AND c.authorIsAdmin = false
          AND (r.seenAt IS NULL OR c.createdAt > r.seenAt OR c.editedAt > r.seenAt)
        GROUP BY c.training.athlete.id
        """)
    List<AthleteActivityCount> countNewAthleteCommentsPerAthlete(UUID adminId);

    /**
     * Coach's athlete list: latest comment per athlete (merged with training activity in the service).
     *
     * <p>Deliberately blind to {@code editedAt}, unlike the counters above: a correction is not new
     * activity and should not push someone back up the roster. The unread badge still lights, which
     * is the signal that matters.
     */
    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.personaltraining.AthleteLastActivity(c.training.athlete.id, MAX(c.createdAt))
        FROM TrainingComment c
        WHERE c.training.athlete.athlete = true
        GROUP BY c.training.athlete.id
        """)
    List<AthleteLastActivity> findLastCommentActivityPerAthlete();
}
