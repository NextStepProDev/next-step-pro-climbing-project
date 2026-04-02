package pl.nextsteppro.climbing.domain.gallery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlbumRepository extends JpaRepository<Album, UUID> {

    List<Album> findAllByOrderByCreatedAtDesc();

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
            (SELECT p.filename
             FROM photos p
             WHERE p.album_id = a.id
             ORDER BY p.display_order ASC, p.created_at ASC
             LIMIT 1) AS firstPhotoFilename,
            COUNT(p.id) AS photoCount
        FROM albums a
        LEFT JOIN photos p ON p.album_id = a.id
        GROUP BY a.id, a.name, a.description, a.created_at, a.updated_at
        ORDER BY a.created_at DESC
        """, nativeQuery = true)
    List<AlbumSummaryProjection> findAllAlbumSummaries();
}
