package pl.nextsteppro.climbing.domain.reschedule;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RescheduleRequestRepository extends JpaRepository<RescheduleRequest, UUID> {

    /**
     * Row-locked, so two answers to one request serialise: a double click on Accept, two admin
     * tabs, or the client withdrawing at the moment the coach accepts. Without it both read PENDING
     * and the term was moved — and mailed about — twice. No fetch joins: Postgres refuses
     * FOR UPDATE on the nullable side of an outer join; the relations load lazily in the transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RescheduleRequest r WHERE r.id = :id")
    Optional<RescheduleRequest> findByIdForUpdate(UUID id);

    @Query("""
        SELECT r FROM RescheduleRequest r
        WHERE r.user.id = :userId AND r.timeSlot.id = :slotId AND r.status = 'PENDING'
        """)
    Optional<RescheduleRequest> findPendingForSlot(UUID userId, UUID slotId);

    @Query("""
        SELECT r FROM RescheduleRequest r
        WHERE r.user.id = :userId AND r.event.id = :eventId AND r.status = 'PENDING'
        """)
    Optional<RescheduleRequest> findPendingForEvent(UUID userId, UUID eventId);

    /** The client's open requests — "My reservations" matches them to cards by slot/event id. */
    @Query("""
        SELECT r FROM RescheduleRequest r
        LEFT JOIN FETCH r.timeSlot
        LEFT JOIN FETCH r.event
        WHERE r.user.id = :userId AND r.status = 'PENDING'
        ORDER BY r.createdAt DESC
        """)
    List<RescheduleRequest> findPendingByUserId(UUID userId);

    @Query("""
        SELECT r FROM RescheduleRequest r
        JOIN FETCH r.user
        LEFT JOIN FETCH r.timeSlot
        LEFT JOIN FETCH r.event
        WHERE r.status = 'PENDING'
        ORDER BY r.createdAt ASC
        """)
    List<RescheduleRequest> findAllPendingWithDetails();

    /** The most recent answered ones, so an accepted request does not vanish from the coach's view. */
    @Query("""
        SELECT r FROM RescheduleRequest r
        JOIN FETCH r.user
        LEFT JOIN FETCH r.timeSlot
        LEFT JOIN FETCH r.event
        WHERE r.status <> 'PENDING'
        ORDER BY r.resolvedAt DESC NULLS LAST, r.createdAt DESC
        """)
    List<RescheduleRequest> findRecentResolvedWithDetails(Pageable pageable);

    int countByStatus(RescheduleRequestStatus status);

    /** Every request this person ever made — the GDPR export. */
    @Query("""
        SELECT r FROM RescheduleRequest r
        LEFT JOIN FETCH r.timeSlot
        LEFT JOIN FETCH r.event
        WHERE r.user.id = :userId
        ORDER BY r.createdAt DESC
        """)
    List<RescheduleRequest> findAllByUserId(UUID userId);

    /** A blocked slot cancels every booking on it, so every open request goes with them. */
    @Query("SELECT r FROM RescheduleRequest r WHERE r.timeSlot.id = :slotId AND r.status = 'PENDING'")
    List<RescheduleRequest> findAllPendingForSlot(UUID slotId);

    /**
     * Requests whose ORIGINAL term is over: past its last day, or a slot that ended earlier today.
     * Reads the snapshot, not the live term — a term moved by the coach is a stale request either way.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE RescheduleRequest r SET r.status = 'EXPIRED', r.resolvedAt = :now
        WHERE r.status = 'PENDING' AND (
            COALESCE(r.oldEndDate, r.oldDate) < :today
            OR (r.oldEndDate IS NULL AND r.oldDate = :today AND r.oldEndTime <= :nowTime)
        )
        """)
    int expireOverdue(LocalDate today, LocalTime nowTime, Instant now);

    // ---- Closing requests whose booking went away ----
    // Plain entity updates, not bulk UPDATEs: the cancel paths calling these still hold reservation
    // entities bound for @Async mails, and a clearAutomatically query would detach them mid-flight.

    default void withdrawPendingForSlot(UUID userId, UUID slotId) {
        findPendingForSlot(userId, slotId).ifPresent(r -> r.resolve(RescheduleRequestStatus.WITHDRAWN, null));
    }

    default void withdrawPendingForEvent(UUID userId, UUID eventId) {
        findPendingForEvent(userId, eventId).ifPresent(r -> r.resolve(RescheduleRequestStatus.WITHDRAWN, null));
    }

    default void withdrawAllPendingForSlot(UUID slotId) {
        findAllPendingForSlot(slotId).forEach(r -> r.resolve(RescheduleRequestStatus.WITHDRAWN, null));
    }
}
