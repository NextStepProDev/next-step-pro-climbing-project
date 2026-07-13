package pl.nextsteppro.climbing.api.admin.trainingrequest;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.course.Course;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequest;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequestRepository;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequestStatus;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.mail.MailService;

import java.util.UUID;

@Service
@Transactional
public class AdminTrainingRequestService {

    private final TrainingRequestRepository trainingRequestRepository;
    private final MailService mailService;
    private final MessageService msg;

    public AdminTrainingRequestService(TrainingRequestRepository trainingRequestRepository,
                                       MailService mailService,
                                       MessageService msg) {
        this.trainingRequestRepository = trainingRequestRepository;
        this.mailService = mailService;
        this.msg = msg;
    }

    /**
     * Paginated request list: without a filter — pending first, then newest;
     * with a status filter — newest first. An archive of hundreds of requests never loads whole.
     */
    @Transactional(readOnly = true)
    public AdminTrainingRequestPageDto getPage(@Nullable TrainingRequestStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        Page<TrainingRequest> result = status != null
            ? trainingRequestRepository.findPageByStatusWithDetails(status, pageable)
            : trainingRequestRepository.findPageWithDetails(pageable);
        return new AdminTrainingRequestPageDto(
            result.getContent().stream().map(AdminTrainingRequestService::toDto).toList(),
            result.getNumber(),
            result.getTotalPages(),
            result.getTotalElements()
        );
    }

    /**
     * Status change: CONTACTED / REJECTED / PENDING (restore to pending).
     * ACCEPTED is only created by creating a slot/event with {@code trainingRequestId}
     * (see AdminService) — it is rejected here so no ACCEPTED without a link can appear.
     */
    public AdminTrainingRequestDto updateStatus(UUID requestId, UpdateTrainingRequestStatusRequest request) {
        // JOIN FETCH user — the rejection email is sent asynchronously and reads the user outside the session
        TrainingRequest tr = trainingRequestRepository.findByIdWithUser(requestId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("training.request.not.found")));

        TrainingRequestStatus newStatus;
        try {
            newStatus = TrainingRequestStatus.valueOf(request.status());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + request.status());
        }

        String note = sanitizeNote(request.adminNote());
        switch (newStatus) {
            case PENDING -> {
                tr.reopen();
                tr.setAdminNote(note);
            }
            case CONTACTED, REJECTED -> {
                tr.setAdminNote(note);
                tr.resolve(newStatus);
                if (newStatus == TrainingRequestStatus.REJECTED && request.notifyUser()) {
                    mailService.sendTrainingRequestRejectedNotification(
                        tr.getUser(), tr.getRequestedDate(), tr.getStartTime(), tr.getEndTime(), note);
                }
            }
            default -> throw new IllegalArgumentException("Invalid status: " + request.status());
        }

        return toDto(tr);
    }

    @Nullable
    private static String sanitizeNote(@Nullable String note) {
        if (note == null || note.isBlank()) return null;
        String trimmed = note.trim();
        return trimmed.length() > TrainingRequest.MAX_ADMIN_NOTE_LENGTH
            ? trimmed.substring(0, TrainingRequest.MAX_ADMIN_NOTE_LENGTH)
            : trimmed;
    }

    static AdminTrainingRequestDto toDto(TrainingRequest tr) {
        Course course = tr.getCourse();
        TimeSlot window = tr.getWindowSlot();
        TimeSlot createdSlot = tr.getCreatedSlot();
        Event createdEvent = tr.getCreatedEvent();
        return new AdminTrainingRequestDto(
            tr.getId(),
            tr.getUser().getId(),
            tr.getUser().getFullName(),
            tr.getUser().getEmail(),
            tr.getUser().getPhone(),
            tr.getRequestedDate(),
            tr.getStartTime(),
            tr.getEndTime(),
            tr.getParticipants(),
            tr.getComment(),
            tr.getStatus().name(),
            tr.getAdminNote(),
            course != null ? course.getId() : null,
            course != null ? course.getTitle() : null,
            window != null,
            window != null ? window.getId() : null,
            window != null ? window.getStartTime() : null,
            window != null ? window.getEndTime() : null,
            createdSlot != null ? createdSlot.getId() : null,
            createdSlot != null ? createdSlot.getDate() : null,
            createdEvent != null ? createdEvent.getId() : null,
            createdEvent != null ? createdEvent.getStartDate() : null,
            tr.getCreatedAt(),
            tr.getResolvedAt()
        );
    }
}
