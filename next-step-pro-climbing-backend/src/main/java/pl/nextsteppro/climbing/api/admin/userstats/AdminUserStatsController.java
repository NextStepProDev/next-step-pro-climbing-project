package pl.nextsteppro.climbing.api.admin.userstats;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Statistics view of the Users panel: the whole base in one response.
 *
 * <p>Its own base path rather than {@code /api/admin/users/stats}: that would sit next to
 * {@code /api/admin/users/{userId}} on the card controller, where a literal segment only wins over
 * the UUID template by Spring's specificity rules. It would work, and it would look like a bug to
 * everybody who read it afterwards.
 *
 * <p>Under {@code /api/admin}, so the {@code admin} rate-limit bucket and the
 * {@code hasRole('ADMIN')} matcher in {@code SecurityConfig} already cover it.
 *
 * <p><b>Read-only, and no parameters.</b> The window that defines an active customer is a policy,
 * not a setting — it ships in the response as a label rather than coming in as a query parameter,
 * so two admins comparing screens are always comparing the same number.
 */
@RestController
@RequestMapping("/api/admin/user-stats")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin User Stats", description = "Aggregate statistics about the user base")
public class AdminUserStatsController {

    private final AdminUserStatsService service;

    public AdminUserStatsController(AdminUserStatsService service) {
        this.service = service;
    }

    @Operation(summary = "User base statistics",
        description = "Totals, registrations per month, the account-to-customer funnel, activity "
            + "cohorts, the top clients by attendance, and the newsletter and athlete breakdowns. "
            + "One snapshot: everything is folded from the same read, so the figures add up "
            + "against each other. Activity is measured in bookings — logins are not recorded.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The statistics",
        content = @Content(schema = @Schema(implementation = UserStatsDto.class))))
    @GetMapping
    public ResponseEntity<UserStatsDto> getStats() {
        return ResponseEntity.ok(service.buildStats());
    }
}
