package pl.nextsteppro.climbing.domain.personaltraining;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TrainingAttachmentRepository extends JpaRepository<TrainingAttachment, UUID> {

    List<TrainingAttachment> findByTrainingIdOrderByPositionAsc(UUID trainingId);

    /** Batch load for the calendar range (avoids N+1 over many trainings). */
    @Query("SELECT a FROM TrainingAttachment a WHERE a.training.id IN :trainingIds ORDER BY a.position ASC")
    List<TrainingAttachment> findByTrainingIdInOrderByPositionAsc(Collection<UUID> trainingIds);

    @Modifying
    void deleteByTrainingId(UUID trainingId);

    // ---- templates (attachments owned by a template instead of a training) ----

    List<TrainingAttachment> findByTemplateIdOrderByPositionAsc(UUID templateId);

    @Query("SELECT a FROM TrainingAttachment a WHERE a.template.id IN :templateIds ORDER BY a.position ASC")
    List<TrainingAttachment> findByTemplateIdInOrderByPositionAsc(Collection<UUID> templateIds);

    @Modifying
    void deleteByTemplateId(UUID templateId);

    /** Reference count of a stored file across ALL attachments (training + template) —
     * a physical file must not be deleted from disk while another row still points at it. */
    long countByFilename(String filename);

    /** All uploaded files with their owner (training or template) — admin materials management. */
    @Query("""
        SELECT a FROM TrainingAttachment a
        LEFT JOIN FETCH a.training t
        LEFT JOIN FETCH a.template tpl
        WHERE a.kind = pl.nextsteppro.climbing.domain.personaltraining.AttachmentKind.FILE
        ORDER BY a.createdAt DESC
        """)
    List<TrainingAttachment> findAllFilesWithOwner();
}
