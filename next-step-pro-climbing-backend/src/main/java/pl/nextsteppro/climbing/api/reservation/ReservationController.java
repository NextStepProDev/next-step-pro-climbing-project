package pl.nextsteppro.climbing.api.reservation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.config.CurrentUserId;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservations", description = "Zarządzanie rezerwacjami użytkownika")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Operation(
        summary = "Zarezerwuj termin",
        description = "Tworzy rezerwację na wybrany termin. Wymaga zalogowania."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Rezerwacja utworzona",
            content = @Content(schema = @Schema(implementation = ReservationResultDto.class))),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany"),
        @ApiResponse(responseCode = "400", description = "Brak wolnych miejsc lub termin zablokowany"),
        @ApiResponse(responseCode = "409", description = "Użytkownik już ma rezerwację na ten termin")
    })
    @PostMapping("/slot/{slotId}")
    public ResponseEntity<ReservationResultDto> createReservation(
            @Parameter(description = "UUID terminu") @PathVariable UUID slotId,
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @RequestBody(required = false) CreateReservationRequest body) {

        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        String comment = body != null ? body.comment() : null;
        int participants = (body != null && body.participants() != null) ? body.participants() : 1;
        ReservationResultDto result = reservationService.createReservation(slotId, userId, comment, participants);
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Anuluj rezerwację",
        description = "Anuluje rezerwację użytkownika."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Rezerwacja anulowana"),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany"),
        @ApiResponse(responseCode = "403", description = "Rezerwacja nie należy do użytkownika"),
        @ApiResponse(responseCode = "404", description = "Rezerwacja nie istnieje")
    })
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<Void> cancelReservation(
            @Parameter(description = "UUID rezerwacji") @PathVariable UUID reservationId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        reservationService.cancelReservation(reservationId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Moje rezerwacje",
        description = "Zwraca wszystkie rezerwacje zalogowanego użytkownika (przeszłe i przyszłe)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista rezerwacji",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserReservationDto.class)))),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany")
    })
    @GetMapping("/my")
    public ResponseEntity<List<UserReservationDto>> getMyReservations(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        List<UserReservationDto> reservations = reservationService.getUserReservations(userId);
        return ResponseEntity.ok(reservations);
    }

    @Operation(
        summary = "Moje nadchodzące rezerwacje",
        description = "Zwraca przyszłe rezerwacje podzielone na pojedyncze terminy i wydarzenia"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista nadchodzących rezerwacji",
            content = @Content(schema = @Schema(implementation = MyReservationsDto.class))),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany")
    })
    @GetMapping("/my/upcoming")
    public ResponseEntity<MyReservationsDto> getMyUpcomingReservations(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        MyReservationsDto reservations = reservationService.getUserUpcomingReservations(userId);
        return ResponseEntity.ok(reservations);
    }

    @Operation(
        summary = "Moje minione rezerwacje",
        description = "Zwraca przeszłe rezerwacje podzielone na pojedyncze terminy i wydarzenia"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista minionych rezerwacji",
            content = @Content(schema = @Schema(implementation = MyReservationsDto.class))),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany")
    })
    @GetMapping("/my/past")
    public ResponseEntity<MyReservationsDto> getMyPastReservations(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        MyReservationsDto reservations = reservationService.getUserPastReservations(userId);
        return ResponseEntity.ok(reservations);
    }

    @Operation(
        summary = "Zapisz na wydarzenie",
        description = "Tworzy rezerwacje na wszystkie aktywne sloty wydarzenia. Wymaga zalogowania."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Zapisano na wydarzenie",
            content = @Content(schema = @Schema(implementation = EventReservationResultDto.class))),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany"),
        @ApiResponse(responseCode = "400", description = "Brak wolnych miejsc lub wydarzenie nieaktywne"),
        @ApiResponse(responseCode = "409", description = "Użytkownik już jest zapisany na to wydarzenie")
    })
    @PostMapping("/event/{eventId}")
    public ResponseEntity<EventReservationResultDto> createEventReservation(
            @Parameter(description = "UUID wydarzenia") @PathVariable UUID eventId,
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @RequestBody(required = false) CreateReservationRequest body) {

        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        String comment = body != null ? body.comment() : null;
        int participants = (body != null && body.participants() != null) ? body.participants() : 1;
        return ResponseEntity.ok(reservationService.createEventReservation(eventId, userId, comment, participants));
    }

    @Operation(
        summary = "Anuluj zapis na wydarzenie",
        description = "Anuluje wszystkie rezerwacje użytkownika na wydarzenie."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Zapis anulowany"),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany"),
        @ApiResponse(responseCode = "404", description = "Nie znaleziono rezerwacji na to wydarzenie")
    })
    @DeleteMapping("/event/{eventId}")
    public ResponseEntity<Void> cancelEventReservation(
            @Parameter(description = "UUID wydarzenia") @PathVariable UUID eventId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        reservationService.cancelEventReservation(eventId, userId);
        return ResponseEntity.noContent().build();
    }
}
