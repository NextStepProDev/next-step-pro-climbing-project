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
}
