package pl.nextsteppro.climbing.api.reservation;

import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

record EventWaitlistEntryDto(
    UUID id,
    UUID eventId,
    String eventTitle,
    LocalDate eventStartDate,
    LocalDate eventEndDate,
    WaitlistStatus status,
    @Nullable Instant confirmationDeadline,
    int position
) {}
