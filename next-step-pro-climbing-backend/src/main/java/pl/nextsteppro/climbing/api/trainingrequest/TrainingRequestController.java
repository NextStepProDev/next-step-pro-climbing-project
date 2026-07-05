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
@Tag(name = "Training Requests", description = "Propozycje terminów treningów składane przez użytkowników")
public class TrainingRequestController {

    private final TrainingRequestService trainingRequestService;

    public TrainingRequestController(TrainingRequestService trainingRequestService) {
        this.trainingRequestService = trainingRequestService;
    }

    @Operation(
        summary = "Zaproponuj termin treningu",
        description = "Składa propozycję terminu (opcjonalnie w oknie dostępności lub dla kursu). Wymaga zalogowania."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Propozycja złożona",
            content = @Content(schema = @Schema(implementation = TrainingRequestResultDto.class))),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe dane (termin w przeszłości, poza oknem)"),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany"),
        @ApiResponse(responseCode = "409", description = "Limit oczekujących propozycji wyczerpany")
    })
    @PostMapping
    public ResponseEntity<TrainingRequestResultDto> create(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody CreateTrainingRequestRequest request) {
        return ResponseEntity.ok(trainingRequestService.create(userId, request));
    }

    @Operation(summary = "Moje propozycje terminów", description = "Zwraca propozycje zalogowanego użytkownika (najnowsze pierwsze).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista propozycji",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = TrainingRequestDto.class)))),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany")
    })
    @GetMapping("/my")
    public ResponseEntity<List<TrainingRequestDto>> getMy(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {
        return ResponseEntity.ok(trainingRequestService.getUserRequests(userId));
    }

    @Operation(summary = "Wycofaj propozycję", description = "Usuwa własną propozycję, dopóki czeka na reakcję admina.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Propozycja wycofana"),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany"),
        @ApiResponse(responseCode = "404", description = "Propozycja nie istnieje"),
        @ApiResponse(responseCode = "409", description = "Propozycja nie należy do użytkownika lub jest już rozpatrzona")
    })
    @DeleteMapping("/{requestId}")
    public ResponseEntity<Void> cancel(
            @Parameter(description = "UUID propozycji") @PathVariable UUID requestId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {
        trainingRequestService.cancel(requestId, userId);
        return ResponseEntity.noContent().build();
    }
}
