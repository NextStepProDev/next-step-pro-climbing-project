package pl.nextsteppro.climbing.domain.assets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SharedAssetRepository extends JpaRepository<SharedAsset, UUID> {
    List<SharedAsset> findAllByOrderByCreatedAtDesc();
}
