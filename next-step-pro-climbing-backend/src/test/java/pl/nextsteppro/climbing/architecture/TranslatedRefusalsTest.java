package pl.nextsteppro.climbing.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A refusal the API sends back is read by a person, in Polish, English or Spanish.
 *
 * <p>{@code GlobalExceptionHandler} answers an {@code IllegalArgumentException} (400) or
 * {@code IllegalStateException} (409) with the exception's own message. Most of the code passes
 * {@code msg.get(key)}; about 150 sites passed a literal English sentence instead, and "Album not
 * found", "File content does not match its declared type" or "This reservation is already
 * cancelled" reached a Polish admin and a Spanish client word for word (audit 2026-10-19). Code
 * with no {@code MessageService} throws {@code LocalizedMessage.Rejected/Conflict} with a key,
 * which the handler translates.
 *
 * <p>Two rules, because the second is how the first fails quietly: no literal message, and every
 * key the code names exists — a misspelt key is shown to the user as the key itself.
 */
class TranslatedRefusalsTest {

    private static final Pattern LITERAL_REFUSAL =
        Pattern.compile("new\\s+(IllegalArgument|IllegalState)Exception\\(\\s*\"");

    private static final Pattern NAMED_KEY = Pattern.compile(
        "(?:msg|messageService)\\.get\\(\\s*\"([a-z0-9_.]+)\"|LocalizedMessage\\.(?:Rejected|Conflict)\\(\\s*\"([a-z0-9_.]+)\"");

    /** Path suffix → why a literal is right there. Nothing here can reach a person through the API. */
    private static final Map<String, String> ALLOWED = Map.of(
        "config/DevProfileGuard.java", "refuses to START the application — read by the developer in the log",
        "infrastructure/security/JwtConfig.java", "startup configuration check",
        "infrastructure/security/PwnedPasswordChecker.java", "the JVM lacking SHA-1 — a broken runtime, not a request",
        "infrastructure/mail/MailDispatcher.java", "a resource missing from the jar, at startup",
        "domain/settlement/PaymentAllocator.java", "invariant of the allocator; callers only ask about charges they passed in",
        "domain/climbingascent/ClimbingAscent.java", "entity invariants; AscentService refuses the same input first, translated",
        "domain/climbingascent/ClimbingGrade.java", "comparing grades across scales is a programming error",
        "domain/athleteweight/WeightTrendCalculator.java", "invariant of a pure calculator"
    );

    @Test
    void shouldNeverSendALiteralSentenceBackAsARefusal() {
        List<String> offenders = new ArrayList<>();
        for (Path file : SourceFiles.mainJavaFiles()) {
            String path = file.toString().replace('\\', '/');
            if (ALLOWED.keySet().stream().anyMatch(path::endsWith)) {
                continue;
            }
            Matcher matcher = LITERAL_REFUSAL.matcher(SourceFiles.readWithoutComments(file));
            while (matcher.find()) {
                offenders.add(path);
            }
        }
        assertEquals(List.of(), offenders,
            "Refuse with msg.get(key) or LocalizedMessage.Rejected/Conflict(key), not an English sentence");
    }

    @Test
    void shouldOnlyNameKeysThatExistInTheBundle() throws IOException {
        Properties bundle = new Properties();
        bundle.load(new StringReader(SourceFiles.read(SourceFiles.mainResource("messages.properties"))));

        List<String> missing = new ArrayList<>();
        int named = 0;
        for (Path file : SourceFiles.mainJavaFiles()) {
            Matcher matcher = NAMED_KEY.matcher(SourceFiles.readWithoutComments(file));
            while (matcher.find()) {
                named++;
                String key = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                if (!bundle.containsKey(key)) {
                    missing.add(file.getFileName() + ": " + key);
                }
            }
        }
        // A broken pattern finds nothing and passes; the code names hundreds of keys.
        assertTrue(named > 300, "The scan must actually find the keys (found " + named + ")");
        assertEquals(List.of(), missing, "A missing key is shown to the user as the key itself");
    }

    @Test
    void shouldKeepTheAllowlistHonest() {
        for (String suffix : ALLOWED.keySet()) {
            boolean exists = SourceFiles.mainJavaFiles().stream()
                .anyMatch(file -> file.toString().replace('\\', '/').endsWith(suffix));
            assertTrue(exists, "Allowlisted file no longer exists: " + suffix);
        }
    }
}
