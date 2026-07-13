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
        return LocalDateTime.of(date, time).isBefore(LocalDateTime.now(WARSAW));
    }
}
