package pl.nextsteppro.climbing.infrastructure.mail;

import org.jspecify.annotations.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

final class CalendarUtils {

    private static final DateTimeFormatter DATE_BASIC = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_BASIC = DateTimeFormatter.ofPattern("HHmmss");
    private static final String TIMEZONE = "Europe/Warsaw";

    private CalendarUtils() {}

    static String buildGoogleCalendarUrl(
            String title,
            LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable LocalTime startTime,
            @Nullable LocalTime endTime,
            @Nullable String location
    ) {
        var sb = new StringBuilder("https://calendar.google.com/calendar/render?action=TEMPLATE");
        sb.append("&text=").append(encode(title));

        if (startTime != null && endTime != null) {
            String start = startDate.format(DATE_BASIC) + "T" + startTime.format(TIME_BASIC);
            LocalDate end = endDate != null ? endDate : startDate;
            String endStr = end.format(DATE_BASIC) + "T" + endTime.format(TIME_BASIC);
            sb.append("&dates=").append(start).append("/").append(endStr);
            sb.append("&ctz=").append(TIMEZONE);
        } else {
            String start = startDate.format(DATE_BASIC);
            LocalDate end = endDate != null ? endDate.plusDays(1) : startDate.plusDays(1);
            sb.append("&dates=").append(start).append("/").append(end.format(DATE_BASIC));
        }

        if (location != null && !location.isBlank()) {
            sb.append("&location=").append(encode(location));
        }

        return sb.toString();
    }

    static byte[] buildIcsFile(
            String title,
            LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable LocalTime startTime,
            @Nullable LocalTime endTime,
            @Nullable String location,
            @Nullable String description
    ) {
        var sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//Next Step Pro Climbing//Reservation//EN\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("BEGIN:VEVENT\r\n");
        sb.append("UID:").append(UUID.randomUUID()).append("@nextsteppro.pl\r\n");

        if (startTime != null && endTime != null) {
            sb.append("DTSTART;TZID=").append(TIMEZONE).append(":").append(startDate.format(DATE_BASIC)).append("T").append(startTime.format(TIME_BASIC)).append("\r\n");
            LocalDate end = endDate != null ? endDate : startDate;
            sb.append("DTEND;TZID=").append(TIMEZONE).append(":").append(end.format(DATE_BASIC)).append("T").append(endTime.format(TIME_BASIC)).append("\r\n");
        } else {
            sb.append("DTSTART;VALUE=DATE:").append(startDate.format(DATE_BASIC)).append("\r\n");
            LocalDate end = endDate != null ? endDate.plusDays(1) : startDate.plusDays(1);
            sb.append("DTEND;VALUE=DATE:").append(end.format(DATE_BASIC)).append("\r\n");
        }

        sb.append("SUMMARY:").append(escapeIcs(title)).append("\r\n");

        if (location != null && !location.isBlank()) {
            sb.append("LOCATION:").append(escapeIcs(location)).append("\r\n");
        }
        if (description != null && !description.isBlank()) {
            sb.append("DESCRIPTION:").append(escapeIcs(description)).append("\r\n");
        }

        sb.append("END:VEVENT\r\n");
        sb.append("END:VCALENDAR\r\n");

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String escapeIcs(String value) {
        return value
                .replace("\\", "\\\\")
                .replace(",", "\\,")
                .replace(";", "\\;")
                .replace("\n", "\\n");
    }
}
