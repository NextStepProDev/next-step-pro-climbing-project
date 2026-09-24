package pl.nextsteppro.climbing.domain.trainingrequest;

import org.junit.jupiter.api.Test;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The one rule behind every "not the hours you asked for" the client sees: the entry answering a
 * proposal is compared against what they proposed, live.
 */
class TrainingRequestAgreedTermTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);

    private static TrainingRequest proposal() {
        User user = new User("client@example.com", "Ala", "Nowak", "+48111222333", "ala");
        return new TrainingRequest(user, DAY, LocalTime.of(17, 0), LocalTime.of(19, 0), 2);
    }

    @Test
    void shouldNotDifferWhileNothingWasCreated() {
        TrainingRequest tr = proposal();
        assertNull(tr.agreedTerm());
        assertFalse(tr.agreedTermDiffers());
    }

    @Test
    void shouldNotDifferWhenTheSlotKeepsTheProposedHours() {
        TrainingRequest tr = proposal();
        tr.setCreatedSlot(new TimeSlot(DAY, LocalTime.of(17, 0), LocalTime.of(19, 0), 2));
        assertFalse(tr.agreedTermDiffers());
    }

    @Test
    void shouldDifferWhenTheSlotMovesByAnHour() {
        TrainingRequest tr = proposal();
        tr.setCreatedSlot(new TimeSlot(DAY, LocalTime.of(18, 0), LocalTime.of(20, 0), 2));
        assertTrue(tr.agreedTermDiffers());
        assertEquals(new AgreedTerm(DAY, null, LocalTime.of(18, 0), LocalTime.of(20, 0)), tr.agreedTerm());
    }

    @Test
    void shouldDifferWhenOnlyTheEndMoves() {
        TrainingRequest tr = proposal();
        tr.setCreatedSlot(new TimeSlot(DAY, LocalTime.of(17, 0), LocalTime.of(18, 30), 2));
        assertTrue(tr.agreedTermDiffers());
    }

    @Test
    void shouldDifferWhenTheSlotLandsOnAnotherDay() {
        TrainingRequest tr = proposal();
        tr.setCreatedSlot(new TimeSlot(DAY.plusDays(1), LocalTime.of(17, 0), LocalTime.of(19, 0), 2));
        assertTrue(tr.agreedTermDiffers());
    }

    @Test
    void shouldNotDifferForAOneDayEventAtTheProposedHours() {
        TrainingRequest tr = proposal();
        Event event = new Event("Kurs", EventType.COURSE, DAY, DAY, 4);
        event.setStartTime(LocalTime.of(17, 0));
        event.setEndTime(LocalTime.of(19, 0));
        tr.setCreatedEvent(event);
        assertFalse(tr.agreedTermDiffers());
    }

    @Test
    void shouldDifferForAnAllDayEvent() {
        TrainingRequest tr = proposal();
        tr.setCreatedEvent(new Event("Kurs", EventType.COURSE, DAY, DAY, 4));
        assertTrue(tr.agreedTermDiffers());
    }

    @Test
    void shouldDifferForAnEventSpanningDaysEvenAtTheProposedHours() {
        TrainingRequest tr = proposal();
        Event event = new Event("Kurs", EventType.COURSE, DAY, DAY.plusDays(1), 4);
        event.setStartTime(LocalTime.of(17, 0));
        event.setEndTime(LocalTime.of(19, 0));
        tr.setCreatedEvent(event);
        assertTrue(tr.agreedTermDiffers());
        assertEquals(DAY.plusDays(1), tr.agreedTerm().endDate());
    }
}
