package pl.nextsteppro.climbing.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The pseudo-markdown written in {@code RichTextEditor} is rendered by TWO independent renderers:
 * {@code utils/renderRichText.ts} for everything on screen, and {@code MailService.richTextToHtml}
 * for the one body the server turns into HTML — mass mail and newsletters.
 *
 * <p>A marker added to the editor but not to the mail renderer reaches subscribers as literal
 * characters ("## Zapraszamy", "~~stara cena~~"). That gap is invisible before it ships:
 * AdminMailPanel has no preview, so the first person to see the mistake is the recipient, in a
 * send that cannot be recalled. It happened when heading and strikethrough were added.
 *
 * <p>Every marker must emit an HTML element, so comparing the ELEMENTS the two renderers produce
 * catches a new marker without this gate having to understand markers at all. A tag the frontend
 * emits that is missing from {@link #MAIL_EQUIVALENT} fails on purpose: the decision of how mail
 * should render it has to be made, not defaulted.
 *
 * <p>Lives on the backend side for the same reason as {@code ActivityActionTypeParityTest} — the
 * mail renderer is backend code, and CI path filters mean a backend-only commit never runs Vitest.
 */
class MailRichTextParityTest {

    private static final Path FRONTEND_RENDERER = SourceFiles.frontendFile("utils/renderRichText.ts");
    private static final Path MAIL_SERVICE =
        Path.of("src/main/java/pl/nextsteppro/climbing/infrastructure/mail/MailService.java");

    /**
     * A block tag as it appears in the renderer's template literals: '<name>' or '<name attr='.
     *
     * Deliberately narrower than "'<' then a name": TypeScript generics look the same, and
     * {@code ReadonlyArray<readonly [...]>} was read as a tag called "readonly" the moment the
     * inline markers became a table.
     */
    private static final Pattern BLOCK_TAG = Pattern.compile("<([a-z][a-z0-9]*)(?:>|\\s+[a-z-]+=)");

    /**
     * The tag half of an INLINE_MARKERS entry — {@code ['**', 'strong']}.
     *
     * Inline tags are no longer written as literal HTML in the renderer (one table drives both
     * the HTML and the plain-text stripper), so scanning only for '<tag' would quietly stop
     * seeing bold, italic, underline and strikethrough — the gate would pass by seeing nothing.
     */
    private static final Pattern INLINE_MARKER_TAG =
        Pattern.compile("\\['[^']+',\\s*'([a-z]+)'\\]");

    /**
     * What the mail body must contain for each element the screen renderer can emit.
     *
     * <p>Not always the same tag: mail uses {@code <h3>} because the message has no {@code <h2>}
     * above it beyond the subject, and line-through as inline CSS because Outlook honours the
     * style far more reliably than {@code <s>}.
     */
    private static final Map<String, String> MAIL_EQUIVALENT = new LinkedHashMap<>();

    static {
        MAIL_EQUIVALENT.put("strong", "<strong>");
        MAIL_EQUIVALENT.put("em", "<em>");
        MAIL_EQUIVALENT.put("u", "<u>");
        MAIL_EQUIVALENT.put("s", "line-through");
        MAIL_EQUIVALENT.put("ul", "<ul");
        MAIL_EQUIVALENT.put("ol", "<ol");
        MAIL_EQUIVALENT.put("li", "<li>");
        MAIL_EQUIVALENT.put("h4", "<h3");
        MAIL_EQUIVALENT.put("br", "<br");
    }

    @Test
    void shouldRenderEveryOnScreenMarkerInMailToo() {
        assumeTrue(Files.exists(FRONTEND_RENDERER),
            "Frontend sources not checked out next to the backend — skipping cross-module gate");

        String renderer = SourceFiles.readWithoutComments(FRONTEND_RENDERER);

        Set<String> blockTags = new LinkedHashSet<>();
        Matcher blocks = BLOCK_TAG.matcher(renderer);
        while (blocks.find()) {
            blockTags.add(blocks.group(1));
        }

        Set<String> inlineTags = new LinkedHashSet<>();
        Matcher inlines = INLINE_MARKER_TAG.matcher(renderer);
        while (inlines.find()) {
            inlineTags.add(inlines.group(1));
        }

        // Both halves have to be found, or a gate that sees nothing reports everything is fine
        assertTrue(blockTags.size() >= 4,
            "Parsed only " + blockTags.size() + " block tags out of renderRichText.ts — the parser "
                + "broke rather than the code. Fix the gate, do not relax it.");
        assertTrue(inlineTags.size() >= 4,
            "Parsed only " + inlineTags.size() + " INLINE_MARKERS entries out of renderRichText.ts "
                + "— the parser broke rather than the code. Fix the gate, do not relax it.");

        Set<String> emitted = new LinkedHashSet<>(blockTags);
        emitted.addAll(inlineTags);

        Set<String> unmapped = new LinkedHashSet<>(emitted);
        unmapped.removeAll(MAIL_EQUIVALENT.keySet());
        assertTrue(unmapped.isEmpty(),
            "renderRichText.ts emits " + unmapped + ", which MailRichTextParityTest has no mail "
                + "equivalent for. A new marker was added to RichTextEditor: decide how the mass "
                + "mail body should render it (MailService.richTextToHtml / inlineFormat), then "
                + "add the mapping here. AdminMailPanel has no preview — an unhandled marker is "
                + "first seen by the recipients.");

        String mail = SourceFiles.read(MAIL_SERVICE);
        Set<String> missing = new LinkedHashSet<>();
        for (String tag : emitted) {
            if (!mail.contains(MAIL_EQUIVALENT.get(tag))) {
                missing.add(tag + " (expects " + MAIL_EQUIVALENT.get(tag) + ")");
            }
        }

        assertTrue(missing.isEmpty(),
            "MailService renders no equivalent for " + missing + ". The same RichTextEditor writes "
                + "both bodies, so a marker the screen understands and the mail does not ships as "
                + "raw characters to every subscriber.");
    }
}
