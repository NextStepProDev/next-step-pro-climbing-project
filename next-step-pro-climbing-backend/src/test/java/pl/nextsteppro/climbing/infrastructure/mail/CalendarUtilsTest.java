package pl.nextsteppro.climbing.infrastructure.mail;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The ICS attachment is the one place an event description leaves the site as plain text, so the
 * pseudo-markdown it is written in has to come off first — otherwise a description that reads well
 * on the page arrives in the reader's calendar as "## Co zabrać".
 *
 * <p>Mirrors {@code toPlainText} in the frontend's renderRichText.ts, which does the same for the
 * browser-side "add to calendar". Both are driven by the same marker set; this pins the Java half.
 */
class CalendarUtilsTest {

    @Test
    void shouldStripEveryInlineMarkerFromTheDescription() {
        assertEquals("mocno lekko pod odwołane",
            CalendarUtils.toPlainText("**mocno** *lekko* __pod__ ~~odwołane~~"));
    }

    @Test
    void shouldDropTheHeadingMarkerButKeepTheHeading() {
        assertEquals("Co zabrać", CalendarUtils.toPlainText("## Co zabrać"));
    }

    @Test
    void shouldKeepBulletsAsACharacterSoAnItemStillReadsAsAList() {
        assertEquals("• uprząż\n• buty\n• kask",
            CalendarUtils.toPlainText("- uprząż\n* buty\n• kask"));
    }

    @Test
    void shouldLeaveNumberedAndLetteredItemsAloneSinceTheySpellTheirOwnLabel() {
        assertEquals("1. zbiórka\na) wariant", CalendarUtils.toPlainText("1. zbiórka\na) wariant"));
    }

    @Test
    void shouldNotLetAnyMarkerReachTheIcsDescription() {
        String ics = new String(CalendarUtils.buildIcsFile(
            "Kurs wspinaczki",
            LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10),
            LocalTime.of(17, 0), LocalTime.of(19, 0),
            "Jura",
            "## Co zabrać\n- **uprząż**\n~~kask~~"), StandardCharsets.UTF_8);

        assertTrue(ics.contains("DESCRIPTION:"), "no DESCRIPTION emitted at all");
        String description = ics.lines()
            .filter(l -> l.startsWith("DESCRIPTION:"))
            .findFirst()
            .orElseThrow();

        assertFalse(description.contains("#"), description);
        assertFalse(description.contains("*"), description);
        assertFalse(description.contains("~~"), description);
        assertTrue(description.contains("Co zabrać"), description);
        // Line breaks stay escaped, or the file stops parsing at the first bare line
        assertTrue(description.contains("\\n"), description);
    }

    @Test
    void shouldDefineTheZoneItsTimesReferToAndStampTheEvent() {
        // The night the clocks go back (25.10.2026): a strict reader needs the zone's rules to know
        // which 02:30 is meant, and without a VTIMEZONE it has none.
        String ics = new String(CalendarUtils.buildIcsFile("Trening", LocalDate.of(2026, 10, 25), null,
            LocalTime.of(18, 0), LocalTime.of(20, 0), null, null), StandardCharsets.UTF_8);

        assertTrue(ics.contains("BEGIN:VTIMEZONE\r\nTZID:Europe/Warsaw\r\n"));
        assertTrue(ics.indexOf("END:VTIMEZONE") < ics.indexOf("BEGIN:VEVENT"), "defined before it is used");
        assertTrue(ics.contains("RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU"));
        assertTrue(ics.matches("(?s).*\r\nDTSTAMP:\\d{8}T\\d{6}Z\r\n.*"), "DTSTAMP is required in a VEVENT");
        assertTrue(ics.contains("DTSTART;TZID=Europe/Warsaw:20261025T180000"));
    }

    @Test
    void shouldLeaveTheZoneOutOfAnAllDayEntry() {
        String ics = new String(CalendarUtils.buildIcsFile("Kurs", LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 25),
            null, null, null, null), StandardCharsets.UTF_8);

        assertFalse(ics.contains("VTIMEZONE"), "a floating date refers to no zone");
        assertTrue(ics.contains("DTEND;VALUE=DATE:20261026"), "an all-day end is exclusive");
    }

    @Test
    void shouldNotLetALoneCarriageReturnStartANewIcsProperty() {
        // \n was escaped, \r was not — and a bare CR ends a content line for many parsers, so a title
        // like "Trening\rATTENDEE:..." injected a property of its own into the calendar file.
        String ics = new String(CalendarUtils.buildIcsFile("Trening\rATTENDEE:mailto:x@evil.example",
            java.time.LocalDate.of(2026, 11, 3), null, java.time.LocalTime.of(18, 0), java.time.LocalTime.of(20, 0),
            "Skała\r\nLOCATION:inne", null), java.nio.charset.StandardCharsets.UTF_8);

        for (String line : ics.split("\r\n")) {
            org.junit.jupiter.api.Assertions.assertFalse(line.contains("\r") || line.contains("\n"),
                "every property must stay on its own line: " + line);
            org.junit.jupiter.api.Assertions.assertFalse(line.startsWith("ATTENDEE"), "no injected property");
        }
    }
}
