package pl.nextsteppro.climbing.api.calendar;

import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public sealed interface CalendarDtos {
}

record MonthViewDto(
    String yearMonth,
    List<DaySummaryDto> days,
    List<EventSummaryDto> events
) implements CalendarDtos {}

record DaySummaryDto(
    LocalDate date,
    int totalSlots,
    int availableSlots,
    boolean hasUserReservation,
    boolean hasAvailabilityWindow
) implements CalendarDtos {}

record DayViewDto(
    LocalDate date,
    List<TimeSlotDto> slots,
    List<EventSummaryDto> events
) implements CalendarDtos {}

record TimeSlotDto(
    UUID id,
    LocalTime startTime,
    LocalTime endTime,
    int maxParticipants,
    SlotStatus status,
    boolean isUserRegistered,
    @Nullable String eventTitle,
    boolean isAvailabilityWindow
) implements CalendarDtos {}

record TimeSlotDetailDto(
    UUID id,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    int maxParticipants,
    int currentParticipants,
    SlotStatus status,
    boolean isUserRegistered,
    @Nullable UUID eventId,
    @Nullable String eventTitle,
    @Nullable String eventDescription,
    @Nullable UUID reservationId,
    // Waitlist
    @Nullable WaitlistStatus userWaitlistStatus,
    @Nullable UUID waitlistEntryId,
    @Nullable Instant confirmationDeadline,
    int userWaitlistPosition,
    boolean isAvailabilityWindow
) implements CalendarDtos {}

record CourseEventDto(
    UUID eventId,
    LocalDate startDate,
    LocalDate endDate,
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime,
    SlotStatus status,
    int availableSpots
) implements CalendarDtos {}

record EventSummaryDto(
    UUID id,
    String title,
    @Nullable String description,
    @Nullable String location,
    String eventType,
    LocalDate startDate,
    LocalDate endDate,
    boolean isMultiDay,
    int maxParticipants,
    int currentParticipants,
    boolean isUserRegistered,
    boolean enrollmentOpen,
    @Nullable UUID courseId,
    boolean coursePublished,
    // Waitlist — null w widokach listy, wypełnione w getEventSummary (pojedyncze wydarzenie)
    @Nullable WaitlistStatus userWaitlistStatus,
    @Nullable UUID waitlistEntryId,
    @Nullable Instant confirmationDeadline,
    int userWaitlistPosition,
    // 0 w widokach listy, wypełnione w getEventSummary gdy użytkownik jest zapisany
    int userParticipants
) implements CalendarDtos {}

record WeekViewDto(
    LocalDate startDate,
    LocalDate endDate,
    List<WeekDayDto> days,
    List<EventSummaryDto> events
) implements CalendarDtos {}

record WeekDayDto(
    LocalDate date,
    List<TimeSlotDto> slots
) implements CalendarDtos {}

enum SlotStatus {
    AVAILABLE,
    FULL,
    BLOCKED,
    PAST,
    BOOKING_CLOSED,
    AVAILABILITY_WINDOW
}
