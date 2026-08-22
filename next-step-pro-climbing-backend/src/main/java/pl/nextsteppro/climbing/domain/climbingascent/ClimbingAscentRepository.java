package pl.nextsteppro.climbing.domain.climbingascent;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClimbingAscentRepository extends JpaRepository<ClimbingAscent, UUID> {

    /**
     * One year of the logbook, newest first. Date range rather than {@code year(climbed_on)} so
     * the query rides {@code idx_climbing_ascents_athlete_date} instead of scanning.
     *
     * <p>{@code createdAt} breaks ties: several ascents share a day (that is a normal climbing
     * day), and without a second key their order would drift between requests, making the table
     * reshuffle itself under the athlete on every refetch.
     */
    @Query("""
        SELECT a FROM ClimbingAscent a
        WHERE a.athlete.id = :athleteId AND a.terrain = :terrain
          AND a.climbedOn BETWEEN :from AND :to
        ORDER BY a.climbedOn DESC, a.createdAt DESC
        """)
    List<ClimbingAscent> findRange(@Param("athleteId") UUID athleteId,
                                   @Param("terrain") AscentTerrain terrain,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    @Query("""
        SELECT a FROM ClimbingAscent a
        WHERE a.athlete.id = :athleteId AND a.terrain = :terrain
        ORDER BY a.climbedOn DESC, a.createdAt DESC
        """)
    List<ClimbingAscent> findAllForAthlete(@Param("athleteId") UUID athleteId,
                                           @Param("terrain") AscentTerrain terrain);

    /** Years the athlete actually climbed in — the year picker offers these and nothing else. */
    @Query("""
        SELECT DISTINCT year(a.climbedOn) FROM ClimbingAscent a
        WHERE a.athlete.id = :athleteId AND a.terrain = :terrain
        ORDER BY 1 DESC
        """)
    List<Integer> findYearsWithData(@Param("athleteId") UUID athleteId,
                                    @Param("terrain") AscentTerrain terrain);

    /**
     * The whole logbook as a projection, for the statistics. One query, one pass in Java —
     * the year filter is a slice of this set rather than a second round trip, because the
     * progression chart is all-time regardless of which year the athlete is looking at.
     */
    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.climbingascent.AscentStatsRow(
            a.discipline, a.grade, a.style, a.climbedOn, a.areaKey, a.area, a.cragKey, a.crag,
            a.routeName, a.attempts, a.qualityStars,
            a.winter, a.lengthMeters, a.pitches, a.durationMinutes, a.ledGrade, a.ledPitches)
        FROM ClimbingAscent a WHERE a.athlete.id = :athleteId AND a.terrain = :terrain
        """)
    List<AscentStatsRow> findStatsRowsByAthleteId(@Param("athleteId") UUID athleteId,
                                                  @Param("terrain") AscentTerrain terrain);

    /**
     * Places the athlete has logged before, across every year — the autocomplete has to suggest
     * a crag from 2019 while they are looking at 2026, so this deliberately ignores the filter.
     */
    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.climbingascent.AreaUsageRow(
            a.areaKey, a.area, a.cragKey, a.crag, COUNT(a), MAX(a.climbedOn))
        FROM ClimbingAscent a WHERE a.athlete.id = :athleteId
        GROUP BY a.areaKey, a.area, a.cragKey, a.crag
        """)
    List<AreaUsageRow> findPlaceUsage(@Param("athleteId") UUID athleteId);

    /**
     * The public feed: newest ascents across every climber who has not opted out.
     *
     * <p>Ordered by {@code climbedOn}, NOT by {@code createdAt} — the list is "what was climbed
     * lately", so somebody backfilling a season from 2019 must not push this week's sends off it.
     * {@code createdAt} only breaks ties within a day.
     *
     * <p>Both filters live in the WHERE clause rather than in a pass afterwards: a caller who
     * forgets one publishes somebody who asked not to be published, or re-publishes an entry the
     * owner took down, and neither is a mistake worth leaving available. Paged rather than
     * {@code LIMIT}, because JPQL has no limit.
     *
     * <p>They are two different exclusions and both are needed. {@code u.ascentsPublic} is the
     * author's own wish about their whole logbook; {@code a.hiddenFromPublicAt} is the site
     * owner's takedown of one row. Either one alone is enough to keep an entry off the list.
     */
    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.climbingascent.PublicAscentRow(
            a.id, u.firstName, u.lastName, a.climbedOn, a.terrain, a.discipline, a.grade, a.style,
            a.area, a.crag, a.routeName)
        FROM ClimbingAscent a JOIN a.athlete u
        WHERE u.ascentsPublic = true AND a.hiddenFromPublicAt IS NULL
        ORDER BY a.climbedOn DESC, a.createdAt DESC
        """)
    List<PublicAscentRow> findRecentPublic(Pageable pageable);

    /**
     * Ownership is part of the lookup, not a check afterwards: addressing by ascent id alone
     * and comparing the athlete later leaves the "forgot to compare" branch available.
     */
    Optional<ClimbingAscent> findByIdAndAthleteId(UUID id, UUID athleteId);

    long countByAthleteIdAndTerrain(UUID athleteId, AscentTerrain terrain);

    /** Admin user card headline count: both terrains, since the card counts logged ascents,
     * not rock ones. */
    long countByAthleteId(UUID athleteId);
}
