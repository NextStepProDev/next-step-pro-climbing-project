package pl.nextsteppro.climbing.api.reschedule;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.config.CurrentUserId;

import java.util.List;
import java.util.UUID;

/**
 * Under {@code /api/reservations} on purpose: the request belongs to a booking, and the paths share
 * the booking's rate-limit buckets ({@code /my/...} reads go to {@code reservationread}).
 */
@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reschedule Requests", description = "A booked client asks to move their term")
public class RescheduleController {

    private final RescheduleService rescheduleService;

    public RescheduleController(RescheduleService rescheduleService) {
        this.rescheduleService = rescheduleService;
    }

    @Operation(summary = "Ask to move a booked term",
        description = "Either reservationId (a training) or eventId (an event). The booking stays as it is until the coach accepts.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Request sent"),
        @ApiResponse(responseCode = "400", description = "Invalid term"),
        @ApiResponse(responseCode = "409", description = "Not your booking, term over, or a request already pending")
    })
    @PostMapping("/reschedule-requests")
    public ResponseEntity<RescheduleResultDto> create(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody CreateRescheduleRequest request) {
        return ResponseEntity.ok(rescheduleService.create(userId, request));
    }

    @Operation(summary = "My pending reschedule requests")
    @GetMapping("/my/reschedule-requests")
    public ResponseEntity<List<MyRescheduleRequestDto>> getMyPending(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {
        return ResponseEntity.ok(rescheduleService.getMyPending(userId));
    }

    @Operation(summary = "Withdraw my reschedule request", description = "Only while it still awaits the coach.")
    @DeleteMapping("/reschedule-requests/{requestId}")
    public ResponseEntity<Void> withdraw(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID requestId) {
        rescheduleService.withdraw(userId, requestId);
        return ResponseEntity.noContent().build();
    }
}
