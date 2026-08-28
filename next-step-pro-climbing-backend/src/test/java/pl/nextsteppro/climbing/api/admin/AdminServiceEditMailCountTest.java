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
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The admin edits a slot/event and the modification mails go out silently — the panel used to say
 * nothing, so the only way to learn whether anyone was told was to ask a participant. The update
 * now reports how many people were mailed.
 *
 * <p>The number has to be the number of mails actually sent, not the number of bookings:
 * {@code MailService} drops anyone with email notifications switched off, so a count of bookings
 * would promise the admin messages nobody received.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceEditMailCountTest {

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
    @Mock private ReservedSeatRepository reservedSeatRepository;
    @Mock private TrainingRequestRepository trainingRequestRepository;
    @Mock private pl.nextsteppro.climbing.api.trainingcalendar.TrainingCalendarService trainingCalendarService;

    private AdminService adminService;

    private UUID adminId;
    private UUID slotId;
    private UUID eventId;
    private User admin;
    private User subscriber;
    private User optedOut;
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
            null, // comment attachments: not exercised here
            null, // training materials: not exercised here
            reservedSeatRepository,
            trainingRequestRepository,
            trainingCalendarService
        );

        adminId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        admin = new User("admin@example.com", "Admin", "User", "+48111111111", "admin");
        setId(admin, adminId);

        subscriber = new User("subscriber@example.com", "Sub", "Scriber", "+48222222222", "sub");
        setId(subscriber, UUID.randomUUID());

        optedOut = new User("quiet@example.com", "Quiet", "User", "+48333333333", "quiet");
        setId(optedOut, UUID.randomUUID());
        optedOut.setEmailNotificationsEnabled(false);

        slot = new TimeSlot(LocalDate.now().plusDays(7), LocalTime.of(10, 0), LocalTime.of(11, 0), 5);
        setId(slot, slotId);

        event = new Event("Test Event", EventType.TRAINING,
            LocalDate.now().plusDays(7), LocalDate.now().plusDays(7), 5);
        setId(event, eventId);
    }

    @Test
    void shouldCountOnlyMailedParticipantsWhenSlotIsMoved() {
        // Given — two bookings, one of them from someone who turned mail notifications off
        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(timeSlotRepository.save(slot)).thenReturn(slot);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(reservationRepository.findConfirmedByTimeSlotIds(List.of(slotId)))
            .thenReturn(List.of(new Reservation(subscriber, slot), new Reservation(optedOut, slot)));

        // When — the slot moves a day later, which is news for everyone holding a booking
        SlotUpdateResultDto result = adminService.updateTimeSlot(adminId, slotId,
            slotRequest(LocalDate.now().plusDays(8), null));

        // Then
        assertEquals(1, result.notifiedCount());
        verify(mailService).sendAdminSlotModificationNotification(eq(subscriber), eq(slot), any(), any());
        verify(mailService, never()).sendAdminSlotModificationNotification(eq(optedOut), any(), any(), any());
    }

    @Test
    void shouldReportParticipantsPresentWhenEveryBookerOptedOutOfMail() {
        // Given — somebody's booking is being moved, but nobody can be written to. That is a
        // different answer from "the slot was empty", and the panel says so.
        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(timeSlotRepository.save(slot)).thenReturn(slot);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(reservationRepository.findConfirmedByTimeSlotIds(List.of(slotId)))
            .thenReturn(List.of(new Reservation(optedOut, slot)));
        when(reservationRepository.countConfirmedByTimeSlotId(slotId)).thenReturn(1);

        // When
        SlotUpdateResultDto result = adminService.updateTimeSlot(adminId, slotId,
            slotRequest(LocalDate.now().plusDays(8), null));

        // Then
        assertEquals(0, result.notifiedCount());
        assertTrue(result.hadParticipants());
    }

    @Test
    void shouldReportNoParticipantsWhenAnEmptySlotIsMoved() {
        // Given — an empty slot: mentioning notifications at all would be noise
        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(timeSlotRepository.save(slot)).thenReturn(slot);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        // When
        SlotUpdateResultDto result = adminService.updateTimeSlot(adminId, slotId,
            slotRequest(LocalDate.now().plusDays(8), null));

        // Then
        assertEquals(0, result.notifiedCount());
        assertFalse(result.hadParticipants());
    }

    @Test
    void shouldReportZeroNotifiedWhenSlotEditChangesNothingParticipantsCareAbout() {
        // Given — the edit touches only the seat count, which builds no change list
        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(timeSlotRepository.save(slot)).thenReturn(slot);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(reservationRepository.countConfirmedByTimeSlotId(slotId)).thenReturn(0);
        when(guestReservationRepository.sumParticipantsByTimeSlotId(slotId)).thenReturn(0);

        // When
        SlotUpdateResultDto result = adminService.updateTimeSlot(adminId, slotId, slotRequest(null, 8));

        // Then — capacity went up, so the waitlist is told; the participants are not
        assertEquals(0, result.notifiedCount());
        verify(mailService, never()).sendAdminSlotModificationNotification(any(), any(), any(), any());
    }

    @Test
    void shouldCountOnlyMailedParticipantsWhenEventIsMoved() {
        // Given — the event's single day-slot carries both bookings
        TimeSlot eventSlot = new TimeSlot(event, event.getStartDate(), LocalTime.of(10, 0), LocalTime.of(11, 0), 5);
        setId(eventSlot, UUID.randomUUID());

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(timeSlotRepository.findByEventId(eventId)).thenReturn(List.of(eventSlot));
        when(reservationRepository.findConfirmedByTimeSlotIds(List.of(eventSlot.getId())))
            .thenReturn(List.of(new Reservation(subscriber, eventSlot), new Reservation(optedOut, eventSlot)));

        // When — the event moves a day later
        LocalDate moved = LocalDate.now().plusDays(8);
        EventUpdateResultDto result = adminService.updateEvent(adminId, eventId,
            new UpdateEventRequest(null, null, null, null, moved, moved, null, null, null, null, null, null, null));

        // Then
        assertEquals(1, result.notifiedCount());
        verify(mailService).sendAdminEventModificationNotification(eq(subscriber), eq(event), any());
        verify(mailService, never()).sendAdminEventModificationNotification(eq(optedOut), any(), any());
    }

    private UpdateTimeSlotRequest slotRequest(java.time.LocalDate date, Integer maxParticipants) {
        return new UpdateTimeSlotRequest(date, null, null, maxParticipants, null, null, null, null, null);
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
