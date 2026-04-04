package pl.nextsteppro.climbing.domain.gallery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlbumRepository extends JpaRepository<Album, UUID> {

    /**
     * Fetch all albums with aggregated photo data in a single query.
     * Avoids N+1 query problem by using subquery and LEFT JOIN with GROUP BY.
     *
     * @return list of album summary projections with photo count and first photo filename
     */
    @Query(value = """
        SELECT
            a.id AS id,
            a.name AS name,
            a.description AS description,
            a.created_at AS createdAt,
            a.updated_at AS updatedAt,
            a.display_order AS displayOrder,
            COALESCE(
                (SELECT p_thumb.filename
                 FROM photos p_thumb
                 WHERE p_thumb.id = a.thumbnail_photo_id),
                (SELECT p_first.filename
                 FROM photos p_first
                 WHERE p_first.album_id = a.id
                 ORDER BY p_first.display_order ASC, p_first.created_at ASC
                 LIMIT 1)
            ) AS firstPhotoFilename,
            COALESCE(
                (SELECT p_thumb.focal_point_x
                 FROM photos p_thumb
                 WHERE p_thumb.id = a.thumbnail_photo_id),
                (SELECT p_first.focal_point_x
                 FROM photos p_first
                 WHERE p_first.album_id = a.id
                 ORDER BY p_first.display_order ASC, p_first.created_at ASC
                 LIMIT 1)
            ) AS thumbnailFocalPointX,
            COALESCE(
                (SELECT p_thumb.focal_point_y
                 FROM photos p_thumb
                 WHERE p_thumb.id = a.thumbnail_photo_id),
                (SELECT p_first.focal_point_y
                 FROM photos p_first
                 WHERE p_first.album_id = a.id
                 ORDER BY p_first.display_order ASC, p_first.created_at ASC
                 LIMIT 1)
            ) AS thumbnailFocalPointY,
            COUNT(p.id) AS photoCount
        FROM albums a
        LEFT JOIN photos p ON p.album_id = a.id
        GROUP BY a.id, a.name, a.description, a.created_at, a.updated_at, a.display_order
        ORDER BY a.display_order ASC
        """, nativeQuery = true)
    List<AlbumSummaryProjection> findAllAlbumSummaries();

    @Query("SELECT COALESCE(MAX(a.displayOrder), -1) FROM Album a")
    Optional<Integer> findMaxDisplayOrder();

    @Query("SELECT COALESCE(MIN(a.displayOrder), 1) FROM Album a")
    Optional<Integer> findMinDisplayOrder();
}
