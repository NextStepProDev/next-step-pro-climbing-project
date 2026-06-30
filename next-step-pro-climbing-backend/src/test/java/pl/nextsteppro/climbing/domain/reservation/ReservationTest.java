package pl.nextsteppro.climbing.domain.reservation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Reservation Entity Tests")
class ReservationTest {

    @Test
    @DisplayName("should return null when sanitizing blank or null comment")
    void shouldReturnNullWhenSanitizingBlankOrNullComment() {
        assertNull(Reservation.sanitizeComment(null));
        assertNull(Reservation.sanitizeComment(""));
        assertNull(Reservation.sanitizeComment("   "));
    }

    @Test
    @DisplayName("should preserve Polish and Spanish accented characters")
    void shouldPreserveAccentedCharacters() {
        String comment = "Czy jest możliwość treningu dla dwóch osób? ñ á é í ó ú";

        String sanitized = Reservation.sanitizeComment(comment);

        // Accented letters must survive untouched (no &oacute;-style entities)
        assertEquals(comment, sanitized);
        assertFalse(sanitized.contains("&oacute;"), "ó must not be turned into an HTML entity");
    }

    @Test
    @DisplayName("should escape dangerous HTML characters to prevent XSS")
    void shouldEscapeDangerousHtmlCharacters() {
        String sanitized = Reservation.sanitizeComment("<script>alert('x')</script> & \"quotes\"");

        assertNotNull(sanitized);
        assertFalse(sanitized.contains("<script>"));
        assertTrue(sanitized.contains("&lt;script&gt;"));
        assertTrue(sanitized.contains("&amp;"));
        assertTrue(sanitized.contains("&quot;"));
    }

    @Test
    @DisplayName("should truncate sanitized comment to 500 characters")
    void shouldTruncateLongComment() {
        String longComment = "a".repeat(600);

        String sanitized = Reservation.sanitizeComment(longComment);

        assertNotNull(sanitized);
        assertEquals(500, sanitized.length());
    }
}
