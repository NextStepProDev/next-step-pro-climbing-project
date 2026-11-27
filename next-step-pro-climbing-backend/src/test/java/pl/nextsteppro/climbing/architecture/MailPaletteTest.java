package pl.nextsteppro.climbing.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every colour in a mail comes from one palette, and the accent is the app's own graphite.
 *
 * <p>Each template writes its colours inline (mail clients ignore stylesheets), so nothing tied
 * them together: by 2026-11 the mails wore purple, two blues and teal for the same job - a
 * button, a link, an info box - and none of them is a colour the app uses. The purple came in with
 * one template and was copied into the next. This gate is the "one place": a template reaching for
 * a colour outside the palette fails the build instead of drifting again.
 *
 * <p>Two cards share the palette: light (white card, the booking mails) and dark (the account
 * mails and the newsletter, on {@code #312e2b}). The accent there is the lighter step of the same
 * scale, as in the app's dark theme - graphite text on the dark card would be unreadable.
 *
 * <p>Red, amber and green stay: they carry meaning (cancelled, "your proposal...", the new time),
 * not the brand.
 */
class MailPaletteTest {

    /** The app's primary scale (index.css {@code --color-primary-*}). */
    private static final Set<String> ACCENT = Set.of(
        "#1e293b", // 700: strong text in a tinted box
        "#334155", // 600: buttons, links, borders on the light card; buttons on the dark one
        "#475569", // 500: borders and rules on the dark card
        "#94a3b8", // 300: headings and links on the dark card (5.3:1; the 400 step is 4.47:1, under AA)
        "#cbd5e1", // 200: tinted box border
        "#f1f5f9"  // 50:  tinted box background
    );

    private static final Set<String> NEUTRAL = Set.of(
        "#1a1816", "#312e2b", // the brand's dark: header bar, dark card
        "#333", "#666", "#888", "#fff", "#ffffff",
        "#f5f5f5", "#e0e0e0", "#f3f4f6", "#d1d5db",
        "#9ca3af", "#6b7280", "#64748b", "#374151"
    );

    /** Colours that say something: cancelled / warning / the new term. */
    private static final Set<String> SEMANTIC = Set.of(
        "#fef2f2", "#fecaca", "#f87171", "#ef4444", "#e11d48", "#991b1b",
        "#fffbeb", "#fef9c3", "#fde047", "#fcd34d", "#fbbf24", "#f59e0b", "#92400e", "#78350f", "#713f12",
        "#f0fdf4", "#bbf7d0", "#15803d"
    );

    private static final Pattern HEX = Pattern.compile("#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{3})\\b");

    @Test
    void shouldColourEveryMailFromTheOnePalette() {
        Set<String> allowed = new TreeSet<>();
        allowed.addAll(ACCENT);
        allowed.addAll(NEUTRAL);
        allowed.addAll(SEMANTIC);

        List<String> offending = new ArrayList<>();
        int seen = 0;
        for (Path file : mailSources()) {
            // Raw, not comment-stripped: the stripper treats the "//" of every https:// as a comment
            String source = SourceFiles.read(file);
            Matcher matcher = HEX.matcher(source);
            while (matcher.find()) {
                seen++;
                String colour = matcher.group().toLowerCase(Locale.ROOT);
                if (!allowed.contains(colour)) {
                    offending.add(file.getFileName() + ": " + colour);
                }
            }
        }

        assertTrue(seen > 150, "The scan must reach the templates (saw " + seen + " colours)");
        assertEquals(List.of(), offending,
            "Use the palette in MailPaletteTest - the accent is graphite #334155 (#94a3b8 on the dark card)");
    }

    /**
     * The templates, the unsubscribe page a mail links to, and the message bundles, where the
     * newsletter footer carries its own link colour.
     */
    private static List<Path> mailSources() {
        Stream<Path> java = SourceFiles.mainJavaFiles().stream()
            .filter(p -> {
                String s = p.toString().replace('\\', '/');
                return s.contains("/infrastructure/mail/") || s.endsWith("/api/user/UserController.java");
            });
        Stream<Path> bundles = Stream.of("messages.properties", "messages_en.properties", "messages_es.properties")
            .map(SourceFiles::mainResource);
        return Stream.concat(java, bundles).toList();
    }
}
