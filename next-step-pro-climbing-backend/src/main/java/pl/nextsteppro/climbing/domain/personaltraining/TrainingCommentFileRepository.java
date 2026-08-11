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

    /** Per-training cap: the limit bounds disk growth, and files hang off comments, not the training. */
    @Query("SELECT COUNT(f) FROM TrainingCommentFile f WHERE f.comment.training.id = :trainingId")
    long countForTraining(@Param("trainingId") UUID trainingId);

    /**
     * Loads the file with its comment (and the comment's author and training) in one go. The
     * authorisation check and the "was this the last thing in the message" rule both need them,
     * and the delete path runs outside an open thread query.
     */
    @Query("""
        SELECT f FROM TrainingCommentFile f
        JOIN FETCH f.comment c
        JOIN FETCH c.author
        JOIN FETCH c.training t
        JOIN FETCH t.athlete
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
     * Filenames to unlink before an account is deleted. Covers BOTH directions, and both are load
     * bearing: files on this person's own trainings, and files they attached in someone else's
     * thread. {@code training_comments.author_id} cascades on user delete, so without the second
     * branch a deleted coach's uploads would sit on disk forever.
     */
    @Query("""
        SELECT f.filename FROM TrainingCommentFile f
        WHERE f.comment.training.athlete.id = :userId OR f.comment.author.id = :userId
        """)
    List<String> findFilenamesForUser(@Param("userId") UUID userId);
}
