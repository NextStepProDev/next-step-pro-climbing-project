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
    @Nullable String description,
    // The person an ADMIN_* action was taken on, read live from their account. Both null with
    // subjectErased = true once that account has been deleted.
    @Nullable String subjectFullName,
    @Nullable String subjectEmail,
    boolean subjectErased,
    Instant createdAt
) {}
