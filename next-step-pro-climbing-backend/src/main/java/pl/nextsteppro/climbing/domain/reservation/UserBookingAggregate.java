package pl.nextsteppro.climbing.domain.reservation;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One person's whole booking record folded into four numbers, for the admin statistics screen.
 *
 * <p>Only users with at least one confirmed booking get a row. That absence is load-bearing: the
 * screen's "never booked" cohort is the accounts total minus the rows returned here, so nobody has
 * to ask the database for the far larger set of people who did nothing.
 *
 * <p>{@code attended} counts what has already happened and {@code confirmed} counts everything
 * confirmed including the future — a person with one booking next Tuesday is a customer, but not
 * yet somebody who came. {@code lastDate} spans both for the same reason: it drives the
 * active/dormant split, and a booking in the future is the strongest possible sign of activity.
 *
 * <p>A multi-day event contributes one row per day here, because a reservation hangs off a slot and
 * an event gets one slot per day. That is the same unit the training statistics use, deliberately —
 * see the note on multi-day events in CLAUDE.md.
 */
public record UserBookingAggregate(
    UUID userId,
    long confirmed,
    long attended,
    LocalDate lastDate
) {}
