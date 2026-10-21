package pl.nextsteppro.climbing.api.reschedule;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.nextsteppro.climbing.api.admin.reschedule.AdminRescheduleTestAccess;
import pl.nextsteppro.climbing.api.reservation.ReservationService;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reschedule.RescheduleRequest;
import pl.nextsteppro.climbing.domain.reschedule.RescheduleRequestRepository;
import pl.nextsteppro.climbing.domain.reschedule.RescheduleRequestStatus;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.infrastructure.mail.MailService;
import pl.nextsteppro.climbing.infrastructure.mail.RescheduleMailService;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RescheduleRequestIntegrationTest extends BaseIntegrationTest {

    @MockitoBean private RescheduleMailService rescheduleMailService;
    @MockitoBean private MailService mailService;

    @PersistenceContext private EntityManager entityManager;
    @Autowired private RescheduleService rescheduleService;
    @Autowired private AdminRescheduleTestAccess admin;
    @Autowired private ReservationService reservationService;
    @Autowired private pl.nextsteppro.climbing.api.admin.AdminService adminService;
    @Autowired private RescheduleRequestRepository requestRepository;

    private User coach;
    private User client;
    private User other;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        coach = user("rr-coach@test.local", UserRole.ADMIN);
        client = user("rr-client@test.local", UserRole.USER);
        other = user("rr-other@test.local", UserRole.USER);
        day = LocalDate.now(ZoneId.of("Europe/Warsaw")).plusDays(21);
    }

    // ---- Asking ----

    @Test
    void shouldRecordTheRequestAndTellTheCoachWhoElseIsBooked() {
        TimeSlot slot = slot(day, 18, 20, 4);
        UUID booking = book(client, slot);
        book(other, slot);
        flushAndClear();

        rescheduleService.create(client.getId(), slotRequest(booking, day.plusDays(2), 18, 20));

        RescheduleRequest saved = requestRepository.findPendingForSlot(client.getId(), slot.getId()).orElseThrow();
        assertEquals(day, saved.oldTerm().date());
        assertEquals(day.plusDays(2), saved.newTerm().date());
        verify(rescheduleMailService).sendRequestToAdmins(any(), any(), any(), any(), isNull(),
            eq(List.of(other.getFullName())), eq(0));
    }

    @Test
    void shouldRefuseASecondPendingRequestForTheSameBooking() {
        TimeSlot slot = slot(day, 18, 20, 1);
        UUID booking = book(client, slot);
        rescheduleService.create(client.getId(), slotRequest(booking, day.plusDays(1), 18, 20));

        assertThrows(IllegalStateException.class,
            () -> rescheduleService.create(client.getId(), slotRequest(booking, day.plusDays(2), 18, 20)));
    }

    @Test
    void shouldRefuseToMoveSomebodyElsesBooking() {
        TimeSlot slot = slot(day, 18, 20, 2);
        UUID theirs = book(other, slot);

        assertThrows(IllegalStateException.class,
            () -> rescheduleService.create(client.getId(), slotRequest(theirs, day.plusDays(1), 18, 20)));
    }

    @Test
    void shouldRefuseARequestThatChangesNothing() {
        TimeSlot slot = slot(day, 18, 20, 1);
        UUID booking = book(client, slot);

        assertThrows(IllegalArgumentException.class,
            () -> rescheduleService.create(client.getId(), slotRequest(booking, day, 18, 20)));
    }

    @Test
    void shouldRefuseANewTermInThePast() {
        TimeSlot slot = slot(day, 18, 20, 1);
        UUID booking = book(client, slot);

        assertThrows(IllegalArgumentException.class,
            () -> rescheduleService.create(client.getId(), slotRequest(booking, LocalDate.now().minusDays(1), 18, 20)));
    }

    // ---- Accepting ----

    @Test
    void shouldMoveTheWholeSlotAndMailTheRequesterOnlyTheAcceptance() {
        TimeSlot slot = slot(day, 18, 20, 4);
        UUID booking = book(client, slot);
        book(other, slot);
        UUID requestId = rescheduleService.create(client.getId(), slotRequest(booking, day.plusDays(2), 9, 11)).id();
        flushAndClear();

        int notifiedOthers = admin.accept(coach.getId(), requestId);
        flushAndClear();

        TimeSlot moved = timeSlotRepository.findById(slot.getId()).orElseThrow();
        assertEquals(day.plusDays(2), moved.getDate());
        assertEquals(LocalTime.of(9, 0), moved.getStartTime());
        assertEquals(LocalTime.of(11, 0), moved.getEndTime());
        assertEquals(2, reservationRepository.countConfirmedByTimeSlotId(slot.getId()), "both bookings came along");
        assertEquals(RescheduleRequestStatus.ACCEPTED, requestRepository.findById(requestId).orElseThrow().getStatus());

        assertEquals(1, notifiedOthers);
        verify(rescheduleMailService).sendAccepted(argThat(u -> u.getId().equals(client.getId())), any(), isNull(),
            argThat(t -> t.date().equals(day)), argThat(t -> t.date().equals(day.plusDays(2))));
        verify(mailService).sendAdminSlotModificationNotification(argThat(u -> u.getId().equals(other.getId())), any(), anyList(), any());
        verify(mailService, never()).sendAdminSlotModificationNotification(argThat(u -> u.getId().equals(client.getId())), any(), anyList(), any());
    }

    @Test
    void shouldRefuseToAcceptWhenTheCoachMovedTheTermSinceTheRequest() {
        TimeSlot slot = slot(day, 18, 20, 1);
        UUID booking = book(client, slot);
        UUID requestId = rescheduleService.create(client.getId(), slotRequest(booking, day.plusDays(2), 18, 20)).id();
        flushAndClear();
        TimeSlot live = timeSlotRepository.findById(slot.getId()).orElseThrow();
        live.setStartTime(LocalTime.of(17, 0));
        flushAndClear();

        assertThrows(IllegalStateException.class, () -> admin.accept(coach.getId(), requestId));
        verify(rescheduleMailService, never()).sendAccepted(any(), any(), any(), any(), any());
    }

    @Test
    void shouldMoveAMultiDayEventKeepingEveryBooking() {
        Event course = eventRepository.save(new Event("Kurs", EventType.COURSE, day, day.plusDays(1), 4));
        TimeSlot first = timeSlotRepository.save(TimeSlot.forEventDay(course, day));
        TimeSlot second = timeSlotRepository.save(TimeSlot.forEventDay(course, day.plusDays(1)));
        book(client, first);
        book(client, second);
        book(other, first);
        book(other, second);
        flushAndClear();

        UUID requestId = rescheduleService.create(client.getId(), new CreateRescheduleRequest(
            null, course.getId(), day.plusDays(7), day.plusDays(8), null, null, "Tydzień później?")).id();
        flushAndClear();

        admin.accept(coach.getId(), requestId);
        flushAndClear();

        Event moved = eventRepository.findById(course.getId()).orElseThrow();
        assertEquals(day.plusDays(7), moved.getStartDate());
        assertEquals(day.plusDays(8), moved.getEndDate());
        List<TimeSlot> days = timeSlotRepository.findByEventId(course.getId());
        assertTrue(days.stream().allMatch(s -> !s.getDate().isBefore(day.plusDays(7))), "day-slots follow the dates");
        assertEquals(List.of(), days.stream().filter(s -> reservationRepository.countConfirmedByTimeSlotId(s.getId()) != 2).toList(),
            "both people keep both days");
        assertEquals(RescheduleRequestStatus.ACCEPTED, requestRepository.findById(requestId).orElseThrow().getStatus());
        verify(mailService, never()).sendAdminEventModificationNotification(argThat(u -> u.getId().equals(client.getId())), any(), anyList());
        verify(mailService).sendAdminEventModificationNotification(argThat(u -> u.getId().equals(other.getId())), any(), anyList());
    }

    // ---- Rejecting and closing ----

    @Test
    void shouldRejectWithANoteAndLeaveTheTermAlone() {
        TimeSlot slot = slot(day, 18, 20, 1);
        UUID booking = book(client, slot);
        UUID requestId = rescheduleService.create(client.getId(), slotRequest(booking, day.plusDays(2), 18, 20)).id();
        flushAndClear();

        admin.reject(coach.getId(), requestId, "  Czwartek mam zajęty  ", true);
        flushAndClear();

        RescheduleRequest r = requestRepository.findById(requestId).orElseThrow();
        assertEquals(RescheduleRequestStatus.REJECTED, r.getStatus());
        assertEquals("Czwartek mam zajęty", r.getAdminNote());
        assertEquals(day, timeSlotRepository.findById(slot.getId()).orElseThrow().getDate());
        verify(rescheduleMailService).sendRejected(any(), any(), any(), any(), eq("Czwartek mam zajęty"));
    }

    @Test
    void shouldKeepTheEventRequestWhenTheCoachRemovesOnlyOneOfItsDays() {
        Event course = eventRepository.save(new Event("Kurs", EventType.COURSE, day, day.plusDays(1), 4));
        TimeSlot first = timeSlotRepository.save(TimeSlot.forEventDay(course, day));
        TimeSlot second = timeSlotRepository.save(TimeSlot.forEventDay(course, day.plusDays(1)));
        UUID firstDay = book(client, first);
        book(client, second);
        flushAndClear();
        UUID requestId = rescheduleService.create(client.getId(), new CreateRescheduleRequest(
            null, course.getId(), day.plusDays(7), day.plusDays(8), null, null, null)).id();
        flushAndClear();

        // One day gone, the other still booked — the request about the whole course still stands.
        adminService.cancelReservationByAdmin(firstDay);
        flushAndClear();

        assertEquals(RescheduleRequestStatus.PENDING, requestRepository.findById(requestId).orElseThrow().getStatus());
    }

    @Test
    void shouldWithdrawTheRequestWhenTheBookingIsCancelled() {
        TimeSlot slot = slot(day, 18, 20, 1);
        UUID booking = book(client, slot);
        UUID requestId = rescheduleService.create(client.getId(), slotRequest(booking, day.plusDays(2), 18, 20)).id();
        flushAndClear();

        reservationService.cancelReservation(booking, client.getId());
        flushAndClear();

        assertEquals(RescheduleRequestStatus.WITHDRAWN, requestRepository.findById(requestId).orElseThrow().getStatus());
    }

    @Test
    void shouldExpireRequestsWhoseOriginalTermIsOver() {
        TimeSlot slot = slot(day, 18, 20, 1);
        UUID booking = book(client, slot);
        UUID requestId = rescheduleService.create(client.getId(), slotRequest(booking, day.plusDays(2), 18, 20)).id();
        flushAndClear();

        int expired = requestRepository.expireOverdue(day.plusDays(1), LocalTime.NOON, Instant.now());

        assertEquals(1, expired);
        assertEquals(RescheduleRequestStatus.EXPIRED, requestRepository.findById(requestId).orElseThrow().getStatus());
    }

    @Test
    void shouldOnlyLetTheRequesterWithdrawIt() {
        TimeSlot slot = slot(day, 18, 20, 1);
        UUID booking = book(client, slot);
        UUID requestId = rescheduleService.create(client.getId(), slotRequest(booking, day.plusDays(2), 18, 20)).id();

        assertThrows(IllegalArgumentException.class, () -> rescheduleService.withdraw(other.getId(), requestId));
        rescheduleService.withdraw(client.getId(), requestId);
        assertTrue(rescheduleService.getMyPending(client.getId()).isEmpty());
    }

    // ---- Fixtures ----

    private static CreateRescheduleRequest slotRequest(UUID reservationId, LocalDate date, int from, int to) {
        return new CreateRescheduleRequest(reservationId, null, date, null,
            LocalTime.of(from, 0), LocalTime.of(to, 0), null);
    }

    private TimeSlot slot(LocalDate date, int from, int to, int seats) {
        return timeSlotRepository.save(new TimeSlot(date, LocalTime.of(from, 0), LocalTime.of(to, 0), seats));
    }

    private UUID book(User user, TimeSlot slot) {
        return reservationRepository.save(new Reservation(user, slot)).getId();
    }

    private User user(String email, UserRole role) {
        User user = new User(email, "Te", email.substring(3, 6), "+48123123123", email.substring(0, email.indexOf('@')));
        user.setRole(role);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
