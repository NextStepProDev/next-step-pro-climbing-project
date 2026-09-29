package pl.nextsteppro.climbing.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StoredTextTest {

    @Test
    void shouldStoreBlankAsNull() {
        assertNull(StoredText.escapeAndCap(null, 10));
        assertNull(StoredText.escapeAndCap("   ", 10));
        assertNull(StoredText.trimAndCap(null, 10));
        assertNull(StoredText.trimAndCap("\n\t ", 10));
    }

    @Test
    void shouldEscapeOnlyDangerousCharactersAndKeepDiacritics() {
        assertEquals("zażółć &lt;b&gt; &amp; ñ", StoredText.escapeAndCap("  zażółć <b> & ñ  ", 100));
    }

    @Test
    void shouldCutEscapedTextToTheLimit() {
        assertEquals("abcde", StoredText.escapeAndCap("abcdefgh", 5));
    }

    @Test
    void shouldDropAnEntityTheCutWouldSplitInsteadOfStoringHalfOfIt() {
        // "ab&amp;" is 7 chars; a cut at 5 used to leave "ab&am", which decodes as literal text.
        assertEquals("ab", StoredText.escapeAndCap("ab&c", 5));
    }

    @Test
    void shouldKeepAnEntityThatEndsExactlyAtTheCut() {
        assertEquals("ab&amp;", StoredText.escapeAndCap("ab&cd", 7));
    }

    @Test
    void shouldNotSplitAnEmojiInHalf() {
        // 😀 is two chars; the cut at 3 falls between them.
        assertEquals("ab", StoredText.escapeAndCap("ab😀c", 3));
        assertEquals("ab", StoredText.trimAndCap("ab😀c", 3));
    }

    @Test
    void shouldTrimAndCapWithoutEscaping() {
        assertEquals("a & <b>", StoredText.trimAndCap("  a & <b>  ", 100));
        assertEquals("a & ", StoredText.trimAndCap("a & <b>", 4));
    }
}
