package pl.nextsteppro.climbing.domain.personaltraining;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TrainingCommentFileRepository extends JpaRepository<TrainingCommentFile, UUID> {

    /** Whole thread in one query — a per-comment lookup would be N+1 on every training opened. */
    @Query("""
        SELECT f FROM TrainingCommentFile f
        WHERE f.comment.id IN :commentIds
        ORDER BY f.position ASC
        """)
    List<TrainingCommentFile> findByCommentIdInOrderByPositionAsc(@Param("commentIds") Collection<UUID> commentIds);

    long countByCommentId(UUID commentId);

    /** Per-thread cap: the limit bounds disk growth, and files hang off comments, not the target. */
    @Query("SELECT COUNT(f) FROM TrainingCommentFile f WHERE f.comment.training.id = :trainingId")
    long countForTraining(@Param("trainingId") UUID trainingId);

    /**
     * Same cap for the thread under a booked slot. Scoped by athlete for the same reason the thread
     * read is: one slot can hold several people, and one client's uploads must not spend another
     * client's allowance.
     */
    @Query("""
        SELECT COUNT(f) FROM TrainingCommentFile f
        WHERE f.comment.timeSlot.id = :slotId AND f.comment.athlete.id = :athleteId
        """)
    long countForSlotThread(@Param("slotId") UUID slotId, @Param("athleteId") UUID athleteId);

    /** Same cap for the thread under a booked event. */
    @Query("""
        SELECT COUNT(f) FROM TrainingCommentFile f
        WHERE f.comment.event.id = :eventId AND f.comment.athlete.id = :athleteId
        """)
    long countForEventThread(@Param("eventId") UUID eventId, @Param("athleteId") UUID athleteId);

    /**
     * Loads the file with its comment (and the comment's author and calendar owner) in one go. The
     * authorisation check and the "was this the last thing in the message" rule both need them,
     * and the delete path runs outside an open thread query.
     *
     * <p>Fetches {@code c.athlete} rather than walking {@code c.training.athlete}: since V97 the
     * message may hang on a booked session instead, and an inner {@code JOIN FETCH c.training}
     * would have silently dropped every such file from this lookup — a 404 on a file that exists.
     */
    @Query("""
        SELECT f FROM TrainingCommentFile f
        JOIN FETCH f.comment c
        JOIN FETCH c.author
        JOIN FETCH c.athlete
        WHERE f.id = :id
        """)
    Optional<TrainingCommentFile> findByIdWithComment(@Param("id") UUID id);

    /** Retention pass. */
    @Query("""
        SELECT f FROM TrainingCommentFile f
        JOIN FETCH f.comment
        WHERE f.expiresAt < :now
        """)
    List<TrainingCommentFile> findExpiredBefore(@Param("now") Instant now);

    /** Every filename the database still knows about — the other half of the orphan sweep. */
    @Query("SELECT f.filename FROM TrainingCommentFile f")
    Set<String> findAllFilenames();

    /**
     * Filenames to unlink before a training is deleted. The rows disappear through the DB cascade
     * without Hibernate ever loading them, so no entity callback can reach the files — it has to
     * be explicit, exactly like {@code AttachmentSupport.purgeTrainingAttachments}.
     */
    @Query("SELECT f.filename FROM TrainingCommentFile f WHERE f.comment.training.id = :trainingId")
    List<String> findFilenamesForTraining(@Param("trainingId") UUID trainingId);

    /**
     * Same, for a slot about to be deleted — every thread on it, not one athlete's.
     *
     * <p>⚠️ Deleting a session now destroys conversations, which it never did before V97. The rows
     * go through the same DB cascade and are just as invisible to Hibernate, so without this the
     * photos in those threads would stay on disk with nothing left pointing at them: someone else's
     * training pictures, orphaned, for a year until the reconciliation pass happens to notice.
     */
    @Query("SELECT f.filename FROM TrainingCommentFile f WHERE f.comment.timeSlot.id = :slotId")
    List<String> findFilenamesForSlot(@Param("slotId") UUID slotId);

    /** Same, for an event about to be deleted (its per-day slots carry no threads of their own). */
    @Query("SELECT f.filename FROM TrainingCommentFile f WHERE f.comment.event.id = :eventId")
    List<String> findFilenamesForEvent(@Param("eventId") UUID eventId);

    /**
     * Filenames to unlink before an account is deleted. Covers BOTH directions, and both are load
     * bearing: files in this person's own calendar, and files they attached in someone else's
     * thread. {@code training_comments.author_id} cascades on user delete, so without the second
     * branch a deleted coach's uploads would sit on disk forever.
     *
     * <p>Reads the owner off the comment rather than through the training, so threads under booked
     * sessions are covered by the same clause — "eventually" is the wrong answer to an erasure
     * request, and a branch per target shape is how one of them gets forgotten.
     */
    @Query("""
        SELECT f.filename FROM TrainingCommentFile f
        WHERE f.comment.athlete.id = :userId OR f.comment.author.id = :userId
        """)
    List<String> findFilenamesForUser(@Param("userId") UUID userId);
}
