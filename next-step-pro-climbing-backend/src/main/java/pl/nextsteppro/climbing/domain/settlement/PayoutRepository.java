package pl.nextsteppro.climbing.domain.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    String ROW_SELECT = """
        SELECT new pl.nextsteppro.climbing.domain.settlement.PayoutRow(
            p.id, src.id, src.name, p.periodMonth, p.amount, p.receivedOn)
        FROM Payout p JOIN p.source src
        """;

    /**
     * Payouts FOR a range of work months. Drives the rate table, which asks "what did the work of
     * October earn", so it is filtered on the period rather than on when the transfer landed.
     */
    @Query(ROW_SELECT + " WHERE p.periodMonth BETWEEN :from AND :to ORDER BY p.periodMonth DESC")
    List<PayoutRow> findByPeriodBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Everything one payer ever sent, for their own screen.
     *
     * <p>All-time and unbounded on purpose: "what do I have with this place" has no year in it, the
     * same reasoning as the money block on a client's card. A collaboration is measured in dozens of
     * transfers, not thousands, so the range that bounds the tab's reads would only hide history.
     */
    @Query(ROW_SELECT + " WHERE src.id = :sourceId ORDER BY p.periodMonth DESC")
    List<PayoutRow> findBySourceId(@Param("sourceId") UUID sourceId);

    /**
     * Payouts that ARRIVED in a range. Drives revenue, which counts on the day money landed —
     * the same axis as {@code settlements.settled_on}, so the monthly total stays one number.
     */
    @Query(ROW_SELECT + " WHERE p.receivedOn BETWEEN :from AND :to")
    List<PayoutRow> findByReceivedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Every transfer ever. Used by the "everything" view, where settlements are read all-time too —
     * clipping one side to the charted window and not the other made the all-time total quietly
     * short by whatever arrived before it.
     */
    @Query(ROW_SELECT)
    List<PayoutRow> findAllRows();
}
