package pl.nextsteppro.climbing.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.athletegoal.AthleteGoal;
import pl.nextsteppro.climbing.domain.athletegoal.AthleteGoalRepository;
import pl.nextsteppro.climbing.domain.athletegoal.GoalKind;
import pl.nextsteppro.climbing.domain.athleteweight.AthleteWeight;
import pl.nextsteppro.climbing.domain.athleteweight.AthleteWeightRepository;
import pl.nextsteppro.climbing.domain.athleteweight.WeightTrendCalculator;
import pl.nextsteppro.climbing.domain.athleteweight.WeightTrendCalculator.ConfirmedTrend;
import pl.nextsteppro.climbing.domain.athleteweight.WeightTrendCalculator.TrendPoint;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Athlete goals (banner above the training calendar) + trophy chest.
 *
 * <p>Rules: at most one ACTIVE goal per (kind, horizon) pair — the DB partial unique index is
 * the race backstop — and only the coach mutates (wrapped by {@link AdminTrainingCalendarService}).
 *
 * <p><b>Two ways a goal closes.</b> The coach marks a GENERAL goal achieved by hand, and that
 * is permanent. A WEIGHT goal closes itself when a weigh-in brings the confirmed 7-day trend
 * to its target; because no human decided that, it — and only it — can be reopened.
 */
@Service
@Transactional
public class AthleteGoalService {

    // Same explicit zone as the rest of the training calendar (container runs UTC)
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final AthleteGoalRepository goalRepository;
    private final AthleteWeightRepository weightRepository;
    private final TrainingCalendarService calendarService;
    private final MessageService msg;

    public AthleteGoalService(AthleteGoalRepository goalRepository,
                              AthleteWeightRepository weightRepository,
                              TrainingCalendarService calendarService,
                              MessageService msg) {
        this.goalRepository = goalRepository;
        this.weightRepository = weightRepository;
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
        GoalKind kind = request.kind() != null ? request.kind() : GoalKind.GENERAL;
        boolean slotTaken = goalRepository.findByAthleteIdAndAchievedAtIsNull(athleteId).stream()
            .anyMatch(g -> g.getKind() == kind && g.getHorizon() == request.horizon());
        if (slotTaken) {
            throw new IllegalStateException(msg.get("training.goal.active.exists"));
        }
        AthleteGoal goal = kind == GoalKind.WEIGHT
            ? buildWeightGoal(athlete, athleteId, request)
            : new AthleteGoal(athlete, request.horizon(), requireContent(request), request.targetDate());
        try {
            return toDto(goalRepository.saveAndFlush(goal));
        } catch (DataIntegrityViolationException e) {
            // Concurrent create for the same (kind, horizon) slipped past the pre-check —
            // the partial unique index catches it; surface the same 409 as the pre-check
            throw new IllegalStateException(msg.get("training.goal.active.exists"));
        }
    }

    /**
     * A weight goal needs a starting point, and that point is the CURRENT trend rather than
     * anything typed in: the progress bar is only honest if its left edge is measured.
     *
     * <p>Uses the display trend, not the confirmed one — the 3-reading rule governs CLOSING a
     * goal, not opening one. Refusing to start until the athlete has weighed in three times
     * would just block the coach on the day they want to set the target.
     */
    private AthleteGoal buildWeightGoal(User athlete, UUID athleteId, SaveGoalRequest request) {
        BigDecimal target = request.targetWeightKg();
        if (target == null) {
            throw new IllegalArgumentException(msg.get("training.goal.weight.target.required"));
        }
        TrendPoint start = currentTrend(athleteId);
        if (start == null) {
            // No reading ever — there is nothing to measure progress from
            throw new IllegalStateException(msg.get("training.goal.weight.no.data"));
        }
        // A target equal to the current trend is already met: it would close on the very next
        // weigh-in and hand out a trophy for nothing. It also gives a zero-length progress bar.
        if (AthleteWeight.normalize(target).compareTo(start.average()) == 0) {
            throw new IllegalArgumentException(msg.get("training.goal.weight.already.met"));
        }
        return AthleteGoal.weightGoal(athlete, request.horizon(), requireContent(request),
            request.targetDate(), target, start.average());
    }

