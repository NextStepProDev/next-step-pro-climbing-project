package pl.nextsteppro.climbing.domain.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Which sessions belong to which payer. Addressed by the session, never by the assignment's own id —
 * the same discipline as the rest of this package.
 */
public interface SessionPayoutRepository extends JpaRepository<SessionPayout, UUID> {

    @Query("SELECT sp.source.id FROM SessionPayout sp WHERE sp.timeSlot.id = :slotId")
    Optional<UUID> findSourceIdForSlot(@Param("slotId") UUID slotId);

    @Query("SELECT sp.source.id FROM SessionPayout sp WHERE sp.event.id = :eventId")
    Optional<UUID> findSourceIdForEvent(@Param("eventId") UUID eventId);

    /**
     * Sessions attributed to any payer within a date range, reduced to (source, day) so the months
     * are bucketed in Java. An event counts once by its first day: the engagement is the unit here,
     * not the night.
     */
    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.settlement.SessionPayoutRow(
            sp.source.id, COALESCE(ts.date, e.startDate))
        FROM SessionPayout sp
        LEFT JOIN sp.timeSlot ts
        LEFT JOIN sp.event e
        WHERE COALESCE(ts.date, e.startDate) BETWEEN :from AND :to
        """)
    List<SessionPayoutRow> findSessionsBetween(@Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO session_payouts (time_slot_id, payout_source_id)
        VALUES (:slotId, :sourceId)
        ON CONFLICT (time_slot_id) WHERE time_slot_id IS NOT NULL
        DO UPDATE SET payout_source_id = :sourceId
        """, nativeQuery = true)
    void assignSlot(@Param("slotId") UUID slotId, @Param("sourceId") UUID sourceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO session_payouts (event_id, payout_source_id)
        VALUES (:eventId, :sourceId)
        ON CONFLICT (event_id) WHERE event_id IS NOT NULL
        DO UPDATE SET payout_source_id = :sourceId
        """, nativeQuery = true)
    void assignEvent(@Param("eventId") UUID eventId, @Param("sourceId") UUID sourceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SessionPayout sp WHERE sp.timeSlot.id = :slotId")
    int clearSlot(@Param("slotId") UUID slotId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SessionPayout sp WHERE sp.event.id = :eventId")
    int clearEvent(@Param("eventId") UUID eventId);
}
