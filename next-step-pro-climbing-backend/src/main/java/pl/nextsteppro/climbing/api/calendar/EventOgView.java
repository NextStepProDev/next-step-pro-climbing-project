package pl.nextsteppro.climbing.api.calendar;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Minimal public projection of an event for social/OG link previews
 * ({@code OgController}). Kept separate from the package-private
 * {@link EventSummaryDto} so the OG module can read it across packages without
 * widening the visibility of the calendar DTO hierarchy.
 */
public record EventOgView(
    String title,
    @Nullable String location,
    LocalDate startDate,
    LocalDate endDate,
    @Nullable UUID courseId,
    boolean coursePublished
) {}
