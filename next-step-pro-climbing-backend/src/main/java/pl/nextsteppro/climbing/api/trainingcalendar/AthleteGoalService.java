package pl.nextsteppro.climbing.api.trainingcalendar;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.athletegoal.AthleteGoal;
import pl.nextsteppro.climbing.domain.athletegoal.AthleteGoalRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Athlete goals (banner above the training calendar) + trophy chest.
 *
 * <p>Rules: at most one ACTIVE goal per horizon (DB partial unique index is the race
 * backstop), only the coach mutates (wrapped by {@link AdminTrainingCalendarService}),
 * and an achieved goal is immutable forever — it lives on in the trophy chest.
 */
@Service
@Transactional
public class AthleteGoalService {

    private final AthleteGoalRepository goalRepository;
    private final TrainingCalendarService calendarService;
    private final MessageService msg;

    public AthleteGoalService(AthleteGoalRepository goalRepository,
                              TrainingCalendarService calendarService,
                              MessageService msg) {
        this.goalRepository = goalRepository;
        this.calendarService = calendarService;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public GoalsDto getMyGoals(UUID userId) {
        calendarService.requireAthlete(userId);
        return buildGoals(userId);
    }

    @Transactional(readOnly = true)
    public GoalsDto getGoalsForAthlete(UUID athleteId) {
        calendarService.requireFlaggedAthlete(athleteId);
        return buildGoals(athleteId);
    }

    public AthleteGoalDto createGoal(UUID athleteId, SaveGoalRequest request) {
        User athlete = calendarService.requireFlaggedAthlete(athleteId);
        boolean slotTaken = goalRepository.findByAthleteIdAndAchievedAtIsNull(athleteId).stream()
            .anyMatch(g -> g.getHorizon() == request.horizon());
        if (slotTaken) {
            throw new IllegalStateException(msg.get("training.goal.active.exists"));
        }
        AthleteGoal goal = new AthleteGoal(athlete, request.horizon(), requireContent(request), request.targetDate());
        try {
            return toDto(goalRepository.saveAndFlush(goal));
        } catch (DataIntegrityViolationException e) {
            // Concurrent create for the same horizon slipped past the pre-check —
            // the partial unique index catches it; surface the same 409 as the pre-check
            throw new IllegalStateException(msg.get("training.goal.active.exists"));
        }
    }

    public AthleteGoalDto updateGoal(UUID goalId, SaveGoalRequest request) {
        AthleteGoal goal = requireActiveGoal(goalId);
        goal.update(requireContent(request), request.targetDate());
        return toDto(goal);
    }

    public void deleteGoal(UUID goalId) {
        goalRepository.delete(requireActiveGoal(goalId));
    }

    public AthleteGoalDto achieveGoal(UUID goalId) {
        AthleteGoal goal = requireActiveGoal(goalId);
        goal.markAchieved();
        return toDto(goal);
    }

    // Package-private for AdminTrainingCalendarService (activity-log descriptions).
    AthleteGoal requireGoal(UUID goalId) {
        return goalRepository.findById(goalId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("training.goal.not.found")));
    }

    /** Achieved goals are trophies: no edit, no delete, no re-achieve — ever. */
    private AthleteGoal requireActiveGoal(UUID goalId) {
        AthleteGoal goal = requireGoal(goalId);
        if (goal.isAchieved()) {
            throw new IllegalStateException(msg.get("training.goal.achieved.immutable"));
        }
        return goal;
    }

    private String requireContent(SaveGoalRequest request) {
        String sanitized = AthleteGoal.sanitizeContent(request.content());
        if (sanitized == null) {
            throw new IllegalArgumentException(msg.get("training.goal.content.empty"));
        }
        return sanitized;
    }

    private GoalsDto buildGoals(UUID athleteId) {
        List<AthleteGoalDto> active = goalRepository.findByAthleteIdAndAchievedAtIsNull(athleteId).stream()
            // Enum order = SHORT, MEDIUM, LONG — exactly the banner card order
            .sorted(Comparator.comparing(AthleteGoal::getHorizon))
            .map(AthleteGoalService::toDto)
            .toList();
        List<AthleteGoalDto> achieved = goalRepository
            .findByAthleteIdAndAchievedAtIsNotNullOrderByAchievedAtDesc(athleteId).stream()
            .map(AthleteGoalService::toDto)
            .toList();
        return new GoalsDto(active, achieved);
    }

    private static AthleteGoalDto toDto(AthleteGoal goal) {
        return new AthleteGoalDto(
            goal.getId(),
            goal.getHorizon().name(),
            goal.getContent(),
            goal.getTargetDate(),
            goal.getAchievedAt(),
            goal.getCreatedAt()
        );
    }
}
