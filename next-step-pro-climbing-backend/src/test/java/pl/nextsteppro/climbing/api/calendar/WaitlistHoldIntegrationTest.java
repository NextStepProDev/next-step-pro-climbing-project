package pl.nextsteppro.climbing.api.calendar;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.nextsteppro.climbing.api.reservation.EventWaitlistService;
import pl.nextsteppro.climbing.api.reservation.ReservationService;
import pl.nextsteppro.climbing.api.reservation.WaitlistService;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationStatus;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlist;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlistRepository;
import pl.nextsteppro.climbing.domain.waitlist.Waitlist;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistRepository;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistStatus;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * While a waitlist offer is open, the freed seat belongs to the queue (owner's decision, 2026-10-20).
 * Before, a walk-in could book it ahead of the people who had queued, and the slot detail said "full"
 * while the week view showed a free seat.
 */
class WaitlistHoldIntegrationTest extends BaseIntegrationTest {

    @PersistenceContext private EntityManager entityManager;
    @Autowired private ReservationService reservationService;
    @Autowired private WaitlistService waitlistService;
    @Autowired private EventWaitlistService eventWaitlistService;
    @Autowired private WaitlistRepository waitlistRepository;
    @Autowired private EventWaitlistRepository eventWaitlistRepository;
    @Autowired private CalendarService calendarService;
    @Autowired private MessageService msg;

    private User holder;
    private User waiter;
    private User secondWaiter;
    private User walkIn;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        holder = verifiedUser("wh-holder@test.local");
        waiter = verifiedUser("wh-waiter@test.local");
        secondWaiter = verifiedUser("wh-waiter2@test.local");
        walkIn = verifiedUser("wh-walkin@test.local");
        day = LocalDate.now().plusDays(20);
    }

    @Test
    void shouldGiveACancelledSeatToTheQueueAndNotToAWalkIn() {
        TimeSlot slot = slot(1);
        UUID booking = reservationRepository.save(new Reservation(holder, slot)).getId();
        UUID entry = waitlistRepository.save(new Waitlist(waiter, slot, 1)).getId();
        flushAndClear();

        reservationService.cancelReservation(booking, holder.getId());
        flushAndClear();

        IllegalStateException refused = assertThrows(IllegalStateException.class,
            () -> reservationService.createReservation(slot.getId(), walkIn.getId(), null, 1));
        assertEquals(msg.get("reservation.no.spots.waitlist"), refused.getMessage());

        waitlistService.confirmOffer(entry, waiter.getId());
        flushAndClear();
        assertTrue(reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(
            waiter.getId(), slot.getId(), ReservationStatus.CONFIRMED));
    }

    @Test
    void shouldShowTheHeldSeatAsTakenEverywhereButToThePeopleItWasOfferedTo() {
        TimeSlot slot = slot(1);
        pendingEntry(waiter, slot, 1);
        flushAndClear();

        TimeSlotDetailDto forWalkIn = calendarService.getSlotDetails(slot.getId(), walkIn.getId());
        assertEquals(SlotStatus.FULL, forWalkIn.status());
        assertEquals(1, forWalkIn.heldForWaitlist());
        assertEquals(0, forWalkIn.currentParticipants(), "the headcount stays the headcount");

        TimeSlotDto inWeek = calendarService.getWeekView(day.with(java.time.DayOfWeek.MONDAY), walkIn.getId())
            .days().stream().flatMap(d -> d.slots().stream())
            .filter(s -> s.id().equals(slot.getId())).findFirst().orElseThrow();
        assertEquals(SlotStatus.FULL, inWeek.status(), "the week view must agree with the slot detail");

        DaySummaryDto inMonth = calendarService.getMonthView(java.time.YearMonth.from(day), walkIn.getId())
            .days().stream().filter(d -> d.date().equals(day)).findFirst().orElseThrow();
        assertEquals(0, inMonth.availableSlots());

        TimeSlotDetailDto forWaiter = calendarService.getSlotDetails(slot.getId(), waiter.getId());
        assertEquals(SlotStatus.AVAILABLE, forWaiter.status(), "their own offer does not hold the seat from them");
        assertEquals(0, forWaiter.heldForWaitlist());
    }

    @Test
    void shouldHoldOnlyAsManySeatsAsAreFreeWhenTheOfferWentToEveryWaiter() {
        TimeSlot slot = slot(2);
        reservationRepository.save(new Reservation(holder, slot));
        pendingEntry(waiter, slot, 1);
        pendingEntry(secondWaiter, slot, 2);
        flushAndClear();

        // One seat free, two offers out: one seat held, not two.
        assertEquals(1, calendarService.getSlotDetails(slot.getId(), walkIn.getId()).heldForWaitlist());
    }

    @Test
    void shouldKeepTheOtherOffersOpenWhileASecondFreedSeatIsLeft() {
        TimeSlot slot = slot(2);
        UUID first = pendingEntry(waiter, slot, 1);
        UUID second = pendingEntry(secondWaiter, slot, 2);
        flushAndClear();

        waitlistService.confirmOffer(first, waiter.getId());
        flushAndClear();

        assertEquals(WaitlistStatus.PENDING_CONFIRMATION, waitlistRepository.findById(second).orElseThrow().getStatus(),
            "the second seat is still the queue's");
        assertThrows(IllegalStateException.class,
            () -> reservationService.createReservation(slot.getId(), walkIn.getId(), null, 1));
    }

    @Test
    void shouldReturnTheOtherOffersToTheQueueOnceTheLastSeatIsTaken() {
        TimeSlot slot = slot(1);
        UUID first = pendingEntry(waiter, slot, 1);
        UUID second = pendingEntry(secondWaiter, slot, 2);
        flushAndClear();

        waitlistService.confirmOffer(first, waiter.getId());
        flushAndClear();

        assertEquals(WaitlistStatus.WAITING, waitlistRepository.findById(second).orElseThrow().getStatus());
    }

    @Test
    void shouldLetAWalkInBookOnceTheOfferHasRunOut() {
        TimeSlot slot = slot(1);
        Waitlist lapsed = new Waitlist(waiter, slot, 1);
        lapsed.offerSpot(Instant.now().minus(1, ChronoUnit.MINUTES));
        waitlistRepository.save(lapsed);
        flushAndClear();

        // Before the five-minute sweep returns it to WAITING, an expired offer already holds nothing.
        reservationService.createReservation(slot.getId(), walkIn.getId(), null, 1);
    }

    @Test
    void shouldLetTheOfferedPersonBookTheSeatDirectly() {
        TimeSlot slot = slot(1);
        pendingEntry(waiter, slot, 1);
        flushAndClear();

        reservationService.createReservation(slot.getId(), waiter.getId(), null, 1);
    }

    @Test
    void shouldGiveACancelledEventSeatToTheQueueAndNotToAWalkIn() {
        Event course = eventRepository.save(new Event("Kurs", EventType.COURSE, day, day.plusDays(1), 1));
        EventWaitlist entry = new EventWaitlist(waiter, course, 1);
        entry.offerSpot(Instant.now().plus(2, ChronoUnit.HOURS));
        UUID entryId = eventWaitlistRepository.save(entry).getId();
        flushAndClear();

        IllegalStateException refused = assertThrows(IllegalStateException.class,
            () -> reservationService.createEventReservation(course.getId(), walkIn.getId(), null, 1));
        assertEquals(msg.get("reservation.event.no.spots.waitlist"), refused.getMessage());
        assertEquals(1, calendarService.getEventSummary(course.getId(), walkIn.getId()).heldForWaitlist());

        eventWaitlistService.confirmEventOffer(entryId, waiter.getId());
    }

    private TimeSlot slot(int seats) {
        return timeSlotRepository.save(new TimeSlot(day, LocalTime.of(10, 0), LocalTime.of(12, 0), seats));
    }

    private UUID pendingEntry(User user, TimeSlot slot, int position) {
        Waitlist entry = new Waitlist(user, slot, position);
        entry.offerSpot(Instant.now().plus(2, ChronoUnit.HOURS));
        return waitlistRepository.save(entry).getId();
    }

    private User verifiedUser(String email) {
        User user = new User(email, "Te", "St", "+48123123123", email.substring(0, email.indexOf('@')));
        user.setRole(UserRole.USER);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
