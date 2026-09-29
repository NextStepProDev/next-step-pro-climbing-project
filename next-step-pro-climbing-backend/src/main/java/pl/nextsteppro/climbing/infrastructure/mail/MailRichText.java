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
                sb.append("<ul style=\"margin:8px 0;padding-left:20px;\">");
                while (i < lines.length && lines[i].matches("^[•\\-*] .*")) {
                    sb.append("<li>").append(inlineFormat(lines[i].replaceFirst("^[•\\-*] ", ""))).append("</li>");
                    i++;
                }
                sb.append("</ul>");
            } else if (line.matches("^\\d+\\. .*")) {
                sb.append("<ol style=\"margin:8px 0;padding-left:20px;\">");
                while (i < lines.length && lines[i].matches("^\\d+\\. .*")) {
                    sb.append("<li>").append(inlineFormat(lines[i].replaceFirst("^\\d+\\. ", ""))).append("</li>");
                    i++;
                }
                sb.append("</ol>");
            } else if (line.matches("^[a-z]\\) .*")) {
                sb.append("<ol type=\"a\" style=\"margin:8px 0;padding-left:20px;\">");
                while (i < lines.length && lines[i].matches("^[a-z]\\) .*")) {
                    sb.append("<li>").append(inlineFormat(lines[i].replaceFirst("^[a-z]\\) ", ""))).append("</li>");
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

    private static String inlineFormat(String text) {
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        text = text.replaceAll("__(.+?)__", "<u>$1</u>");
        text = text.replaceAll("\\*(.+?)\\*", "<em>$1</em>");
        // A styled span rather than <s>: Outlook renders the CSS reliably, the tag less so.
        text = text.replaceAll("~~(.+?)~~", "<span style=\"text-decoration: line-through;\">$1</span>");
        return text;
    }
}
