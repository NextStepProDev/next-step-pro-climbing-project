package pl.nextsteppro.climbing.api.admin.userhistory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogDto;

import java.util.List;
import java.util.UUID;

/**
 * The admin's user card: one person's account state and history, read-only.
 *
 * <p>Sits under {@code /api/admin/users} alongside the mutations already on
 * {@code AdminController} — a separate class because those are actions on an account and these are
 * questions about one, and because the card's DTOs are its own. Falling under {@code /api/admin}
 * also means the {@code admin} rate-limit bucket and the {@code hasRole('ADMIN')} matcher in
 * {@code SecurityConfig} cover it without a new rule.
 *
 * <p><b>No write endpoints, deliberately.</b> Changing a role, the athlete flag or forcing a logout
 * stays in the user list; the coach's calendar stays the place to edit training. Reading somebody's
 * whole history in one screen is not a reason to grow new ways of changing it.
 *
 * <p>The Training tab has no endpoint here at all: it reuses
 * {@code /api/admin/training-calendar/athletes/{id}} and {@code /api/admin/ascents/athletes/{id}},
 * which already refuse users without the athlete flag. The card asks for them only when
 * {@code UserDetailDto.athlete()} is true.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin User History", description = "Read-only card for a single user")
public class AdminUserHistoryController {

    private final AdminUserHistoryService service;

    public AdminUserHistoryController(AdminUserHistoryService service) {
        this.service = service;
    }

    @Operation(summary = "User card",
        description = "Account state, verification and lockout, newsletter and consent trail, plus "
            + "the headline counts. The two training counts are null for anyone without the athlete "
            + "flag — that data is out of the admin's reach, which is not the same as a zero.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The user card",
            content = @Content(schema = @Schema(implementation = UserDetailDto.class))),
        @ApiResponse(responseCode = "404", description = "No such user")
    })
    @GetMapping("/{userId}")
    public ResponseEntity<UserDetailDto> getUser(@PathVariable UUID userId) {
        return service.getUserDetail(userId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "This user's activity timeline",
        description = "Newest first. Shows what the person did themselves, plus admin cancellations "
            + "of their bookings — those are filed under the affected user, not the admin who "
            + "clicked. Actions taken ON the account stay under the acting admin and are not here.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "A page of log entries"),
        @ApiResponse(responseCode = "404", description = "No such user")
    })
    @GetMapping("/{userId}/activity")
    public ResponseEntity<List<ActivityLogDto>> getActivity(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.getActivity(userId, page, size)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "This user's bookings, queues, held seats and proposals",
        description = "Everything booking-shaped in one response. Upcoming/past are split in "
            + "Europe/Warsaw, since slot times in the database are Warsaw wall-clock. Only the past "
            + "list is paged — it is the one section that grows without a ceiling; the rest hold "
            + "future or active rows only.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Booking history",
            content = @Content(schema = @Schema(implementation = UserReservationHistoryDto.class))),
        @ApiResponse(responseCode = "404", description = "No such user")
    })
    @GetMapping("/{userId}/reservations")
    public ResponseEntity<UserReservationHistoryDto> getReservations(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int pastPage,
            @RequestParam(defaultValue = "25") int pastSize) {
        return service.getReservationHistory(userId, pastPage, pastSize)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
