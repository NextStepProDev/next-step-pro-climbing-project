package pl.nextsteppro.climbing.domain.personaltraining;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TrainingCommentRepository extends JpaRepository<TrainingComment, UUID> {

    /** Thread of one plan entry, chronological; author fetched for name/avatar in the DTO. */
    @Query("""
        SELECT c FROM TrainingComment c
        JOIN FETCH c.author
        WHERE c.training.id = :trainingId
        ORDER BY c.createdAt ASC
        """)
    List<TrainingComment> findThread(UUID trainingId);

    /**
     * Thread of a booked slot, for one athlete.
     *
     * <p>Split from {@link #findThread} rather than folded into it behind nullable parameters: the
     * target columns are mutually exclusive, so a single query would need {@code :x IS NULL} arms
     * that read as if a message could hang on two things at once — and would not use the partial
     * indexes, which exist precisely because two of the three columns are always NULL.
     *
     * <p>⚠️ Filtered by athlete as well as by slot. A slot can hold several people and each
     * conversation is private; dropping the athlete here would publish one client's thread to
     * everyone else standing on the same session.
     */
    @Query("""
        SELECT c FROM TrainingComment c
        JOIN FETCH c.author
        WHERE c.timeSlot.id = :slotId AND c.athlete.id = :athleteId
        ORDER BY c.createdAt ASC
        """)
    List<TrainingComment> findSlotThread(UUID slotId, UUID athleteId);

    /** Same for an event: one conversation for the whole event, however many days it spans. */
    @Query("""
        SELECT c FROM TrainingComment c
        JOIN FETCH c.author
        WHERE c.event.id = :eventId AND c.athlete.id = :athleteId
        ORDER BY c.createdAt ASC
        """)
    List<TrainingComment> findEventThread(UUID eventId, UUID athleteId);

    /**
     * Athlete's unread counter: coach messages written — or corrected — after the athlete's marker.
     *
     * <p>Reads the owner straight off the row. It used to walk {@code c.training.athlete}, which a
     * message under a booked session does not have, so the old shape would have counted the plan
     * and stayed silent about everything else.
     *
     * <p>The {@code editedAt} half is what stops an edit from being silent. Without it someone who
     * had already read "3x10" would never learn the message now says "4x8", and the author may edit
     * at any time. {@code COUNT} counts rows, so a message that is both new and freshly corrected
     * still counts once — fixing a typo right after sending does not ring twice.
     */
    @Query("""
        SELECT COUNT(c) FROM TrainingComment c
        WHERE c.athlete.id = :athleteId AND c.authorIsAdmin = true
          AND (c.createdAt > :since OR c.editedAt > :since)
        """)
    long countCoachCommentsSince(UUID athleteId, Instant since);

    /** Unread dots on plan entries: trainings with messages from the other side since the marker. */
    @Query("""
        SELECT DISTINCT c.training.id FROM TrainingComment c
        WHERE c.athlete.id = :athleteId AND c.authorIsAdmin = :fromAdmin
          AND c.training IS NOT NULL
          AND (c.createdAt > :since OR c.editedAt > :since)
        """)
    List<UUID> findTrainingIdsWithNewComments(UUID athleteId, boolean fromAdmin, Instant since);

    /**
     * The same question for booked sessions, so an overlaid booking can carry the unread dot too.
     *
     * <p>A separate query rather than a union: the plan answers with training ids and a session
     * with a slot or an event id, and the caller matches them against different collections.
     */
    @Query("""
        SELECT DISTINCT new pl.nextsteppro.climbing.domain.personaltraining.SessionCommentTarget(s.id, e.id)
        FROM TrainingComment c
        LEFT JOIN c.timeSlot s
        LEFT JOIN c.event e
        WHERE c.athlete.id = :athleteId AND c.authorIsAdmin = :fromAdmin
          AND c.training IS NULL
          AND (c.createdAt > :since OR c.editedAt > :since)
        """)
    List<SessionCommentTarget> findSessionsWithNewComments(UUID athleteId, boolean fromAdmin, Instant since);

    /** Coach-side per-athlete counter: athlete messages after this admin's per-athlete seen marker. */
    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.personaltraining.AthleteActivityCount(a.id, COUNT(c))
        FROM TrainingComment c
        JOIN c.athlete a
        LEFT JOIN TrainingCalendarRead r ON r.userId = :adminId AND r.athleteId = a.id
        WHERE a.athlete = true
          AND c.authorIsAdmin = false
          AND (r.seenAt IS NULL OR c.createdAt > r.seenAt OR c.editedAt > r.seenAt)
        GROUP BY a.id
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
        SELECT new pl.nextsteppro.climbing.domain.personaltraining.AthleteLastActivity(a.id, MAX(c.createdAt))
        FROM TrainingComment c
        JOIN c.athlete a
        WHERE a.athlete = true
        GROUP BY a.id
        """)
    List<AthleteLastActivity> findLastCommentActivityPerAthlete();
}
