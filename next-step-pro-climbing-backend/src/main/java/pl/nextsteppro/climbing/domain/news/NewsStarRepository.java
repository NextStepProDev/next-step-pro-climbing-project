package pl.nextsteppro.climbing.domain.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;

@Repository
public interface NewsStarRepository extends JpaRepository<NewsStar, NewsStarId> {

    boolean existsByIdUserIdAndIdNewsId(UUID userId, UUID newsId);

    void deleteByIdUserIdAndIdNewsId(UUID userId, UUID newsId);

    @Query("SELECT s.id.newsId FROM NewsStar s WHERE s.id.userId = :userId")
    Set<UUID> findNewsIdsByIdUserId(UUID userId);
}
