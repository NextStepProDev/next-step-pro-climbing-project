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
    // Propozycja złożona w oknie dostępności (jeśli okno usunięto, FK jest NULL i flaga gaśnie)
    boolean inWindow,
    @Nullable UUID windowSlotId,
    @Nullable LocalTime windowStartTime,
    @Nullable LocalTime windowEndTime,
    // Co powstało z propozycji
    @Nullable UUID createdSlotId,
    @Nullable LocalDate createdSlotDate,
    @Nullable UUID createdEventId,
    @Nullable LocalDate createdEventStartDate,
    Instant createdAt,
    @Nullable Instant resolvedAt
) {}

/**
 * Zmiana statusu przez admina: CONTACTED / REJECTED / PENDING (przywrócenie).
 * ACCEPTED nie ustawia się tutaj — powstaje wyłącznie przez utworzenie slotu/wydarzenia
 * z {@code trainingRequestId} (spójność linku created_slot/created_event).
 */
record UpdateTrainingRequestStatusRequest(
    @NotNull String status,
    @Nullable String adminNote,
    // Przy REJECTED: czy wysłać użytkownikowi mail z decyzją (i notatką)
    boolean notifyUser
) {}

record AdminTrainingRequestPageDto(
    java.util.List<AdminTrainingRequestDto> content,
    int page,
    int totalPages,
    long totalElements
) {}
