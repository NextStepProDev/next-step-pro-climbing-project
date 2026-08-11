package pl.nextsteppro.climbing.api.trainingcalendar;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentFile;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The two passes that keep the retention promise. Deliberately a bean of its own rather than
 * methods on the scheduler: a {@code @Scheduled} method calling its own class's
 * {@code @Transactional} methods goes straight through the object, bypassing the Spring proxy, so
 * the transactions silently would not apply — and a test that calls those methods directly still
 * passes, because a direct call is the one case where the annotation is not needed. Injecting the
 * bean means the proxy is always in the path.
 */
@Service
public class CommentFileRetentionService {

    /**
     * A file younger than this is left alone by the orphan pass. The known filenames are read
     * before the folder is listed, so an upload landing between the two looks orphaned — and
     * deleting a file somebody just sent is far worse than sweeping it a day later.
     */
    static final Duration ORPHAN_GRACE = Duration.ofHours(6);

    private final CommentFileSupport commentFiles;

    public CommentFileRetentionService(CommentFileSupport commentFiles) {
        this.commentFiles = commentFiles;
    }

    /**
     * Removes what has outlived its retention window. The words survive and only the file goes;
     * a message that was nothing but the file goes with it (see {@code CommentFileSupport}).
     *
     * @return how many files were removed
     */
    @Transactional
    public int deleteExpired() {
        List<TrainingCommentFile> expired = commentFiles.findExpired(Instant.now());
        for (TrainingCommentFile file : expired) {
            commentFiles.deleteFile(file);
        }
        return expired.size();
    }

    /**
     * Reconciles the folder against the rows. This is the pass that makes "gone after a year"
     * something we can stand behind: every explicit unlink elsewhere sits inside a transaction that
     * can still roll back, and the storage layer logs its failures rather than raising them.
     *
     * @return how many files were removed
     */
    @Transactional(readOnly = true)
    public int deleteOrphans() {
        return commentFiles.sweepOrphans(ORPHAN_GRACE);
    }
}
