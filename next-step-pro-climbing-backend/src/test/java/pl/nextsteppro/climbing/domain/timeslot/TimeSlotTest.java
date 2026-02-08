package pl.nextsteppro.climbing.domain.timeslot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TimeSlot Entity Tests")
class TimeSlotTest {

    @Test
    @DisplayName("should create time slot with required fields")
    void shouldCreateTimeSlotWithRequiredFields() {
        LocalDate date = LocalDate.of(2026, 2, 14);
        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(12, 0);

        TimeSlot slot = new TimeSlot(date, startTime, endTime, 4);

        assertEquals(date, slot.getDate());
        assertEquals(startTime, slot.getStartTime());
        assertEquals(endTime, slot.getEndTime());
        assertEquals(4, slot.getMaxParticipants());
        assertFalse(slot.isBlocked());
        assertNull(slot.getBlockReason());
    }

    @Test
    @DisplayName("should block time slot with reason")
    void shouldBlockTimeSlotWithReason() {
        TimeSlot slot = new TimeSlot(
            LocalDate.of(2026, 2, 14),
            LocalTime.of(10, 0),
            LocalTime.of(12, 0),
            4
        );

        slot.block("Maintenance");

        assertTrue(slot.isBlocked());
        assertEquals("Maintenance", slot.getBlockReason());
    }

    @Test
    @DisplayName("should unblock time slot")
    void shouldUnblockTimeSlot() {
        TimeSlot slot = new TimeSlot(
            LocalDate.of(2026, 2, 14),
            LocalTime.of(10, 0),
            LocalTime.of(12, 0),
            4
        );
        slot.block("Maintenance");

        slot.unblock();

        assertFalse(slot.isBlocked());
        assertNull(slot.getBlockReason());
    }

    @Test
    @DisplayName("should block time slot without reason")
    void shouldBlockTimeSlotWithoutReason() {
        TimeSlot slot = new TimeSlot(
            LocalDate.of(2026, 2, 14),
            LocalTime.of(10, 0),
            LocalTime.of(12, 0),
            4
        );

        slot.block(null);

        assertTrue(slot.isBlocked());
        assertNull(slot.getBlockReason());
    }
}
