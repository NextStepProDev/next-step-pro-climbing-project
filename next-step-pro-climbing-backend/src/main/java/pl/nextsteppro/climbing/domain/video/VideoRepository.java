package pl.nextsteppro.climbing.domain.video;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VideoRepository extends JpaRepository<Video, UUID> {

    List<Video> findAllByPublishedTrueOrderByDisplayOrderAsc();

    List<Video> findAllByOrderByDisplayOrderAsc();

    @Query("SELECT COALESCE(MAX(v.displayOrder), -1) FROM Video v")
    Optional<Integer> findMaxDisplayOrder();

    @Query("SELECT COALESCE(MIN(v.displayOrder), 1) FROM Video v")
    Optional<Integer> findMinDisplayOrder();
}
