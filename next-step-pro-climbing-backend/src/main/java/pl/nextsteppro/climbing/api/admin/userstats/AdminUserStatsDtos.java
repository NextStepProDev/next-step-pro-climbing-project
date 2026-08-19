package pl.nextsteppro.climbing.api.admin.userstats;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only shapes for the admin's user statistics. No request record here on purpose — the screen
 * answers questions about the base, it does not change it.
 *
 * <p>Everything below comes from ONE snapshot: a single service call reads the accounts and the
 * bookings, so the totals, the funnel and the cohorts always add up against each other. Splitting
 * any of it back onto the client (which already holds the user list) would reintroduce two
 * denominators from two moments, and that difference reads as a bug long before anyone guesses it
 * is a race with a registration.
 */
record UserStatsDto(
    AccountTotalsDto totals,
    List<MonthlyRegistrationsDto> registrations,
    FunnelDto funnel,
    CohortsDto cohorts,
    List<TopClientDto> topClients,
    NewsletterBreakdownDto newsletter,
    AthleteBreakdownDto athletes
) {}

/** The headline row. Every number shares the {@code accounts} denominator. */
record AccountTotalsDto(long accounts, long verified, long athletes, long newsletter, long admins) {}

/**
 * One bar of the registrations chart.
 *
 * <p>{@code month} is the first day of the month rather than {@code yyyy-MM}, so the client can
 * parse it with the same date-label helper as every other date on the wire instead of hand-rolling
 * a parse for one field. Months with no registrations are present with zeroes — a gap the client
 * has to fill is a gap the client can get wrong.
 */
record MonthlyRegistrationsDto(LocalDate month, long total, long verified) {}

/**
 * Registration → confirmed address → first booking → came back.
 *
 * <p>The two booking steps count CONFIRMED reservations only, and hold for life: "booked" is
 * whether this person has ever held a booking, not whether they hold one now. Someone whose only
 * booking was cancelled counts as never booked — the step asks whether the account turned into a
 * customer, and a cancelled booking is exactly the case where it did not.
 */
record FunnelDto(long booked, long returning) {}

/**
 * Active / dormant / never, split by the most recent booking.
 *
 * <p>Deliberately measured in bookings, not logins: there is no last-login column in the database,
 * so a "last seen" cohort would be invented rather than measured. {@code windowDays} ships with the
 * numbers so the screen can name the rule it is applying instead of implying a stronger one.
 *
 * <p>A booking in the future counts as active — it is the strongest sign there is.
 */
record CohortsDto(long active, long dormant, long never, int windowDays) {}

/**
 * Ranked by sessions actually attended, so an upcoming booking cannot buy a place on the list.
 * Carries the id because each row links through to that person's card.
 */
record TopClientDto(UUID userId, String firstName, String lastName, long attended) {}

/**
 * Subscribed / unsubscribed / never asked.
 *
 * <p>The third bucket is the point of the breakdown: {@code newsletter_choice_made} distinguishes
 * someone who declined from someone who was never given the choice, and only the second group is
 * worth doing anything about.
 */
record NewsletterBreakdownDto(long subscribed, long unsubscribed, long undecided) {}

/**
 * Flag granted → consent signed → plan actually used.
 *
 * <p>Reads as a funnel for a reason: the gap between the first and the last is the list of people
 * who were given calendar access and never opened it, which is invisible from the roster.
 */
record AthleteBreakdownDto(long flagged, long consented, long withPlan) {}
