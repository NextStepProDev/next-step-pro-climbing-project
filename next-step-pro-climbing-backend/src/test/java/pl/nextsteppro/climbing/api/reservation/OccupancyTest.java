package pl.nextsteppro.climbing.api.reservation;

import org.junit.jupiter.api.Test;
import pl.nextsteppro.climbing.domain.reservation.GuestReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.SlotParticipantCount;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OccupancyTest {

    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final GuestReservationRepository guests = mock(GuestReservationRepository.class);
    private final Occupancy occupancy = new Occupancy(reservations, guests);
    private final UUID eventId = UUID.randomUUID();

    @Test
    void shouldTakeTheFullestDayNotTheSumBecauseEveryoneHoldsEveryDay() {
        TimeSlot day1 = slot();
        TimeSlot day2 = slot();
        when(reservations.countConfirmedByTimeSlotIds(anyList())).thenReturn(List.of(
            new SlotParticipantCount(day1.getId(), 5),
            new SlotParticipantCount(day2.getId(), 3)));

        assertEquals(5, occupancy.ofEvent(eventId, List.of(day1, day2)));
    }

    @Test
    void shouldAddGuestsBecauseNoSlotCountCanSeeThem() {
        TimeSlot day = slot();
        when(reservations.countConfirmedByTimeSlotIds(anyList()))
            .thenReturn(List.of(new SlotParticipantCount(day.getId(), 4)));
        when(guests.sumParticipantsByEventId(eventId)).thenReturn(2);

        assertEquals(6, occupancy.ofEvent(eventId, List.of(day)));
    }

    @Test
    void shouldCountGuestsOnAnEventNobodyRegisteredForYet() {
        // No slots exist until the first registered signup, but a walk-in can already be there.
        when(guests.sumParticipantsByEventId(eventId)).thenReturn(3);

        assertEquals(3, occupancy.ofEvent(eventId, List.of()));
        verify(reservations, never()).countConfirmedByTimeSlotIds(anyList());
    }

    @Test
    void shouldAddSlotGuestsToThatSlotsConfirmedCount() {
        UUID booked = UUID.randomUUID();
        UUID guestsOnly = UUID.randomUUID();
        when(reservations.countConfirmedByTimeSlotIds(anyList()))
            .thenReturn(List.of(new SlotParticipantCount(booked, 3)));
        when(guests.sumParticipantsByTimeSlotIds(anyList())).thenReturn(List.of(
            new SlotParticipantCount(booked, 2),
            new SlotParticipantCount(guestsOnly, 1)));

        assertEquals(java.util.Map.of(booked, 5, guestsOnly, 1), occupancy.perSlot(List.of(booked, guestsOnly)));
    }

    private static TimeSlot slot() {
        TimeSlot slot = new TimeSlot(LocalDate.of(2026, 10, 1), LocalTime.of(10, 0), LocalTime.of(12, 0), 10);
        try {
            Field id = TimeSlot.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(slot, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return slot;
    }
}
