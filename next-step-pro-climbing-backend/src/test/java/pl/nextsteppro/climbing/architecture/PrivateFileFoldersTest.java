package pl.nextsteppro.climbing.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /api/files/**} is {@code permitAll} for GET and HEAD, and {@link
 * pl.nextsteppro.climbing.api.file.FileController} has one mapping per folder with no generic
 * {@code /{folder}/{filename}} route. That per-folder list <em>is</em> the access control: a folder
 * is world-readable exactly when a method there names it.
 *
 * <p>Until V80 {@code training/} was on that list, so a coach's PDF or an athlete's photo was
 * reachable by anyone holding the URL — out of browser history, a pasted message, a bookmark — with
 * an unguessable filename as its only protection. Both training folders are now streamed with an
 * ownership check instead.
 *
 * <p>This is a gate rather than a code comment because nothing about adding
 * {@code @GetMapping("/training/{filename}")} looks wrong at the call site: it looks like filling
 * an obvious gap, six months from now, in a file full of near-identical methods.
 */
class PrivateFileFoldersTest {

    private static final Path FILE_CONTROLLER =
        Path.of("src/main/java/pl/nextsteppro/climbing/api/file/FileController.java");

    /**
     * Folders holding data about identifiable people's training and health. {@code training} is
     * the coach's materials; {@code commentfiles} is what people attach to thread messages.
     */
    private static final List<String> PRIVATE_FOLDERS = List.of("training", "commentfiles");

    /** Path of any request mapping in the controller, e.g. {@code @GetMapping("/avatars/{filename}")}. */
    private static final Pattern MAPPING_PATH = Pattern.compile(
        "@(?:Get|Post|Put|Patch|Delete|Request)Mapping\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"");

    @Test
    void shouldNotServeAnyPrivateTrainingFolderPublicly() {
        String source = SourceFiles.readWithoutComments(FILE_CONTROLLER);

        Matcher matcher = MAPPING_PATH.matcher(source);
        while (matcher.find()) {
            String path = matcher.group(1);
            String folder = path.replaceAll("^/", "").split("/", 2)[0];
            assertTrue(
                !PRIVATE_FOLDERS.contains(folder),
                """
                FileController maps "%s", which publishes the private folder "%s".

                Everything under /api/files is unauthenticated (SecurityConfig lists it in
                publicReadPaths for GET and HEAD), so this mapping would hand out training and
                health-related files to anyone with the URL.

                Serve it from TrainingCalendarController instead — GET /api/training-calendar/files/{id}
                for coach materials, GET /api/training-calendar/comment-files/{id} for thread
                attachments. Both resolve the owner and check who is asking.
                """.formatted(path, folder));
        }
    }

    @Test
    void shouldStillServeThePublicMediaFolders() {
        // Guards the gate itself: if the regex ever stops matching, the assertion above passes
        // vacuously and this file becomes indistinguishable from one that checks nothing.
        String source = SourceFiles.readWithoutComments(FILE_CONTROLLER);
        Matcher matcher = MAPPING_PATH.matcher(source);

        int mappings = 0;
        while (matcher.find()) {
            mappings++;
        }
        assertTrue(mappings >= 5,
            "Expected the public media mappings (gallery, news, courses, avatars, ...) to be found; "
                + "matched " + mappings + ". The mapping regex is probably stale.");
    }
}
