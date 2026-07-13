package pl.nextsteppro.climbing.api.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequestRepository;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequestStatus;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlistRepository;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AdminService.getNotifications — badge counters (pending training requests,
 * new reservations and new waitlist signups since the per-admin "seen" marker).
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceNotificationsTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private UserRepository userRepository;
    @Mock private WaitlistRepository waitlistRepository;
    @Mock private EventWaitlistRepository eventWaitlistRepository;
    @Mock private TrainingRequestRepository trainingRequestRepository;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
            null, null, null, reservationRepository, null,
            userRepository, null, null, null, null, null,
            waitlistRepository, eventWaitlistRepository, null, null, null, null,
            trainingRequestRepository);
    }

    @Test
    void shouldSumWaitlistCountsFromBothWaitlistsWhenBuildingNotifications() {
        // Given
        UUID adminId = UUID.randomUUID();
        Instant seenAt = Instant.parse("2026-07-01T10:00:00Z");
        User admin = mock(User.class);
        when(admin.getAdminReservationsSeenAt()).thenReturn(seenAt);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(trainingRequestRepository.countByStatus(TrainingRequestStatus.PENDING)).thenReturn(2);
        when(reservationRepository.countConfirmedCreatedAfter(seenAt)).thenReturn(3);
        when(waitlistRepository.countActiveCreatedAfter(seenAt)).thenReturn(1);
        when(eventWaitlistRepository.countActiveCreatedAfter(seenAt)).thenReturn(4);

        // When
        AdminNotificationsDto notifications = adminService.getNotifications(adminId);

        // Then
        assertEquals(2, notifications.pendingRequests());
        assertEquals(3, notifications.newReservations());
        assertEquals(5, notifications.newWaitlistEntries());
    }

    @Test
    void shouldReturnZeroWaitlistEntriesWhenNothingNewSinceSeenMarker() {
        // Given
        UUID adminId = UUID.randomUUID();
        Instant seenAt = Instant.parse("2026-07-01T10:00:00Z");
        User admin = mock(User.class);
        when(admin.getAdminReservationsSeenAt()).thenReturn(seenAt);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(trainingRequestRepository.countByStatus(TrainingRequestStatus.PENDING)).thenReturn(0);
        when(reservationRepository.countConfirmedCreatedAfter(seenAt)).thenReturn(0);
        when(waitlistRepository.countActiveCreatedAfter(seenAt)).thenReturn(0);
        when(eventWaitlistRepository.countActiveCreatedAfter(seenAt)).thenReturn(0);

        // When
        AdminNotificationsDto notifications = adminService.getNotifications(adminId);

        // Then
        assertEquals(0, notifications.newWaitlistEntries());
    }
}
