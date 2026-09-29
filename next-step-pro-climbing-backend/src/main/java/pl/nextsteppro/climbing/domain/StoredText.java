package pl.nextsteppro.climbing.domain;

import org.jspecify.annotations.Nullable;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;

/**
 * The two ways free text from a client is stored. Blank input is {@code null} in both, which is
 * what the NOT-blank CHECKs on those columns expect.
 *
 * <p>Both cut to a hard maximum rather than reject: the column has a size, and a comment that is
 * a few characters too long is still a comment.
 */
public final class StoredText {

    private StoredText() {}

    /**
     * HTML-escaped (defence in depth for text that reaches mail or {@code innerHTML}), trimmed,
     * then cut to {@code maxLength}. The UTF-8 overload escapes only {@code < > " & '}; the
     * one-argument one assumes ISO-8859-1 and would turn Polish letters into named entities
     * ({@code ó → &oacute;}).
     *
     * <p>The cut comes after escaping, because escaping lengthens the text and the column bounds
     * the stored form. So it must not land inside an entity: {@code &amp;} cut to {@code &am}
     * decodes on the client as literal "&amp;am". A cut inside an entity drops the whole entity.
     */
    public static @Nullable String escapeAndCap(@Nullable String text, int maxLength) {
        if (text == null || text.isBlank()) return null;
        String escaped = HtmlUtils.htmlEscape(text.trim(), StandardCharsets.UTF_8.name());
        if (escaped.length() <= maxLength) return escaped;

        int end = backOffSurrogate(escaped, maxLength);
        int amp = escaped.lastIndexOf('&', end - 1);
        if (amp >= 0 && escaped.indexOf(';', amp) >= end) end = amp;
        return escaped.substring(0, end);
    }

    /**
     * Trimmed and cut, NOT escaped — for text whose only reader renders it as a text node and
     * whose round trip would otherwise need decoding (an admin's private note, an admin's
     * rejection note, a payer's name).
     */
    public static @Nullable String trimAndCap(@Nullable String text, int maxLength) {
        if (text == null || text.isBlank()) return null;
        String trimmed = text.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, backOffSurrogate(trimmed, maxLength)) : trimmed;
    }

    /** An emoji is two chars; a cut between them stores half a character. */
    private static int backOffSurrogate(String text, int end) {
        return Character.isHighSurrogate(text.charAt(end - 1)) ? end - 1 : end;
    }
}
