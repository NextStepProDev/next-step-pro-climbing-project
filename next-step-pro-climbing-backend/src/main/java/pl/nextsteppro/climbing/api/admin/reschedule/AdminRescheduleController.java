package pl.nextsteppro.climbing.api.admin.reschedule;

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

@RestController
@RequestMapping("/api/admin/reschedule-requests")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Reschedule Requests", description = "Clients asking to move a booked term (admin only)")
public class AdminRescheduleController {

    private final AdminRescheduleService adminRescheduleService;

    public AdminRescheduleController(AdminRescheduleService adminRescheduleService) {
        this.adminRescheduleService = adminRescheduleService;
    }

    @Operation(summary = "Pending requests + the most recently answered ones")
    @GetMapping
    public ResponseEntity<AdminRescheduleListDto> list() {
        return ResponseEntity.ok(adminRescheduleService.list());
    }

    @Operation(summary = "Accept: moves the whole slot/event, everyone on it included")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Term moved"),
        @ApiResponse(responseCode = "409", description = "Already answered, booking gone, term moved since, or term over")
    })
    @PostMapping("/{requestId}/accept")
    public ResponseEntity<AcceptRescheduleResultDto> accept(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(adminRescheduleService.accept(adminId, requestId));
    }

    @Operation(summary = "Reject: the booking stays; optionally mails the client with the note")
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<AdminRescheduleRequestDto> reject(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @PathVariable UUID requestId,
            @Valid @RequestBody RejectRescheduleRequest request) {
        return ResponseEntity.ok(adminRescheduleService.reject(adminId, requestId, request));
    }
}
