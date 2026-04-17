package pl.nextsteppro.climbing.domain.waitlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitlistRepository extends JpaRepository<Waitlist, UUID> {

    @Query("SELECT w FROM Waitlist w WHERE w.user.id = :userId AND w.timeSlot.id = :slotId")
    Optional<Waitlist> findByUserIdAndSlotId(UUID userId, UUID slotId);

    @Query("SELECT w FROM Waitlist w JOIN FETCH w.user WHERE w.timeSlot.id = :slotId AND w.status = 'WAITING' ORDER BY w.position ASC")
    List<Waitlist> findWaitingBySlotIdOrdered(UUID slotId);

    @Query("SELECT w FROM Waitlist w JOIN FETCH w.user JOIN FETCH w.timeSlot WHERE w.timeSlot.id = :slotId AND w.status = :status ORDER BY w.position ASC")
    List<Waitlist> findBySlotIdAndStatusWithUser(UUID slotId, WaitlistStatus status);

    @Query("SELECT COALESCE(COUNT(w), 0) FROM Waitlist w WHERE w.timeSlot.id = :slotId AND w.status = 'PENDING_CONFIRMATION'")
    int countPendingConfirmationBySlotId(UUID slotId);

    @Query("SELECT w FROM Waitlist w JOIN FETCH w.user JOIN FETCH w.timeSlot WHERE w.status = 'PENDING_CONFIRMATION' AND w.confirmationDeadline < :now")
    List<Waitlist> findExpiredPendingConfirmations(Instant now);

    @Query("SELECT COALESCE(COUNT(w), 0) FROM Waitlist w WHERE w.timeSlot.id = :slotId AND w.status = 'WAITING' AND w.position <= :position")
    int countWaitingAtOrBeforePosition(UUID slotId, int position);

    @Query("SELECT COALESCE(MAX(w.position), 0) FROM Waitlist w WHERE w.timeSlot.id = :slotId")
    int findMaxPositionForSlot(UUID slotId);

    @Query("SELECT w FROM Waitlist w JOIN FETCH w.timeSlot WHERE w.user.id = :userId AND w.status IN ('WAITING', 'PENDING_CONFIRMATION') ORDER BY w.timeSlot.date ASC, w.timeSlot.startTime ASC")
    List<Waitlist> findActiveByUserId(UUID userId);

    @Query("SELECT COUNT(w) > 0 FROM Waitlist w WHERE w.user.id = :userId AND w.timeSlot.id = :slotId AND w.status IN :statuses")
    boolean existsByUserAndSlotAndStatuses(UUID userId, UUID slotId, List<WaitlistStatus> statuses);

    @Modifying
    @Query("DELETE FROM Waitlist w WHERE w.timeSlot.id = :slotId")
    void deleteByTimeSlotId(UUID slotId);

    @Modifying
    @Query("DELETE FROM Waitlist w WHERE w.timeSlot.id IN :slotIds")
    void deleteByTimeSlotIdIn(Collection<UUID> slotIds);
}
