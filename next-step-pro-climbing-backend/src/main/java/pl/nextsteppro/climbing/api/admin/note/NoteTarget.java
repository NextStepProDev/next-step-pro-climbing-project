package pl.nextsteppro.climbing.api.admin.note;

import java.util.Locale;
import java.util.Optional;

/**
 * What a private note is attached to. Travels as a path segment
 * ({@code /api/admin/notes/slot/{id}}), never into the database — the table keeps three separate
 * foreign keys instead of a discriminator, so that a note dies with the slot, event or training it
 * describes (see V89).
 *
 * <p>One typed segment rather than three endpoint families keeps the whole feature on a single code
 * path. The slot/event twinning in this codebase has repeatedly failed the same way: the fix lands
 * in one copy.
 */
public enum NoteTarget {
    SLOT,
    EVENT,
    TRAINING;

    /**
     * Parses the lower-case path segment. Bound by hand rather than by declaring
     * {@code @PathVariable NoteTarget}: Spring matches enum constants by exact name, so the URL
     * would have to shout {@code /notes/SLOT/…}. Returns empty rather than throwing so the caller
     * can raise a translated message — the raw segment comes from the client and does not belong
     * in the response.
     */
    public static Optional<NoteTarget> tryFrom(String segment) {
        try {
            return Optional.of(valueOf(segment.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
