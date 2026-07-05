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
    // Kurs, którego dotyczy propozycja (opcjonalnie)
    @Nullable UUID courseId,
    // Okno dostępności, w którym złożono propozycję (opcjonalnie)
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
    // Co powstało z propozycji — do linku "zobacz w kalendarzu"
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
