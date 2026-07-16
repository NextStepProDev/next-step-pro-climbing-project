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
    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.personaltraining.AthleteActivityCount(t.athlete.id, COUNT(t))
        FROM PersonalTraining t
        LEFT JOIN TrainingCalendarRead r ON r.userId = :adminId AND r.athleteId = t.athlete.id
        WHERE t.createdByAdmin = false AND (r.seenAt IS NULL OR t.createdAt > r.seenAt)
        GROUP BY t.athlete.id
        """)
    List<AthleteActivityCount> countNewAthleteTrainingsPerAthlete(UUID adminId);

    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.personaltraining.AthleteActivityCount(t.athlete.id, COUNT(t))
        FROM PersonalTraining t
        LEFT JOIN TrainingCalendarRead r ON r.userId = :adminId AND r.athleteId = t.athlete.id
        WHERE t.completedAt IS NOT NULL AND (r.seenAt IS NULL OR t.completedAt > r.seenAt)
        GROUP BY t.athlete.id
        """)
    List<AthleteActivityCount> countNewCompletionsPerAthlete(UUID adminId);

    /** Coach's athlete list: latest training change per athlete (merged with comment activity in the service). */
    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.personaltraining.AthleteLastActivity(t.athlete.id, MAX(t.updatedAt))
        FROM PersonalTraining t
        GROUP BY t.athlete.id
        """)
    List<AthleteLastActivity> findLastTrainingActivityPerAthlete();
}
