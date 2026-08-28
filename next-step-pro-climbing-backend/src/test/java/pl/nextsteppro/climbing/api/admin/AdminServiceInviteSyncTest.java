package pl.nextsteppro.climbing.api.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.api.reservation.EventWaitlistService;
import pl.nextsteppro.climbing.api.reservation.UserSeatReleaseService;
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
import pl.nextsteppro.climbing.domain.user.UserRole;
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
import static org.mockito.Mockito.inOrder;
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
 *
 * Also covers the two other guards on the same paths: unverified accounts cannot be bound to
 * anything (invites, manual sign-ups, roles), and the admin-side account deletion has to release
 * seats through the shared service.
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
    @Mock private UserSeatReleaseService userSeatReleaseService;
    @Mock private pl.nextsteppro.climbing.api.trainingcalendar.CommentFileSupport commentFileSupport;
    @Mock private pl.nextsteppro.climbing.api.trainingcalendar.AttachmentSupport attachmentSupport;
    @Mock private ReservedSeatRepository reservedSeatRepository;
    @Mock private TrainingRequestRepository trainingRequestRepository;
    @Mock private pl.nextsteppro.climbing.api.trainingcalendar.TrainingCalendarService trainingCalendarService;

    private AdminService adminService;

    private UUID adminId;
    private UUID slotId;
    private UUID eventId;
    private User admin;
    private User invitedUser;
    private User unverifiedUser;
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
            userSeatReleaseService,
            commentFileSupport,
            attachmentSupport,
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
        // A real invitee has confirmed their address — the guard below refuses anyone who has not.
        invitedUser.setEmailVerified(true);

        unverifiedUser = new User("unverified@example.com", "Never", "Confirmed", "+48555555555", "never");
        setId(unverifiedUser, UUID.randomUUID());

        slot = new TimeSlot(LocalDate.now().minusDays(7), LocalTime.of(10, 0), LocalTime.of(11, 0), 1);
        setId(slot, slotId);

        event = new Event("Test Event", EventType.TRAINING,
            LocalDate.now().minusDays(7), LocalDate.now().minusDays(7), 1);
        setId(event, eventId);
    }

    private UpdateTimeSlotRequest slotRequestWithInvites(List<UUID> invitedUserIds) {
        return new UpdateTimeSlotRequest(null, null, null, null, null, null, null, false, invitedUserIds);
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

    // ========== Unverified accounts cannot be bound to anything ==========

    @Test
    void shouldRejectNewSlotInviteWhenAccountIsUnverified() {
        // Given: an empty slot and an account that never confirmed its address
        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(timeSlotRepository.save(slot)).thenReturn(slot);
        when(reservationRepository.countConfirmedByTimeSlotId(slotId)).thenReturn(0);
        when(guestReservationRepository.sumParticipantsByTimeSlotId(slotId)).thenReturn(0);
        when(reservationRepository.findConfirmedUserIdsByTimeSlotId(slotId)).thenReturn(List.of());
        when(reservedSeatRepository.findBySlotIdWithUser(slotId)).thenReturn(List.of());
        when(userRepository.findById(unverifiedUser.getId())).thenReturn(Optional.of(unverifiedUser));
        when(msg.get("admin.user.unverified", unverifiedUser.getFullName())).thenReturn("unverified");

        // When & Then: 400, and no seat is held for someone who cannot log in to use it
        assertThrows(IllegalArgumentException.class, () ->
            adminService.updateTimeSlot(adminId, slotId, slotRequestWithInvites(List.of(unverifiedUser.getId()))));
        verify(reservedSeatRepository, never()).save(any());
    }

    @Test
    void shouldStillAllowEditingSlotWhoseExistingInviteIsUnverified() {
        // Given: an invitation issued before the guard existed. The front resubmits the full
        // invitee list on every edit, so checking all of them would make this slot uneditable.
        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(timeSlotRepository.save(slot)).thenReturn(slot);
        when(reservationRepository.countConfirmedByTimeSlotId(slotId)).thenReturn(0);
        when(guestReservationRepository.sumParticipantsByTimeSlotId(slotId)).thenReturn(0);
        when(reservationRepository.findConfirmedUserIdsByTimeSlotId(slotId)).thenReturn(List.of());
        when(reservedSeatRepository.findBySlotIdWithUser(slotId))
            .thenReturn(List.of(new ReservedSeat(slot, unverifiedUser)));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        // When & Then
        assertDoesNotThrow(() ->
            adminService.updateTimeSlot(adminId, slotId, slotRequestWithInvites(List.of(unverifiedUser.getId()))));
        verify(reservedSeatRepository, never()).delete(any());
    }

    @Test
    void shouldRejectNewEventInviteWhenAccountIsUnverified() {
        // Given
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of());
        when(guestReservationRepository.sumParticipantsByEventId(eventId)).thenReturn(0);
        when(reservationRepository.findConfirmedUserIdsByEventId(eventId)).thenReturn(List.of());
        when(reservedSeatRepository.findByEventIdWithUser(eventId)).thenReturn(List.of());
        when(userRepository.findById(unverifiedUser.getId())).thenReturn(Optional.of(unverifiedUser));
        when(msg.get("admin.user.unverified", unverifiedUser.getFullName())).thenReturn("unverified");

        // When & Then: the twin path has to refuse it too
        assertThrows(IllegalArgumentException.class, () ->
            adminService.updateEvent(adminId, eventId, eventRequestWithInvites(List.of(unverifiedUser.getId()))));
        verify(reservedSeatRepository, never()).save(any());
    }

    @Test
    void shouldRejectManualSlotSignUpWhenAccountIsUnverified() {
        // Given
        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(userRepository.findById(unverifiedUser.getId())).thenReturn(Optional.of(unverifiedUser));
        when(msg.get("admin.user.unverified", unverifiedUser.getFullName())).thenReturn("unverified");

        // When & Then: a reservation they could never see, and a confirmation mail to an address
        // nobody proved they own
        assertThrows(IllegalArgumentException.class, () -> adminService.addRegisteredParticipantToSlot(
            slotId, new AddRegisteredParticipantRequest(unverifiedUser.getId(), 1, null)));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldRejectManualEventSignUpWhenAccountIsUnverified() {
        // Given
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(userRepository.findById(unverifiedUser.getId())).thenReturn(Optional.of(unverifiedUser));
        when(msg.get("admin.user.unverified", unverifiedUser.getFullName())).thenReturn("unverified");

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> adminService.addRegisteredParticipantToEvent(
            eventId, new AddRegisteredParticipantRequest(unverifiedUser.getId(), 1, null)));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldRejectMakeAdminWhenAccountIsUnverified() {
        // Given
        when(userRepository.findById(unverifiedUser.getId())).thenReturn(Optional.of(unverifiedUser));
        when(msg.get("admin.user.unverified", unverifiedUser.getFullName())).thenReturn("unverified");

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            adminService.makeAdmin(adminId, unverifiedUser.getId()));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldRejectGrantingAthleteFlagWhenAccountIsUnverified() {
        // Given
        when(userRepository.findById(unverifiedUser.getId())).thenReturn(Optional.of(unverifiedUser));
        when(msg.get("admin.user.unverified", unverifiedUser.getFullName())).thenReturn("unverified");

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            adminService.setAthlete(adminId, unverifiedUser.getId(), true));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldAllowRevokingAthleteFlagFromUnverifiedAccount() {
        // Given: a flag granted before the guard existed — taking it back must stay possible,
        // or the guard would trap the very state it forbids
        unverifiedUser.setAthlete(true);
        when(userRepository.findById(unverifiedUser.getId())).thenReturn(Optional.of(unverifiedUser));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        // When
        adminService.setAthlete(adminId, unverifiedUser.getId(), false);

        // Then
        verify(userRepository).save(unverifiedUser);
    }

    // ========== REGRESSION: admin-side account deletion ==========

    @Test
    void shouldReleaseSeatsAndNotifyWaitlistsWhenAdminDeletesUser() {
        // Given — the admin path used to cancel the reservations with a bare bulk UPDATE and stop
        // there: no waitlist was ever told, so a queue could sit idle on seats that had just been
        // freed. The self-service path (UserService.deleteAccount) always did this properly.
        UUID victimId = UUID.randomUUID();
        User victim = new User("victim@example.com", "Vic", "Tim", "+48222222222", "victim");
        setId(victim, victimId);

        when(userRepository.findById(victimId)).thenReturn(Optional.of(victim));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        // When
        adminService.deleteUser(adminId, victimId);

        // Then
        verify(userSeatReleaseService).releaseSeatsAndNotifyWaitlists(victimId);
        verify(userRepository).delete(victim);
        verify(jwtAuthenticationFilter).evictUser(victimId);
    }

    @Test
    void shouldReleaseSeatsBeforeDeletingWhenAdminDeletesUser() {
        // Given — the reservations cascade away with the user, so the seats have to be released
        // and re-offered while the rows still exist.
        UUID victimId = UUID.randomUUID();
        User victim = new User("victim2@example.com", "Vic", "Tim", "+48333333333", "victim2");
        setId(victim, victimId);

        when(userRepository.findById(victimId)).thenReturn(Optional.of(victim));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        // When
        adminService.deleteUser(adminId, victimId);

        // Then
        var inOrder = inOrder(userSeatReleaseService, userRepository);
        inOrder.verify(userSeatReleaseService).releaseSeatsAndNotifyWaitlists(victimId);
        inOrder.verify(userRepository).delete(victim);
    }

    @Test
    void shouldRefuseToDeleteAnotherAdmin() {
        UUID otherAdminId = UUID.randomUUID();
        User otherAdmin = new User("admin2@example.com", "Other", "Admin", "+48444444444", "admin2");
        setId(otherAdmin, otherAdminId);
        otherAdmin.setRole(UserRole.ADMIN);

        when(userRepository.findById(otherAdminId)).thenReturn(Optional.of(otherAdmin));
        when(msg.get("admin.user.cannot.delete.admin")).thenReturn("Cannot delete an admin");

        assertThrows(IllegalStateException.class, () -> adminService.deleteUser(adminId, otherAdminId));
        verify(userSeatReleaseService, never()).releaseSeatsAndNotifyWaitlists(any());
        verify(userRepository, never()).delete(any(User.class));
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
