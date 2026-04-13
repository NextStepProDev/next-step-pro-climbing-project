package pl.nextsteppro.climbing.domain.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NewsContentBlockRepository extends JpaRepository<NewsContentBlock, UUID> {

    List<NewsContentBlock> findByNewsIdOrderByDisplayOrderAsc(UUID newsId);

    @Query("SELECT COALESCE(MAX(b.displayOrder), -1) FROM NewsContentBlock b WHERE b.news.id = :newsId")
    int findMaxDisplayOrder(@Param("newsId") UUID newsId);

    @Query("SELECT b.imageFilename FROM NewsContentBlock b WHERE b.imageFilename IS NOT NULL")
    List<String> findAllImageFilenames();
}
