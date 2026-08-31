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
     * Payouts that ARRIVED in a range. Drives revenue, which counts on the day money landed —
     * the same axis as {@code settlements.settled_on}, so the monthly total stays one number.
     */
    @Query(ROW_SELECT + " WHERE p.receivedOn BETWEEN :from AND :to")
    List<PayoutRow> findByReceivedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
