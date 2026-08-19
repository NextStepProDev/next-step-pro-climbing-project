package pl.nextsteppro.climbing.domain.personaltraining;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PersonalTrainingRepository extends JpaRepository<PersonalTraining, UUID> {

    /** Calendar range view (month/week) of a single athlete. */
    List<PersonalTraining> findByAthleteIdAndTrainingDateBetweenOrderByTrainingDateAscStartTimeAsc(
        UUID athleteId, LocalDate from, LocalDate to);

    /** Admin user card headline count. TRAINING only — a month of kept calorie limits is not
     * a month of training, the same split {@code TrainingStatsService} makes on the way in. */
    @Query("""
        SELECT COUNT(t) FROM PersonalTraining t
        WHERE t.athlete.id = :athleteId AND t.kind = 'TRAINING' AND t.completedAt IS NOT NULL
        """)
    long countCompletedTrainings(UUID athleteId);

    /**
     * Admin statistics: how many athletes have anything on their plan at all.
     *
     * <p>Counts both kinds — the question is "was this flag ever used", and a plan of nothing but
     * daily calorie targets is still a plan being used.
     *
     * <p>⚠️ <b>The flag test is not a filter for tidiness, it is the same boundary as
     * {@code requireFlaggedAthlete}.</b> Revoking the athlete flag clears the consent but
     * deliberately leaves the plan rows in place, so counting the whole table counts people whose
     * calendar the coach can no longer open — and lets this number exceed the flagged total, which
     * the UI renders as a share of it (a plan card reading "150%").
     */
    @Query("SELECT COUNT(DISTINCT t.athlete.id) FROM PersonalTraining t WHERE t.athlete.athlete = true")
    long countAthletesWithPlan();

    /** Athlete's unread counter: trainings the coach created or last edited after the athlete's seen marker. */
    @Query("""
        SELECT COUNT(t) FROM PersonalTraining t
        WHERE t.athlete.id = :athleteId
          AND ((t.createdByAdmin = true AND t.createdAt > :since)
               OR (t.lastModifiedByAdmin = true AND t.updatedAt > :since))
        """)
    long countCoachChangesSince(UUID athleteId, Instant since);

    // Coach-side per-athlete counters. LEFT JOIN on the read-marker entity (no association —
    // arbitrary ON condition); a missing row means "never seen" → count everything.
    // The `athlete.athlete = true` filter belongs IN the query: these feed a navbar badge polled
    // every 60s, and aggregating every user's history only to drop the un-flagged ones in Java
    // made the badge the heaviest recurring query in the app.
    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.personaltraining.AthleteActivityCount(t.athlete.id, COUNT(t))
        FROM PersonalTraining t
        LEFT JOIN TrainingCalendarRead r ON r.userId = :adminId AND r.athleteId = t.athlete.id
        WHERE t.athlete.athlete = true
          AND t.createdByAdmin = false AND (r.seenAt IS NULL OR t.createdAt > r.seenAt)
        GROUP BY t.athlete.id
        """)
    List<AthleteActivityCount> countNewAthleteTrainingsPerAthlete(UUID adminId);

    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.personaltraining.AthleteActivityCount(t.athlete.id, COUNT(t))
        FROM PersonalTraining t
        LEFT JOIN TrainingCalendarRead r ON r.userId = :adminId AND r.athleteId = t.athlete.id
        WHERE t.athlete.athlete = true
          AND t.completedAt IS NOT NULL AND (r.seenAt IS NULL OR t.completedAt > r.seenAt)
        GROUP BY t.athlete.id
        """)
    List<AthleteActivityCount> countNewCompletionsPerAthlete(UUID adminId);

    /** Coach's athlete list: latest training change per athlete (merged with comment activity in the service). */
    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.personaltraining.AthleteLastActivity(t.athlete.id, MAX(t.updatedAt))
        FROM PersonalTraining t
        WHERE t.athlete.athlete = true
        GROUP BY t.athlete.id
        """)
    List<AthleteLastActivity> findLastTrainingActivityPerAthlete();

    /** Athlete statistics: full history of one athlete reduced to (kind, date, endTime, completedAt, rpe). */
    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.personaltraining.TrainingStatsRow(
            t.kind, t.trainingDate, t.endTime, t.completedAt, t.rpe)
        FROM PersonalTraining t
        WHERE t.athlete.id = :athleteId
        """)
    List<TrainingStatsRow> findStatsRowsByAthleteId(UUID athleteId);
}
