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
@Tag(name = "Admin - Training Requests", description = "Propozycje terminów od użytkowników (tylko admin)")
public class AdminTrainingRequestController {

    private final AdminTrainingRequestService adminTrainingRequestService;

    public AdminTrainingRequestController(AdminTrainingRequestService adminTrainingRequestService) {
        this.adminTrainingRequestService = adminTrainingRequestService;
    }

    @Operation(
        summary = "Propozycje (stronicowane)",
        description = "Bez filtra statusu: oczekujące pierwsze, potem najnowsze. Z filtrem: najnowsze pierwsze."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Strona propozycji",
            content = @Content(schema = @Schema(implementation = AdminTrainingRequestPageDto.class))),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping
    public ResponseEntity<AdminTrainingRequestPageDto> getPage(
            @Parameter(description = "Filtr statusu (np. PENDING); brak = wszystkie") @RequestParam(required = false) TrainingRequestStatus status,
            @Parameter(description = "Numer strony (od 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Rozmiar strony (max 100)") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminTrainingRequestService.getPage(status, page, size));
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
