package pl.nextsteppro.climbing.api.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.api.reservation.EventWaitlistService;
import pl.nextsteppro.climbing.api.reservation.WaitlistService;
import pl.nextsteppro.climbing.domain.auth.AuthTokenRepository;
import pl.nextsteppro.climbing.domain.course.CourseRepository;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventRepository;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reservation.GuestReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.SlotParticipantCount;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeat;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeatRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequestRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlistRepository;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.mail.AuthMailService;
import pl.nextsteppro.climbing.infrastructure.mail.MailService;
import pl.nextsteppro.climbing.infrastructure.security.JwtAuthenticationFilter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the invite-capacity guard in syncSlotInvites / syncEventInvites.
 *
 * Regression: an invited user who already booked is counted in confirmed
 * reservations — the guard must not count them a second time via the invite
 * list, otherwise a full slot/event with a used invitation rejects every edit.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceInviteSyncTest {

    @Mock private TimeSlotRepository timeSlotRepository;
    @Mock private EventRepository eventRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private GuestReservationRepository guestReservationRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuthTokenRepository authTokenRepository;
    @Mock private MailService mailService;
    @Mock private ActivityLogService activityLogService;
    @Mock private JwtAuthenticationFilter jwtAuthenticationFilter;
    @Mock private MessageService msg;
    @Mock private WaitlistRepository waitlistRepository;
    @Mock private EventWaitlistRepository eventWaitlistRepository;
    @Mock private AuthMailService authMailService;
    @Mock private WaitlistService waitlistService;
    @Mock private EventWaitlistService eventWaitlistService;
    @Mock private ReservedSeatRepository reservedSeatRepository;
    @Mock private TrainingRequestRepository trainingRequestRepository;
    @Mock private pl.nextsteppro.climbing.api.trainingcalendar.TrainingCalendarService trainingCalendarService;

    private AdminService adminService;

    private UUID adminId;
    private UUID slotId;
    private UUID eventId;
    private User admin;
    private User invitedUser;
    private TimeSlot slot;
    private Event event;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
            timeSlotRepository,
            eventRepository,
            courseRepository,
            reservationRepository,
            guestReservationRepository,
            userRepository,
            authTokenRepository,
            mailService,
            activityLogService,
            jwtAuthenticationFilter,
            msg,
            waitlistRepository,
            eventWaitlistRepository,
            authMailService,
            waitlistService,
            eventWaitlistService,
            reservedSeatRepository,
            trainingRequestRepository,
            trainingCalendarService
        );

        adminId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        admin = new User("admin@example.com", "Admin", "User", "+48111111111", "admin");
        setId(admin, adminId);

        invitedUser = new User("invited@example.com", "Invited", "User", "+48222222222", "invited");
        setId(invitedUser, UUID.randomUUID());

        slot = new TimeSlot(LocalDate.now().minusDays(7), LocalTime.of(10, 0), LocalTime.of(11, 0), 1);
        setId(slot, slotId);

        event = new Event("Test Event", EventType.TRAINING,
            LocalDate.now().minusDays(7), LocalDate.now().minusDays(7), 1);
        setId(event, eventId);
    }

    private UpdateTimeSlotRequest slotRequestWithInvites(List<UUID> invitedUserIds) {
        return new UpdateTimeSlotRequest(null, null, null, null, null, null, false, invitedUserIds);
    }

    private UpdateEventRequest eventRequestWithInvites(List<UUID> invitedUserIds) {
        return new UpdateEventRequest(null, null, null, null, null, null, null, null, null, null, null, null, invitedUserIds);
    }

    // ========== SLOTS ==========

    @Test
    void shouldUpdateFullSlotWhenInvitedUserAlreadyBooked() {
        // Given: max 1, the single confirmed booking belongs to the invited user
        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(timeSlotRepository.save(slot)).thenReturn(slot);
        when(reservationRepository.countConfirmedByTimeSlotId(slotId)).thenReturn(1);
        when(guestReservationRepository.sumParticipantsByTimeSlotId(slotId)).thenReturn(0);
        when(reservationRepository.findConfirmedUserIdsByTimeSlotId(slotId))
            .thenReturn(List.of(invitedUser.getId()));
        when(reservedSeatRepository.findBySlotIdWithUser(slotId))
            .thenReturn(List.of(new ReservedSeat(slot, invitedUser)));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        // When & Then: no double counting — the edit saves
        assertDoesNotThrow(() ->
            adminService.updateTimeSlot(adminId, slotId, slotRequestWithInvites(List.of(invitedUser.getId()))));

        verify(reservedSeatRepository, never()).delete(any());
        verify(reservedSeatRepository, never()).save(any());
        verify(activityLogService).logAdminSlotUpdated(admin, slot);
    }

    @Test
    void shouldRejectSlotUpdateWhenPendingInvitesExceedCapacity() {
        // Given: max 1, confirmed booking belongs to someone else, invite is pending
        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(timeSlotRepository.save(slot)).thenReturn(slot);
        when(reservationRepository.countConfirmedByTimeSlotId(slotId)).thenReturn(1);
        when(guestReservationRepository.sumParticipantsByTimeSlotId(slotId)).thenReturn(0);
        when(reservationRepository.findConfirmedUserIdsByTimeSlotId(slotId))
            .thenReturn(List.of(UUID.randomUUID()));
        when(msg.get("admin.invites.too.many", "1")).thenReturn("too many invites");

        // When & Then
        assertThrows(IllegalStateException.class, () ->
            adminService.updateTimeSlot(adminId, slotId, slotRequestWithInvites(List.of(invitedUser.getId()))));
    }

    // ========== EVENTS ==========

    @Test
    void shouldUpdateFullEventWhenInvitedUserAlreadyBooked() {
        // Given: max 1, the single confirmed booking belongs to the invited user
        TimeSlot eventSlot = new TimeSlot(event, event.getStartDate(), LocalTime.of(10, 0), LocalTime.of(11, 0), 1);
        setId(eventSlot, UUID.randomUUID());

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of(eventSlot));
        when(reservationRepository.countConfirmedByTimeSlotIds(List.of(eventSlot.getId())))
            .thenReturn(List.of(new SlotParticipantCount(eventSlot.getId(), 1)));
        when(reservationRepository.findConfirmedUserIdsByEventId(eventId))
            .thenReturn(List.of(invitedUser.getId()));
        when(reservedSeatRepository.findByEventIdWithUser(eventId))
            .thenReturn(List.of(new ReservedSeat(event, invitedUser)));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        // When & Then: no double counting — the edit saves
        assertDoesNotThrow(() ->
            adminService.updateEvent(adminId, eventId, eventRequestWithInvites(List.of(invitedUser.getId()))));

        verify(reservedSeatRepository, never()).delete(any());
        verify(reservedSeatRepository, never()).save(any());
        verify(activityLogService).logAdminEventUpdated(admin, event);
    }

    @Test
    void shouldRejectEventUpdateWhenPendingInvitesExceedCapacity() {
        // Given: max 1, confirmed booking belongs to someone else, invite is pending
        TimeSlot eventSlot = new TimeSlot(event, event.getStartDate(), LocalTime.of(10, 0), LocalTime.of(11, 0), 1);
        setId(eventSlot, UUID.randomUUID());

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of(eventSlot));
        when(reservationRepository.countConfirmedByTimeSlotIds(List.of(eventSlot.getId())))
            .thenReturn(List.of(new SlotParticipantCount(eventSlot.getId(), 1)));
        when(reservationRepository.findConfirmedUserIdsByEventId(eventId))
            .thenReturn(List.of(UUID.randomUUID()));
        when(msg.get("admin.invites.too.many", "1")).thenReturn("too many invites");

        // When & Then
        assertThrows(IllegalStateException.class, () ->
            adminService.updateEvent(adminId, eventId, eventRequestWithInvites(List.of(invitedUser.getId()))));
    }

    private void setId(Object entity, UUID id) {
        try {
            var idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set entity id", e);
        }
    }
}
