package pl.nextsteppro.climbing.domain.athletegoal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AthleteGoalRepository extends JpaRepository<AthleteGoal, UUID> {

    /** Active goals (banner cards); caller sorts by kind, then SHORT → MEDIUM → LONG. */
    List<AthleteGoal> findByAthleteIdAndAchievedAtIsNull(UUID athleteId);

    /** Evaluation path on every weigh-in — no reason to load the training goals too. */
    List<AthleteGoal> findByAthleteIdAndKindAndAchievedAtIsNull(UUID athleteId, GoalKind kind);

    /** Trophy chest: full achievement history, newest first. */
    List<AthleteGoal> findByAthleteIdAndAchievedAtIsNotNullOrderByAchievedAtDesc(UUID athleteId);
}
