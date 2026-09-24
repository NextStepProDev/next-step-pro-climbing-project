package pl.nextsteppro.climbing.domain.trainingrequest;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * When the entry created from a training request actually takes place — read live from the slot or
 * event, so an edit made after accepting is reflected too.
 *
 * @param endDate   set only when the entry spans more than one day
 * @param startTime null for an all-day event (both times are null together)
 */
public record AgreedTerm(
    LocalDate date,
    @Nullable LocalDate endDate,
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime
) {}
