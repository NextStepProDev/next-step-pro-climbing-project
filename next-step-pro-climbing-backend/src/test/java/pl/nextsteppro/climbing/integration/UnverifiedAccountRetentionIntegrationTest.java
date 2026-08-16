package pl.nextsteppro.climbing.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.nextsteppro.climbing.api.auth.UnverifiedAccountRetentionService;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.infrastructure.mail.AuthMailService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Retention of accounts that registered and never confirmed their address.
 *
 * <p>The window is driven by the explicit-{@code now} overloads rather than by back-dating rows:
 * {@code users.created_at} is {@code updatable = false} and written by {@code @PrePersist}, so a
 * test that wanted an eight-day-old account would have to reach past JPA with native SQL to build
 * the very state it is asserting on.
 */
class UnverifiedAccountRetentionIntegrationTest extends BaseIntegrationTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private UnverifiedAccountRetentionService retention;

    @MockitoBean
    private AuthMailService authMailService;

    private Instant registeredAt;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        eventRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();
        registeredAt = Instant.now();
    }

    private User account(String email, boolean verified, UserRole role) {
        User user = new User(email, "Test", "User", "+48123456789", email.split("@")[0]);
        user.setRole(role);
        user.setEmailVerified(verified);
        return userRepository.save(user);
    }

    private Instant daysAfterRegistration(long days) {
        return registeredAt.plus(Duration.ofDays(days));
    }

    @Test
    void shouldDeleteUnverifiedAccountWhenOlderThanRetentionWindow() {
        // Given
        User user = account("stale@example.com", false, UserRole.USER);

        // When: the sweep runs eight days after the registration
        int deleted = retention.deleteExpired(daysAfterRegistration(8));

        // Then
        assertEquals(1, deleted);
        assertFalse(userRepository.findById(user.getId()).isPresent());
    }

    @Test
    void shouldKeepUnverifiedAccountWhenStillInsideRetentionWindow() {
        // Given
        User user = account("fresh@example.com", false, UserRole.USER);

        // When
        int deleted = retention.deleteExpired(daysAfterRegistration(5));

        // Then
        assertEquals(0, deleted);
        assertTrue(userRepository.findById(user.getId()).isPresent());
    }

    @Test
    void shouldKeepVerifiedAccountRegardlessOfAge() {
        // Given
        User user = account("verified@example.com", true, UserRole.USER);

        // When: a year later
        int deleted = retention.deleteExpired(daysAfterRegistration(365));

        // Then
        assertEquals(0, deleted);
        assertTrue(userRepository.findById(user.getId()).isPresent());
    }

    @Test
    void shouldKeepUnverifiedAdminAccount() {
        // Given: ADMIN_EMAIL registered and never finished — a signal, not litter
        User admin = account("admin@example.com", false, UserRole.ADMIN);

        // When
        int deleted = retention.deleteExpired(daysAfterRegistration(30));

        // Then
        assertEquals(0, deleted);
        assertTrue(userRepository.findById(admin.getId()).isPresent());
    }

    @Test
    void shouldRemindOnTheFinalDayWithoutDeletingInTheSameSweep() {
        // Given
        User user = account("lastday@example.com", false, UserRole.USER);
        Instant finalDay = registeredAt.plus(Duration.ofDays(6)).plus(Duration.ofHours(1));

        // When: the sweep that catches the [6d, 7d) band
        int reminded = retention.sendReminders(finalDay);
        int deleted = retention.deleteExpired(finalDay);

        // Then: warned, and still there to act on the warning
        assertEquals(1, reminded);
        assertEquals(0, deleted);
        verify(authMailService).sendVerificationReminder(argThatIs(user));
        assertTrue(userRepository.findById(user.getId()).isPresent());
    }

    @Test
    void shouldNotRemindTwiceAcrossConsecutiveSweeps() {
        // Given
        account("once@example.com", false, UserRole.USER);

        // When: two daily runs, one on either side of the band
        int beforeBand = retention.sendReminders(daysAfterRegistration(5));
        int insideBand = retention.sendReminders(registeredAt.plus(Duration.ofDays(6)).plus(Duration.ofHours(1)));
        int afterBand = retention.sendReminders(daysAfterRegistration(8));

        // Then: exactly one run hits it — which is why no reminder marker is stored
        assertEquals(0, beforeBand);
        assertEquals(1, insideBand);
        assertEquals(0, afterBand);
    }

    @Test
    void shouldNotRemindVerifiedAccount() {
        // Given
        account("confirmed@example.com", true, UserRole.USER);

        // When
        int reminded = retention.sendReminders(registeredAt.plus(Duration.ofDays(6)).plus(Duration.ofHours(1)));

        // Then
        assertEquals(0, reminded);
        verify(authMailService, never()).sendVerificationReminder(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldFreeTheSeatWhenDeletingAnUnverifiedAccountThatHeldOne() {
        // Given: a seat taken before the guards existed — an admin had signed them up by hand
        User user = account("booked@example.com", false, UserRole.USER);
        TimeSlot slot = timeSlotRepository.save(
            new TimeSlot(LocalDate.now().plusDays(7), LocalTime.of(10, 0), LocalTime.of(12, 0), 2));
        reservationRepository.save(new Reservation(user, slot));
        // Detach the fixture before sweeping. The sweep runs with a fresh persistence context in
        // production; here the reservation this test just built would still be managed and
        // referencing the user being deleted, which Hibernate reports as a transient reference —
        // an artefact of the shared test transaction, not of the deletion path.
        entityManager.flush();
        entityManager.clear();
        assertEquals(1, reservationRepository.countConfirmedByTimeSlotId(slot.getId()));

        // When
        retention.deleteExpired(daysAfterRegistration(8));
        entityManager.flush();
        entityManager.clear();

        // Then: the seat goes back to the pool — the deletion runs the shared release path, it
        // does not just drop the row and leave the slot looking full
        assertEquals(0, reservationRepository.countConfirmedByTimeSlotId(slot.getId()));
        assertFalse(userRepository.findById(user.getId()).isPresent());
    }

    private static User argThatIs(User expected) {
        return org.mockito.ArgumentMatchers.argThat(actual -> actual.getId().equals(expected.getId()));
    }
}
