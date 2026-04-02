package pl.nextsteppro.climbing.domain.instructor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InstructorRepository extends JpaRepository<Instructor, UUID> {

    List<Instructor> findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc();

    List<Instructor> findAllByOrderByDisplayOrderAscCreatedAtAsc();
}
