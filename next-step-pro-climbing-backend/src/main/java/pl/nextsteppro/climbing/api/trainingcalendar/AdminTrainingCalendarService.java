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
    private final ActivityLogService activityLogService;
    private final UserRepository userRepository;

    public AdminTrainingCalendarService(TrainingCalendarService core,
                                        TrainingStatsService statsService,
                                        ActivityLogService activityLogService,
                                        UserRepository userRepository) {
        this.core = core;
        this.statsService = statsService;
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
