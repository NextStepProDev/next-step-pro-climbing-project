package pl.nextsteppro.climbing.api.reservation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.config.CurrentUserService;
import pl.nextsteppro.climbing.config.CustomOAuth2User;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservations", description = "Zarządzanie rezerwacjami użytkownika")
public class ReservationController {

    private final ReservationService reservationService;
    private final CurrentUserService currentUserService;

    public ReservationController(ReservationService reservationService, CurrentUserService currentUserService) {
        this.reservationService = reservationService;
        this.currentUserService = currentUserService;
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
            @Parameter(hidden = true) @AuthenticationPrincipal CustomOAuth2User oAuth2User,
            @RequestBody(required = false) CreateReservationRequest body,
            HttpServletRequest request) {

        UUID userId = currentUserService.getCurrentUserId(oAuth2User, request).orElse(null);
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
        description = "Anuluje rezerwację użytkownika. Automatycznie powiadamia pierwszą osobę z listy rezerwowej."
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
            @Parameter(hidden = true) @AuthenticationPrincipal CustomOAuth2User oAuth2User,
            HttpServletRequest request) {

        UUID userId = currentUserService.getCurrentUserId(oAuth2User, request).orElse(null);
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
            @Parameter(hidden = true) @AuthenticationPrincipal CustomOAuth2User oAuth2User,
            HttpServletRequest request) {

        UUID userId = currentUserService.getCurrentUserId(oAuth2User, request).orElse(null);
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
            @Parameter(hidden = true) @AuthenticationPrincipal CustomOAuth2User oAuth2User,
            HttpServletRequest request) {

        UUID userId = currentUserService.getCurrentUserId(oAuth2User, request).orElse(null);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        MyReservationsDto reservations = reservationService.getUserUpcomingReservations(userId);
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
            @Parameter(hidden = true) @AuthenticationPrincipal CustomOAuth2User oAuth2User,
            @RequestBody(required = false) CreateReservationRequest body,
            HttpServletRequest request) {

        UUID userId = currentUserService.getCurrentUserId(oAuth2User, request).orElse(null);
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
            @Parameter(hidden = true) @AuthenticationPrincipal CustomOAuth2User oAuth2User,
            HttpServletRequest request) {

        UUID userId = currentUserService.getCurrentUserId(oAuth2User, request).orElse(null);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        reservationService.cancelEventReservation(eventId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Zapisz na listę rezerwową",
        description = "Dodaje użytkownika do listy rezerwowej gdy brak wolnych miejsc. Użytkownik otrzyma email gdy miejsce się zwolni."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dodano do listy rezerwowej",
            content = @Content(schema = @Schema(implementation = WaitlistResultDto.class))),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany"),
        @ApiResponse(responseCode = "400", description = "Są jeszcze wolne miejsca - użyj rezerwacji"),
        @ApiResponse(responseCode = "409", description = "Użytkownik już jest na liście rezerwowej")
    })
    @PostMapping("/waitlist/slot/{slotId}")
    public ResponseEntity<WaitlistResultDto> joinWaitlist(
            @Parameter(description = "UUID terminu") @PathVariable UUID slotId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomOAuth2User oAuth2User,
            HttpServletRequest request) {

        UUID userId = currentUserService.getCurrentUserId(oAuth2User, request).orElse(null);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        WaitlistResultDto result = reservationService.joinWaitlist(slotId, userId);
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Usuń z listy rezerwowej",
        description = "Usuwa użytkownika z listy rezerwowej"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Usunięto z listy rezerwowej"),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany"),
        @ApiResponse(responseCode = "403", description = "Wpis nie należy do użytkownika"),
        @ApiResponse(responseCode = "404", description = "Wpis nie istnieje")
    })
    @DeleteMapping("/waitlist/{entryId}")
    public ResponseEntity<Void> leaveWaitlist(
            @Parameter(description = "UUID wpisu na liście rezerwowej") @PathVariable UUID entryId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomOAuth2User oAuth2User,
            HttpServletRequest request) {

        UUID userId = currentUserService.getCurrentUserId(oAuth2User, request).orElse(null);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        reservationService.leaveWaitlist(entryId, userId);
        return ResponseEntity.noContent().build();
    }
}
