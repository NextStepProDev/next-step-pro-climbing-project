package pl.nextsteppro.climbing.api.ascent;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.domain.climbingascent.AscentTerrain;

import java.util.UUID;

/**
 * The admin's view of one user's logbook. Lives in this package (not api/admin/*) to share the
 * package-private DTOs with the climber's own controller — the same arrangement as
 * {@code AdminTrainingCalendarController}.
 *
 * <p><b>Addressed by user, not by athlete.</b> The logbook is open to every signed-in user, so
 * this screen is too — for anyone who has not switched the visibility off. The path says
 * {@code users} rather than {@code athletes} because that is what it now means; a path claiming
 * otherwise is a lie the next reader has to untangle. Who exactly is readable, and why a
 * designated athlete stays readable regardless of the switch, is decided in one place:
 * {@code AscentService.requireReadableLogbook}.
 *
 * <p><b>Read-only about the logbook; the one write is about the noticeboard.</b> Nothing here
 * creates, edits or deletes an entry — crediting somebody else with an ascent is not the coach's
 * call. The single exception, {@link #setPublicVisibility}, changes only whether one entry appears
 * on the public list; the row itself stays the author's and keeps counting in their statistics.
 * The line: the logbook is the author's, the public list is the site owner's.
 */
@RestController
@RequestMapping("/api/admin/ascents")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Climbing Logbook", description = "Admin view of one user's ascents")
public class AdminAscentController {

    private final AscentService ascentService;
    private final AscentStatsService ascentStatsService;

    public AdminAscentController(AscentService ascentService, AscentStatsService ascentStatsService) {
        this.ascentService = ascentService;
        this.ascentStatsService = ascentStatsService;
    }

    @Operation(summary = "A user's climbing logbook",
        description = "Ascents for one year (or 'all'), the years with data and the user's own "
            + "place suggestions. There is deliberately no write endpoint here.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Logbook slice",
            content = @Content(schema = @Schema(implementation = AscentLogDto.class))),
        @ApiResponse(responseCode = "400", description = "Unknown user, or one who hid their logbook")
    })
    @GetMapping("/users/{userId}")
    public ResponseEntity<AscentLogDto> getAscents(
            @PathVariable UUID userId,
            @RequestParam(required = false) @Nullable AscentTerrain terrain,
            @Parameter(description = "Four-digit year, or 'all'") @RequestParam(required = false) @Nullable String year) {
        return ResponseEntity.ok(ascentService.getLogForAthlete(userId,
                terrain != null ? terrain : AscentTerrain.ROCK, year));
    }

    @Operation(summary = "A user's logbook statistics",
        description = "One block per discipline — grade scales are separate axes and never share "
            + "a pyramid. Uncached, like the climber's own view.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Statistics",
            content = @Content(schema = @Schema(implementation = AscentStatsDto.class))),
        @ApiResponse(responseCode = "400", description = "Unknown user, or one who hid their logbook")
    })
    @GetMapping("/users/{userId}/stats")
    public ResponseEntity<AscentStatsDto> getAscentStats(
            @PathVariable UUID userId,
            @RequestParam(required = false) @Nullable AscentTerrain terrain,
            @RequestParam(required = false) @Nullable String year) {
        return ResponseEntity.ok(ascentStatsService.getStatsForAthlete(userId,
                terrain != null ? terrain : AscentTerrain.ROCK, year));
    }

    @Operation(summary = "Take one entry off the public list, or put it back",
        description = "The site owner's remedy for something they do not want on their own front "
            + "page. Touches publication only — the entry stays in the author's logbook and keeps "
            + "counting in their statistics, because it is still their ascent. Addressed by entry "
            + "rather than by author on purpose: this removes one row from the noticeboard, not a "
            + "person from the list. Only an admin can set or clear it; the author has no control "
            + "over this field, so they cannot undo a takedown from their own settings.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The entry, with its new state",
            content = @Content(schema = @Schema(implementation = AscentDto.class))),
        @ApiResponse(responseCode = "400", description = "Unknown entry, or its author hid their logbook")
    })
    @PutMapping("/entries/{ascentId}/public-visibility")
    public ResponseEntity<AscentDto> setPublicVisibility(
            @PathVariable UUID ascentId,
            @Valid @RequestBody PublicVisibilityRequest request) {
        return ResponseEntity.ok(ascentService.setPublicVisibility(ascentId, request.hidden()));
    }
}
