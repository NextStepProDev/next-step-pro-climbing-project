package pl.nextsteppro.climbing.domain.waitlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventWaitlistRepository extends JpaRepository<EventWaitlist, UUID> {

    @Query("SELECT w FROM EventWaitlist w WHERE w.user.id = :userId AND w.event.id = :eventId")
    Optional<EventWaitlist> findByUserIdAndEventId(UUID userId, UUID eventId);

    @Query("SELECT w FROM EventWaitlist w WHERE w.event.id = :eventId AND w.status = 'WAITING' ORDER BY w.position ASC")
    List<EventWaitlist> findWaitingByEventIdOrdered(UUID eventId);

    @Query("SELECT w FROM EventWaitlist w JOIN FETCH w.user JOIN FETCH w.event WHERE w.event.id = :eventId AND w.status = :status ORDER BY w.position ASC")
    List<EventWaitlist> findByEventIdAndStatusWithUser(UUID eventId, WaitlistStatus status);

    @Query("SELECT COALESCE(COUNT(w), 0) FROM EventWaitlist w WHERE w.event.id = :eventId AND w.status = 'PENDING_CONFIRMATION'")
    int countPendingConfirmationByEventId(UUID eventId);

    @Query("SELECT w FROM EventWaitlist w JOIN FETCH w.user JOIN FETCH w.event WHERE w.status = 'PENDING_CONFIRMATION' AND w.confirmationDeadline < :now")
    List<EventWaitlist> findExpiredPendingConfirmations(Instant now);

    @Query("SELECT COALESCE(COUNT(w), 0) FROM EventWaitlist w WHERE w.event.id = :eventId AND w.status = 'WAITING' AND w.position <= :position")
    int countWaitingAtOrBeforePosition(UUID eventId, int position);

    @Query("SELECT COALESCE(MAX(w.position), 0) FROM EventWaitlist w WHERE w.event.id = :eventId")
    int findMaxPositionForEvent(UUID eventId);

    @Query("SELECT w FROM EventWaitlist w JOIN FETCH w.event WHERE w.user.id = :userId AND w.status IN ('WAITING', 'PENDING_CONFIRMATION') ORDER BY w.event.startDate ASC")
    List<EventWaitlist> findActiveByUserId(UUID userId);

    @Query("SELECT COUNT(w) > 0 FROM EventWaitlist w WHERE w.user.id = :userId AND w.event.id = :eventId AND w.status IN :statuses")
    boolean existsByUserAndEventAndStatuses(UUID userId, UUID eventId, List<WaitlistStatus> statuses);
}
