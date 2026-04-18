package pl.nextsteppro.climbing.api.calendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventRepository;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reservation.GuestReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.SlotParticipantCount;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlistRepository;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for CalendarService - the core calendar view logic.
 *
 * Tests cover:
 * - Month/Week/Day view generation with/without user context
 * - Slot status determination (AVAILABLE, FULL, BLOCKED, PAST, BOOKING_CLOSED)
 * - 12-hour booking cutoff window enforcement
 * - Batch loading optimization (N+1 query prevention)
 * - Event data computation with user enrollment tracking
 * - Edge cases (empty results, past dates, blocked slots)
 */
@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private GuestReservationRepository guestReservationRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private WaitlistRepository waitlistRepository;

    @Mock
    private EventWaitlistRepository eventWaitlistRepository;

    private CalendarService calendarService;

    private UUID testUserId;
    private TimeSlot testSlot;
    private Event testEvent;
    private User testUser;

    @BeforeEach
    void setUp() {
        calendarService = new CalendarService(timeSlotRepository, reservationRepository, guestReservationRepository, eventRepository, waitlistRepository, eventWaitlistRepository);
        testUserId = UUID.randomUUID();

        // Setup test user
        testUser = new User("test@example.com", "Test", "User", "+48123456789", "testuser");
        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(testUser, testUserId);

            var createdAtField = User.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(testUser, Instant.now());

            var updatedAtField = User.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(testUser, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup test user", e);
        }
        testUser.setRole(UserRole.USER);

        // Setup test slot (tomorrow at 10:00-12:00, far from booking cutoff)
        testSlot = new TimeSlot(
                LocalDate.now().plusDays(2),
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                10
        );
        try {
            var idField = TimeSlot.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(testSlot, UUID.randomUUID());

            var createdAtField = TimeSlot.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(testSlot, Instant.now());

            var updatedAtField = TimeSlot.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(testSlot, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup test slot", e);
        }

        // Setup test event
        testEvent = new Event(
                "Test Course",
                EventType.COURSE,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(7),
                20
        );
        testEvent.setDescription("Test description");
        testEvent.setLocation("Climbing Wall");
        try {
            var idField = Event.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(testEvent, UUID.randomUUID());

            var createdAtField = Event.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(testEvent, Instant.now());

            var updatedAtField = Event.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(testEvent, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup test event", e);
        }
    }

    // ========== MONTH VIEW TESTS ==========

    @Test
    void shouldGetMonthViewWithoutUser() {
        // Given
        YearMonth yearMonth = YearMonth.now();
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        when(timeSlotRepository.findByDateRangeOrdered(eq(startDate), eq(endDate)))
                .thenReturn(List.of(testSlot));
        when(eventRepository.findActiveEventsBetween(eq(startDate), eq(endDate)))
                .thenReturn(List.of(testEvent));

        SlotParticipantCount mockCount = mock(SlotParticipantCount.class);
        when(mockCount.slotId()).thenReturn(testSlot.getId());
        when(mockCount.countAsInt()).thenReturn(5);
        when(reservationRepository.countConfirmedByTimeSlotIds(any()))
                .thenReturn(List.of(mockCount));

        // When
        MonthViewDto result = calendarService.getMonthView(yearMonth, null);

        // Then
        assertNotNull(result);
        assertEquals(yearMonth.toString(), result.yearMonth());
        assertFalse(result.days().isEmpty());
        assertEquals(1, result.events().size());

        // Verify batch loading (no N+1 queries)
        verify(timeSlotRepository, times(1)).findByDateRangeOrdered(any(), any());
        verify(reservationRepository, times(1)).countConfirmedByTimeSlotIds(any());
    }

    @Test
    void shouldGetMonthViewWithUser() {
        // Given
        YearMonth yearMonth = YearMonth.now();
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        when(timeSlotRepository.findByDateRangeOrdered(eq(startDate), eq(endDate)))
                .thenReturn(List.of(testSlot));
        when(eventRepository.findActiveEventsBetween(eq(startDate), eq(endDate)))
                .thenReturn(List.of(testEvent));

        SlotParticipantCount mockCount = mock(SlotParticipantCount.class);
        when(mockCount.slotId()).thenReturn(testSlot.getId());
        when(mockCount.countAsInt()).thenReturn(5);
        when(reservationRepository.countConfirmedByTimeSlotIds(any()))
                .thenReturn(List.of(mockCount));

        when(reservationRepository.findUserConfirmedSlotIds(eq(testUserId), any()))
                .thenReturn(List.of());

        // When
        MonthViewDto result = calendarService.getMonthView(yearMonth, testUserId);

        // Then
        assertNotNull(result);
        assertEquals(yearMonth.toString(), result.yearMonth());

        // Verify user-specific queries called (once for slots, possibly again for events)
        verify(reservationRepository, atLeastOnce()).findUserConfirmedSlotIds(eq(testUserId), any());
    }

    @Test
    void shouldReturnEmptyMonthViewWhenNoSlots() {
        // Given
        YearMonth yearMonth = YearMonth.now();
        when(timeSlotRepository.findByDateRangeOrdered(any(), any())).thenReturn(List.of());
        when(eventRepository.findActiveEventsBetween(any(), any())).thenReturn(List.of());

        // When
        MonthViewDto result = calendarService.getMonthView(yearMonth, null);

        // Then
        assertNotNull(result);
        assertEquals(yearMonth.toString(), result.yearMonth());
        assertFalse(result.days().isEmpty()); // Days list contains all days of month
        assertTrue(result.events().isEmpty());
    }

    // ========== WEEK VIEW TESTS ==========

    @Test
    void shouldGetWeekViewWithStandaloneSlotsOnly() {
        // Given
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(6);

        // Standalone slot (no event)
        TimeSlot standaloneSlot = new TimeSlot(
                startDate.plusDays(1),
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                5
        );
        try {
            var idField = TimeSlot.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(standaloneSlot, UUID.randomUUID());

            var createdAtField = TimeSlot.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(standaloneSlot, Instant.now());

            var updatedAtField = TimeSlot.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(standaloneSlot, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(timeSlotRepository.findByDateRangeOrdered(eq(startDate), eq(endDate)))
                .thenReturn(List.of(standaloneSlot));
        when(eventRepository.findActiveEventsBetween(eq(startDate), eq(endDate)))
                .thenReturn(List.of());

        SlotParticipantCount mockCount = mock(SlotParticipantCount.class);
        when(mockCount.slotId()).thenReturn(standaloneSlot.getId());
        when(mockCount.countAsInt()).thenReturn(2);
        when(reservationRepository.countConfirmedByTimeSlotIds(any()))
                .thenReturn(List.of(mockCount));

        // When
        WeekViewDto result = calendarService.getWeekView(startDate, null);

        // Then
        assertNotNull(result);
        assertEquals(startDate, result.startDate());
        assertEquals(endDate, result.endDate());
        assertEquals(7, result.days().size());

        // Verify batch loading
        verify(reservationRepository, times(1)).countConfirmedByTimeSlotIds(any());
    }

    @Test
    void shouldGetWeekViewWithUserContext() {
        // Given
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(6);

        when(timeSlotRepository.findByDateRangeOrdered(eq(startDate), eq(endDate)))
                .thenReturn(List.of(testSlot));
        when(eventRepository.findActiveEventsBetween(eq(startDate), eq(endDate)))
                .thenReturn(List.of());

        SlotParticipantCount mockCount = mock(SlotParticipantCount.class);
        when(mockCount.slotId()).thenReturn(testSlot.getId());
        when(mockCount.countAsInt()).thenReturn(5);
        when(reservationRepository.countConfirmedByTimeSlotIds(any()))
                .thenReturn(List.of(mockCount));

        when(reservationRepository.findUserConfirmedSlotIds(eq(testUserId), any()))
                .thenReturn(List.of(testSlot.getId()));

        // When
        WeekViewDto result = calendarService.getWeekView(startDate, testUserId);

        // Then
        assertNotNull(result);
        assertEquals(7, result.days().size());

        // Verify user registration status tracked
        verify(reservationRepository).findUserConfirmedSlotIds(eq(testUserId), any());
    }

    // ========== DAY VIEW TESTS ==========

    @Test
    void shouldGetDayViewWithSlotsAndEvents() {
        // Given
        LocalDate date = LocalDate.now().plusDays(1);
        testSlot.setEvent(testEvent);

        when(timeSlotRepository.findByDateSorted(eq(date)))
                .thenReturn(List.of(testSlot));
        when(eventRepository.findActiveEventsOnDate(eq(date)))
                .thenReturn(List.of(testEvent));

        SlotParticipantCount mockCount = mock(SlotParticipantCount.class);
        when(mockCount.slotId()).thenReturn(testSlot.getId());
        when(mockCount.countAsInt()).thenReturn(5);
        when(reservationRepository.countConfirmedByTimeSlotIds(any()))
                .thenReturn(List.of(mockCount));

        when(timeSlotRepository.findByEventIdIn(any()))
                .thenReturn(List.of(testSlot));

        // When
        DayViewDto result = calendarService.getDayView(date, null);

        // Then
        assertNotNull(result);
        assertEquals(date, result.date());
        assertTrue(result.slots().isEmpty()); // Slot belongs to event, so filtered out
        assertFalse(result.events().isEmpty());
    }

    @Test
    void shouldGetDayViewWithOnlyStandaloneSlots() {
        // Given
        LocalDate date = LocalDate.now().plusDays(2);

        when(timeSlotRepository.findByDateSorted(eq(date)))
                .thenReturn(List.of(testSlot));
        when(eventRepository.findActiveEventsOnDate(eq(date)))
                .thenReturn(List.of());

        SlotParticipantCount mockCount = mock(SlotParticipantCount.class);
        when(mockCount.slotId()).thenReturn(testSlot.getId());
        when(mockCount.countAsInt()).thenReturn(5);
        when(reservationRepository.countConfirmedByTimeSlotIds(any()))
                .thenReturn(List.of(mockCount));

        // When
        DayViewDto result = calendarService.getDayView(date, null);

        // Then
        assertNotNull(result);
        assertEquals(date, result.date());
        assertFalse(result.slots().isEmpty());
        assertTrue(result.events().isEmpty());
    }

    // ========== EVENT SUMMARY TESTS ==========

    @Test
    void shouldGetEventSummaryWithoutUser() throws Exception {
        // Given
        UUID eventId = testEvent.getId();
        when(eventRepository.findById(eq(eventId))).thenReturn(Optional.of(testEvent));
        when(timeSlotRepository.findByEventIdIn(any())).thenReturn(List.of());

        // When
        EventSummaryDto result = calendarService.getEventSummary(eventId, null);

        // Then
        assertNotNull(result);
        assertEquals(eventId, result.id());
        assertEquals(testEvent.getTitle(), result.title());
        assertEquals(0, result.currentParticipants());
        assertFalse(result.isUserRegistered());
    }

    @Test
    void shouldGetEventSummaryWithUserEnrolled() throws Exception {
        // Given
        UUID eventId = testEvent.getId();
        testSlot.setEvent(testEvent);

        when(eventRepository.findById(eq(eventId))).thenReturn(Optional.of(testEvent));
        when(timeSlotRepository.findByEventIdIn(any())).thenReturn(List.of(testSlot));

        SlotParticipantCount mockCount = mock(SlotParticipantCount.class);
        when(mockCount.slotId()).thenReturn(testSlot.getId());
        when(mockCount.countAsInt()).thenReturn(5);
        when(reservationRepository.countConfirmedByTimeSlotIds(any()))
                .thenReturn(List.of(mockCount));

        when(reservationRepository.findUserConfirmedSlotIds(eq(testUserId), any()))
                .thenReturn(List.of(testSlot.getId()));

        // When
        EventSummaryDto result = calendarService.getEventSummary(eventId, testUserId);

        // Then
        assertNotNull(result);
        assertTrue(result.isUserRegistered());
        assertEquals(5, result.currentParticipants());
    }

    @Test
    void shouldThrowExceptionWhenEventNotFound() {
        // Given
        UUID nonExistentEventId = UUID.randomUUID();
        when(eventRepository.findById(eq(nonExistentEventId))).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
                calendarService.getEventSummary(nonExistentEventId, null)
        );
    }

    // ========== SLOT DETAILS TESTS ==========

    @Test
    void shouldGetSlotDetailsWithoutUser() throws Exception {
        // Given
        UUID slotId = testSlot.getId();
        when(timeSlotRepository.findById(eq(slotId))).thenReturn(Optional.of(testSlot));
        when(reservationRepository.countConfirmedByTimeSlotId(eq(slotId))).thenReturn(5);

        // When
        TimeSlotDetailDto result = calendarService.getSlotDetails(slotId, null);

        // Then
        assertNotNull(result);
        assertEquals(slotId, result.id());
        assertEquals(5, result.currentParticipants());
        assertFalse(result.isUserRegistered());
        assertNull(result.reservationId());
    }

    @Test
    void shouldGetSlotDetailsWithUserRegistered() throws Exception {
        // Given
        UUID slotId = testSlot.getId();
        Reservation testReservation = new Reservation(testUser, testSlot);
        try {
            var idField = Reservation.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(testReservation, UUID.randomUUID());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(timeSlotRepository.findById(eq(slotId))).thenReturn(Optional.of(testSlot));
        when(reservationRepository.countConfirmedByTimeSlotId(eq(slotId))).thenReturn(5);
        when(reservationRepository.findByUserIdAndTimeSlotId(eq(testUserId), eq(slotId)))
                .thenReturn(testReservation);

        // When
        TimeSlotDetailDto result = calendarService.getSlotDetails(slotId, testUserId);

        // Then
        assertNotNull(result);
        assertTrue(result.isUserRegistered());
        assertNotNull(result.reservationId());
        assertEquals(testReservation.getId(), result.reservationId());
    }

    @Test
    void shouldThrowExceptionWhenSlotNotFound() {
        // Given
        UUID nonExistentSlotId = UUID.randomUUID();
        when(timeSlotRepository.findById(eq(nonExistentSlotId))).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
                calendarService.getSlotDetails(nonExistentSlotId, null)
        );
    }

    // ========== SLOT STATUS DETERMINATION TESTS ==========

    @Test
    void shouldDetermineStatusAsBlocked() {
        // Given
        testSlot.block("Maintenance");

        SlotParticipantCount mockCount = mock(SlotParticipantCount.class);
        when(mockCount.slotId()).thenReturn(testSlot.getId());
        when(mockCount.countAsInt()).thenReturn(0);

        when(timeSlotRepository.findByDateSorted(any())).thenReturn(List.of(testSlot));
        when(eventRepository.findActiveEventsOnDate(any())).thenReturn(List.of());
        when(reservationRepository.countConfirmedByTimeSlotIds(any()))
                .thenReturn(List.of(mockCount));

        // When
        DayViewDto result = calendarService.getDayView(testSlot.getDate(), null);

        // Then
        assertNotNull(result);
        TimeSlotDto slot = result.slots().get(0);
        assertEquals(SlotStatus.BLOCKED, slot.status());
    }

    @Test
    void shouldDetermineStatusAsFull() {
        // Given
        SlotParticipantCount mockCount = mock(SlotParticipantCount.class);
        when(mockCount.slotId()).thenReturn(testSlot.getId());
        when(mockCount.countAsInt()).thenReturn(10); // maxParticipants = 10

        when(timeSlotRepository.findByDateSorted(any())).thenReturn(List.of(testSlot));
        when(eventRepository.findActiveEventsOnDate(any())).thenReturn(List.of());
        when(reservationRepository.countConfirmedByTimeSlotIds(any()))
                .thenReturn(List.of(mockCount));

        // When
        DayViewDto result = calendarService.getDayView(testSlot.getDate(), null);

        // Then
        TimeSlotDto slot = result.slots().get(0);
        assertEquals(SlotStatus.FULL, slot.status());
    }

    @Test
    void shouldDetermineStatusAsPast() {
        // Given: slot in the past
        TimeSlot pastSlot = new TimeSlot(
                LocalDate.now().minusDays(1),
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                10
        );
        try {
            var idField = TimeSlot.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(pastSlot, UUID.randomUUID());

            var createdAtField = TimeSlot.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(pastSlot, Instant.now());

            var updatedAtField = TimeSlot.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(pastSlot, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        SlotParticipantCount mockCount = mock(SlotParticipantCount.class);
        when(mockCount.slotId()).thenReturn(pastSlot.getId());
        when(mockCount.countAsInt()).thenReturn(5);

        when(timeSlotRepository.findByDateSorted(eq(pastSlot.getDate()))).thenReturn(List.of(pastSlot));
        when(eventRepository.findActiveEventsOnDate(any())).thenReturn(List.of());
        when(reservationRepository.countConfirmedByTimeSlotIds(any()))
                .thenReturn(List.of(mockCount));

        // When
        DayViewDto result = calendarService.getDayView(pastSlot.getDate(), null);

        // Then
        TimeSlotDto slot = result.slots().get(0);
        assertEquals(SlotStatus.PAST, slot.status());
    }

    @Test
    void shouldDetermineStatusAsBookingClosed() {
        // Given: slot within 12-hour cutoff window (6 hours from now)
        LocalDateTime targetStart = LocalDateTime.now().plusHours(6);
        LocalDateTime targetEnd = targetStart.plusHours(2);

        TimeSlot nearFutureSlot = new TimeSlot(
                targetStart.toLocalDate(),
                targetStart.toLocalTime(),
                targetEnd.toLocalTime(),
                10
        );
        try {
            var idField = TimeSlot.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(nearFutureSlot, UUID.randomUUID());

            var createdAtField = TimeSlot.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(nearFutureSlot, Instant.now());

            var updatedAtField = TimeSlot.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(nearFutureSlot, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        SlotParticipantCount mockCount = mock(SlotParticipantCount.class);
        when(mockCount.slotId()).thenReturn(nearFutureSlot.getId());
        when(mockCount.countAsInt()).thenReturn(5);

        when(timeSlotRepository.findByDateSorted(eq(nearFutureSlot.getDate()))).thenReturn(List.of(nearFutureSlot));
        when(eventRepository.findActiveEventsOnDate(any())).thenReturn(List.of());
        when(reservationRepository.countConfirmedByTimeSlotIds(any()))
                .thenReturn(List.of(mockCount));

        // When
        DayViewDto result = calendarService.getDayView(nearFutureSlot.getDate(), null);

        // Then
        TimeSlotDto slot = result.slots().get(0);
        assertEquals(SlotStatus.BOOKING_CLOSED, slot.status());
    }

    @Test
    void shouldDetermineStatusAsAvailable() {
        // Given: future slot with available capacity (testSlot is 2 days ahead, > 12h cutoff)
        SlotParticipantCount mockCount = mock(SlotParticipantCount.class);
        when(mockCount.slotId()).thenReturn(testSlot.getId());
        when(mockCount.countAsInt()).thenReturn(5); // < maxParticipants (10)

        when(timeSlotRepository.findByDateSorted(eq(testSlot.getDate()))).thenReturn(List.of(testSlot));
        when(eventRepository.findActiveEventsOnDate(any())).thenReturn(List.of());
        when(reservationRepository.countConfirmedByTimeSlotIds(any()))
                .thenReturn(List.of(mockCount));

        // When
        DayViewDto result = calendarService.getDayView(testSlot.getDate(), null);

        // Then
        TimeSlotDto slot = result.slots().get(0);
        assertEquals(SlotStatus.AVAILABLE, slot.status());
    }

    // ========== BATCH LOADING OPTIMIZATION TESTS ==========

    @Test
    void shouldUseBatchLoadingForMultipleSlots() {
        // Given: Multiple slots on same day
        TimeSlot slot2 = new TimeSlot(
                testSlot.getDate(),
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                10
        );
        try {
            var idField = TimeSlot.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(slot2, UUID.randomUUID());

            var createdAtField = TimeSlot.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(slot2, Instant.now());

            var updatedAtField = TimeSlot.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(slot2, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(timeSlotRepository.findByDateSorted(any())).thenReturn(List.of(testSlot, slot2));
        when(eventRepository.findActiveEventsOnDate(any())).thenReturn(List.of());

        SlotParticipantCount mockCount1 = mock(SlotParticipantCount.class);
        when(mockCount1.slotId()).thenReturn(testSlot.getId());
        when(mockCount1.countAsInt()).thenReturn(5);

        SlotParticipantCount mockCount2 = mock(SlotParticipantCount.class);
        when(mockCount2.slotId()).thenReturn(slot2.getId());
        when(mockCount2.countAsInt()).thenReturn(3);

        when(reservationRepository.countConfirmedByTimeSlotIds(any()))
                .thenReturn(List.of(mockCount1, mockCount2));

        // When
        DayViewDto result = calendarService.getDayView(testSlot.getDate(), null);

        // Then
        assertNotNull(result);
        assertEquals(2, result.slots().size());

        // CRITICAL: Verify only ONE batch query (not N+1)
        verify(reservationRepository, times(1)).countConfirmedByTimeSlotIds(any());
    }

    @Test
    void shouldHandleEmptySlotList() {
        // Given
        LocalDate date = LocalDate.now().plusDays(10);
        when(timeSlotRepository.findByDateSorted(eq(date))).thenReturn(List.of());
        when(eventRepository.findActiveEventsOnDate(eq(date))).thenReturn(List.of());

        // When
        DayViewDto result = calendarService.getDayView(date, null);

        // Then
        assertNotNull(result);
        assertTrue(result.slots().isEmpty());
        assertTrue(result.events().isEmpty());

        // Verify no unnecessary repository calls
        verify(reservationRepository, never()).countConfirmedByTimeSlotIds(any());
    }
}
