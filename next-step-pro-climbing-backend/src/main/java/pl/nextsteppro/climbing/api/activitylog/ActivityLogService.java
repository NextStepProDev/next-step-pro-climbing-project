package pl.nextsteppro.climbing.api.activitylog;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.activitylog.ActivityActionType;
import pl.nextsteppro.climbing.domain.activitylog.ActivityLog;
import pl.nextsteppro.climbing.domain.activitylog.ActivityLogRepository;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;

import java.util.List;

@Service
@Transactional
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    public void logReservationCreated(User user, TimeSlot timeSlot, int participants) {
        save(user, ActivityActionType.RESERVATION_CREATED, timeSlot, null, participants, null);
    }

    public void logReservationReactivated(User user, TimeSlot timeSlot, int participants) {
        save(user, ActivityActionType.RESERVATION_REACTIVATED, timeSlot, null, participants, null);
    }

    public void logReservationCancelled(User user, TimeSlot timeSlot, int participants) {
        save(user, ActivityActionType.RESERVATION_CANCELLED, timeSlot, null, participants, null);
    }

    public void logEventReservationCreated(User user, Event event, int participants) {
        save(user, ActivityActionType.EVENT_RESERVATION_CREATED, null, event, participants, null);
    }

    public void logEventReservationCancelled(User user, Event event) {
        save(user, ActivityActionType.EVENT_RESERVATION_CANCELLED, null, event, null, null);
    }

    public void logReservationUpdated(User user, TimeSlot timeSlot, int participants) {
        save(user, ActivityActionType.RESERVATION_UPDATED, timeSlot, null, participants, null);
    }

    public void logEventReservationUpdated(User user, Event event, int participants) {
        save(user, ActivityActionType.EVENT_RESERVATION_UPDATED, null, event, participants, null);
    }

    public void logCancelledByAdmin(User user, TimeSlot timeSlot, int participants) {
        save(user, ActivityActionType.RESERVATION_CANCELLED_BY_ADMIN, timeSlot, null, participants, null);
    }

    public void logAdminSlotCreated(User admin, TimeSlot slot) {
        save(admin, ActivityActionType.ADMIN_SLOT_CREATED, slot, null, null, null);
    }

    public void logAdminSlotUpdated(User admin, TimeSlot slot) {
        save(admin, ActivityActionType.ADMIN_SLOT_UPDATED, slot, null, null, null);
    }

    public void logAdminSlotDeleted(User admin, String description) {
        save(admin, ActivityActionType.ADMIN_SLOT_DELETED, null, null, null, description);
    }

    public void logAdminSlotBlocked(User admin, TimeSlot slot, @Nullable String reason) {
        save(admin, ActivityActionType.ADMIN_SLOT_BLOCKED, slot, null, null, reason);
    }

    public void logAdminSlotUnblocked(User admin, TimeSlot slot) {
        save(admin, ActivityActionType.ADMIN_SLOT_UNBLOCKED, slot, null, null, null);
    }

    public void logAdminEventCreated(User admin, Event event) {
        save(admin, ActivityActionType.ADMIN_EVENT_CREATED, null, event, null, null);
    }

    public void logAdminEventUpdated(User admin, Event event) {
        save(admin, ActivityActionType.ADMIN_EVENT_UPDATED, null, event, null, null);
    }

    public void logAdminEventDeleted(User admin, String description) {
        save(admin, ActivityActionType.ADMIN_EVENT_DELETED, null, null, null, description);
    }

    public void logAdminUserMakeAdmin(User admin, String description) {
        save(admin, ActivityActionType.ADMIN_USER_MAKE_ADMIN, null, null, null, description);
    }

    public void logAdminUserAdminRemoved(User admin, String description) {
        save(admin, ActivityActionType.ADMIN_USER_ADMIN_REMOVED, null, null, null, description);
    }

    public void logAdminUserDeleted(User admin, String description) {
        save(admin, ActivityActionType.ADMIN_USER_DELETED, null, null, null, description);
    }

    @Transactional(readOnly = true)
    public List<ActivityLogDto> getRecentLogs(int page, int size) {
        List<ActivityLog> logs = activityLogRepository.findRecentWithDetails(PageRequest.of(page, size));
        return logs.stream().map(this::toDto).toList();
    }

    private void save(User user, ActivityActionType actionType,
                      @Nullable TimeSlot timeSlot, @Nullable Event event,
                      @Nullable Integer participants, @Nullable String description) {
        ActivityLog log = new ActivityLog(user, actionType);
        log.setTimeSlot(timeSlot);
        log.setEvent(event);
        log.setParticipants(participants);
        log.setDescription(description);
        activityLogRepository.save(log);
    }

    private ActivityLogDto toDto(ActivityLog log) {
        TimeSlot slot = log.getTimeSlot();
        Event event = log.getEvent();

        return new ActivityLogDto(
            log.getId(),
            log.getUser().getFullName(),
            log.getUser().getEmail(),
            log.getActionType().name(),
            slot != null ? slot.getDate() : null,
            slot != null ? slot.getStartTime() : null,
            slot != null ? slot.getEndTime() : null,
            slot != null ? slot.getDisplayTitle() : null,
            event != null ? event.getTitle() : null,
            event != null ? event.getStartDate() : null,
            event != null ? event.getEndDate() : null,
            log.getParticipants(),
            log.getDescription(),
            log.getCreatedAt()
        );
    }
}
