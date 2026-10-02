package pl.nextsteppro.climbing.domain.settlement;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The payments ledger. Insert and delete only — see {@link Payment} for why there is no update.
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** LEFT JOINs: a payment is a user's XOR a guest's, and an inner join would drop the other half. */
    String ROW_SELECT = """
        SELECT new pl.nextsteppro.climbing.domain.settlement.PaymentRow(
            p.id, u.id, u.firstName, u.lastName, g.id, g.note, p.amount, p.receivedOn,
            es.id, ee.id, COALESCE(es.title, ee.title), COALESCE(es.date, ee.startDate), p.createdAt)
        FROM Payment p
        LEFT JOIN p.user u
        LEFT JOIN p.guest g
        LEFT JOIN p.enteredSlot es
        LEFT JOIN p.enteredEvent ee
        """;

    @Query(ROW_SELECT)
    List<PaymentRow> findAllRows();

    @Query(ROW_SELECT + " WHERE u.id IN :ids")
    List<PaymentRow> findRowsForUsers(@Param("ids") Collection<UUID> ids);

    @Query(ROW_SELECT + " WHERE g.id IN :ids")
    List<PaymentRow> findRowsForGuests(@Param("ids") Collection<UUID> ids);

    /** Distinct payment days, for the year picker — a year can hold money without holding sessions. */
    @Query("SELECT DISTINCT p.receivedOn FROM Payment p")
    List<LocalDate> findDistinctReceivedDates();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO payments (user_id, guest_reservation_id, amount, received_on,
                              entered_slot_id, entered_event_id)
        VALUES (:userId, :guestId, CAST(:amount AS NUMERIC), CAST(:receivedOn AS DATE),
                :slotId, :eventId)
        """, nativeQuery = true)
    void insert(@Param("userId") @Nullable UUID userId,
                @Param("guestId") @Nullable UUID guestId,
                @Param("amount") BigDecimal amount,
                @Param("receivedOn") LocalDate receivedOn,
                @Param("slotId") @Nullable UUID slotId,
                @Param("eventId") @Nullable UUID eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Payment p WHERE p.id = :id")
    int deleteRow(@Param("id") UUID id);
}
