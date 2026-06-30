package pl.nextsteppro.climbing.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

public final class BookingTimeValidator {

    private static final int BOOKING_WINDOW_HOURS = 12;
    // Terminy zapisywane są jako czas lokalny PL; porównujemy względem czasu polskiego, NIE strefy JVM
    // (kontener prod działa w UTC — gołe LocalDateTime.now() dawałoby latem 2 h przesunięcia okna 12 h).
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
