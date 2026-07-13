package pl.nextsteppro.climbing.api.admin.trainingrequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequestStatus;

@RestController
@RequestMapping("/api/admin/training-requests")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Training Requests", description = "Training requests from users (admin only)")
public class AdminTrainingRequestController {

    private final AdminTrainingRequestService adminTrainingRequestService;

    public AdminTrainingRequestController(AdminTrainingRequestService adminTrainingRequestService) {
        this.adminTrainingRequestService = adminTrainingRequestService;
    }

    @Operation(
        summary = "Requests (paginated)",
        description = "Without a status filter: pending first, then newest. With a filter: newest first."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of requests",
            content = @Content(schema = @Schema(implementation = AdminTrainingRequestPageDto.class))),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping
    public ResponseEntity<AdminTrainingRequestPageDto> getPage(
            @Parameter(description = "Status filter (e.g. PENDING); empty = all") @RequestParam(required = false) TrainingRequestStatus status,
            @Parameter(description = "Page number (from 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminTrainingRequestService.getPage(status, page, size));
    }

    @Operation(
        summary = "Change request status",
        description = "CONTACTED / REJECTED (optional note + email to the user on rejection) or PENDING (restore). ACCEPTED is only created by creating a slot/event with trainingRequestId."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status changed",
            content = @Content(schema = @Schema(implementation = AdminTrainingRequestDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid status or request does not exist"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PutMapping("/{requestId}/status")
    public ResponseEntity<AdminTrainingRequestDto> updateStatus(
            @Parameter(description = "UUID propozycji") @PathVariable UUID requestId,
            @Valid @RequestBody UpdateTrainingRequestStatusRequest request) {
        return ResponseEntity.ok(adminTrainingRequestService.updateStatus(requestId, request));
    }
}
