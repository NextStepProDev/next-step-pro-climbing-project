package pl.nextsteppro.climbing.api.admin;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

// Time Slot DTOs

record CreateTimeSlotRequest(
    @NotNull LocalDate date,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @Min(1) @Max(100) int maxParticipants,
    @Nullable String title,
    @Nullable UUID eventId
) {}

record TimeSlotAdminDto(
    UUID id,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    int maxParticipants,
    int currentParticipants,
    boolean blocked,
    @Nullable String blockReason,
    @Nullable String title,
    @Nullable UUID eventId
) {}

record SlotParticipantsDto(
    UUID slotId,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    int maxParticipants,
    List<ParticipantDto> participants
) {}

record ParticipantDto(
    UUID userId,
    String fullName,
    String email,
    String phone,
    @Nullable String comment,
    int participants,
    Instant registeredAt
) {}

record UpdateTimeSlotRequest(
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime,
    @Nullable @Min(1) @Max(100) Integer maxParticipants,
    @Nullable String title
) {}

// Event DTOs

record CreateEventRequest(
    @NotBlank String title,
    @Nullable String description,
    @Nullable String location,
    @NotBlank String eventType,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @Min(1) @Max(100) int maxParticipants,
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime
) {
    @AssertTrue(message = "Data zakończenia nie może być wcześniejsza niż data rozpoczęcia")
    boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}

record UpdateEventRequest(
    @Nullable String title,
    @Nullable String description,
    @Nullable String location,
    @Nullable String eventType,
    @Nullable LocalDate startDate,
    @Nullable LocalDate endDate,
    @Nullable Integer maxParticipants,
    @Nullable Boolean active,
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime
) {}

record EventAdminDto(
    UUID id,
    String title,
    @Nullable String description,
    @Nullable String location,
    String eventType,
    LocalDate startDate,
    LocalDate endDate,
    int maxParticipants,
    boolean active,
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime
) {}

record EventDetailAdminDto(
    UUID id,
    String title,
    @Nullable String description,
    @Nullable String location,
    String eventType,
    LocalDate startDate,
    LocalDate endDate,
    int maxParticipants,
    boolean active,
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime,
    List<TimeSlotAdminDto> slots
) {}

record EventParticipantsDto(
    UUID eventId,
    int maxParticipants,
    List<ParticipantDto> participants
) {}

// Reservation DTOs

record ReservationAdminDto(
    UUID id,
    String userFullName,
    String userEmail,
    String userPhone,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    @Nullable String title,
    @Nullable String comment,
    int participants,
    @Nullable LocalDate eventStartDate,
    @Nullable LocalDate eventEndDate
) {}

// User DTOs

record UserAdminDto(
    UUID id,
    String fullName,
    String email,
    String phone,
    String nickname,
    String role,
    Instant createdAt
) {}
