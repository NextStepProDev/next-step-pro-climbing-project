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
    private final WaitlistService waitlistService;
    private final EventWaitlistService eventWaitlistService;

    public ReservationController(ReservationService reservationService,
                                 WaitlistService waitlistService,
                                 EventWaitlistService eventWaitlistService) {
        this.reservationService = reservationService;
        this.waitlistService = waitlistService;
        this.eventWaitlistService = eventWaitlistService;
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

        reservationService.cancelEventReservation(eventId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Aktualizuj liczbę uczestników rezerwacji slotu",
        description = "Zmienia liczbę uczestników dla istniejącej rezerwacji. Wymaga zalogowania."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Zaktualizowano",
            content = @Content(schema = @Schema(implementation = ReservationResultDto.class))),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany"),
        @ApiResponse(responseCode = "400", description = "Brak wystarczającej liczby wolnych miejsc")
    })
    @PutMapping("/{reservationId}/participants")
    public ResponseEntity<ReservationResultDto> updateSlotParticipants(
            @Parameter(description = "UUID rezerwacji") @PathVariable UUID reservationId,
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @RequestBody UpdateParticipantsRequest body) {

        return ResponseEntity.ok(reservationService.updateSlotParticipants(reservationId, userId, body.participants()));
    }

    @Operation(
        summary = "Aktualizuj liczbę uczestników zapisu na wydarzenie",
        description = "Zmienia liczbę uczestników dla wszystkich rezerwacji użytkownika na wydarzenie. Wymaga zalogowania."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Zaktualizowano",
            content = @Content(schema = @Schema(implementation = EventReservationResultDto.class))),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany"),
        @ApiResponse(responseCode = "400", description = "Brak wystarczającej liczby wolnych miejsc")
    })
    @PutMapping("/event/{eventId}/participants")
    public ResponseEntity<EventReservationResultDto> updateEventParticipants(
            @Parameter(description = "UUID wydarzenia") @PathVariable UUID eventId,
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @RequestBody UpdateParticipantsRequest body) {

        return ResponseEntity.ok(reservationService.updateEventParticipants(eventId, userId, body.participants()));
    }

    // -------------------------------------------------------------------------
    // Waitlist endpoints
    // -------------------------------------------------------------------------

    @Operation(summary = "Dołącz do listy oczekujących", description = "Dołącza do kolejki gdy slot jest pełny. Wymaga zalogowania.")
    @PostMapping("/slot/{slotId}/waitlist")
    public ResponseEntity<WaitlistResultDto> joinWaitlist(
            @PathVariable UUID slotId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        return ResponseEntity.ok(waitlistService.joinWaitlist(slotId, userId));
    }

    @Operation(summary = "Opuść listę oczekujących", description = "Usuwa użytkownika z kolejki.")
    @DeleteMapping("/slot/{slotId}/waitlist")
    public ResponseEntity<Void> leaveWaitlist(
            @PathVariable UUID slotId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        waitlistService.leaveWaitlist(slotId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Potwierdź ofertę z listy oczekujących", description = "Potwierdza rezerwację po otrzymaniu oferty (jedno kliknięcie).")
    @PostMapping("/waitlist/{waitlistId}/confirm")
    public ResponseEntity<ReservationResultDto> confirmWaitlistOffer(
            @PathVariable UUID waitlistId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        return ResponseEntity.ok(waitlistService.confirmOffer(waitlistId, userId));
    }

    @Operation(summary = "Moje wpisy na liście oczekujących", description = "Zwraca aktywne wpisy (WAITING + PENDING_CONFIRMATION) dla zalogowanego użytkownika.")
    @GetMapping("/my/waitlist")
    public ResponseEntity<List<WaitlistEntryDto>> getMyWaitlist(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        return ResponseEntity.ok(waitlistService.getUserWaitlist(userId));
    }

    // -------------------------------------------------------------------------
    // Event waitlist endpoints
    // -------------------------------------------------------------------------

    @Operation(summary = "Dołącz do listy oczekujących na wydarzenie", description = "Dołącza do kolejki gdy wydarzenie jest pełne. Wymaga zalogowania.")
    @PostMapping("/event/{eventId}/waitlist")
    public ResponseEntity<WaitlistResultDto> joinEventWaitlist(
            @PathVariable UUID eventId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        return ResponseEntity.ok(eventWaitlistService.joinEventWaitlist(eventId, userId));
    }

    @Operation(summary = "Opuść listę oczekujących na wydarzenie", description = "Usuwa użytkownika z kolejki na wydarzenie.")
    @DeleteMapping("/event/{eventId}/waitlist")
    public ResponseEntity<Void> leaveEventWaitlist(
            @PathVariable UUID eventId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        eventWaitlistService.leaveEventWaitlist(eventId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Potwierdź ofertę z listy oczekujących na wydarzenie", description = "Potwierdza zapis na wydarzenie po otrzymaniu oferty (kto pierwszy, ten lepszy).")
    @PostMapping("/event-waitlist/{waitlistId}/confirm")
    public ResponseEntity<EventReservationResultDto> confirmEventWaitlistOffer(
            @PathVariable UUID waitlistId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        return ResponseEntity.ok(eventWaitlistService.confirmEventOffer(waitlistId, userId));
    }

    @Operation(summary = "Moje wpisy na listach oczekujących na wydarzenia", description = "Zwraca aktywne wpisy (WAITING + PENDING_CONFIRMATION) dla zalogowanego użytkownika.")
    @GetMapping("/my/event-waitlist")
    public ResponseEntity<List<EventWaitlistEntryDto>> getMyEventWaitlist(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        return ResponseEntity.ok(eventWaitlistService.getUserEventWaitlist(userId));
    }
}
