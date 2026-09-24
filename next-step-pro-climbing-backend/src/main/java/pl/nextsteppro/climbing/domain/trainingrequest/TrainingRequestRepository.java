package pl.nextsteppro.climbing.domain.trainingrequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingRequestRepository extends JpaRepository<TrainingRequest, UUID> {

    /**
     * Request with the user eagerly fetched — for paths that pass the user to asynchronous
     * emails (a lazy proxy would initialize only on the mail thread, outside the Hibernate session).
     */
    @Query("SELECT tr FROM TrainingRequest tr JOIN FETCH tr.user WHERE tr.id = :id")
    Optional<TrainingRequest> findByIdWithUser(UUID id);

    /** Anti-spam limit: how many of the user's requests are awaiting a response. */
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

    // Pagination: JOIN FETCH only on to-one relations, so Hibernate applies LIMIT in SQL
    // (the in-memory pagination warning applies only to collection fetches).
    @Query(value = """
        SELECT tr FROM TrainingRequest tr
        JOIN FETCH tr.user
        LEFT JOIN FETCH tr.course
        LEFT JOIN FETCH tr.windowSlot
        LEFT JOIN FETCH tr.createdSlot
        LEFT JOIN FETCH tr.createdEvent
        ORDER BY CASE WHEN tr.status = 'PENDING' THEN 0 ELSE 1 END, tr.createdAt DESC
        """,
        countQuery = "SELECT COUNT(tr) FROM TrainingRequest tr")
    Page<TrainingRequest> findPageWithDetails(Pageable pageable);

    @Query(value = """
        SELECT tr FROM TrainingRequest tr
        JOIN FETCH tr.user
        LEFT JOIN FETCH tr.course
        LEFT JOIN FETCH tr.windowSlot
        LEFT JOIN FETCH tr.createdSlot
        LEFT JOIN FETCH tr.createdEvent
        WHERE tr.status = :status
        ORDER BY tr.createdAt DESC
        """,
        countQuery = "SELECT COUNT(tr) FROM TrainingRequest tr WHERE tr.status = :status")
    Page<TrainingRequest> findPageByStatusWithDetails(TrainingRequestStatus status, Pageable pageable);

    int countByStatus(TrainingRequestStatus status);

    /** The user's accepted requests with what was created from them — to tell an invitation apart. */
    @Query("""
        SELECT tr FROM TrainingRequest tr
        LEFT JOIN FETCH tr.createdSlot
        LEFT JOIN FETCH tr.createdEvent
        WHERE tr.user.id = :userId AND tr.status = 'ACCEPTED'
        """)
    List<TrainingRequest> findAcceptedByUserId(UUID userId);

    /** Accepted requests answered with this slot — the invitation mail names the proposal it moved. */
    @Query("""
        SELECT tr FROM TrainingRequest tr
        JOIN FETCH tr.createdSlot
        WHERE tr.createdSlot.id = :slotId AND tr.status = 'ACCEPTED'
        """)
    List<TrainingRequest> findAcceptedBySlotId(UUID slotId);

    /** Like {@link #findAcceptedBySlotId}, for an event. */
    @Query("""
        SELECT tr FROM TrainingRequest tr
        JOIN FETCH tr.createdEvent
        WHERE tr.createdEvent.id = :eventId AND tr.status = 'ACCEPTED'
        """)
    List<TrainingRequest> findAcceptedByEventId(UUID eventId);

    /** Scheduler: PENDING requests whose date has already passed → EXPIRED. */
    @Modifying
    @Query("""
        UPDATE TrainingRequest tr SET tr.status = 'EXPIRED'
        WHERE tr.status = 'PENDING' AND tr.requestedDate < :today
        """)
    int expirePendingBefore(LocalDate today);
}
