package pl.nextsteppro.climbing.api.reservation;

import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

record WaitlistResultDto(
    boolean success,
    String message
) {}

record WaitlistEntryDto(
    UUID id,
    UUID slotId,
    LocalDate slotDate,
    LocalTime slotStartTime,
    LocalTime slotEndTime,
    @Nullable String slotTitle,
    WaitlistStatus status,
    @Nullable Instant confirmationDeadline,
    int position
) {}
