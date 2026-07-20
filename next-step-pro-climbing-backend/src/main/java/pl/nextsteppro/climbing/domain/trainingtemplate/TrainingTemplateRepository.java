package pl.nextsteppro.climbing.domain.trainingtemplate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TrainingTemplateRepository extends JpaRepository<TrainingTemplate, UUID> {

    List<TrainingTemplate> findAllByOrderByTitleAsc();
}
