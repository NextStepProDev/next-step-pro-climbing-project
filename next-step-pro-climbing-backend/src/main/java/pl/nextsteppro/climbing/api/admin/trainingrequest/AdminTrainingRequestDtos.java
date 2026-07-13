package pl.nextsteppro.climbing.api.admin.trainingrequest;

import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

record AdminTrainingRequestDto(
    UUID id,
    UUID userId,
    String userFullName,
    String userEmail,
    String userPhone,
    LocalDate requestedDate,
    LocalTime startTime,
    LocalTime endTime,
    int participants,
    @Nullable String comment,
    String status,
    @Nullable String adminNote,
    @Nullable UUID courseId,
    @Nullable String courseTitle,
    // Request submitted within an availability window (if the window was deleted, the FK is NULL and the flag goes off)
    boolean inWindow,
    @Nullable UUID windowSlotId,
    @Nullable LocalTime windowStartTime,
    @Nullable LocalTime windowEndTime,
    // What was created from the request
    @Nullable UUID createdSlotId,
    @Nullable LocalDate createdSlotDate,
    @Nullable UUID createdEventId,
    @Nullable LocalDate createdEventStartDate,
    Instant createdAt,
    @Nullable Instant resolvedAt
) {}

/**
 * Status change by the admin: CONTACTED / REJECTED / PENDING (restore).
 * ACCEPTED is not set here — it is only created by creating a slot/event
 * with {@code trainingRequestId} (keeps the created_slot/created_event link consistent).
 */
record UpdateTrainingRequestStatusRequest(
    @NotNull String status,
    @Nullable String adminNote,
    // For REJECTED: whether to email the user the decision (and the note)
    boolean notifyUser
) {}

record AdminTrainingRequestPageDto(
    java.util.List<AdminTrainingRequestDto> content,
    int page,
    int totalPages,
    long totalElements
) {}
