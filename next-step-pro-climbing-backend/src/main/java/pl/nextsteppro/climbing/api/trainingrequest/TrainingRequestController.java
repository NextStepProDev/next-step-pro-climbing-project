package pl.nextsteppro.climbing.api.trainingrequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.config.CurrentUserId;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/training-requests")
@Tag(name = "Training Requests", description = "Training time requests submitted by users")
public class TrainingRequestController {

    private final TrainingRequestService trainingRequestService;

    public TrainingRequestController(TrainingRequestService trainingRequestService) {
        this.trainingRequestService = trainingRequestService;
    }

    @Operation(
        summary = "Propose a training time",
        description = "Submits a training time request (optionally within an availability window or for a course). Requires login."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Request submitted",
            content = @Content(schema = @Schema(implementation = TrainingRequestResultDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid data (date in the past, outside the window)"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "409", description = "Pending request limit reached")
    })
    @PostMapping
    public ResponseEntity<TrainingRequestResultDto> create(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody CreateTrainingRequestRequest request) {
        return ResponseEntity.ok(trainingRequestService.create(userId, request));
    }

    @Operation(summary = "My training requests", description = "Returns the logged-in user's requests (newest first).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of requests",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = TrainingRequestDto.class)))),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @GetMapping("/my")
    public ResponseEntity<List<TrainingRequestDto>> getMy(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {
        return ResponseEntity.ok(trainingRequestService.getUserRequests(userId));
    }

    @Operation(summary = "Withdraw request", description = "Deletes the user's own request while it still awaits an admin response.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Request withdrawn"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "404", description = "Request not found"),
        @ApiResponse(responseCode = "409", description = "Request does not belong to the user or is already resolved")
    })
    @DeleteMapping("/{requestId}")
    public ResponseEntity<Void> cancel(
            @Parameter(description = "UUID propozycji") @PathVariable UUID requestId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {
        trainingRequestService.cancel(requestId, userId);
        return ResponseEntity.noContent().build();
    }
}
