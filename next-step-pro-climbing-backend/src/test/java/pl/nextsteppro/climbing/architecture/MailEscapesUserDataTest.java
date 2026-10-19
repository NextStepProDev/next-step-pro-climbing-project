package pl.nextsteppro.climbing.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every mail template is a run of {@code label, value} pairs fed to {@code .formatted(...)}:
 * {@code msg.getForLang("email.admin.phone", lang), esc(user.getPhone())}. A value a person typed
 * — a name, an address, a phone number, a title — goes through {@code esc()}, which also turns a
 * missing value into nothing rather than the word "null".
 *
 * <p>The 2026-10-19 audit found the two admin "waitlist offer confirmed" mails passing email and
 * phone raw, while the same fields in every other admin mail were escaped. Not exploitable that
 * day (both fields are validated on the way in), but an account from Google without a phone
 * printed "null" in the owner's inbox, and the next relaxed validator would have made it HTML.
 */
class MailEscapesUserDataTest {

    private static final Pattern RAW_VALUE_AFTER_LABEL = Pattern.compile(
        "getForLang\\([^()]*\\),\\s*(?!esc\\()([a-zA-Z]+\\.get(?:Email|Phone|FullName|FirstName|LastName|Nickname"
            + "|Title|DisplayTitle|Comment|Location|Description|Name)\\(\\))");

    @Test
    void shouldEscapeEveryValueAPersonTypedIntoAMail() {
        List<String> raw = new ArrayList<>();
        int labels = 0;
        for (Path file : SourceFiles.mainJavaFiles()) {
            if (!file.toString().replace('\\', '/').contains("/infrastructure/mail/")) {
                continue;
            }
            String source = SourceFiles.readWithoutComments(file);
            labels += source.split("getForLang\\(", -1).length - 1;
            Matcher matcher = RAW_VALUE_AFTER_LABEL.matcher(source);
            while (matcher.find()) {
                raw.add(file.getFileName() + ": " + matcher.group(1));
            }
        }
        assertTrue(labels > 100, "The scan must reach the templates (saw " + labels + " labels)");
        assertEquals(List.of(), raw, "Wrap it in esc(): HTML-safe, and blank instead of \"null\"");
    }
}
