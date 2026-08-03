package pl.nextsteppro.climbing.domain.athleteweight;

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

public interface AthleteWeightRepository extends JpaRepository<AthleteWeight, UUID> {

    /** Upsert lookup: one reading per day, so this either corrects or creates. */
    Optional<AthleteWeight> findByAthleteIdAndMeasuredOn(UUID athleteId, LocalDate measuredOn);

    /**
     * Race-free weigh-in: a read-then-save pair lost to a double tap or a second tab, and
     * uq_athlete_weights_day then turned the loser into a 500. Postgres settles it instead —
     * the second writer simply corrects the first one, which is exactly the intended semantics
     * (weighing again the same day is a correction, not a second reading).
     *
     * <p>updatedAt comes from the JVM clock rather than SQL now(), for the same reason as
     * TrainingCalendarReadRepository.upsertSeen: now() is the TRANSACTION start time.
     * clearAutomatically: a native write bypasses the persistence context, so an AthleteWeight
     * already loaded in this session would keep serving the stale value on re-query.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO athlete_weights (athlete_id, measured_on, weight_kg, updated_at)
        VALUES (:athleteId, :measuredOn, :weightKg, :updatedAt)
        ON CONFLICT (athlete_id, measured_on)
        DO UPDATE SET weight_kg = :weightKg, updated_at = :updatedAt
        """, nativeQuery = true)
    void upsertReading(@Param("athleteId") UUID athleteId,
                       @Param("measuredOn") LocalDate measuredOn,
                       @Param("weightKg") BigDecimal weightKg,
                       @Param("updatedAt") Instant updatedAt);

    /** Chart series, oldest first — the panel and the trend calculator both read it in order. */
    @Query("""
        SELECT w FROM AthleteWeight w
        WHERE w.athlete.id = :athleteId AND w.measuredOn BETWEEN :from AND :to
        ORDER BY w.measuredOn ASC
        """)
    List<AthleteWeight> findRange(@Param("athleteId") UUID athleteId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    void deleteByAthleteIdAndMeasuredOn(UUID athleteId, LocalDate measuredOn);

    boolean existsByAthleteId(UUID athleteId);
}