    public AthleteGoalDto updateGoal(UUID goalId, SaveGoalRequest request) {
        AthleteGoal goal = requireActiveGoal(goalId);
        goal.update(requireContent(request), request.targetDate());
        return toDto(goal);
    }

    public void deleteGoal(UUID goalId) {
        goalRepository.delete(requireActiveGoal(goalId));
    }

    /** Achievement date is backdatable (null = now); a future date makes no sense → 400. */
    public AthleteGoalDto achieveGoal(UUID goalId, @Nullable LocalDate achievedDate) {
        AthleteGoal goal = requireActiveGoal(goalId);
        Instant when = Instant.now();
        if (achievedDate != null) {
            if (achievedDate.isAfter(LocalDate.now(WARSAW))) {
                throw new IllegalArgumentException(msg.get("training.goal.achieved.future"));
            }
            when = achievedDate.atStartOfDay(WARSAW).toInstant();
        }
        goal.markAchieved(when);
        return toDto(goal);
    }

    /**
     * Closes every active weight goal the athlete has just reached. Called synchronously from
     * the weigh-in — there is no scheduler, so a goal met while the athlete is not weighing in
     * simply waits for the next reading.
     *
     * <p>The {@code trend} parameter is a {@link ConfirmedTrend}, so this method structurally
     * cannot be handed a thin two-reading average. Null (too few readings) is a no-op.
     */
    void evaluateWeightGoals(UUID athleteId, @Nullable ConfirmedTrend trend) {
        if (trend == null) return;
        Instant now = Instant.now();
        for (AthleteGoal goal : goalRepository.findByAthleteIdAndKindAndAchievedAtIsNull(athleteId, GoalKind.WEIGHT)) {
            if (goal.isMetBy(trend)) {
                goal.markAchievedAutomatically(now);
            }
        }
    }

    /**
     * Undo of an AUTOMATIC closure — the escape hatch for a mistyped weigh-in (86 entered as
     * 68 closes a goal nobody reached). A goal the coach closed by hand stays closed: that was
     * a human decision, and the trophy chest must stay trustworthy.
     */
    public AthleteGoalDto reopenGoal(UUID goalId) {
        AthleteGoal goal = requireGoal(goalId);
        if (!goal.isAchieved()) {
            throw new IllegalStateException(msg.get("training.goal.reopen.not.achieved"));
        }
        if (!goal.isAchievedAutomatically()) {
            throw new IllegalStateException(msg.get("training.goal.reopen.manual.only"));
        }
        // The coach may have set a replacement goal in the freed slot meanwhile; without this
        // check the partial unique index would answer with a raw 500
        boolean slotTaken = goalRepository.findByAthleteIdAndAchievedAtIsNull(goal.getAthlete().getId()).stream()
            .anyMatch(g -> g.getKind() == goal.getKind() && g.getHorizon() == goal.getHorizon());
        if (slotTaken) {
            throw new IllegalStateException(msg.get("training.goal.active.exists"));
        }
        goal.reopen();
        try {
            return toDto(goalRepository.saveAndFlush(goal));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(msg.get("training.goal.active.exists"));
        }
    }

    /** Display trend for today (any number of readings) — progress bars and the start snapshot. */
    @Nullable
    TrendPoint currentTrend(UUID athleteId) {
        LocalDate today = LocalDate.now(WARSAW);
        List<AthleteWeight> window = weightRepository.findRange(
            athleteId, today.minusDays(WeightTrendCalculator.WINDOW_DAYS - 1L), today);
        return WeightTrendCalculator.trendOn(WeightTrendCalculator.index(window), today);
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
            // Enum order GENERAL, WEIGHT × SHORT, MEDIUM, LONG — exactly the two banner rows
            .sorted(Comparator.comparing(AthleteGoal::getKind).thenComparing(AthleteGoal::getHorizon))
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
            goal.getKind().name(),
            goal.getHorizon().name(),
            goal.getContent(),
            goal.getTargetDate(),
            goal.getTargetWeightKg(),
            goal.getStartWeightKg(),
            goal.isAchievedAutomatically(),
            goal.getAchievedAt(),
            goal.getCreatedAt()
        );
    }
}
