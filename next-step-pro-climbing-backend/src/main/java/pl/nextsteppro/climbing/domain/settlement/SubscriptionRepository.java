package pl.nextsteppro.climbing.domain.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @Query("SELECT s FROM Subscription s JOIN FETCH s.user WHERE s.user.id = :userId ORDER BY s.startedOn DESC")
    List<Subscription> findByUserId(@Param("userId") UUID userId);

    /** Mirrors {@code uq_subscriptions_active_user}: at most one running at a time per person. */
    @Query("SELECT s FROM Subscription s WHERE s.user.id = :userId AND s.endedOn IS NULL")
    Optional<Subscription> findActiveByUserId(@Param("userId") UUID userId);

    /**
     * Everything the biller has to walk. Ended ones are included: a subscription closed last week
     * may still owe the month it was closed in, and the catch-up run has to be able to produce it.
     */
    @Query("SELECT s FROM Subscription s JOIN FETCH s.user")
    List<Subscription> findAllWithUser();

    /**
     * Months already billed to one subscriber, so the biller can tell which are missing. Months
     * rather than rows: the unique index makes the month the identity.
     */
    @Query("SELECT s.periodMonth FROM Settlement s WHERE s.user.id = :userId AND s.periodMonth IS NOT NULL")
    List<LocalDate> findBilledMonths(@Param("userId") UUID userId);

    /**
     * Drops fees for months beginning after a backdated end — but only the UNPAID ones.
     *
     * <p>A paid fee stays whatever the dates say afterwards: the money arrived, and rewriting that
     * because a date was corrected a week later would be the application overruling the bank.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Settlement s WHERE s.user.id = :userId AND s.periodMonth > :lastBilled "
        + "AND s.settledOn IS NULL")
    int deleteUnpaidFeesAfter(@Param("userId") UUID userId, @Param("lastBilled") LocalDate lastBilled);

    /**
     * Creates one month's fee, or leaves an existing one alone.
     *
     * <p>⚠️ {@code DO NOTHING}, not {@code DO UPDATE}: the biller runs daily and catches up on
     * missed months, so it revisits months that already exist. Overwriting would silently undo every
     * amount corrected by hand and re-open every fee already settled.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO settlements (user_id, period_month, amount, updated_at)
        VALUES (:userId, CAST(:month AS DATE), CAST(:amount AS NUMERIC), :now)
        ON CONFLICT (user_id, period_month) WHERE period_month IS NOT NULL DO NOTHING
        """, nativeQuery = true)
    void billMonth(@Param("userId") UUID userId,
                   @Param("month") LocalDate month,
                   @Param("amount") BigDecimal amount,
                   @Param("now") Instant now);
}
