package pl.nextsteppro.climbing.api.admin;

import pl.nextsteppro.climbing.domain.BookingTimeValidator;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Does an admin edit still concern something upcoming — i.e. should the people who booked it hear
 * about it by mail?
 *
 * <p>Deletion already answers this: {@code deleteEvent} and {@code deleteTimeSlot} both go silent
 * for entries that are over, because nobody needs a mail about a session that happened. Editing
 * never got the same rule, so fixing a typo in a location from last month mailed everyone who
 * attended. This class is that missing rule.
 *
 * <p><b>Both placements decide.</b> Silence requires the entry to have been over BEFORE the edit
 * <em>and</em> to stay over after it: moving a finished session to a future date is a reschedule,
 * and that IS news for the people holding a booking. That rule is the whole content of this class —
 * "is it over" itself belongs to {@link BookingTimeValidator}, which every other temporal decision
 * in the app already uses, so it is asked, not re-derived.
 */
final class EditNotificationPolicy {

    private EditNotificationPolicy() {}

    /** Events carry no times (an all-day event has none), so they are over at day resolution. */
    static boolean eventEditIsNews(LocalDate oldEndDate, LocalDate newEndDate, LocalDate todayWarsaw) {
        return !BookingTimeValidator.dayHasPassed(oldEndDate, todayWarsaw)
            || !BookingTimeValidator.dayHasPassed(newEndDate, todayWarsaw);
    }

    /** Slots carry an end time, so "over" is exact to the minute. */
    static boolean slotEditIsNews(LocalDateTime oldEnd, LocalDateTime newEnd, LocalDateTime nowWarsaw) {
        return !BookingTimeValidator.isPast(oldEnd.toLocalDate(), oldEnd.toLocalTime(), nowWarsaw)
            || !BookingTimeValidator.isPast(newEnd.toLocalDate(), newEnd.toLocalTime(), nowWarsaw);
    }
}
