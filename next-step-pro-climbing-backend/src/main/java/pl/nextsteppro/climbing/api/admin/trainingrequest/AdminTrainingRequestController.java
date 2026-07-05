package pl.nextsteppro.climbing.api.admin.trainingrequest;

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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/training-requests")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Training Requests", description = "Propozycje terminów od użytkowników (tylko admin)")
public class AdminTrainingRequestController {

    private final AdminTrainingRequestService adminTrainingRequestService;

    public AdminTrainingRequestController(AdminTrainingRequestService adminTrainingRequestService) {
        this.adminTrainingRequestService = adminTrainingRequestService;
    }

    @Operation(summary = "Wszystkie propozycje", description = "Zwraca propozycje terminów (oczekujące pierwsze, potem najnowsze).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista propozycji",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AdminTrainingRequestDto.class)))),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping
    public ResponseEntity<List<AdminTrainingRequestDto>> getAll() {
        return ResponseEntity.ok(adminTrainingRequestService.getAll());
    }

    @Operation(summary = "Liczba oczekujących propozycji", description = "Licznik do badge'a w nawigacji panelu admina.")
    @GetMapping("/pending-count")
    public ResponseEntity<PendingCountDto> getPendingCount() {
        return ResponseEntity.ok(new PendingCountDto(adminTrainingRequestService.getPendingCount()));
    }

    @Operation(
        summary = "Zmień status propozycji",
        description = "CONTACTED / REJECTED (opcjonalna notatka + mail do użytkownika przy odrzuceniu) lub PENDING (przywrócenie). ACCEPTED powstaje przez utworzenie slotu/wydarzenia z trainingRequestId."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status zmieniony",
            content = @Content(schema = @Schema(implementation = AdminTrainingRequestDto.class))),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowy status lub propozycja nie istnieje"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PutMapping("/{requestId}/status")
    public ResponseEntity<AdminTrainingRequestDto> updateStatus(
            @Parameter(description = "UUID propozycji") @PathVariable UUID requestId,
            @Valid @RequestBody UpdateTrainingRequestStatusRequest request) {
        return ResponseEntity.ok(adminTrainingRequestService.updateStatus(requestId, request));
    }
}
