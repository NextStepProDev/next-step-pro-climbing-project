package pl.nextsteppro.climbing.domain.activitylog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    @Query("SELECT a FROM ActivityLog a " +
           "JOIN FETCH a.user " +
           "LEFT JOIN FETCH a.timeSlot " +
           "LEFT JOIN FETCH a.event " +
           "ORDER BY a.createdAt DESC")
    List<ActivityLog> findRecentWithDetails(Pageable pageable);
}
