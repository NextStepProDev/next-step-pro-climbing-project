package pl.nextsteppro.climbing.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.nextsteppro.climbing.api.reservation.ReservationService;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationStatus;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for reservation flow:
 * - Create slot → Book → Cancel → Rebook
 * - Test capacity limits
 * - Test concurrent bookings
 * - Test database constraints
 *
 * Uses real PostgreSQL database via Testcontainers.
 */
class ReservationFlowIntegrationTest extends BaseIntegrationTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ReservationService reservationService;

    private User testUser;
    private TimeSlot testSlot;

    @BeforeEach
    void setUp() {
        // Clean up
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        eventRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user (let JPA manage ID generation)
        testUser = new User(
                "test@example.com",
                "Test",
                "User",
                "+48123456789",
                "testuser"
        );
        testUser.setRole(UserRole.USER);
        testUser.setEmailVerified(true);
        testUser = userRepository.save(testUser); // JPA will set ID and timestamps

        // Create test slot (far in future to avoid booking cutoff)
        testSlot = new TimeSlot(
                LocalDate.now().plusDays(7),
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                10
        );
        testSlot = timeSlotRepository.save(testSlot);
    }

    // ========== FULL RESERVATION FLOW ==========

    @Test
    void shouldCompleteFullReservationLifecycle() {
        // Given: Empty slot
        assertEquals(0, reservationRepository.countConfirmedByTimeSlotId(testSlot.getId()));

        // When: User books slot
        reservationService.createReservation(testSlot.getId(), testUser.getId(), null, 1);

        // Then: Reservation created
        List<Reservation> reservations = reservationRepository.findByUserId(testUser.getId());
        assertEquals(1, reservations.size());

        Reservation reservation = reservations.get(0);
        assertEquals(testUser.getId(), reservation.getUser().getId());
        assertEquals(testSlot.getId(), reservation.getTimeSlot().getId());
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        assertEquals(1, reservation.getParticipants());

        // And: Slot count updated
        assertEquals(1, reservationRepository.countConfirmedByTimeSlotId(testSlot.getId()));

        // When: User cancels reservation
        UUID reservationId = reservation.getId();
        reservationService.cancelReservation(reservationId, testUser.getId());

        // Then: Reservation cancelled
        reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());

        // And: Slot count decremented
        assertEquals(0, reservationRepository.countConfirmedByTimeSlotId(testSlot.getId()));

        // When: User rebooks the same slot
        reservationService.createReservation(testSlot.getId(), testUser.getId(), null, 1);

        // Then: Reservation is reactivated (same record, CONFIRMED — no duplicate due to UNIQUE constraint)
        reservations = reservationRepository.findByUserId(testUser.getId());
        assertEquals(1, reservations.size());
        assertEquals(ReservationStatus.CONFIRMED, reservations.get(0).getStatus());
    }

    // ========== CAPACITY LIMITS ==========

    @Test
    void shouldEnforceCapacityLimits() {
        // Given: Slot with capacity 10
        assertEquals(10, testSlot.getMaxParticipants());

        // When: Fill 9 slots
        for (int i = 0; i < 9; i++) {
            User user = createUser("user" + i + "@example.com");
            reservationService.createReservation(testSlot.getId(), user.getId(), null, 1);
        }

        // Then: 9 slots taken, 1 available
        assertEquals(9, reservationRepository.countConfirmedByTimeSlotId(testSlot.getId()));

        // When: Book last slot
        User lastUser = createUser("last@example.com");
        reservationService.createReservation(testSlot.getId(), lastUser.getId(), null, 1);

        // Then: Slot is now full
        assertEquals(10, reservationRepository.countConfirmedByTimeSlotId(testSlot.getId()));

        // When: Try to book when full
        User extraUser = createUser("extra@example.com");
        assertThrows(IllegalStateException.class, () ->
                reservationService.createReservation(testSlot.getId(), extraUser.getId(), null, 1)
        );
    }

    // ========== DUPLICATE BOOKING PREVENTION ==========

    @Test
    void shouldPreventDuplicateBooking() {
        // Given: User booked a slot
        reservationService.createReservation(testSlot.getId(), testUser.getId(), null, 1);
        assertEquals(1, reservationRepository.countConfirmedByTimeSlotId(testSlot.getId()));

        // When: User tries to book same slot again
        Exception exception = assertThrows(IllegalStateException.class, () ->
                reservationService.createReservation(testSlot.getId(), testUser.getId(), null, 1)
        );

        // Then: Booking rejected
        assertTrue(exception.getMessage().contains("already") ||
                   exception.getMessage().contains("duplicate"));

        // And: Only one reservation exists
        List<Reservation> reservations = reservationRepository.findByUserId(testUser.getId());
        assertEquals(1, reservations.size());
    }

    // ========== MULTI-PARTICIPANT BOOKING ==========

    @Test
    void shouldSupportMultiParticipantBooking() {
        // Given: User wants to book for 3 people
        int participants = 3;

        // When: Book with participants
        reservationService.createReservation(testSlot.getId(), testUser.getId(), null, 1);

        Reservation reservation = reservationRepository.findByUserId(testUser.getId()).get(0);

        // Update participants (simulating admin or user modification)
        try {
            var participantsField = Reservation.class.getDeclaredField("participants");
            participantsField.setAccessible(true);
            participantsField.set(reservation, participants);
            reservationRepository.save(reservation);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Then: Count reflects all participants
        assertEquals(3, reservationRepository.countConfirmedByTimeSlotId(testSlot.getId()));

        // When: Book 7 more slots (3 + 7 = 10, at capacity)
        for (int i = 0; i < 7; i++) {
            User user = createUser("participant" + i + "@example.com");
            reservationService.createReservation(testSlot.getId(), user.getId(), null, 1);
        }

        // Then: Slot is full
        assertEquals(10, reservationRepository.countConfirmedByTimeSlotId(testSlot.getId()));

        // When: Try to book one more
        User extraUser = createUser("overflow@example.com");
        assertThrows(IllegalStateException.class, () ->
                reservationService.createReservation(testSlot.getId(), extraUser.getId(), null, 1)
        );
    }

    // ========== DATABASE CONSTRAINTS ==========

    @Test
    void shouldMaintainDatabaseIntegrity() {
        // Given: Reservation
        reservationService.createReservation(testSlot.getId(), testUser.getId(), null, 1);
        Reservation reservation = reservationRepository.findByUserId(testUser.getId()).get(0);

        // When: Delete slot (ON DELETE CASCADE at DB level removes reservation too)
        UUID reservationId = reservation.getId();
        // Clear entire L1 cache first — createReservation also saved ActivityLog which
        // references TimeSlot; detaching only reservation isn't enough
        entityManager.clear();
        timeSlotRepository.deleteById(testSlot.getId()); // loads fresh from DB, marks for removal
        entityManager.flush(); // send DELETE to DB, triggering ON DELETE CASCADE
        entityManager.clear();

        // Then: Reservation also deleted (ON DELETE CASCADE)
        assertFalse(reservationRepository.findById(reservationId).isPresent());
    }

    @Test
    void shouldPreserveDataAcrossTransactions() {
        // Given: Book slot in one transaction
        reservationService.createReservation(testSlot.getId(), testUser.getId(), null, 1);

        // When: Fetch in new transaction (simulated by method call)
        List<Reservation> reservations = reservationRepository.findByUserId(testUser.getId());

        // Then: Data persisted
        assertEquals(1, reservations.size());
        assertEquals(ReservationStatus.CONFIRMED, reservations.get(0).getStatus());

        // When: Cancel and verify persistence
        UUID reservationId = reservations.get(0).getId();
        reservationService.cancelReservation(reservationId, testUser.getId());

        // Then: Status change persisted
        Reservation cancelled = reservationRepository.findById(reservationId).orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, cancelled.getStatus());
    }

    // ========== HELPER METHODS ==========

    private User createUser(String email) {
        User user = new User(
                email,
                "Test",
                "User",
                "+48123456789",
                email.split("@")[0]
        );
        user.setRole(UserRole.USER);
        user.setEmailVerified(true);
        return userRepository.save(user); // JPA will set ID and timestamps
    }
}
