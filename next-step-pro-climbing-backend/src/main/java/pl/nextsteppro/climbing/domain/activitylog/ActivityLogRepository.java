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

    /**
     * One person's timeline for the admin user card. {@code user} is the ACTOR, so this returns
     * what the person did themselves — plus admin cancellations of their bookings, which
     * {@code ActivityLogService.logCancelledByAdmin} deliberately files under the affected user
     * rather than the admin who clicked. Actions an admin took ON the account (role, athlete flag,
     * forced logout) stay filed under that admin and are out of reach here; the card shows their
     * resulting state instead. Backed by {@code idx_activity_logs_user}.
     */
    @Query("SELECT a FROM ActivityLog a " +
           "JOIN FETCH a.user " +
           "LEFT JOIN FETCH a.timeSlot " +
           "LEFT JOIN FETCH a.event " +
           "WHERE a.user.id = :userId " +
           "ORDER BY a.createdAt DESC")
    List<ActivityLog> findByUserIdWithDetails(UUID userId, Pageable pageable);
}
