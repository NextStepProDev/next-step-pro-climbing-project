package pl.nextsteppro.climbing.api.reservation;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reservation.GuestReservation;
import pl.nextsteppro.climbing.domain.reservation.GuestReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeat;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeatRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.domain.waitlist.Waitlist;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistRepository;
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
 * Findings from the line-by-line read of the reservation and waitlist services (audit 2026-10),
 * each one seen red against the code it fixes.
 */
class ReservationReviewIntegrationTest extends BaseIntegrationTest {

    @PersistenceContext private EntityManager entityManager;
    @Autowired private ReservationService reservationService;
    @Autowired private WaitlistService waitlistService;
    @Autowired private WaitlistRepository waitlistRepository;
    @Autowired private ReservedSeatRepository reservedSeatRepository;
    @Autowired private GuestReservationRepository guestReservationRepository;

    private User client;
    private User invitee;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        client = verifiedUser("rr-client@test.local");
        invitee = verifiedUser("rr-invitee@test.local");
        day = LocalDate.now().plusDays(14);
    }

    @Test
    void shouldConfirmAnOfferForSomeoneAlreadyBookedInsteadOfSayingTheyLostTheRace() {
        TimeSlot slot = slot(1);
        UUID entryId = pendingEntry(client, slot);
        // Booked by another route while the offer was out (the admin added them by hand).
        UUID booked = reservationRepository.save(new Reservation(client, slot)).getId();
        flushAndClear();

        var result = waitlistService.confirmOffer(entryId, client.getId());

        assertEquals(booked, result.reservationId());
        assertTrue(waitlistRepository.findById(entryId).isEmpty(), "no longer waiting for a seat they hold");
    }

    @Test
    void shouldTakeSomeoneOffTheQueueOnceTheyBookTheSlotDirectly() {
        TimeSlot slot = slot(2);
        UUID entryId = pendingEntry(client, slot);
        flushAndClear();

        reservationService.createReservation(slot.getId(), client.getId(), null, 1);
        flushAndClear();

        assertTrue(waitlistRepository.findById(entryId).isEmpty());
    }

    @Test
    void shouldNotLetABookingGrowIntoASeatHeldForAnInvitee() {
        TimeSlot slot = slot(3);
        Reservation booking = new Reservation(client, slot);
        booking.setParticipants(2);
        UUID bookingId = reservationRepository.save(booking).getId();
        reservedSeatRepository.save(new ReservedSeat(slot, invitee));
        flushAndClear();

        assertThrows(IllegalStateException.class,
            () -> reservationService.updateSlotParticipants(bookingId, client.getId(), 3));
    }

    @Test
    void shouldNotLetAnEventBookingGrowIntoASeatHeldForAnInvitee() {
        Event course = eventRepository.save(new Event("Kurs", EventType.COURSE, day, day.plusDays(1), 3));
        reservationService.createEventReservation(course.getId(), client.getId(), null, 2);
        reservedSeatRepository.save(new ReservedSeat(course, invitee));
        flushAndClear();

        assertThrows(IllegalStateException.class,
            () -> reservationService.updateEventParticipants(course.getId(), client.getId(), 3));
    }

    @Test
    void shouldOfferOnlyTheSeatsTheServerWouldAcceptOnTheEditForm() {
        TimeSlot slot = slot(5);
        reservationRepository.save(new Reservation(client, slot));
        guestReservationRepository.save(new GuestReservation(slot, "Gość", 2));
        reservedSeatRepository.save(new ReservedSeat(slot, invitee));
        flushAndClear();

        var mine = reservationService.getUserUpcomingReservations(client.getId()).slots().getFirst();

        // 5 seats − own 1 − 2 guests − 1 held + own 1 = 2: the most this booking may grow to.
        assertEquals(2, mine.spotsAvailable());
    }

    @Test
    void shouldRefuseToCancelOrResizeOneDayOfAnEventOnItsOwn() {
        Event course = eventRepository.save(new Event("Kurs", EventType.COURSE, day, day.plusDays(2), 6));
        reservationService.createEventReservation(course.getId(), client.getId(), null, 1);
        flushAndClear();
        UUID oneDay = reservationRepository.findAll().getFirst().getId();

        assertThrows(IllegalStateException.class, () -> reservationService.cancelReservation(oneDay, client.getId()));
        assertThrows(IllegalStateException.class,
            () -> reservationService.updateSlotParticipants(oneDay, client.getId(), 2));
    }

    @Test
    void shouldRefuseAQueueOnOneDayOfAnEvent() {
        Event course = eventRepository.save(new Event("Kurs", EventType.COURSE, day, day.plusDays(1), 1));
        TimeSlot firstDay = timeSlotRepository.save(TimeSlot.forEventDay(course, day));
        reservationRepository.save(new Reservation(invitee, firstDay));
        flushAndClear();

        assertThrows(IllegalStateException.class, () -> waitlistService.joinWaitlist(firstDay.getId(), client.getId()));
        assertEquals(0, waitlistRepository.count());
    }

    private TimeSlot slot(int seats) {
        return timeSlotRepository.save(new TimeSlot(day, LocalTime.of(10, 0), LocalTime.of(12, 0), seats));
    }

    private UUID pendingEntry(User user, TimeSlot slot) {
        Waitlist entry = new Waitlist(user, slot, 1);
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
