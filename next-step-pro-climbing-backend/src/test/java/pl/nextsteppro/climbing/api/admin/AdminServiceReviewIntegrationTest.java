package pl.nextsteppro.climbing.api.admin;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.nextsteppro.climbing.domain.activitylog.ActivityActionType;
import pl.nextsteppro.climbing.domain.activitylog.ActivityLogRepository;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reservation.GuestReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Findings from the line-by-line read of {@code AdminService} (audit 2026-10), each one seen red
 * against the code it fixes.
 */
class AdminServiceReviewIntegrationTest extends BaseIntegrationTest {

    @PersistenceContext private EntityManager entityManager;
    @Autowired private AdminService adminService;
    @Autowired private ActivityLogRepository activityLogRepository;
    @Autowired private GuestReservationRepository guestReservationRepository;

    private User admin;
    private User client;

    @BeforeEach
    void setUp() {
        admin = verifiedUser("review-admin@test.local", UserRole.ADMIN);
        client = verifiedUser("review-client@test.local", UserRole.USER);
    }

    @Test
    void shouldListASessionThatEndedThisMorningOnlyAmongThePastReservations() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Warsaw"));
        // Needs a stretch of today that is already over; in the first minutes after midnight there is none.
        assumeTrue(now.toLocalTime().isAfter(LocalTime.of(0, 30)));
        TimeSlot ended = timeSlotRepository.save(new TimeSlot(
            now.toLocalDate(), LocalTime.of(0, 0), now.toLocalTime().minusMinutes(10).withSecond(0).withNano(0), 4));
        UUID reservationId = reservationRepository.save(new Reservation(client, ended)).getId();
        flushAndClear();

        assertFalse(adminService.getAllUpcomingReservations(admin.getId()).stream()
            .anyMatch(r -> r.id().equals(reservationId)), "an ended session is not upcoming");
        assertEquals(1, adminService.getAllPastReservations().stream()
            .filter(r -> r.id().equals(reservationId)).count());
    }

    @Test
    void shouldRefuseToDemoteTheOnlyAdministrator() {
        assertThrows(IllegalStateException.class, () -> adminService.removeAdmin(admin.getId(), admin.getId()));
        assertEquals(UserRole.ADMIN, userRepository.findById(admin.getId()).orElseThrow().getRole());
    }

    @Test
    void shouldLetAnAdministratorStepDownWhileAnotherOneRemains() {
        verifiedUser("second-admin@test.local", UserRole.ADMIN);

        assertDoesNotThrow(() -> adminService.removeAdmin(admin.getId(), admin.getId()));
    }

    @Test
    void shouldRefuseAGuestOnASingleDayOfAnEvent() {
        Event course = eventRepository.save(new Event("Kurs", EventType.COURSE,
            LocalDate.now().plusDays(20), LocalDate.now().plusDays(22), 6));
        TimeSlot day = timeSlotRepository.save(TimeSlot.forEventDay(course, course.getStartDate()));
        flushAndClear();

        assertThrows(IllegalStateException.class, () ->
            adminService.addGuestParticipantToSlot(day.getId(), new AddGuestParticipantRequest("Gość", 1)));
        assertThrows(IllegalStateException.class, () ->
            adminService.addRegisteredParticipantToSlot(day.getId(),
                new AddRegisteredParticipantRequest(client.getId(), 1, null)));
        assertEquals(0, guestReservationRepository.count());
        assertEquals(0, reservationRepository.count());
    }

    @Test
    void shouldLogAnAdminsChangeOfABookingAndNothingForAnUntouchedOne() {
        TimeSlot slot = timeSlotRepository.save(new TimeSlot(
            LocalDate.now().plusDays(10), LocalTime.of(10, 0), LocalTime.of(12, 0), 6));
        Reservation booking = new Reservation(client, slot);
        booking.setParticipants(2);
        UUID bookingId = reservationRepository.save(booking).getId();
        flushAndClear();

        adminService.updateReservationParticipants(bookingId, 2);
        assertEquals(0, entries(ActivityActionType.RESERVATION_UPDATED));

        adminService.updateReservationParticipants(bookingId, 3);
        assertEquals(1, entries(ActivityActionType.RESERVATION_UPDATED));
    }

    @Test
    void shouldLogOneCancellationPerPersonWhenAnAdminRemovesThemFromAThreeDayEvent() {
        Event course = eventRepository.save(new Event("Kurs", EventType.COURSE,
            LocalDate.now().plusDays(20), LocalDate.now().plusDays(22), 6));
        adminService.addRegisteredParticipantToEvent(course.getId(),
            new AddRegisteredParticipantRequest(client.getId(), 1, null));
        flushAndClear();
        assertEquals(3, reservationRepository.count(), "precondition: one row per day");

        adminService.cancelEventParticipantByAdmin(course.getId(), client.getId());

        assertEquals(1, entries(ActivityActionType.RESERVATION_CANCELLED_BY_ADMIN));
    }

    @Test
    void shouldLogOneCancellationPerPersonWhenAThreeDayEventIsDeleted() {
        Event course = eventRepository.save(new Event("Kurs", EventType.COURSE,
            LocalDate.now().plusDays(20), LocalDate.now().plusDays(22), 6));
        adminService.addRegisteredParticipantToEvent(course.getId(),
            new AddRegisteredParticipantRequest(client.getId(), 1, null));
        flushAndClear();

        adminService.deleteEvent(admin.getId(), course.getId());

        assertEquals(1, entries(ActivityActionType.RESERVATION_CANCELLED_BY_ADMIN));
    }

    @Test
    void shouldKeepTheCancellationOnTheTimelineWhenAnAdminDeletesABookedSlot() {
        TimeSlot slot = timeSlotRepository.save(new TimeSlot(
            LocalDate.now().plusDays(10), LocalTime.of(10, 0), LocalTime.of(12, 0), 6));
        reservationRepository.save(new Reservation(client, slot));
        flushAndClear();

        adminService.deleteTimeSlot(admin.getId(), slot.getId());

        assertEquals(1, entries(ActivityActionType.RESERVATION_CANCELLED_BY_ADMIN));
        // The slot reference is gone with the row; the entry still has to say which session it was.
        String term = activityLogRepository.findAll().stream()
            .filter(e -> e.getActionType() == ActivityActionType.RESERVATION_CANCELLED_BY_ADMIN)
            .findFirst().orElseThrow().getDescription();
        assertEquals(slot.getDate() + " 10:00-12:00", term);
    }

    private long entries(ActivityActionType type) {
        flushAndClear();
        return activityLogRepository.findAll().stream()
            .filter(e -> e.getActionType() == type && e.getUser().getId().equals(client.getId()))
            .count();
    }

    private User verifiedUser(String email, UserRole role) {
        User user = new User(email, "Te", "St", "+48123123123", email.substring(0, email.indexOf('@')));
        user.setRole(role);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
