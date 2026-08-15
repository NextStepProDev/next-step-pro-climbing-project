package pl.nextsteppro.climbing.api.ascent;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.domain.climbingascent.AscentTerrain;

import java.util.UUID;

/**
 * Coach view of an athlete's logbook. Lives in this package (not api/admin/*) to share the
 * package-private DTOs with the climber's own controller — the same arrangement as
 * {@code AdminTrainingCalendarController}.
 *
 * <p><b>Read-only, and designated athletes only.</b> The logbook itself is open to every
 * signed-in user, but being READ by a coach follows from the athlete flag — that is, from a
 * decision somebody actually made. A user who never signed up for 1:1 coaching keeps a private
 * logbook, and there is no write path here at all: crediting somebody else with an ascent is not
 * the coach's call.
 */
@RestController
@RequestMapping("/api/admin/ascents")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Climbing Logbook", description = "Coach view of a designated athlete's ascents")
public class AdminAscentController {

    private final AscentService ascentService;
    private final AscentStatsService ascentStatsService;

    public AdminAscentController(AscentService ascentService, AscentStatsService ascentStatsService) {
        this.ascentService = ascentService;
        this.ascentStatsService = ascentStatsService;
    }

    @Operation(summary = "Athlete's climbing logbook",
        description = "Ascents for one year (or 'all'), the years with data and the athlete's own "
            + "place suggestions. There is deliberately no write endpoint here.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Logbook slice",
            content = @Content(schema = @Schema(implementation = AscentLogDto.class))),
        @ApiResponse(responseCode = "400", description = "Unknown athlete, or not a designated one")
    })
    @GetMapping("/athletes/{athleteId}")
    public ResponseEntity<AscentLogDto> getAscents(
            @PathVariable UUID athleteId,
            @RequestParam(required = false) @Nullable AscentTerrain terrain,
            @Parameter(description = "Four-digit year, or 'all'") @RequestParam(required = false) @Nullable String year) {
        return ResponseEntity.ok(ascentService.getLogForAthlete(athleteId,
                terrain != null ? terrain : AscentTerrain.ROCK, year));
    }

    @Operation(summary = "Athlete's logbook statistics",
        description = "One block per discipline — grade scales are separate axes and never share "
            + "a pyramid. Uncached, like the climber's own view.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Statistics",
            content = @Content(schema = @Schema(implementation = AscentStatsDto.class))),
        @ApiResponse(responseCode = "400", description = "Unknown athlete, or not a designated one")
    })
    @GetMapping("/athletes/{athleteId}/stats")
    public ResponseEntity<AscentStatsDto> getAscentStats(
            @PathVariable UUID athleteId,
            @RequestParam(required = false) @Nullable AscentTerrain terrain,
            @RequestParam(required = false) @Nullable String year) {
        return ResponseEntity.ok(ascentStatsService.getStatsForAthlete(athleteId,
                terrain != null ? terrain : AscentTerrain.ROCK, year));
    }
}
