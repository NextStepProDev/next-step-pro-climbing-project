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
}
