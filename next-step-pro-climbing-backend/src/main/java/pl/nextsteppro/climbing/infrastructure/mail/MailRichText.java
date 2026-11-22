package pl.nextsteppro.climbing.infrastructure.mail;

import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;

/**
 * Server-side twin of the frontend's {@code utils/renderRichText.ts}, for the bodies the server
 * turns into HTML itself: the admin's mass mail and the article newsletter.
 *
 * <p>⚠️ The two MUST accept the same markers, because the same RichTextEditor writes both — and
 * a gap here is invisible before it ships: neither mail has a preview, so an unsupported marker
 * is first seen by the recipients, in a send that cannot be recalled. The newsletter was that
 * case for its whole life: it inserted the article text raw, so subscribers read "## Co
 * zabieramy" and "**ważne**". {@code MailRichTextParityTest} keeps the tag sets in step.
 *
 * <p>The text is escaped here, not by the caller, exactly as the frontend renderer does: an
 * article's text is stored raw, and the site escapes before it formats.
 */
final class MailRichText {

    private MailRichText() {}

    /** @param headingColor the one colour that depends on the mail's background */
    static String toHtml(String rawText, String headingColor) {
        String[] lines = HtmlUtils.htmlEscape(rawText, StandardCharsets.UTF_8.name()).split("\r?\n", -1);
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            if (line.matches("^#{1,3} .*")) {
                sb.append("<h3 style=\"margin:16px 0 4px 0;font-size:16px;color:").append(headingColor).append(";\">")
                    .append(inlineFormat(line.replaceFirst("^#{1,3} ", "")))
                    .append("</h3>");
                i++;
            } else if (line.matches("^[•\\-*] .*")) {
                sb.append("<ul style=\"margin:8px 0;padding-left:28px;\">");
                while (i < lines.length && lines[i].matches("^[•\\-*] .*")) {
                    sb.append("<li>").append(inlineFormat(lines[i].replaceFirst("^[•\\-*] ", ""))).append("</li>");
                    i++;
                }
                sb.append("</ul>");
            } else if (line.matches("^\\d+\\. .*")) {
                sb.append("<ol").append(startAttr(listNumber(line.substring(0, line.indexOf('.')))))
                    .append(" style=\"margin:8px 0;padding-left:28px;\">");
                while (i < lines.length && lines[i].matches("^\\d+\\. .*")) {
                    sb.append("<li>").append(inlineFormat(lines[i].replaceFirst("^\\d+\\. ", ""))).append("</li>");
                    i++;
                }
                sb.append("</ol>");
            } else if (line.matches("^[a-zA-Z]\\) .*")) {
                // Either case, as on the site: "C)" is a list there, so it must not be a paragraph here.
                sb.append("<ol type=\"a\"").append(startAttr(Character.toLowerCase(line.charAt(0)) - 'a' + 1))
                    .append(" style=\"margin:8px 0;padding-left:28px;\">");
                while (i < lines.length && lines[i].matches("^[a-zA-Z]\\) .*")) {
                    sb.append("<li>").append(inlineFormat(lines[i].replaceFirst("^[a-zA-Z]\\) ", ""))).append("</li>");
                    i++;
                }
                sb.append("</ol>");
            } else if (line.isBlank()) {
                sb.append("<br/>");
                i++;
            } else {
                sb.append("<p style=\"margin:4px 0;\">").append(inlineFormat(line)).append("</p>");
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * A list resumed after a heading ("3.", "4.") keeps its numbers: the marker is stripped and
     * the client draws the numbering, which would otherwise restart at 1. Same rule as the
     * frontend's {@code startAttr}.
     */
    private static String startAttr(int start) {
        return start > 1 ? " start=\"" + start + "\"" : "";
    }

    /**
     * The marker regex takes any run of digits, so a typed "99999999999." must not throw: an
     * exception here would fail the whole send. Out of range falls back to the default numbering.
     */
    private static int listNumber(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String inlineFormat(String text) {
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        text = text.replaceAll("__(.+?)__", "<u>$1</u>");
        text = text.replaceAll("\\*(.+?)\\*", "<em>$1</em>");
        // A styled span rather than <s>: Outlook renders the CSS reliably, the tag less so.
        text = text.replaceAll("~~(.+?)~~", "<span style=\"text-decoration: line-through;\">$1</span>");
        return text;
    }
}
