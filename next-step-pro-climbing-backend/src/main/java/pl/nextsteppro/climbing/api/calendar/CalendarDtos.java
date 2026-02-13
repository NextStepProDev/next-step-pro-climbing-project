package pl.nextsteppro.climbing.api.calendar;

import org.jspecify.annotations.Nullable;

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
    boolean hasUserReservation
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
    @Nullable String eventTitle
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
    @Nullable UUID reservationId
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
    boolean enrollmentOpen
) implements CalendarDtos {}

enum SlotStatus {
    AVAILABLE,
    FULL,
    BLOCKED,
    PAST,
    BOOKING_CLOSED
}
