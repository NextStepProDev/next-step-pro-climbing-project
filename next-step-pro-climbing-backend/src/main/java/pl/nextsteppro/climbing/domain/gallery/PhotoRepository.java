package pl.nextsteppro.climbing.domain.gallery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhotoRepository extends JpaRepository<Photo, UUID> {

    List<Photo> findByAlbumIdOrderByDisplayOrderAscCreatedAtAsc(UUID albumId);

    @Query("SELECT p FROM Photo p WHERE p.album.id = :albumId ORDER BY p.displayOrder ASC, p.createdAt ASC LIMIT 1")
    Optional<Photo> findFirstByAlbumId(UUID albumId);

    long countByAlbumId(UUID albumId);

    List<Photo> findByAlbumId(UUID albumId);

    @Query("SELECT p.filename FROM Photo p")
    List<String> findAllFilenames();
}
