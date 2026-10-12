package pl.nextsteppro.climbing.api.admin.userexport;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.nextsteppro.climbing.config.CurrentUserId;

import java.util.UUID;

/**
 * One person's data in one file, for a GDPR access / portability request. Admin-only: requests
 * arrive by e-mail and the owner answers them from the user card — there is no self-service button.
 *
 * <p>Under {@code /api/admin/users} like the user card, so the {@code admin} rate-limit bucket and
 * the role matcher in {@code SecurityConfig} cover it without a new rule.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin User Data Export", description = "Personal-data export for GDPR requests")
public class UserDataExportController {

    private final UserDataExportService service;

    public UserDataExportController(UserDataExportService service) {
        this.service = service;
    }

    @Operation(summary = "Everything held about one user",
        description = "Account, consents, bookings, queues, invitations, proposals, activity, training "
            + "plan, messages, goals, weigh-ins and logbook. Money is a second request to "
            + "/api/admin/settlements/payers/user/{userId}/export. Files are listed, not embedded. "
            + "Each call is logged under the calling admin.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The export",
            content = @Content(schema = @Schema(implementation = UserDataExportDto.class))),
        @ApiResponse(responseCode = "404", description = "No such user")
    })
    @GetMapping("/{userId}/export")
    public ResponseEntity<UserDataExportDto> export(@PathVariable UUID userId,
                                                    @CurrentUserId UUID adminId) {
        return service.export(userId, adminId)
            // Somebody's whole file must not linger in a browser or proxy cache.
            .map(dto -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dto))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
