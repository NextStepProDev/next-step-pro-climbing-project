package pl.nextsteppro.climbing.domain.trainingrequest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingRequestRepository extends JpaRepository<TrainingRequest, UUID> {

    /**
     * Propozycja z dociągniętym userem — do ścieżek, które przekazują usera do asynchronicznych
     * maili (lazy proxy zainicjalizowałby się dopiero na wątku mailowym, poza sesją Hibernate).
     */
    @Query("SELECT tr FROM TrainingRequest tr JOIN FETCH tr.user WHERE tr.id = :id")
    Optional<TrainingRequest> findByIdWithUser(UUID id);

    /** Limit anty-spamowy: ile propozycji użytkownika czeka na reakcję. */
    int countByUserIdAndStatus(UUID userId, TrainingRequestStatus status);

    @Query("""
        SELECT tr FROM TrainingRequest tr
        LEFT JOIN FETCH tr.course
        LEFT JOIN FETCH tr.createdSlot
        LEFT JOIN FETCH tr.createdEvent
        WHERE tr.user.id = :userId
        ORDER BY tr.createdAt DESC
        """)
    List<TrainingRequest> findByUserIdWithDetails(UUID userId);

    @Query("""
        SELECT tr FROM TrainingRequest tr
        JOIN FETCH tr.user
        LEFT JOIN FETCH tr.course
        LEFT JOIN FETCH tr.windowSlot
        LEFT JOIN FETCH tr.createdSlot
        LEFT JOIN FETCH tr.createdEvent
        ORDER BY CASE WHEN tr.status = 'PENDING' THEN 0 ELSE 1 END, tr.createdAt DESC
        """)
    List<TrainingRequest> findAllWithDetails();

    int countByStatus(TrainingRequestStatus status);

    /** Scheduler: propozycje PENDING, których data już minęła → EXPIRED. */
    @Modifying
    @Query("""
        UPDATE TrainingRequest tr SET tr.status = 'EXPIRED'
        WHERE tr.status = 'PENDING' AND tr.requestedDate < :today
        """)
    int expirePendingBefore(LocalDate today);
}
