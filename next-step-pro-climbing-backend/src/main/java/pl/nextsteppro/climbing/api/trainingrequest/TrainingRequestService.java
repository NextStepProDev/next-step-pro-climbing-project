package pl.nextsteppro.climbing.api.trainingrequest;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.BookingTimeValidator;
import pl.nextsteppro.climbing.domain.course.Course;
import pl.nextsteppro.climbing.domain.course.CourseRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequest;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequestRepository;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequestStatus;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.mail.MailService;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TrainingRequestService {

    /** Anti-spam limit: this many of a user's requests may await a response at once. */
    static final int MAX_PENDING_PER_USER = 3;

    private final TrainingRequestRepository trainingRequestRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final MessageService msg;

    public TrainingRequestService(TrainingRequestRepository trainingRequestRepository,
                                  TimeSlotRepository timeSlotRepository,
                                  CourseRepository courseRepository,
                                  UserRepository userRepository,
                                  MailService mailService,
                                  MessageService msg) {
        this.trainingRequestRepository = trainingRequestRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.msg = msg;
    }

    public TrainingRequestResultDto create(UUID userId, CreateTrainingRequestRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException(msg.get("admin.slot.end.after.start"));
        }
        if (BookingTimeValidator.isPast(request.requestedDate(), request.startTime())) {
            throw new IllegalArgumentException(msg.get("training.request.past"));
        }
        if (trainingRequestRepository.countByUserIdAndStatus(userId, TrainingRequestStatus.PENDING) >= MAX_PENDING_PER_USER) {
            throw new IllegalStateException(msg.get("training.request.limit", String.valueOf(MAX_PENDING_PER_USER)));
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        TrainingRequest tr = new TrainingRequest(
            user, request.requestedDate(), request.startTime(), request.endTime(), request.participants());
        tr.setComment(TrainingRequest.sanitizeComment(request.comment()));

        boolean inWindow = false;
        if (request.windowSlotId() != null) {
            TimeSlot window = timeSlotRepository.findById(request.windowSlotId())
                .orElseThrow(() -> new IllegalArgumentException(msg.get("training.request.window.invalid")));
            if (!window.isAvailabilityWindow()) {
                throw new IllegalArgumentException(msg.get("training.request.window.invalid"));
            }
            boolean sameDate = window.getDate().equals(request.requestedDate());
            boolean withinHours = !request.startTime().isBefore(window.getStartTime())
                && !request.endTime().isAfter(window.getEndTime());
            if (!sameDate || !withinHours) {
                throw new IllegalArgumentException(msg.get("training.request.window.outside"));
            }
            tr.setWindowSlot(window);
            inWindow = true;
        }

        String courseTitle = null;
        if (request.courseId() != null) {
            Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));
            tr.setCourse(course);
            courseTitle = course.getTitle();
        }

        tr = trainingRequestRepository.save(tr);

        mailService.sendTrainingRequestAdminNotification(
            user, tr.getRequestedDate(), tr.getStartTime(), tr.getEndTime(),
            tr.getParticipants(), tr.getComment(), courseTitle, inWindow);

        return new TrainingRequestResultDto(tr.getId(), msg.get("training.request.created"));
    }

    @Transactional(readOnly = true)
    public List<TrainingRequestDto> getUserRequests(UUID userId) {
        return trainingRequestRepository.findByUserIdWithDetails(userId).stream()
            .map(TrainingRequestService::toDto)
            .toList();
    }

    /** The user may withdraw their own request as long as the admin has not responded to it. */
    public void cancel(UUID requestId, UUID userId) {
        TrainingRequest tr = trainingRequestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("training.request.not.found")));
        if (!tr.getUser().getId().equals(userId)) {
            throw new IllegalStateException(msg.get("training.request.not.owner"));
        }
        if (tr.getStatus() != TrainingRequestStatus.PENDING) {
            throw new IllegalStateException(msg.get("training.request.not.pending"));
        }
        trainingRequestRepository.delete(tr);
    }

    static TrainingRequestDto toDto(TrainingRequest tr) {
        Course course = tr.getCourse();
        TimeSlot createdSlot = tr.getCreatedSlot();
        var createdEvent = tr.getCreatedEvent();
        return new TrainingRequestDto(
            tr.getId(),
            tr.getRequestedDate(),
            tr.getStartTime(),
            tr.getEndTime(),
            tr.getParticipants(),
            tr.getComment(),
            tr.getStatus().name(),
            visibleAdminNote(tr),
            course != null ? course.getTitle() : null,
            createdSlot != null ? createdSlot.getId() : null,
            createdSlot != null ? createdSlot.getDate() : null,
            createdEvent != null ? createdEvent.getId() : null,
            createdEvent != null ? createdEvent.getStartDate() : null,
            tr.getCreatedAt()
        );
    }

    /** The admin's note is visible to the user only for CONTACTED/REJECTED (decision context). */
    @Nullable
    private static String visibleAdminNote(TrainingRequest tr) {
        return switch (tr.getStatus()) {
            case CONTACTED, REJECTED -> tr.getAdminNote();
            default -> null;
        };
    }
}
