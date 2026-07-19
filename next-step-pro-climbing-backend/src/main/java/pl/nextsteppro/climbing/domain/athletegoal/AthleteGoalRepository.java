package pl.nextsteppro.climbing.domain.athletegoal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AthleteGoalRepository extends JpaRepository<AthleteGoal, UUID> {

    /** Active goals (banner cards); caller sorts SHORT → MEDIUM → LONG. */
    List<AthleteGoal> findByAthleteIdAndAchievedAtIsNull(UUID athleteId);

    /** Trophy chest: full achievement history, newest first. */
    List<AthleteGoal> findByAthleteIdAndAchievedAtIsNotNullOrderByAchievedAtDesc(UUID athleteId);
}
