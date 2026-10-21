package pl.nextsteppro.climbing.api.admin.reschedule;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

record AdminRescheduleRequestDto(
    UUID id,
    UUID userId,
    String userFullName,
    String userEmail,
    @Nullable String userPhone,
    String targetType,               // SLOT | EVENT
    @Nullable UUID timeSlotId,
    @Nullable UUID eventId,
    String title,
    LocalDate oldDate,
    @Nullable LocalDate oldEndDate,
    @Nullable LocalTime oldStartTime,
    @Nullable LocalTime oldEndTime,
    LocalDate newDate,
    @Nullable LocalDate newEndDate,
    @Nullable LocalTime newStartTime,
    @Nullable LocalTime newEndTime,
    @Nullable String comment,        // HTML-escaped at save time
    String status,
    @Nullable String adminNote,
    Instant createdAt,
    @Nullable Instant resolvedAt,
    // Who else accepting would move along — pending requests only, empty for answered ones
    List<String> otherParticipants,
    int otherGuests,
    // The coach moved the term after the request was made: accepting is refused, the card says why
    boolean stale
) {}

record AdminRescheduleListDto(
    List<AdminRescheduleRequestDto> pending,
    List<AdminRescheduleRequestDto> recent
) {}

record AcceptRescheduleResultDto(
    AdminRescheduleRequestDto request,
    // How many OTHER participants got the ordinary "term changed" mail
    int notifiedOthers
) {}

record RejectRescheduleRequest(
    @Nullable @Size(max = 500) String adminNote,
    boolean notifyUser
) {}
