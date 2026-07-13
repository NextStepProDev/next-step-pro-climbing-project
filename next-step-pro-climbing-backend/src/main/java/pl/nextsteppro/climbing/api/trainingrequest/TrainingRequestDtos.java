package pl.nextsteppro.climbing.api.trainingrequest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

record CreateTrainingRequestRequest(
    @NotNull LocalDate requestedDate,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @Min(1) @Max(20) int participants,
    @Nullable String comment,
    // Course the request refers to (optional)
    @Nullable UUID courseId,
    // Availability window the request was submitted in (optional)
    @Nullable UUID windowSlotId
) {}

record TrainingRequestDto(
    UUID id,
    LocalDate requestedDate,
    LocalTime startTime,
    LocalTime endTime,
    int participants,
    @Nullable String comment,
    String status,
    @Nullable String adminNote,
    @Nullable String courseTitle,
    // What was created from the request — for the "see in calendar" link
    @Nullable UUID createdSlotId,
    @Nullable LocalDate createdSlotDate,
    @Nullable UUID createdEventId,
    @Nullable LocalDate createdEventStartDate,
    Instant createdAt
) {}

record TrainingRequestResultDto(
    UUID id,
    String message
) {}
