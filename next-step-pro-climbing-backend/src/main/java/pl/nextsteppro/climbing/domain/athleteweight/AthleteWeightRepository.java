package pl.nextsteppro.climbing.domain.athleteweight;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AthleteWeightRepository extends JpaRepository<AthleteWeight, UUID> {

    /** Upsert lookup: one reading per day, so this either corrects or creates. */
    Optional<AthleteWeight> findByAthleteIdAndMeasuredOn(UUID athleteId, LocalDate measuredOn);

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
