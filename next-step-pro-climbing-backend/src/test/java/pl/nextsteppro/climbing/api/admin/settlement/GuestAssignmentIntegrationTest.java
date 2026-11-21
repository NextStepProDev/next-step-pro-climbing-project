package pl.nextsteppro.climbing.api.admin.settlement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.nextsteppro.climbing.api.admin.GuestAssignmentService;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reservation.GuestReservation;
import pl.nextsteppro.climbing.domain.reservation.GuestReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationStatus;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Handing a guest entry over to the account that person registered later. The money is the point:
 * a guest's charge and payments are deleted with the guest row, so every test here checks that they
 * arrived on the account instead — or, when the handover is refused, that nothing moved at all.
 */
class GuestAssignmentIntegrationTest extends BaseIntegrationTest {

    @Autowired private GuestAssignmentService assignment;
    @Autowired private AdminSettlementService settlements;
    @Autowired private GuestReservationRepository guestReservationRepository;
    @Autowired private JdbcTemplate jdbc;

    private User admin;
    private User client;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM settlements");
        jdbc.update("DELETE FROM activity_logs");
        guestReservationRepository.deleteAll();
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        eventRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        admin = saveUser("admin@example.com", "Trener", "Admin", UserRole.ADMIN);
        client = saveUser("client@example.com", "Anna", "Kowalska", UserRole.USER);
        date = LocalDate.now().minusDays(10);
    }

    @Test
    @DisplayName("shouldMoveAnEventGuestsChargeAndPaymentsToTheAccountAndBookEveryDay")
    void shouldMoveAnEventGuestsChargeAndPaymentsToTheAccountAndBookEveryDay() {
        Event camp = eventRepository.saveAndFlush(
            new Event("Obóz", EventType.WORKSHOP, date, date.plusDays(2), 10));
        List<TimeSlot> days = List.of(daySlot(camp, date), daySlot(camp, date.plusDays(1)),
            daySlot(camp, date.plusDays(2)));
        GuestReservation guest = guestReservationRepository.saveAndFlush(
            new GuestReservation(camp, "Anna z synem", 2));
        settlements.save("event", camp.getId(), "guest", guest.getId(),
            new SaveSettlementRequest(new BigDecimal("2400")));
        settlements.addPayment(new AddPaymentRequest("guest", guest.getId(), new BigDecimal("1000"),
            date.minusDays(5), "event", camp.getId()));

        assignment.assignEventGuest(admin.getId(), camp.getId(), guest.getId(), client.getId());

        assertTrue(guestReservationRepository.findById(guest.getId()).isEmpty());
        for (TimeSlot day : days) {
            Reservation booked = reservationRepository.findByUserIdAndTimeSlotId(client.getId(), day.getId());
            assertEquals(ReservationStatus.CONFIRMED, booked.getStatus(), "every day of the event is booked");
            assertEquals(2, booked.getParticipants(), "a guest for two becomes one booking for two seats");
        }

        SettlementLineDto line = settlements.getSection("event", camp.getId()).lines().stream()
            .filter(l -> l.payerId().equals(client.getId())).findFirst()
            .orElseThrow(() -> new AssertionError("the charge did not reach the account"));
        assertEquals("user", line.payerType());
        assertMoney("2400", line.amount());
        assertMoney("1000", line.covered());
        assertEquals(date.minusDays(5), line.payments().getFirst().receivedOn(),
            "the day the money was handed over must survive the handover");

        assertEquals(1, count("SELECT COUNT(*) FROM settlements"), "moved, not copied");
        assertEquals(1, count("SELECT COUNT(*) FROM payments WHERE user_id = ?", client.getId()));
        assertEquals(0, count("SELECT COUNT(*) FROM payments WHERE guest_reservation_id IS NOT NULL"));
        assertEquals(1, count("SELECT COUNT(*) FROM activity_logs WHERE action_type = "
            + "'ADMIN_GUEST_ASSIGNED_TO_ACCOUNT' AND user_id = ? AND subject_user_id = ? AND description IS NULL",
            admin.getId(), client.getId()));
    }

    @Test
    @DisplayName("shouldCreateTheEventDaySlotsWhenNoRegisteredPersonHasBookedYet")
    void shouldCreateTheEventDaySlotsWhenNoRegisteredPersonHasBookedYet() {
        Event camp = eventRepository.saveAndFlush(
            new Event("Obóz", EventType.WORKSHOP, date, date.plusDays(1), 10));
        GuestReservation guest = guestReservationRepository.saveAndFlush(
            new GuestReservation(camp, "Anna", 1));

        assignment.assignEventGuest(admin.getId(), camp.getId(), guest.getId(), client.getId());

        assertEquals(2, count("SELECT COUNT(*) FROM reservations r JOIN time_slots ts ON ts.id = r.time_slot_id "
            + "WHERE r.user_id = ? AND ts.event_id = ?", client.getId(), camp.getId()));
    }

    @Test
    @DisplayName("shouldAcceptAnEventGuestWrittenOntoOneOfItsDays")
    void shouldAcceptAnEventGuestWrittenOntoOneOfItsDays() {
        Event camp = eventRepository.saveAndFlush(
            new Event("Obóz", EventType.WORKSHOP, date, date.plusDays(1), 10));
        TimeSlot first = daySlot(camp, date);
        daySlot(camp, date.plusDays(1));
        GuestReservation guest = guestReservationRepository.saveAndFlush(
            new GuestReservation(first, "Anna z widoku dnia", 1));
        settlements.save("event", camp.getId(), "guest", guest.getId(),
            new SaveSettlementRequest(new BigDecimal("600")));

        assignment.assignEventGuest(admin.getId(), camp.getId(), guest.getId(), client.getId());

        assertEquals(1, count("SELECT COUNT(*) FROM settlements WHERE event_id = ? AND user_id = ?",
            camp.getId(), client.getId()));
        assertEquals(2, count("SELECT COUNT(*) FROM reservations WHERE user_id = ?", client.getId()));
    }

    @Test
    @DisplayName("shouldMoveASlotGuestToTheAccount")
    void shouldMoveASlotGuestToTheAccount() {
        TimeSlot slot = standaloneSlot();
        GuestReservation guest = guestReservationRepository.saveAndFlush(
            new GuestReservation(slot, "Anna", 2));
        settlements.save("slot", slot.getId(), "guest", guest.getId(),
            new SaveSettlementRequest(new BigDecimal("200")));
        settlements.addPayment(new AddPaymentRequest("guest", guest.getId(), new BigDecimal("200"),
            date, "slot", slot.getId()));

        assignment.assignSlotGuest(admin.getId(), slot.getId(), guest.getId(), client.getId());

        Reservation booked = reservationRepository.findByUserIdAndTimeSlotId(client.getId(), slot.getId());
        assertEquals(2, booked.getParticipants());
        assertTrue(booked.isCreatedByAdmin(), "must not light the 'new reservations' dot");
        assertEquals(1, count("SELECT COUNT(*) FROM settlements WHERE time_slot_id = ? AND user_id = ?",
            slot.getId(), client.getId()));
        assertEquals(1, count("SELECT COUNT(*) FROM payments WHERE user_id = ?", client.getId()));
    }

    @Test
    @DisplayName("shouldRefuseAnAccountAlreadyBookedAndLeaveTheGuestsMoneyWhereItWas")
    void shouldRefuseAnAccountAlreadyBookedAndLeaveTheGuestsMoneyWhereItWas() {
        TimeSlot slot = standaloneSlot();
        reservationRepository.saveAndFlush(new Reservation(client, slot));
        GuestReservation guest = guestReservationRepository.saveAndFlush(
            new GuestReservation(slot, "Anna", 1));
        settlements.save("slot", slot.getId(), "guest", guest.getId(),
            new SaveSettlementRequest(new BigDecimal("200")));

        assertThrows(IllegalStateException.class,
            () -> assignment.assignSlotGuest(admin.getId(), slot.getId(), guest.getId(), client.getId()));

        assertTrue(guestReservationRepository.findById(guest.getId()).isPresent());
        assertEquals(1, count("SELECT COUNT(*) FROM settlements WHERE guest_reservation_id = ?", guest.getId()));
    }

    @Test
    @DisplayName("shouldRefuseAnAccountWithItsOwnChargeLeftFromACancelledBooking")
    void shouldRefuseAnAccountWithItsOwnChargeLeftFromACancelledBooking() {
        TimeSlot slot = standaloneSlot();
        Reservation earlier = reservationRepository.saveAndFlush(new Reservation(client, slot));
        settlements.save("slot", slot.getId(), "user", client.getId(),
            new SaveSettlementRequest(new BigDecimal("150")));
        earlier.cancel();
        reservationRepository.saveAndFlush(earlier);
        GuestReservation guest = guestReservationRepository.saveAndFlush(
            new GuestReservation(slot, "Anna", 1));
        settlements.save("slot", slot.getId(), "guest", guest.getId(),
            new SaveSettlementRequest(new BigDecimal("200")));

        assertThrows(IllegalStateException.class,
            () -> assignment.assignSlotGuest(admin.getId(), slot.getId(), guest.getId(), client.getId()));

        assertEquals(1, count("SELECT COUNT(*) FROM settlements WHERE guest_reservation_id = ?", guest.getId()));
        assertEquals(1, count("SELECT COUNT(*) FROM settlements WHERE user_id = ?", client.getId()));
        // The refusal must come before any write, not rely on the rollback to undo a booking.
        assertEquals(0, count("SELECT COUNT(*) FROM reservations WHERE user_id = ? AND status = 'CONFIRMED'",
            client.getId()));
    }

    @Test
    @DisplayName("shouldRefuseAGuestFromAnotherSession")
    void shouldRefuseAGuestFromAnotherSession() {
        TimeSlot slot = standaloneSlot();
        TimeSlot other = standaloneSlot();
        GuestReservation guest = guestReservationRepository.saveAndFlush(
            new GuestReservation(other, "Ktoś inny", 1));

        assertThrows(IllegalArgumentException.class,
            () -> assignment.assignSlotGuest(admin.getId(), slot.getId(), guest.getId(), client.getId()));
    }

    // ------------------------------------------------------------------ fixtures

    private User saveUser(String email, String firstName, String lastName, UserRole role) {
        User user = new User(email, firstName, lastName, "+48123456789", email.split("@")[0]);
        user.setRole(role);
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }

    private TimeSlot daySlot(Event event, LocalDate on) {
        return timeSlotRepository.saveAndFlush(
            new TimeSlot(event, on, LocalTime.of(9, 0), LocalTime.of(17, 0), event.getMaxParticipants()));
    }

    private TimeSlot standaloneSlot() {
        return timeSlotRepository.saveAndFlush(
            new TimeSlot(date, LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
    }

    private int count(String sql, Object... args) {
        Integer result = jdbc.queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }
}
