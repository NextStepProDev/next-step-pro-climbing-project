package pl.nextsteppro.climbing.api.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Does an admin edit still concern something upcoming — i.e. should the people who booked it hear
 * about it by mail?
 *
 * <p>Deletion already answers this: {@code deleteEvent} and {@code deleteTimeSlot} both go silent
 * for entries that are over, because nobody needs a mail about a session that already happened.
 * Editing never got the same rule, so fixing a typo in a location from last month mailed everyone
 * who attended. This class is that missing rule, kept pure (no Spring, no clock of its own — "now"
 * is always passed in) so it can be tested without standing up the service.
 *
 * <p><b>Both placements decide.</b> Silence requires the entry to have been over BEFORE the edit
 * <em>and</em> to stay over after it: moving a finished session to a future date is a reschedule,
 * and that IS news for the people holding a booking.
 */
final class EditNotificationPolicy {

    private EditNotificationPolicy() {}

    /**
     * Events are date-granular (all-day events carry no times) and an event ending <em>today</em>
     * already counts as over — the same line {@code deleteEvent} draws.
     */
    static boolean eventEditIsNews(LocalDate oldEndDate, LocalDate newEndDate, LocalDate todayWarsaw) {
        return !hasEnded(oldEndDate, todayWarsaw) || !hasEnded(newEndDate, todayWarsaw);
    }

    /**
     * Slots carry an end time, so "over" is exact to the minute — the same line
     * {@code deleteTimeSlot} draws (a slot that ended earlier today is already archived).
     */
    static boolean slotEditIsNews(LocalDateTime oldEnd, LocalDateTime newEnd, LocalDateTime nowWarsaw) {
        return oldEnd.isAfter(nowWarsaw) || newEnd.isAfter(nowWarsaw);
    }

    private static boolean hasEnded(LocalDate endDate, LocalDate today) {
        return !endDate.isAfter(today);
    }
}
