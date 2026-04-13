package pl.nextsteppro.climbing.domain.instructor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstructorRepository extends JpaRepository<Instructor, UUID> {

    List<Instructor> findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc();

    List<Instructor> findAllByOrderByDisplayOrderAscCreatedAtAsc();

    @Query("SELECT COALESCE(MIN(i.displayOrder), 1) FROM Instructor i")
    Optional<Integer> findMinDisplayOrder();

    @Query("SELECT i.photoFilename FROM Instructor i WHERE i.photoFilename IS NOT NULL")
    List<String> findAllPhotoFilenames();
}
