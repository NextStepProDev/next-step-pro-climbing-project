package pl.nextsteppro.climbing.api.trainingcalendar;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTraining;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Coach (admin) side of the personal training calendar: thin wrapper over
 * {@link TrainingCalendarService} adding activity logging for coach mutations.
 * Lives in this package (not api/admin/*) to share the package-private DTOs.
 */
@Service
@Transactional
public class AdminTrainingCalendarService {

    private final TrainingCalendarService core;
    private final TrainingStatsService statsService;
    private final AthleteGoalService goalService;
    private final ActivityLogService activityLogService;
    private final UserRepository userRepository;

    public AdminTrainingCalendarService(TrainingCalendarService core,
                                        TrainingStatsService statsService,
                                        AthleteGoalService goalService,
                                        ActivityLogService activityLogService,
                                        UserRepository userRepository) {
        this.core = core;
        this.statsService = statsService;
        this.goalService = goalService;
        this.activityLogService = activityLogService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AthleteSummaryDto> getAthleteSummaries(UUID adminId) {
        return core.getAthleteSummaries(adminId);
    }

    @Transactional(readOnly = true)
    public CalendarRangeDto getRangeForAthlete(UUID adminId, UUID athleteId, LocalDate from, LocalDate to) {
        return core.getRangeForAthlete(adminId, athleteId, from, to);
    }

    @Transactional(readOnly = true)
    public AthleteStatsDto getStatsForAthlete(UUID athleteId) {
        return statsService.getStatsForAthlete(athleteId);
    }

    public PersonalTrainingDto createForAthlete(UUID adminId, UUID athleteId, CreatePersonalTrainingRequest request) {
        PersonalTrainingDto dto = core.createForAthlete(athleteId, request);
        activityLogService.logAdminTrainingCreated(admin(adminId), describe(athleteId, dto.title(), dto.date()));
        return dto;
    }

    public PersonalTrainingDto update(UUID adminId, UUID trainingId, CreatePersonalTrainingRequest request) {
        PersonalTrainingDto dto = core.updateAsAdmin(trainingId, request);
        UUID athleteId = core.requireTraining(trainingId).getAthlete().getId();
        activityLogService.logAdminTrainingUpdated(admin(adminId), describe(athleteId, dto.title(), dto.date()));
        return dto;
    }

    public void delete(UUID adminId, UUID trainingId) {
        PersonalTraining training = core.requireTraining(trainingId);
        String description = describe(training.getAthlete().getId(), training.getTitle(), training.getTrainingDate());
        core.deleteAsAdmin(trainingId);
        activityLogService.logAdminTrainingDeleted(admin(adminId), description);
    }

    @Transactional(readOnly = true)
    public List<TrainingCommentDto> getComments(UUID adminId, UUID trainingId) {
        return core.getCommentsAsAdmin(adminId, trainingId);
    }

    public TrainingCommentDto addComment(UUID adminId, UUID trainingId, String body) {
        // Comments are their own record (the thread) — not activity-logged
        return core.addCommentAsAdmin(adminId, trainingId, body);
    }

    public void markSeen(UUID adminId, UUID athleteId) {
        core.markCoachSeen(adminId, athleteId);
    }

    public AttachmentUploadResponse uploadAttachment(org.springframework.web.multipart.MultipartFile file) {
        return core.uploadAttachmentAsAdmin(file);
    }

    // ---------- athlete goals ----------

    @Transactional(readOnly = true)
    public GoalsDto getGoals(UUID athleteId) {
        return goalService.getGoalsForAthlete(athleteId);
    }

    public AthleteGoalDto createGoal(UUID adminId, UUID athleteId, SaveGoalRequest request) {
        AthleteGoalDto dto = goalService.createGoal(athleteId, request);
        activityLogService.logAdminGoalCreated(admin(adminId), describeGoal(athleteId, dto));
        return dto;
    }

    public AthleteGoalDto updateGoal(UUID adminId, UUID goalId, SaveGoalRequest request) {
        UUID athleteId = goalService.requireGoal(goalId).getAthlete().getId();
        AthleteGoalDto dto = goalService.updateGoal(goalId, request);
        activityLogService.logAdminGoalUpdated(admin(adminId), describeGoal(athleteId, dto));
        return dto;
    }

    public void deleteGoal(UUID adminId, UUID goalId) {
        var goal = goalService.requireGoal(goalId);
        String description = describe(goal.getAthlete().getId(), goal.getContent(), goal.getTargetDate());
        goalService.deleteGoal(goalId);
        activityLogService.logAdminGoalDeleted(admin(adminId), description);
    }

    public AthleteGoalDto achieveGoal(UUID adminId, UUID goalId, AchieveGoalRequest request) {
        UUID athleteId = goalService.requireGoal(goalId).getAthlete().getId();
        AthleteGoalDto dto = goalService.achieveGoal(goalId, request.achievedDate());
        activityLogService.logAdminGoalAchieved(admin(adminId), describeGoal(athleteId, dto));
        return dto;
    }

    private String describeGoal(UUID athleteId, AthleteGoalDto dto) {
        return describe(athleteId, dto.content(), dto.targetDate());
    }

    private User admin(UUID adminId) {
        return userRepository.findById(adminId).orElseThrow();
    }

    private String describe(UUID athleteId, String title, LocalDate date) {
        String athlete = userRepository.findById(athleteId)
            .map(u -> u.getFullName() + " (" + u.getEmail() + ")")
            .orElse(athleteId.toString());
        String description = athlete + " — " + date + " " + title;
        // activity_logs.description is VARCHAR(500) and the entity does not clamp
        return description.length() > 500 ? description.substring(0, 500) : description;
    }
}
