package pl.nextsteppro.climbing.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Event Entity Tests")
class EventTest {

    @Test
    @DisplayName("should create event with required fields")
    void shouldCreateEventWithRequiredFields() {
        Event event = new Event(
            "Test Course",
            EventType.COURSE,
            LocalDate.of(2026, 2, 14),
            LocalDate.of(2026, 2, 15),
            4
        );

        assertEquals("Test Course", event.getTitle());
        assertNull(event.getDescription());
        assertEquals(EventType.COURSE, event.getEventType());
        assertEquals(LocalDate.of(2026, 2, 14), event.getStartDate());
        assertEquals(LocalDate.of(2026, 2, 15), event.getEndDate());
        assertEquals(4, event.getMaxParticipants());
        assertTrue(event.isActive());
    }

    @Test
    @DisplayName("should deactivate event")
    void shouldDeactivateEvent() {
        Event event = new Event(
            "Test Course",
            EventType.COURSE,
            LocalDate.of(2026, 2, 14),
            LocalDate.of(2026, 2, 15),
            4
        );

        event.setActive(false);

        assertFalse(event.isActive());
    }

    @Test
    @DisplayName("should check if event is multi-day")
    void shouldCheckIfEventIsMultiDay() {
        Event multiDayEvent = new Event(
            "Multi-day Course",
            EventType.COURSE,
            LocalDate.of(2026, 2, 14),
            LocalDate.of(2026, 2, 16),
            4
        );

        Event singleDayEvent = new Event(
            "Single-day Workshop",
            EventType.WORKSHOP,
            LocalDate.of(2026, 2, 14),
            LocalDate.of(2026, 2, 14),
            4
        );

        assertTrue(multiDayEvent.isMultiDay());
        assertFalse(singleDayEvent.isMultiDay());
    }

    @Test
    @DisplayName("should support all event types")
    void shouldSupportAllEventTypes() {
        assertNotNull(EventType.COURSE);
        assertNotNull(EventType.TRAINING);
        assertNotNull(EventType.WORKSHOP);
    }
}
