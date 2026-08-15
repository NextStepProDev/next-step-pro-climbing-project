package pl.nextsteppro.climbing.api.admin;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.nextsteppro.climbing.api.reservation.ReservationService;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An event's per-day slots must follow the event's date range.
 *
 * <p>A reservation hangs off a slot, not off an event, so the first signup silently creates one
 * slot per day of the event and books the person onto all of them. Editing the event used to move
 * only the event's own dates and leave those slots — with their confirmed reservations — sitting on
 * days the event no longer had. Nothing on screen showed it: the calendar filters event slots out
 * entirely, and "my reservations" groups by event and prints the event's dates.
 * {@code TrainingStatsService} counts reservation ROWS, though, so a day the admin had cancelled
 * still scored an activity, painted a heatmap square and asked the athlete to rate it.
 *
 * <p>The dangerous half of the fix is the shift case, which is why it comes first below: a course
 * pushed a week forward has EVERY slot outside its new range, so a naive "delete whatever falls
 * outside" would quietly unbook everybody while telling them only that the dates had changed.
 *
 * <p>Lives in this package rather than under {@code integration/} because {@code UpdateEventRequest}
 * is package-private.
 */
class EventSlotReconcileIntegrationTest extends BaseIntegrationTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private AdminService adminService;

    @Autowired
    private ReservationService reservationService;

    private UUID adminId;
    private UUID eventId;
    private LocalDate start;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        eventRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        User admin = new User("admin@test.local", "Ad", "Min", "+48123123123", "adminuser");
        admin.setRole(UserRole.ADMIN);
        admin.setEmailVerified(true);
        adminId = userRepository.save(admin).getId();

        User climber = new User("climber@test.local", "Cli", "Mber", "+48123456789", "climber");
        climber.setRole(UserRole.USER);
        climber.setEmailVerified(true);
        UUID userId = userRepository.save(climber).getId();

        // Far enough out that the 12 h booking window never interferes.
        start = LocalDate.now().plusDays(30);
        Event event = new Event("Kurs trzydniowy", EventType.TRAINING, start, start.plusDays(2), 6);
        eventId = eventRepository.save(event).getId();

        // The signup is what brings the slots into existence — one per day, all three booked.
        reservationService.createEventReservation(eventId, userId, null, 1);
        flushAndClear();
        assertEquals(3, slotDates().size(), "signup should have created one slot per day");
        assertEquals(3, confirmedReservations().size());
    }

    @Test
    void shouldKeepEverySeatWhenTheEventIsPushedForward() {
        LocalDate moved = start.plusDays(7);
        adminService.updateEvent(adminId, eventId, datesOnly(moved, moved.plusDays(2)));
        flushAndClear();

        assertEquals(List.of(moved, moved.plusDays(1), moved.plusDays(2)), slotDates(),
            "a shifted course must carry its slots along, not lose them");
        assertEquals(3, confirmedReservations().size(),
            "nobody may be unbooked by a reschedule");
    }

    @Test
    void shouldDropOnlyTheDayTheEventLost() {
        adminService.updateEvent(adminId, eventId, datesOnly(start, start.plusDays(1)));
        flushAndClear();

        assertEquals(List.of(start, start.plusDays(1)), slotDates());
        List<Reservation> left = confirmedReservations();
        assertEquals(2, left.size(), "the dropped day must not keep a live reservation");
        assertTrue(left.stream().noneMatch(r -> r.getTimeSlot().getDate().isAfter(start.plusDays(1))),
            "no reservation may survive on a date the event no longer covers");
    }

    @Test
    void shouldGiveAnAddedDayItsOwnSlot() {
        adminService.updateEvent(adminId, eventId, datesOnly(start, start.plusDays(3)));
        flushAndClear();

        assertEquals(List.of(start, start.plusDays(1), start.plusDays(2), start.plusDays(3)), slotDates(),
            "a lengthened event needs a slot for the new day, or the next signup silently skips it");
        assertEquals(3, confirmedReservations().size(),
            "the added day is not quietly booked for people who signed up for the old range");
    }

    @Test
    void shouldRejectAnInvertedRangeInsteadOfEmptyingTheEvent() {
        assertThrows(IllegalArgumentException.class,
            () -> adminService.updateEvent(adminId, eventId, datesOnly(start.plusDays(2), start)));
        flushAndClear();

        assertEquals(3, slotDates().size(), "a rejected edit must leave the slots alone");
        assertEquals(3, confirmedReservations().size());
    }

    private static UpdateEventRequest datesOnly(LocalDate from, LocalDate to) {
        return new UpdateEventRequest(null, null, null, null, from, to,
            null, null, null, null, null, null, null);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private List<LocalDate> slotDates() {
        return timeSlotRepository.findByEventId(eventId).stream()
            .map(TimeSlot::getDate)
            .sorted()
            .toList();
    }

    private List<Reservation> confirmedReservations() {
        List<UUID> slotIds = timeSlotRepository.findByEventId(eventId).stream()
            .map(TimeSlot::getId)
            .toList();
        if (slotIds.isEmpty()) return List.of();
        return reservationRepository.findConfirmedByTimeSlotIds(slotIds).stream()
            .sorted(Comparator.comparing(r -> r.getTimeSlot().getDate()))
            .toList();
    }
}
