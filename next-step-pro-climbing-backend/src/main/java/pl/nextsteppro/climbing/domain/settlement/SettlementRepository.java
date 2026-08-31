package pl.nextsteppro.climbing.domain.settlement;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Every settlement is addressed by the pair (target, payer) — never by its own id. Same trick as
 * {@code AdminPrivateNoteRepository} and {@code findByIdAndAthleteId} in the ascent log: there is
 * no code path that loads a row and then has to remember to check what it belongs to.
 *
 * <p>Writes are single-statement upserts rather than read-then-save, for the reason spelled out on
 * {@code AthleteWeightRepository.upsertReading}: a second tab or a double-click loses the race on
 * the unique index and surfaces as a 500. Overwriting is the correct outcome — the admin is
 * correcting his own figure.
 *
 * <p>⚠️ The {@code WHERE} clause on each {@code ON CONFLICT} is not decoration: the unique indexes
 * are <em>partial</em> (half the target and payer columns are NULL in every row), and Postgres can
 * only infer a partial index when the statement repeats its predicate. Dropping it turns the upsert
 * into "there is no unique index matching the ON CONFLICT specification" at runtime.
 *
 * <p>The three near-identical upserts are the price of real foreign keys — see the header of V92.
 */
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    /**
     * The projection every read goes through. Written once and reused with different WHERE clauses,
     * because the modal section and the Settlements tab want the same thirteen columns.
     *
     * <p>LEFT JOINs throughout: navigating {@code s.timeSlot.date} would make an implicit inner
     * join and silently drop every event settlement.
     */
    String ROW_SELECT = """
        SELECT new pl.nextsteppro.climbing.domain.settlement.SettlementRow(
            s.id, ts.id, e.id, u.id, u.firstName, u.lastName, g.id, g.note,
            COALESCE(ts.date, e.startDate), COALESCE(ts.title, e.title), e.eventType,
            s.amount, s.settledOn)
        FROM Settlement s
        LEFT JOIN s.timeSlot ts
        LEFT JOIN s.event e
        LEFT JOIN s.user u
        LEFT JOIN s.guest g
        """;

    @Query(ROW_SELECT + " WHERE ts.id = :slotId")
    List<SettlementRow> findRowsForSlot(@Param("slotId") UUID slotId);

    @Query(ROW_SELECT + " WHERE e.id = :eventId")
    List<SettlementRow> findRowsForEvent(@Param("eventId") UUID eventId);

    /**
     * Everything touching a year on <em>either</em> axis: sessions held in it and money that arrived
     * in it. A row can belong to two years at once — a December session paid in January is revenue
     * of January and a session of December — and both readings are wanted, so the filter is an OR
     * and the tab labels which axis each figure uses.
     */
    @Query(ROW_SELECT + """
        WHERE (COALESCE(ts.date, e.startDate) BETWEEN :from AND :to)
           OR (s.settledOn BETWEEN :from AND :to)
        """)
    List<SettlementRow> findRowsInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(ROW_SELECT)
    List<SettlementRow> findAllRows();

    /**
     * Outstanding debt, whole history. ⚠️ This is the one read that deliberately ignores the year
     * filter: a debt from two years ago is still a debt, and hiding it behind a year picker is how
     * it stops being collected.
     */
    @Query(ROW_SELECT + " WHERE s.settledOn IS NULL")
    List<SettlementRow> findUnsettledRows();

    /** Distinct session days, for the year picker. */
    @Query("SELECT DISTINCT COALESCE(ts.date, e.startDate) FROM Settlement s "
        + "LEFT JOIN s.timeSlot ts LEFT JOIN s.event e")
    List<LocalDate> findDistinctTargetDates();

    /** Distinct payment days, for the year picker — a year can hold money without holding sessions. */
    @Query("SELECT DISTINCT s.settledOn FROM Settlement s WHERE s.settledOn IS NOT NULL")
    List<LocalDate> findDistinctSettledDates();

    /**
     * The most recent amount charged to each of these people, for prefilling the field.
     *
     * <p>Bounded by a correlated MAX rather than by ordering the whole history and taking the first
     * of each in Java: a regular client accumulates hundreds of settlements, and the prefill is not
     * worth reading them. A tie on {@code createdAt} returns both rows and the caller keeps either
     * — two amounts written in the same instant are the same amount for this purpose.
     */
    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.settlement.PayerLastAmount(s.user.id, s.amount)
        FROM Settlement s
        WHERE s.user.id IN :userIds
          AND s.createdAt = (SELECT MAX(s2.createdAt) FROM Settlement s2 WHERE s2.user.id = s.user.id)
        """)
    List<PayerLastAmount> findLastAmountsForUsers(@Param("userIds") Collection<UUID> userIds);

    /**
     * Sessions that have already happened and still carry somebody with no amount against them.
     *
     * <p>Four queries rather than one because there are two kinds of session (a standalone slot, an
     * event) and two kinds of payer (registered, guest), and the settlement key differs for each
     * pair. They live here rather than in {@code ReservationRepository} because every one of them
     * names {@code Settlement}, and that type is not reachable outside this package —
     * {@code SettlementIsolationTest} enforces it.
     *
     * <p>The past predicate is the same one {@code findPastByUserId} uses: a session is over when
     * its last minute has passed, so one still running today does not count as unpriced work.
     */
    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.settlement.UnpricedPayer(
            ts.id, ts.date, ts.title, r.user.id)
        FROM Reservation r JOIN r.timeSlot ts
        WHERE r.status = 'CONFIRMED'
          AND ts.event IS NULL
          AND ts.date >= :from
          AND (ts.date < :today OR (ts.date = :today AND ts.endTime <= :now))
          AND NOT EXISTS (
            SELECT 1 FROM Settlement s WHERE s.timeSlot.id = ts.id AND s.user.id = r.user.id)
          AND NOT EXISTS (SELECT 1 FROM SessionPayout sp WHERE sp.timeSlot.id = ts.id)
        """)
    List<UnpricedPayer> findUnpricedSlotUsers(@Param("from") LocalDate from,
                                              @Param("today") LocalDate today,
                                              @Param("now") LocalTime now);

    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.settlement.UnpricedPayer(
            ts.id, ts.date, ts.title, g.id)
        FROM GuestReservation g JOIN g.timeSlot ts
        WHERE ts.event IS NULL
          AND ts.date >= :from
          AND (ts.date < :today OR (ts.date = :today AND ts.endTime <= :now))
          AND NOT EXISTS (SELECT 1 FROM Settlement s WHERE s.guest.id = g.id)
          AND NOT EXISTS (SELECT 1 FROM SessionPayout sp WHERE sp.timeSlot.id = ts.id)
        """)
    List<UnpricedPayer> findUnpricedSlotGuests(@Param("from") LocalDate from,
                                               @Param("today") LocalDate today,
                                               @Param("now") LocalTime now);

    /** DISTINCT collapses the one-reservation-per-day an event booking writes. */
    @Query("""
        SELECT DISTINCT new pl.nextsteppro.climbing.domain.settlement.UnpricedPayer(
            e.id, e.startDate, e.title, r.user.id)
        FROM Reservation r JOIN r.timeSlot ts JOIN ts.event e
        WHERE r.status = 'CONFIRMED'
          AND e.endDate >= :from AND e.endDate < :today
          AND NOT EXISTS (
            SELECT 1 FROM Settlement s WHERE s.event.id = e.id AND s.user.id = r.user.id)
          AND NOT EXISTS (SELECT 1 FROM SessionPayout sp WHERE sp.event.id = e.id)
        """)
    List<UnpricedPayer> findUnpricedEventUsers(@Param("from") LocalDate from,
                                               @Param("today") LocalDate today);

    /**
     * ⚠️ Both attachment shapes at once. A guest is {@code timeSlot} XOR {@code event}, and an admin
     * adding one from a day view hangs them on that day's slot — so an event's unpriced guests are
     * not all found by looking at {@code event_id}.
     */
    @Query("""
        SELECT DISTINCT new pl.nextsteppro.climbing.domain.settlement.UnpricedPayer(
            COALESCE(ge.id, gtse.id),
            COALESCE(ge.startDate, gtse.startDate),
            COALESCE(ge.title, gtse.title),
            g.id)
        FROM GuestReservation g
        LEFT JOIN g.event ge
        LEFT JOIN g.timeSlot gts
        LEFT JOIN gts.event gtse
        WHERE COALESCE(ge.endDate, gtse.endDate) >= :from
          AND COALESCE(ge.endDate, gtse.endDate) < :today
          AND NOT EXISTS (SELECT 1 FROM Settlement s WHERE s.guest.id = g.id)
          AND NOT EXISTS (
            SELECT 1 FROM SessionPayout sp WHERE sp.event.id = COALESCE(ge.id, gtse.id))
        """)
    List<UnpricedPayer> findUnpricedEventGuests(@Param("from") LocalDate from,
                                                @Param("today") LocalDate today);

    @Query("SELECT COUNT(s) > 0 FROM Settlement s WHERE s.timeSlot.id = :slotId AND s.user.id = :userId")
    boolean existsForSlotUser(@Param("slotId") UUID slotId, @Param("userId") UUID userId);

    @Query("SELECT COUNT(s) > 0 FROM Settlement s WHERE s.event.id = :eventId AND s.user.id = :userId")
    boolean existsForEventUser(@Param("eventId") UUID eventId, @Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO settlements (time_slot_id, user_id, amount, settled_on, updated_at)
        VALUES (:slotId, :userId, CAST(:amount AS NUMERIC), CAST(:settledOn AS DATE), :updatedAt)
        ON CONFLICT (time_slot_id, user_id) WHERE time_slot_id IS NOT NULL AND user_id IS NOT NULL
        DO UPDATE SET amount = CAST(:amount AS NUMERIC),
                      settled_on = CAST(:settledOn AS DATE),
                      updated_at = :updatedAt
        """, nativeQuery = true)
    void upsertForSlotUser(@Param("slotId") UUID slotId,
                           @Param("userId") UUID userId,
                           @Param("amount") BigDecimal amount,
                           @Param("settledOn") @Nullable LocalDate settledOn,
                           @Param("updatedAt") Instant updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO settlements (event_id, user_id, amount, settled_on, updated_at)
        VALUES (:eventId, :userId, CAST(:amount AS NUMERIC), CAST(:settledOn AS DATE), :updatedAt)
        ON CONFLICT (event_id, user_id) WHERE event_id IS NOT NULL AND user_id IS NOT NULL
        DO UPDATE SET amount = CAST(:amount AS NUMERIC),
                      settled_on = CAST(:settledOn AS DATE),
                      updated_at = :updatedAt
        """, nativeQuery = true)
    void upsertForEventUser(@Param("eventId") UUID eventId,
                            @Param("userId") UUID userId,
                            @Param("amount") BigDecimal amount,
                            @Param("settledOn") @Nullable LocalDate settledOn,
                            @Param("updatedAt") Instant updatedAt);

    /**
     * ⚠️ The target written here is the one the CALLER addressed, never the one copied off the guest
     * row — and that distinction was a real bug. A guest is {@code timeSlot} XOR {@code event}, and
     * an admin adding one from a day view attaches them to that day's slot, so copying the guest's
     * own target wrote an event's settlement onto a per-day slot that no read of the event ever
     * looks at. The amount was accepted, then invisible on the very screen it was typed into.
     *
     * <p>Safe because {@code requireGuestOfTarget} has already established that this guest belongs
     * to this session, whichever of the two shapes they were written in with.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO settlements (time_slot_id, guest_reservation_id, amount, settled_on, updated_at)
        VALUES (:slotId, :guestId, CAST(:amount AS NUMERIC), CAST(:settledOn AS DATE), :updatedAt)
        ON CONFLICT (guest_reservation_id) WHERE guest_reservation_id IS NOT NULL
        DO UPDATE SET time_slot_id = :slotId,
                      event_id = NULL,
                      amount = CAST(:amount AS NUMERIC),
                      settled_on = CAST(:settledOn AS DATE),
                      updated_at = :updatedAt
        """, nativeQuery = true)
    void upsertForSlotGuest(@Param("slotId") UUID slotId,
                            @Param("guestId") UUID guestId,
                            @Param("amount") BigDecimal amount,
                            @Param("settledOn") @Nullable LocalDate settledOn,
                            @Param("updatedAt") Instant updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO settlements (event_id, guest_reservation_id, amount, settled_on, updated_at)
        VALUES (:eventId, :guestId, CAST(:amount AS NUMERIC), CAST(:settledOn AS DATE), :updatedAt)
        ON CONFLICT (guest_reservation_id) WHERE guest_reservation_id IS NOT NULL
        DO UPDATE SET event_id = :eventId,
                      time_slot_id = NULL,
                      amount = CAST(:amount AS NUMERIC),
                      settled_on = CAST(:settledOn AS DATE),
                      updated_at = :updatedAt
        """, nativeQuery = true)
    void upsertForEventGuest(@Param("eventId") UUID eventId,
                             @Param("guestId") UUID guestId,
                             @Param("amount") BigDecimal amount,
                             @Param("settledOn") @Nullable LocalDate settledOn,
                             @Param("updatedAt") Instant updatedAt);

    /**
     * Settles everything this payer still owes, on one date.
     *
     * <p>One statement rather than a loop of upserts: a regular client can owe a month of sessions,
     * and issuing one request each would be twenty round trips, twenty chances to fail halfway, and
     * a real dent in the admin rate-limit bucket. It also means all of it carries the SAME payment
     * date, which is the whole point — one transfer covered them, so one day did.
     *
     * <p>Only unsettled rows are touched, so running it twice is a no-op rather than a rewrite of
     * dates somebody already corrected by hand.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Settlement s SET s.settledOn = :settledOn, s.updatedAt = :now "
        + "WHERE s.settledOn IS NULL AND s.user.id = :userId")
    int settleAllForUser(@Param("userId") UUID userId,
                         @Param("settledOn") LocalDate settledOn,
                         @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Settlement s SET s.settledOn = :settledOn, s.updatedAt = :now "
        + "WHERE s.settledOn IS NULL AND s.guest.id = :guestId")
    int settleAllForGuest(@Param("guestId") UUID guestId,
                          @Param("settledOn") LocalDate settledOn,
                          @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Settlement s WHERE s.timeSlot.id = :slotId AND s.user.id = :userId")
    int deleteForSlotUser(@Param("slotId") UUID slotId, @Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Settlement s WHERE s.event.id = :eventId AND s.user.id = :userId")
    int deleteForEventUser(@Param("eventId") UUID eventId, @Param("userId") UUID userId);

    /**
     * Scoped to the target as well as the guest, even though the guest id is unique on its own.
     * The address the caller used claims a session; a statement that quietly ignores half of it
     * would delete a settlement belonging to a different one whenever the two ever disagree.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Settlement s WHERE s.guest.id = :guestId AND s.timeSlot.id = :slotId")
    int deleteForSlotGuest(@Param("slotId") UUID slotId, @Param("guestId") UUID guestId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Settlement s WHERE s.guest.id = :guestId AND s.event.id = :eventId")
    int deleteForEventGuest(@Param("eventId") UUID eventId, @Param("guestId") UUID guestId);
}
