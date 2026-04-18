package pl.nextsteppro.climbing.domain.news;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NewsRepository extends JpaRepository<News, UUID> {

    @Query(value = """
            SELECT id, title, excerpt,
                   thumbnail_filename AS thumbnailFilename,
                   thumbnail_url AS thumbnailUrl,
                   thumbnail_focal_point_x AS thumbnailFocalPointX,
                   thumbnail_focal_point_y AS thumbnailFocalPointY,
                   is_published AS published, published_at AS publishedAt,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM news
            WHERE is_published = true
            ORDER BY published_at DESC
            """,
            countQuery = "SELECT COUNT(*) FROM news WHERE is_published = true",
            nativeQuery = true)
    Page<NewsSummaryProjection> findAllPublishedSummaries(Pageable pageable);

    @Query(value = """
            SELECT id, title, excerpt,
                   thumbnail_filename AS thumbnailFilename,
                   thumbnail_url AS thumbnailUrl,
                   thumbnail_focal_point_x AS thumbnailFocalPointX,
                   thumbnail_focal_point_y AS thumbnailFocalPointY,
                   is_published AS published, published_at AS publishedAt,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM news
            ORDER BY created_at DESC
            """,
            countQuery = "SELECT COUNT(*) FROM news",
            nativeQuery = true)
    Page<NewsSummaryProjection> findAllSummaries(Pageable pageable);

    @Query("SELECT n.thumbnailFilename FROM News n WHERE n.thumbnailFilename IS NOT NULL")
    List<String> findAllThumbnailFilenames();
}
