package pl.nextsteppro.climbing.api.admin.note;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.config.CurrentUserId;

import java.util.UUID;

/**
 * The owner's private notes about individual sessions.
 *
 * <p><b>Private means private to the author,</b> not to the ADMIN role: every operation is scoped
 * to the caller's own id, so a second admin would see his own (empty) notebook on the same slot.
 *
 * <p>The target type is a path segment — {@code /api/admin/notes/{slot|event|training}/{id}} —
 * so the whole feature is three endpoints on one code path instead of three near-identical
 * families. Under {@code /api/admin}, so the {@code admin} rate-limit bucket and the
 * {@code hasRole('ADMIN')} matcher in {@code SecurityConfig} already cover it.
 */
@RestController
@RequestMapping("/api/admin/notes")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Private Notes", description = "The calling admin's own notes about sessions — never visible to anybody else")
public class AdminNoteController {

    private final AdminNoteService adminNoteService;

    public AdminNoteController(AdminNoteService adminNoteService) {
        this.adminNoteService = adminNoteService;
    }

    @Operation(summary = "Read my note for a session",
        description = "Returns the calling admin's note for the slot, event or training. Both fields "
            + "are null when there is none — a 200 with an empty body, so the client never has to "
            + "interpret a missing response body.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The note, or an empty one"),
        @ApiResponse(responseCode = "400", description = "Unknown target type, missing target, or a slot that belongs to an event")
    })
    @GetMapping("/{targetType}/{targetId}")
    public ResponseEntity<AdminNoteDto> getNote(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @Parameter(description = "slot, event or training") @PathVariable String targetType,
            @PathVariable UUID targetId) {
        return ResponseEntity.ok(adminNoteService.getNote(adminId, targetType, targetId));
    }

    @Operation(summary = "Save my note for a session",
        description = "Idempotent upsert. An event takes one note however many days it spans, so a "
            + "slot that belongs to an event is refused — write on the event instead.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Saved"),
        @ApiResponse(responseCode = "400", description = "Blank or oversized text, unknown target type, or a slot that belongs to an event")
    })
    @PutMapping("/{targetType}/{targetId}")
    public ResponseEntity<Void> saveNote(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @Parameter(description = "slot, event or training") @PathVariable String targetType,
            @PathVariable UUID targetId,
            @Valid @RequestBody SaveAdminNoteRequest request) {
        adminNoteService.saveNote(adminId, targetType, targetId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete my note for a session",
        description = "Idempotent — deleting a note that is not there succeeds.")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Deleted, or there was nothing to delete"))
    @DeleteMapping("/{targetType}/{targetId}")
    public ResponseEntity<Void> deleteNote(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @Parameter(description = "slot, event or training") @PathVariable String targetType,
            @PathVariable UUID targetId) {
        adminNoteService.deleteNote(adminId, targetType, targetId);
        return ResponseEntity.noContent().build();
    }
}
