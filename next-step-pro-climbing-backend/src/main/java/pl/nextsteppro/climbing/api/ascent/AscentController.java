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
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.config.CurrentUserId;
import pl.nextsteppro.climbing.domain.climbingascent.AscentTerrain;

import java.util.List;
import java.util.UUID;

/**
 * A climber's own logbook.
 *
 * <p>Open to every logged-in user, which is why it is mapped on a base of its own rather than
 * under {@code /api/training-calendar}: it carries no health data, so neither the athlete flag
 * nor the GDPR art. 9 consent applies, and a path claiming otherwise would be a lie the next
 * reader has to untangle.
 *
 * <p>Ownership is enforced per row, not per request — the service looks a row up by (id, owner).
 */
@RestController
@RequestMapping("/api/ascents")
@Tag(name = "Climbing Logbook", description = "Completed ascents logged by any signed-in climber")
public class AscentController {

    private final AscentService ascentService;
    private final AscentStatsService ascentStatsService;
    private final PublicAscentService publicAscentService;

    public AscentController(AscentService ascentService,
                            AscentStatsService ascentStatsService,
                            PublicAscentService publicAscentService) {
        this.ascentService = ascentService;
        this.ascentStatsService = ascentStatsService;
        this.publicAscentService = publicAscentService;
    }

    @Operation(summary = "Recent ascents across the club",
        description = "The ten newest ascents by climbers who have not switched public visibility "
            + "off. Ordered by the DATE CLIMBED, not by when the entry was typed in — somebody "
            + "backfilling an old season must not push this week's sends off the list. Public: no "
            + "login required, and it carries only name, route, grade, style, place and date.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Recent ascents",
        content = @Content(schema = @Schema(implementation = PublicAscentDto.class))))
    @GetMapping("/recent")
    public ResponseEntity<List<PublicAscentDto>> getRecent() {
        return ResponseEntity.ok(publicAscentService.getRecent());
    }

    @Operation(summary = "My logbook",
        description = "Ascents for one year (newest first) plus the years that have data, the "
            + "all-time count and the place suggestions the form autocompletes from. The year "
            + "parameter takes a year or the literal 'all'; omitting it selects the newest year "
            + "with data — not the current one, since an empty January would look like data loss.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Logbook slice",
            content = @Content(schema = @Schema(implementation = AscentLogDto.class))),
        @ApiResponse(responseCode = "400", description = "Unparseable year"),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @GetMapping
    public ResponseEntity<AscentLogDto> getMyLog(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Parameter(description = "ROCK (default) or MOUNTAIN") @RequestParam(required = false) @Nullable AscentTerrain terrain,
            @Parameter(description = "Four-digit year, or 'all'") @RequestParam(required = false) @Nullable String year) {
        return ResponseEntity.ok(ascentService.getMyLog(userId, terrainOrRock(terrain), year));
    }

    /** Rock is the default so older links and the rock form need no parameter. */
    private static AscentTerrain terrainOrRock(@Nullable AscentTerrain terrain) {
        return terrain != null ? terrain : AscentTerrain.ROCK;
    }

    @Operation(summary = "My logbook statistics",
        description = "Live-derived, never cached — correcting a grade has to move the numbers in "
            + "the same render. One block per discipline, because grade scales are separate axes: "
            + "7a (French) and 7A (Font) are three grades apart and never share a pyramid. Only "
            + "the figures counted in days or places are shared across disciplines. The "
            + "progression series is all-time whatever year is selected.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Statistics",
            content = @Content(schema = @Schema(implementation = AscentStatsDto.class))),
        @ApiResponse(responseCode = "400", description = "Unparseable year")
    })
    @GetMapping("/stats")
    public ResponseEntity<AscentStatsDto> getMyStats(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @RequestParam(required = false) @Nullable AscentTerrain terrain,
            @RequestParam(required = false) @Nullable String year) {
        return ResponseEntity.ok(ascentStatsService.getMyStats(userId, terrainOrRock(terrain), year));
    }

    @Operation(summary = "Log an ascent",
        description = "The grade must belong to the discipline's scale and the style must apply "
            + "to it (no pinkpoint or toprope on a boulder). Attempts are forced to 1 for OS and "
            + "FLASH, which mean 'first go' by definition.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Created ascent",
            content = @Content(schema = @Schema(implementation = AscentDto.class))),
        @ApiResponse(responseCode = "400", description = "Grade/discipline mismatch, style not applicable, or a future date")
    })
    @PostMapping
    public ResponseEntity<AscentDto> create(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody SaveAscentRequest request) {
        return ResponseEntity.ok(ascentService.createMyAscent(userId, request));
    }

    @Operation(summary = "Correct one of my ascents")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Updated ascent",
            content = @Content(schema = @Schema(implementation = AscentDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid combination, future date, or unknown ascent")
    })
    @PutMapping("/{ascentId}")
    public ResponseEntity<AscentDto> update(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID ascentId,
            @Valid @RequestBody SaveAscentRequest request) {
        return ResponseEntity.ok(ascentService.updateMyAscent(userId, ascentId, request));
    }

    @Operation(summary = "Delete one of my ascents")
    @DeleteMapping("/{ascentId}")
    public ResponseEntity<Void> delete(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID ascentId) {
        ascentService.deleteMyAscent(userId, ascentId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Grade and style catalogue",
        description = "Which scale and which styles each discipline allows, and every grade on "
            + "each scale with its label and rank. Serves BOTH roles from one path — a twin admin "
            + "route would be a second copy of the same list to keep in step.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Catalogue",
            content = @Content(schema = @Schema(implementation = AscentOptionsDto.class))),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @GetMapping("/options")
    public ResponseEntity<AscentOptionsDto> getOptions() {
        return ResponseEntity.ok(ascentService.getOptions());
    }
}
