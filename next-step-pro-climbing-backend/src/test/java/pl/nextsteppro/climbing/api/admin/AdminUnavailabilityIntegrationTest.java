package pl.nextsteppro.climbing.api.admin;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.nextsteppro.climbing.api.reservation.ReservationService;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationStatus;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A multi-day absence is an UNAVAILABLE event, and this is what that has to mean.
 *
 * <p>An absence that lasts more than a day cannot be a slot — a slot lives on one date — so the
 * calendar form creates an event instead. That choice only holds if the event behaves like an
 * absence and nothing more: it must close nothing it was not asked to close (people already
 * booked on those days keep their seats), it must refuse to be switched on top of a live
 * booking (the slot twin already refuses exactly that), and a single-day window of negative
 * length must be rejected rather than saved into an entry no view can draw.
 *
 * <p>Lives in this package because {@code CreateEventRequest} / {@code UpdateEventRequest} are
 * package-private.
 */
class AdminUnavailabilityIntegrationTest extends BaseIntegrationTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private AdminService adminService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private Validator validator;

    private UUID adminId;
    private UUID climberId;
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
        climberId = userRepository.save(climber).getId();

        // Far enough out that the 12 h booking window never interferes.
        start = LocalDate.now().plusDays(30);
    }

    @Test
    void shouldLeaveEveryBookedSeatAloneWhenAnAbsenceCoversTheSameDays() {
        TimeSlot booked = timeSlotRepository.save(new TimeSlot(start.plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0), 4));
        reservationService.createReservation(booked.getId(), climberId, null, 1);
        flushAndClear();

        adminService.createEvent(adminId, absence(start, start.plusDays(4), LocalTime.of(18, 0), LocalTime.of(20, 0)));
        flushAndClear();

        TimeSlot after = timeSlotRepository.findById(booked.getId()).orElseThrow();
        assertFalse(after.isUnavailable(), "an absence must not reach into slots it was never told about");
        assertEquals(4, after.getMaxParticipants(), "nobody's seats may vanish as a side effect");
        assertEquals(1, confirmedOn(booked.getId()).size(),
            "the booking stands: closing the calendar is not the same as cancelling on people");
    }

    @Test
    void shouldKeepNoSeatsOnAnAbsenceEvenWhenAskedFor() {
        CreateEventRequest request = new CreateEventRequest(
            "Wyjazd", null, null, EventType.UNAVAILABLE.name(), start, start.plusDays(4),
            8, null, null, null, null, null);

        EventAdminDto created = adminService.createEvent(adminId, request);

        assertEquals(0, created.maxParticipants(),
            "seats on an absence are a number nobody can ever use, and the next reader has to explain it away");
    }

    @Test
    void shouldRefuseASignupOnTheDaysTheAbsenceCovers() {
        // The point of the whole feature, and the one assertion nothing covered: the waitlist
        // guard had a test, the reservation guard only a comment mentioning it exists.
        EventAdminDto absence = adminService.createEvent(adminId,
            absence(start, start.plusDays(4), null, null));
        flushAndClear();

        assertThrows(IllegalStateException.class,
            () -> reservationService.createEventReservation(absence.id(), climberId, null, 1));
        flushAndClear();

        assertTrue(timeSlotRepository.findByEventId(absence.id()).isEmpty(),
            "a refused signup must not leave the day-slots it would have booked behind");
    }

    @Test
    void shouldRefuseTurningAnEventWithBookingsIntoAnAbsence() {
        Event course = eventRepository.save(new Event("Kurs", EventType.TRAINING, start, start.plusDays(1), 6));
        reservationService.createEventReservation(course.getId(), climberId, null, 1);
        flushAndClear();

        assertThrows(IllegalStateException.class,
            () -> adminService.updateEvent(adminId, course.getId(), typeOnly(EventType.UNAVAILABLE)));
        flushAndClear();

        Event after = eventRepository.findById(course.getId()).orElseThrow();
        assertEquals(EventType.TRAINING, after.getEventType(), "a rejected switch must leave the event as it was");
        List<UUID> slotIds = timeSlotRepository.findByEventId(course.getId()).stream().map(TimeSlot::getId).toList();
        assertEquals(2, reservationRepository.findConfirmedByTimeSlotIds(slotIds).size(),
            "the booking that blocked the switch must still be there");
    }

    @Test
    void shouldAllowTheSwitchOnceTheEventHasNobodyOnIt() {
        Event course = eventRepository.save(new Event("Kurs", EventType.TRAINING, start, start.plusDays(1), 6));
        flushAndClear();

        adminService.updateEvent(adminId, course.getId(), typeOnly(EventType.UNAVAILABLE));
        flushAndClear();

        Event after = eventRepository.findById(course.getId()).orElseThrow();
        assertEquals(EventType.UNAVAILABLE, after.getEventType());
        assertEquals(0, after.getMaxParticipants(), "the switch drops the seats the absence cannot use");
    }

    @Test
    void shouldLetAnAbsenceThatAlreadyBlocksEnrollmentStayEditable() {
        Event absence = eventRepository.save(new Event("Wyjazd", EventType.UNAVAILABLE, start, start.plusDays(2), 0));
        flushAndClear();

        // The guard refuses the CHANGE, never the state — otherwise a row that somehow got there
        // (a switch made before the guard existed) would be frozen out of the only form that fixes it.
        adminService.updateEvent(adminId, absence.getId(), typeOnly(EventType.UNAVAILABLE));
        flushAndClear();

        assertEquals(EventType.UNAVAILABLE, eventRepository.findById(absence.getId()).orElseThrow().getEventType());
    }

    @Test
    void shouldRejectASingleDayWindowThatEndsBeforeItStarts() {
        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(
            absence(start, start, LocalTime.of(20, 0), LocalTime.of(8, 0)));

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("sameDayTimeRangeValid")),
            "a same-day window of negative length draws as nothing — it must not be saved");
    }

    @Test
    void shouldAcceptTheSameHoursAcrossARangeOfDays() {
        // 20:00 Friday until 08:00 Sunday is a weekend away, not an inverted window.
        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(
            absence(start, start.plusDays(2), LocalTime.of(20, 0), LocalTime.of(8, 0)));

        assertTrue(violations.isEmpty(), "across days the two times sit on different dates: " + violations);
    }

    @Test
    void shouldRejectAnInvertedSingleDayWindowOnUpdateToo() {
        Event absence = eventRepository.save(new Event("Wolne", EventType.UNAVAILABLE, start, start, 0));
        absence.setStartTime(LocalTime.of(8, 0));
        absence.setEndTime(LocalTime.of(20, 0));
        eventRepository.save(absence);
        flushAndClear();

        assertThrows(IllegalArgumentException.class, () -> adminService.updateEvent(adminId, absence.getId(),
            new UpdateEventRequest(null, null, null, null, null, null, null, null,
                LocalTime.of(22, 0), null, null, null, null)));
    }

    private CreateEventRequest absence(LocalDate from, LocalDate to, LocalTime startTime, LocalTime endTime) {
        return new CreateEventRequest("Wyjazd", null, null, EventType.UNAVAILABLE.name(), from, to,
            0, startTime, endTime, null, null, null);
    }

    private static UpdateEventRequest typeOnly(EventType type) {
        return new UpdateEventRequest(null, null, null, type.name(), null, null,
            null, null, null, null, null, null, null);
    }

    private List<Reservation> confirmedOn(UUID slotId) {
        return reservationRepository.findConfirmedByTimeSlotIds(List.of(slotId)).stream()
            .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)
            .toList();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
