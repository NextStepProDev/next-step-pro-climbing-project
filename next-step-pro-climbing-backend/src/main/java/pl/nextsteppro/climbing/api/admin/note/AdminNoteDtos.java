package pl.nextsteppro.climbing.api.admin.note;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.adminnote.AdminPrivateNote;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The calling admin's private note for one target. Both fields are {@code null} when nothing has
 * been written — a 200 with an empty body rather than a 204, so the client never has to interpret
 * a missing response body.
 *
 * <p><b>A separate response type, deliberately, rather than a field on {@code TimeSlotDto},
 * {@code EventSummaryDto} or {@code PersonalTrainingDto}.</b> Those shapes are shared: the calendar
 * DTOs are served to anonymous visitors and cached under {@code calendarMonth/Week/Day} whenever
 * {@code userId == null}, and the training DTO is one record for both the coach and the athlete.
 * A note added to any of them leaks by default. Living in a type nothing else returns means no
 * shared shape and no cache has anything to leak — the privacy does not depend on remembering.
 */
record AdminNoteDto(
    @Nullable String body,
    @Nullable Instant updatedAt
) {
    static final AdminNoteDto EMPTY = new AdminNoteDto(null, null);

    static AdminNoteDto of(AdminPrivateNote note) {
        return new AdminNoteDto(note.getBody(), note.getUpdatedAt());
    }
}

/** Upsert payload. Blank is rejected rather than read as a delete — that is what DELETE is for. */
record SaveAdminNoteRequest(
    @NotBlank @Size(max = AdminPrivateNote.MAX_BODY_LENGTH) String body
) {}

/**
 * Which sessions in the visible range the calling admin has already written about.
 *
 * <p><b>Ids only, never text.</b> The calendar needs to answer "is there a note here" for a whole
 * month at once; answering it with the notes themselves would put the notebook into a response
 * that exists to draw icons, and would undo the reason the note has its own per-session endpoint
 * in the first place. The marker says where to look — reading still costs a deliberate open.
 */
record AdminNoteMarkersDto(
    List<UUID> slotIds,
    List<LocalDate> slotDates,
    List<UUID> eventIds,
    List<UUID> trainingIds
) {}
