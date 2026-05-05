package pl.nextsteppro.climbing.domain.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {

    @Query(value = """
            SELECT id, title, price, thumbnail_filename AS thumbnailFilename,
                   thumbnail_url AS thumbnailUrl,
                   thumbnail_focal_point_x AS thumbnailFocalPointX,
                   thumbnail_focal_point_y AS thumbnailFocalPointY,
                   display_order AS displayOrder,
                   is_published AS published, published_at AS publishedAt,
                   language, translation_group_id AS translationGroupId,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM courses
            WHERE is_published = true AND language = :language
            ORDER BY display_order ASC
            """, nativeQuery = true)
    List<CourseSummaryProjection> findAllPublishedSummariesByLanguage(@Param("language") String language);

    @Query(value = """
            SELECT id, title, price, thumbnail_filename AS thumbnailFilename,
                   thumbnail_url AS thumbnailUrl,
                   thumbnail_focal_point_x AS thumbnailFocalPointX,
                   thumbnail_focal_point_y AS thumbnailFocalPointY,
                   display_order AS displayOrder,
                   is_published AS published, published_at AS publishedAt,
                   language, translation_group_id AS translationGroupId,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM courses
            ORDER BY display_order ASC
            """, nativeQuery = true)
    List<CourseSummaryProjection> findAllSummaries();

    @Query("SELECT COALESCE(MAX(c.displayOrder), -1) FROM Course c")
    int findMaxDisplayOrder();

    @Query("SELECT c.thumbnailFilename FROM Course c WHERE c.thumbnailFilename IS NOT NULL")
    List<String> findAllThumbnailFilenames();

    List<Course> findByTranslationGroupId(UUID translationGroupId);

    boolean existsByTranslationGroupIdAndLanguage(UUID translationGroupId, String language);

    boolean existsByThumbnailFilenameAndIdNot(String thumbnailFilename, UUID id);
}
