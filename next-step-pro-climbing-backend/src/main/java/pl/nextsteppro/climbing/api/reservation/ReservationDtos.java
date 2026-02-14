package pl.nextsteppro.climbing.api.reservation;

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
    Instant createdAt
) {}

record CreateReservationRequest(
    @Nullable @Size(max = 500, message = "Komentarz może mieć maksymalnie 500 znaków") String comment,
    @Nullable @Min(value = 1, message = "Liczba miejsc musi wynosić co najmniej 1") Integer participants
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
    Instant createdAt
) {}

record MyReservationsDto(
    List<UserReservationDto> slots,
    List<UserEventReservationDto> events
) {}
