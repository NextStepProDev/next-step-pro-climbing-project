package pl.nextsteppro.climbing.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

public final class BookingTimeValidator {

    private static final int BOOKING_WINDOW_HOURS = 12;
    // Slot times are stored as Polish local time; we compare against Polish time, NOT the JVM zone
    // (the prod container runs in UTC — a bare LocalDateTime.now() would skew the 12 h window by 2 h in summer).
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private BookingTimeValidator() {}

    public static boolean isWithinBookingWindow(LocalDate date, LocalTime time) {
        LocalDateTime slotDateTime = LocalDateTime.of(date, time);
        return slotDateTime.isAfter(LocalDateTime.now(WARSAW).plusHours(BOOKING_WINDOW_HOURS));
    }

    public static boolean isPast(LocalDate date, LocalTime time) {
        return isPast(date, time, LocalDateTime.now(WARSAW));
    }

    /** Testable overload — "now" passed in explicitly. */
    public static boolean isPast(LocalDate date, LocalTime time, LocalDateTime nowWarsaw) {
        return LocalDateTime.of(date, time).isBefore(nowWarsaw);
    }

    /**
     * Whether a whole day is behind us — the date-level counterpart of {@link #isPast}, for entries
     * that carry no time at all (an all-day event has null start/end times).
     *
     * <p>Same line as {@code isPast} draws, just at day resolution: over means the LAST MINUTE has
     * passed, so a date that is still today has not passed. Notification decisions depend on this —
     * an event ending today is still an event people are going to.
     */
    public static boolean dayHasPassed(LocalDate date) {
        return dayHasPassed(date, LocalDate.now(WARSAW));
    }

    /** Testable overload — "today" passed in explicitly. */
    public static boolean dayHasPassed(LocalDate date, LocalDate todayWarsaw) {
        return date.isBefore(todayWarsaw);
    }
}
