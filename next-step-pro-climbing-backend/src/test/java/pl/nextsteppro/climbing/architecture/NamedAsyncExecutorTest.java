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
 * Every {@code @Async} names its executor.
 *
 * <p>There are two {@code Executor} beans (transactional mail, mail campaigns), neither named
 * {@code taskExecutor}, and no {@code AsyncConfigurer} — so a bare {@code @Async} is not routed to
 * either. Spring falls back to {@code SimpleAsyncTaskExecutor}: a new thread per task, no bound, and
 * none of the failure logging the pools decorate their tasks with. Found 2026-10-18 by reading the
 * thread name of a password-reset mail on a live instance ({@code SimpleAsyncTaskExecutor-1}): every
 * verification, reset and waitlist mail went that way.
 */
class NamedAsyncExecutorTest {

    private static final Pattern ASYNC = Pattern.compile("@Async\\b(\\s*\\(\\s*\"?([A-Za-z]*)\"?\\s*\\))?");

    @Test
    void shouldNameTheExecutorOnEveryAsyncMethod() {
        List<String> bare = new ArrayList<>();
        int seen = 0;
        for (Path file : SourceFiles.mainJavaFiles()) {
            Matcher matcher = ASYNC.matcher(SourceFiles.readWithoutComments(file));
            while (matcher.find()) {
                seen++;
                String name = matcher.group(2);
                if (name == null || name.isBlank()) {
                    bare.add(file.getFileName().toString());
                }
            }
        }
        assertTrue(seen >= 30, "The scan must find the async methods it guards (found " + seen + ")");
        assertEquals(List.of(), bare, "@Async without an executor name runs on an unbounded fallback");
    }

    /**
     * Every cron names its zone. Without one the expression runs in the JVM's zone: UTC in the
     * container, Europe/Warsaw on a developer's machine — so "03:50" meant two different hours
     * depending on where the code ran, and nothing in the source said which was meant. UTC is the
     * production behaviour and is now written down; the jobs compute their dates in Warsaw anyway.
     */
    @Test
    void shouldNameTheZoneOfEveryCron() {
        List<String> zoneless = new ArrayList<>();
        Pattern cron = Pattern.compile("@Scheduled\\s*\\(([^)]*cron\\s*=[^)]*)\\)");
        for (Path file : SourceFiles.mainJavaFiles()) {
            Matcher matcher = cron.matcher(SourceFiles.readWithoutComments(file));
            while (matcher.find()) {
                if (!matcher.group(1).contains("zone")) zoneless.add(file.getFileName().toString());
            }
        }
        assertEquals(List.of(), zoneless);
    }
}
