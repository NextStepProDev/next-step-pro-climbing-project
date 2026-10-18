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
 * A bulk query that clears the persistence context flushes it first.
 *
 * <p>{@code clearAutomatically} drops every managed entity after the statement — including the ones
 * still waiting to be inserted. Hibernate auto-flushes before a bulk statement only the tables that
 * statement touches, so anything else saved earlier in the transaction is discarded without a word.
 * Found 2026-10-18: deleting a booked slot or event logged "cancelled by admin" for each client and
 * then ran the waitlist and reservation deletes — the log rows never reached the database, so the
 * one timeline entry a client's card exists to show was missing after every such deletion.
 */
class BulkQueryFlushesFirstTest {

    private static final Pattern MODIFYING = Pattern.compile("@Modifying\\s*\\(([^)]*)\\)");

    @Test
    void shouldFlushBeforeEveryBulkQueryThatClearsTheContext() {
        List<String> clearingWithoutFlush = new ArrayList<>();
        int seen = 0;
        for (Path file : SourceFiles.mainJavaFiles()) {
            Matcher matcher = MODIFYING.matcher(SourceFiles.readWithoutComments(file));
            while (matcher.find()) {
                String attributes = matcher.group(1);
                if (!attributes.contains("clearAutomatically = true")) continue;
                seen++;
                if (!attributes.contains("flushAutomatically = true")) {
                    clearingWithoutFlush.add(file.getFileName().toString());
                }
            }
        }
        assertTrue(seen >= 30, "The scan must find the bulk queries it guards (found " + seen + ")");
        assertEquals(List.of(), clearingWithoutFlush,
            "clearAutomatically without flushAutomatically discards whatever the transaction saved before it");
    }
}
