package pl.nextsteppro.climbing.domain.settlement;

import org.jspecify.annotations.Nullable;
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

    /**
     * Both payer kinds in one read. Two separate lookups would let a caller ask about a source, get
     * nothing back, and conclude the session is unmarked while a subscription covers it.
     */
    @Query("SELECT new pl.nextsteppro.climbing.domain.settlement.SessionCoverage(sp.source.id, sp.user.id) "
        + "FROM SessionPayout sp WHERE sp.timeSlot.id = :slotId")
    Optional<SessionCoverage> findCoverageForSlot(@Param("slotId") UUID slotId);

    @Query("SELECT new pl.nextsteppro.climbing.domain.settlement.SessionCoverage(sp.source.id, sp.user.id) "
        + "FROM SessionPayout sp WHERE sp.event.id = :eventId")
    Optional<SessionCoverage> findCoverageForEvent(@Param("eventId") UUID eventId);

    /**
     * Sessions an INSTITUTION settles, within a date range, reduced to (source, day) so the months
     * are bucketed in Java. An event counts once by its first day: the engagement is the unit here.
     *
     * <p>Filtered to institutions on purpose — the rate table on the Settlements tab groups by payer
     * source, and a session covered by somebody's own subscription has none.
     */
    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.settlement.SessionPayoutRow(
            sp.source.id, COALESCE(ts.date, e.startDate))
        FROM SessionPayout sp
        LEFT JOIN sp.timeSlot ts
        LEFT JOIN sp.event e
        WHERE sp.source.id IS NOT NULL
          AND COALESCE(ts.date, e.startDate) BETWEEN :from AND :to
        """)
    List<SessionPayoutRow> findSessionsBetween(@Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    /**
     * How many sessions one client's subscription covered in a month — the denominator of "what did
     * the retainer work out at per session", which is the figure that says whether it is priced right.
     */
    @Query("""
        SELECT COUNT(sp) FROM SessionPayout sp
        LEFT JOIN sp.timeSlot ts
        LEFT JOIN sp.event e
        WHERE sp.user.id = :userId
          AND COALESCE(ts.date, e.startDate) BETWEEN :from AND :to
        """)
    long countCoveredSessions(@Param("userId") UUID userId,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to);

    /**
     * ⚠️ Every upsert sets BOTH payer columns, one of them to NULL. Setting only the one being
     * assigned would leave the previous payer of the other kind in place — tripping
     * {@code chk_session_payouts_single_payer}, or worse, quietly keeping a session marked for a
     * school after it was moved onto a client's subscription.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO session_payouts (time_slot_id, payout_source_id, user_id)
        VALUES (:slotId, :sourceId, :userId)
        ON CONFLICT (time_slot_id) WHERE time_slot_id IS NOT NULL
        DO UPDATE SET payout_source_id = :sourceId, user_id = :userId
        """, nativeQuery = true)
    void assignSlot(@Param("slotId") UUID slotId,
                    @Param("sourceId") @Nullable UUID sourceId,
                    @Param("userId") @Nullable UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO session_payouts (event_id, payout_source_id, user_id)
        VALUES (:eventId, :sourceId, :userId)
        ON CONFLICT (event_id) WHERE event_id IS NOT NULL
        DO UPDATE SET payout_source_id = :sourceId, user_id = :userId
        """, nativeQuery = true)
    void assignEvent(@Param("eventId") UUID eventId,
                     @Param("sourceId") @Nullable UUID sourceId,
                     @Param("userId") @Nullable UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SessionPayout sp WHERE sp.timeSlot.id = :slotId")
    int clearSlot(@Param("slotId") UUID slotId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SessionPayout sp WHERE sp.event.id = :eventId")
    int clearEvent(@Param("eventId") UUID eventId);
}
