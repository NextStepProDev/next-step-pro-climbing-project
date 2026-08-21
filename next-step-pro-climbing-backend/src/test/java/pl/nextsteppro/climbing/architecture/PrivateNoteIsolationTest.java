package pl.nextsteppro.climbing.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The owner's private notes must stay unreadable from anywhere that builds a shared response.
 *
 * <p>The risk this gate exists for is not a missing permission check — it is a helpful field. Every
 * shape describing a session is shared: {@code TimeSlotDto} and {@code DaySummaryDto} are served to
 * anonymous visitors and cached under {@code calendarMonth/Week/Day} whenever {@code userId == null},
 * and {@code PersonalTrainingDto} is one record for both the coach and the athlete. Adding
 * {@code privateNote} to any of them would compile, would look like a convenience, and would publish
 * one admin's notebook to the people it is written about.
 *
 * <p>Rather than asserting the absence of a field name — which drifts, and which cannot cover a DTO
 * nobody has written yet — this pins the reachability: the note type and its repository are visible
 * only inside their own two packages. A service that cannot read a note cannot leak one.
 *
 * <p>To see this gate red: inject {@code AdminPrivateNoteRepository} into {@code CalendarService}.
 */
class PrivateNoteIsolationTest {

    private static final String NOTE_PACKAGE = "pl.nextsteppro.climbing.domain.adminnote";

    // Longest first: \bAdminPrivateNote\b also matches inside nothing else, but reporting the
    // repository as the plain entity would send the reader to the wrong file.
    private static final List<String> NOTE_TYPES =
        List.of("AdminPrivateNoteRepository", "AdminPrivateNote");

    /**
     * The only two packages allowed to touch the note: the entity's own home, and the admin API
     * that serves it. Widening this list is a decision about who can read the owner's notebook —
     * make it deliberately, not by adding an import.
     */
    private static final Set<String> ALLOWED_PACKAGE_PATHS = Set.of(
        "pl/nextsteppro/climbing/domain/adminnote",
        "pl/nextsteppro/climbing/api/admin/note"
    );

    @Test
    @DisplayName("shouldKeepThePrivateNoteUnreachableFromEveryOtherPackage")
    void shouldKeepThePrivateNoteUnreachableFromEveryOtherPackage() {
        List<String> offenders = new ArrayList<>();

        for (Path file : SourceFiles.mainJavaFiles()) {
            String path = file.toString().replace('\\', '/');
            if (ALLOWED_PACKAGE_PATHS.stream().anyMatch(path::contains)) continue;

            String source = SourceFiles.readWithoutComments(file);

            // The package in any import shape. `import ...domain.adminnote.*;` is the local house
            // style — three services already import their own domain package that way — so keying
            // only off the fully qualified type name left the most likely bypass wide open.
            if (source.contains(NOTE_PACKAGE)) {
                offenders.add(path + " imports " + NOTE_PACKAGE);
                continue;
            }
            for (String type : NOTE_TYPES) {
                // Word boundaries, not `type + " "`: a wildcard import followed by
                // `AdminPrivateNote.MAX_BODY_LENGTH` or `List<AdminPrivateNote>` puts a `.` or a
                // `>` after the name, and the old check waved both through.
                if (Pattern.compile("\\b" + type + "\\b").matcher(source).find()) {
                    offenders.add(path + " references " + type);
                    break;
                }
            }
        }

        assertEquals(List.of(), offenders,
            "A private note must not be readable outside domain/adminnote and api/admin/note. "
                + "Every shape describing a session is shared with clients, athletes or the "
                + "anonymous calendar cache, so a note reachable from one of those services is a "
                + "note one field away from being published.");
    }

    @Test
    @DisplayName("shouldFindTheNoteSourcesItClaimsToGuard")
    void shouldFindTheNoteSourcesItClaimsToGuard() {
        // Self-check: a gate scanning the wrong tree passes silently and proves nothing.
        long guarded = SourceFiles.mainJavaFiles().stream()
            .map(p -> p.toString().replace('\\', '/'))
            .filter(p -> ALLOWED_PACKAGE_PATHS.stream().anyMatch(p::contains))
            .count();

        assertTrue(guarded >= 5,
            "Expected the note's entity, repository, controller, service and DTOs — found " + guarded);
    }
}
