package pl.nextsteppro.climbing.domain.settlement;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One participant of a session that has already happened and still has no amount against them.
 *
 * <p>This is the other half of "what is missing", and the half that cannot ask for itself. An unpaid
 * amount at least exists as a row and shows up as a debt; a session nobody ever priced is neither
 * revenue nor debt, so without this it is invisible everywhere and the only way to find it is to
 * read the calendar.
 *
 * <p>One row per unpriced payer, deliberately, even though the tab groups them per session before
 * drawing: the count of people still to price is the number that says how much work the row is, and
 * counting in SQL across two payer sources (registered and guest) would be a second query shape to
 * keep in step with this one.
 *
 * @param targetId    the slot or the event — never a per-day slot of an event, since that is not an
 *                    address an amount can be written to.
 * @param targetDate  the slot's date, or the event's first day.
 * @param payerId     ⚠️ carried so DISTINCT can collapse an event booking's one-row-per-day without
 *                    also collapsing two DIFFERENT people who both still need pricing. Without it
 *                    a course with five unpriced attendees reports one.
 */
public record UnpricedPayer(UUID targetId, LocalDate targetDate, @Nullable String targetTitle,
                            UUID payerId) {}
