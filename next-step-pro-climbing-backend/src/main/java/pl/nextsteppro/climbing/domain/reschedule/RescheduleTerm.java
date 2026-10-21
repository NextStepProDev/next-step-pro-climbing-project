package pl.nextsteppro.climbing.domain.reschedule;

import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * When a slot or an event takes place, as a plain value that can cross into an {@code @Async} mail.
 *
 * <p>A slot always has hours and one day ({@code endDate == null}). An event always has an end
 * day, and its hours are both set or both null (all day) — the shape {@code events} itself has.
 */
public record RescheduleTerm(
    LocalDate date,
    @Nullable LocalDate endDate,
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime
) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public static RescheduleTerm of(TimeSlot slot) {
        return new RescheduleTerm(slot.getDate(), null, slot.getStartTime(), slot.getEndTime());
    }

    public static RescheduleTerm of(Event event) {
        return new RescheduleTerm(event.getStartDate(), event.getEndDate(), event.getStartTime(), event.getEndTime());
    }

    /** The last day of the term — the one that decides whether it is over. */
    public LocalDate lastDay() {
        return endDate != null ? endDate : date;
    }

    /**
     * "21.10.2026, 18:00 – 20:00", "21.10.2026 – 23.10.2026, 09:00 – 17:00" or a bare date range —
     * language-neutral, so one string serves the mails in every language and the activity log.
     */
    public String label() {
        StringBuilder sb = new StringBuilder(date.format(DATE));
        if (endDate != null && !endDate.equals(date)) {
            sb.append(" – ").append(endDate.format(DATE));
        }
        if (startTime != null && endTime != null) {
            sb.append(", ").append(startTime.format(TIME)).append(" – ").append(endTime.format(TIME));
        }
        return sb.toString();
    }

    public boolean sameAs(RescheduleTerm other) {
        return date.equals(other.date)
            && Objects.equals(endDate, other.endDate)
            && Objects.equals(startTime, other.startTime)
            && Objects.equals(endTime, other.endTime);
    }
}
