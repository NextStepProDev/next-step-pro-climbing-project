package pl.nextsteppro.climbing.api.activitylog;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record ActivityLogDto(
    UUID id,
    String userFullName,
    String userEmail,
    String actionType,
    @Nullable LocalDate slotDate,
    @Nullable LocalTime slotStartTime,
    @Nullable LocalTime slotEndTime,
    @Nullable String slotTitle,
    @Nullable String eventTitle,
    @Nullable LocalDate eventStartDate,
    @Nullable LocalDate eventEndDate,
    @Nullable Integer participants,
    Instant createdAt
) {}
