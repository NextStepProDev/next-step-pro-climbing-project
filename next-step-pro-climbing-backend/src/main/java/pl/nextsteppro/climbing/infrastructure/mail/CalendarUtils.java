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
            sb.append("DESCRIPTION:").append(escapeIcs(toPlainText(description))).append("\r\n");
        }

        sb.append("END:VEVENT\r\n");
        sb.append("END:VCALENDAR\r\n");

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Strips the pseudo-markdown an event description is written in.
     *
     * DESCRIPTION is a plain-text field, so the markers that structure the description on the site
     * would land in the reader's calendar as literal "## Co zabrać". Mirrors {@code toPlainText}
     * in the frontend's renderRichText.ts, which does the same job for the browser-side export —
     * a marker taught to the editor has to reach both, and {@code CalendarUtilsTest} pins it here.
     *
     * <p>Bullets keep a character because a marked item still reads as a list without markup;
     * numbered and lettered items already spell their own label out.
     */
    static String toPlainText(String description) {
        return description.lines()
            .map(line -> line
                .replaceFirst("^#{1,3} ", "")
                .replaceFirst("^[•\\-*] ", "• "))
            .map(line -> line
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("\\*(.+?)\\*", "$1")
                .replaceAll("__(.+?)__", "$1")
                .replaceAll("~~(.+?)~~", "$1"))
            .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String escapeIcs(String value) {
        return value
                .replace("\\", "\\\\")
                .replace(",", "\\,")
                .replace(";", "\\;")
                .replace("\n", "\\n");
    }
}
