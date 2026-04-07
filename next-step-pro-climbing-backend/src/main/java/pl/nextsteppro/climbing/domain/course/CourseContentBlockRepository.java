package pl.nextsteppro.climbing.domain.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CourseContentBlockRepository extends JpaRepository<CourseContentBlock, UUID> {

    List<CourseContentBlock> findByCourseIdOrderByDisplayOrderAsc(UUID courseId);

    @Query("SELECT COALESCE(MAX(b.displayOrder), -1) FROM CourseContentBlock b WHERE b.course.id = :courseId")
    int findMaxDisplayOrder(@Param("courseId") UUID courseId);
}
