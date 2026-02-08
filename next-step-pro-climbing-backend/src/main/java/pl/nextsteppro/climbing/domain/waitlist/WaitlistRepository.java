package pl.nextsteppro.climbing.domain.waitlist;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, UUID> {

    List<WaitlistEntry> findByTimeSlotIdOrderByPosition(UUID timeSlotId);

    List<WaitlistEntry> findByUserId(UUID userId);

    @Nullable
    WaitlistEntry findByUserIdAndTimeSlotId(UUID userId, UUID timeSlotId);

    @Query("SELECT COALESCE(MAX(w.position), 0) FROM WaitlistEntry w WHERE w.timeSlot.id = :timeSlotId")
    int findMaxPositionByTimeSlotId(UUID timeSlotId);

    @Query("SELECT w FROM WaitlistEntry w WHERE w.timeSlot.id = :timeSlotId AND w.notifiedAt IS NULL ORDER BY w.position")
    List<WaitlistEntry> findNotNotifiedByTimeSlotId(UUID timeSlotId);

    @Nullable
    @Query("SELECT w FROM WaitlistEntry w WHERE w.timeSlot.id = :timeSlotId AND w.notifiedAt IS NULL ORDER BY w.position LIMIT 1")
    WaitlistEntry findFirstNotNotifiedByTimeSlotId(UUID timeSlotId);

    @Modifying
    @Query("UPDATE WaitlistEntry w SET w.position = w.position - 1 WHERE w.timeSlot.id = :timeSlotId AND w.position > :position")
    void decrementPositionsAfter(UUID timeSlotId, int position);

    boolean existsByUserIdAndTimeSlotId(UUID userId, UUID timeSlotId);

    int countByTimeSlotId(UUID timeSlotId);

    void deleteByUserId(UUID userId);
}
