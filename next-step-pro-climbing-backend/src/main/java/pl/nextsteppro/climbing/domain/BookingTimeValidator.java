package pl.nextsteppro.climbing.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class BookingTimeValidator {

    private static final int BOOKING_WINDOW_HOURS = 12;

    private BookingTimeValidator() {}

    public static boolean isWithinBookingWindow(LocalDate date, LocalTime time) {
        LocalDateTime slotDateTime = LocalDateTime.of(date, time);
        return slotDateTime.isAfter(LocalDateTime.now().plusHours(BOOKING_WINDOW_HOURS));
    }

    public static boolean isPast(LocalDate date, LocalTime time) {
        return LocalDateTime.of(date, time).isBefore(LocalDateTime.now());
    }
}
