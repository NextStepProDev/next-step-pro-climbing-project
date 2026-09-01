package pl.nextsteppro.climbing.domain.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutSourceRepository extends JpaRepository<PayoutSource, UUID> {

    /** Active first, then archived — the picker offers the live ones and the tab still names old ones. */
    @Query("SELECT s FROM PayoutSource s ORDER BY CASE WHEN s.archivedAt IS NULL THEN 0 ELSE 1 END, s.name")
    List<PayoutSource> findAllOrdered();

    /**
     * Mirrors {@code uq_payout_sources_active_name}: the name is unique among ACTIVE sources only,
     * so re-adding a club after archiving it is allowed. Checked in the service to give a translated
     * message instead of a constraint violation.
     */
    @Query("SELECT s FROM PayoutSource s WHERE lower(s.name) = lower(:name) AND s.archivedAt IS NULL")
    Optional<PayoutSource> findActiveByName(@Param("name") String name);
}
