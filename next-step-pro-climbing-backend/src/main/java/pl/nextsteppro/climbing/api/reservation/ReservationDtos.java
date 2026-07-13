package pl.nextsteppro.climbing.api.reservation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

record ReservationResultDto(
    UUID reservationId,
    boolean success,
    String message
) {}

record UserReservationDto(
    UUID id,
    UUID timeSlotId,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    String status,
    @Nullable String eventTitle,
    @Nullable String comment,
    int participants,
    int spotsAvailable,
    Instant createdAt
) {}

record CreateReservationRequest(
    @Nullable @Size(max = 500, message = "{validation.comment.size}") String comment,
    @Nullable @Min(value = 1, message = "{validation.min.participants}") @Max(value = 50, message = "{validation.max.participants}") Integer participants
) {}

record UpdateParticipantsRequest(
    @Min(value = 1, message = "{validation.min.participants}") @Max(value = 50, message = "{validation.max.participants}") int participants
) {}

record EventReservationResultDto(
    UUID eventId,
    boolean success,
    String message,
    int slotsReserved
) {}

record UserEventReservationDto(
    UUID eventId,
    String eventTitle,
    String eventType,
    LocalDate startDate,
    LocalDate endDate,
    @Nullable String comment,
    int participants,
    int slotsCount,
    int spotsAvailable,
    Instant createdAt,
    @Nullable UUID courseId,
    boolean cancelledByAdmin
) {}

record MyReservationsDto(
    List<UserReservationDto> slots,
    List<UserEventReservationDto> events
) {}

// A pending invitation from the recipient's perspective — a held seat they have not taken yet.
record MyInvitationDto(
    String type,                  // SLOT | EVENT
    @Nullable UUID slotId,
    @Nullable UUID eventId,
    @Nullable String title,
    @Nullable String eventType,   // events only
    LocalDate date,               // slot date or event start
    @Nullable LocalDate endDate,  // multi-day events only
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime,
    @Nullable String location
) {}
