package pl.nextsteppro.climbing.api.admin;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for EditNotificationPolicy — who gets mailed when an admin edits a slot or an event.
 * The rule mirrors the one deletion already applies: nothing that is over sends mail, but a
 * reschedule out of the past does.
 */
class EditNotificationPolicyTest {

    // Wednesday 2026-08-05, 12:00 Warsaw
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 12, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    // ========== events (date granularity, today counts as over) ==========

    @Test
    void shouldStaySilentWhenEditingAnEventThatIsAlreadyOver() {
        // Fixing the location on last month's trip is bookkeeping — the athletes already went
        assertFalse(EditNotificationPolicy.eventEditIsNews(d(2026, 7, 28), d(2026, 7, 28), TODAY));
    }

    @Test
    void shouldStaySilentWhenTheEventEndedToday() {
        // deleteEvent already treats an event ending today as past; editing must agree
        assertFalse(EditNotificationPolicy.eventEditIsNews(TODAY, TODAY, TODAY));
    }

    @Test
    void shouldNotifyWhenTheEventIsStillUpcoming() {
        assertTrue(EditNotificationPolicy.eventEditIsNews(d(2026, 8, 20), d(2026, 8, 20), TODAY));
    }

    @Test
    void shouldNotifyWhenAFinishedEventIsMovedIntoTheFuture() {
        // A reschedule out of the past is exactly the case people booked for
        assertTrue(EditNotificationPolicy.eventEditIsNews(d(2026, 7, 28), d(2026, 9, 1), TODAY));
    }

    @Test
    void shouldNotifyWhenAnUpcomingEventIsMovedIntoThePast() {
        // Someone still holds a booking for a date that no longer exists — they have to hear it
        assertTrue(EditNotificationPolicy.eventEditIsNews(d(2026, 8, 20), d(2026, 7, 1), TODAY));
    }

    // ========== slots (minute granularity) ==========

    @Test
    void shouldStaySilentWhenTheSlotEndedEarlierToday() {
        // Same line deleteTimeSlot draws: ended today is archived, not upcoming
        LocalDateTime endedThisMorning = LocalDateTime.of(2026, 8, 5, 10, 0);
        assertFalse(EditNotificationPolicy.slotEditIsNews(endedThisMorning, endedThisMorning, NOW));
    }

    @Test
    void shouldNotifyWhenTheSlotEndsLaterToday() {
        LocalDateTime endsThisEvening = LocalDateTime.of(2026, 8, 5, 18, 0);
        assertTrue(EditNotificationPolicy.slotEditIsNews(endsThisEvening, endsThisEvening, NOW));
    }

    @Test
    void shouldNotifyWhenAFinishedSlotIsMovedForward() {
        assertTrue(EditNotificationPolicy.slotEditIsNews(
            LocalDateTime.of(2026, 8, 1, 18, 0),
            LocalDateTime.of(2026, 8, 9, 18, 0),
            NOW));
    }

    @Test
    void shouldTreatASlotEndingExactlyNowAsOver() {
        // Boundary: end == now means the session is done, nothing left to announce
        assertFalse(EditNotificationPolicy.slotEditIsNews(NOW, NOW, NOW));
    }

    private static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }
}
