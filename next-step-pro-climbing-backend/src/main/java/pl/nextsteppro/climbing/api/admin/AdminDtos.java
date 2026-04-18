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
    @Nullable UUID eventId,
    boolean isAvailabilityWindow
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
    @Nullable UUID eventId,
    boolean isAvailabilityWindow
) {}

record SlotParticipantsDto(
    UUID slotId,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    int maxParticipants,
    List<ParticipantDto> participants,
    List<GuestParticipantDto> guestParticipants
) {}

record ParticipantDto(
    UUID reservationId,
    UUID userId,
    String fullName,
    String email,
    String phone,
    @Nullable String comment,
    int participants,
    Instant registeredAt
) {}

record GuestParticipantDto(
    UUID id,
    String note,
    int participants,
    Instant createdAt
) {}

record AddRegisteredParticipantRequest(
    @NotNull UUID userId,
    @Min(1) @Max(20) int participants,
    @Nullable String comment
) {}

record AddGuestParticipantRequest(
    @NotBlank String note,
    @Min(1) @Max(20) int participants
) {}

record SlotWaitlistDto(
    UUID slotId,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    List<WaitlistAdminEntryDto> entries
) {}

record WaitlistAdminEntryDto(
    UUID waitlistId,
    UUID userId,
    String fullName,
    String email,
    String phone,
    int position,
    String status,
    @Nullable Instant confirmationDeadline,
    Instant joinedAt
) {}

record UpdateTimeSlotRequest(
    @Nullable LocalDate date,
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime,
    @Nullable @Min(1) @Max(100) Integer maxParticipants,
    @Nullable String title,
    @Nullable Boolean isAvailabilityWindow,
    @Nullable Boolean sendNotifications
) {}

record UpdateReservationParticipantsRequest(
    @Min(1) @Max(20) int participants
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
    @Nullable LocalTime endTime,
    @Nullable UUID courseId
) {
    @AssertTrue(message = "{validation.event.date.range}")
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
    @Nullable LocalTime endTime,
    @Nullable UUID courseId,
    @Nullable Boolean removeCourse
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
    int currentParticipants,
    boolean active,
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime,
    @Nullable UUID courseId,
    @Nullable String courseTitle
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
    int currentParticipants,
    boolean active,
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime,
    @Nullable UUID courseId,
    @Nullable String courseTitle,
    List<TimeSlotAdminDto> slots
) {}

record EventParticipantsDto(
    UUID eventId,
    int maxParticipants,
    List<ParticipantDto> participants,
    List<GuestParticipantDto> guestParticipants
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
    String firstName,
    String lastName,
    String email,
    String phone,
    String role,
    Instant createdAt,
    boolean newsletterSubscribed
) {}

// Mail DTOs

enum RecipientType { ALL, NEWSLETTER, SELECTED }

record SendMailRequest(
    @NotNull RecipientType recipientType,
    @Nullable List<UUID> userIds,
    @NotBlank String subject,
    @NotBlank String body
) {}

record MailSendResponse(int recipientCount) {}

record NotifyParticipantsResult(int notifiedCount) {}

record NotifySlotParticipantsRequest(
    @Nullable LocalDate previousDate,
    @Nullable LocalTime previousStartTime,
    @Nullable LocalTime previousEndTime
) {}

