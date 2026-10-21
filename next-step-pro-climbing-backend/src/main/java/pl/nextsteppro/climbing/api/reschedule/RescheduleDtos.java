package pl.nextsteppro.climbing.api.reschedule;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Exactly one of {@code reservationId} (a training) or {@code eventId} (a course/event) — the
 * reservation is only an address on the wire, resolved to the pair (term, person).
 * {@code newEndDate} is for events only; hours are required for a training and optional (both or
 * neither) for an event.
 */
record CreateRescheduleRequest(
    @Nullable UUID reservationId,
    @Nullable UUID eventId,
    @NotNull LocalDate newDate,
    @Nullable LocalDate newEndDate,
    @Nullable LocalTime newStartTime,
    @Nullable LocalTime newEndTime,
    @Nullable @Size(max = 1000, message = "{validation.comment.size}") String comment
) {}

record RescheduleResultDto(UUID id, String message) {}

/** One open request, as the client's reservation card shows it. */
record MyRescheduleRequestDto(
    UUID id,
    @Nullable UUID timeSlotId,
    @Nullable UUID eventId,
    LocalDate newDate,
    @Nullable LocalDate newEndDate,
    @Nullable LocalTime newStartTime,
    @Nullable LocalTime newEndTime,
    @Nullable String comment,
    Instant createdAt
) {}
