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
@Tag(name = "Reservations", description = "User reservation management")
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
        summary = "Book a slot",
        description = "Creates a reservation for the chosen slot. Requires login."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reservation created",
            content = @Content(schema = @Schema(implementation = ReservationResultDto.class))),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "400", description = "No free seats or slot blocked"),
        @ApiResponse(responseCode = "409", description = "User already has a reservation for this slot")
    })
    @PostMapping("/slot/{slotId}")
    public ResponseEntity<ReservationResultDto> createReservation(
            @Parameter(description = "Slot UUID") @PathVariable UUID slotId,
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @RequestBody(required = false) CreateReservationRequest body) {

        String comment = body != null ? body.comment() : null;
        int participants = (body != null && body.participants() != null) ? body.participants() : 1;
        ReservationResultDto result = reservationService.createReservation(slotId, userId, comment, participants);
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Cancel reservation",
        description = "Cancels the user's reservation."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Reservation cancelled"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "403", description = "Reservation does not belong to the user"),
        @ApiResponse(responseCode = "404", description = "Reservation not found")
    })
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<Void> cancelReservation(
            @Parameter(description = "Reservation UUID") @PathVariable UUID reservationId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        reservationService.cancelReservation(reservationId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "My reservations",
        description = "Returns all reservations of the logged-in user (past and future)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of reservations",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserReservationDto.class)))),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @GetMapping("/my")
    public ResponseEntity<List<UserReservationDto>> getMyReservations(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        List<UserReservationDto> reservations = reservationService.getUserReservations(userId);
        return ResponseEntity.ok(reservations);
    }

    @Operation(
        summary = "My upcoming reservations",
        description = "Returns future reservations split into single slots and events"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of upcoming reservations",
            content = @Content(schema = @Schema(implementation = MyReservationsDto.class))),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @GetMapping("/my/upcoming")
    public ResponseEntity<MyReservationsDto> getMyUpcomingReservations(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        MyReservationsDto reservations = reservationService.getUserUpcomingReservations(userId);
        return ResponseEntity.ok(reservations);
    }

    @Operation(
        summary = "My past reservations",
        description = "Returns past reservations split into single slots and events"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of past reservations",
            content = @Content(schema = @Schema(implementation = MyReservationsDto.class))),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @GetMapping("/my/past")
    public ResponseEntity<MyReservationsDto> getMyPastReservations(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        MyReservationsDto reservations = reservationService.getUserPastReservations(userId);
        return ResponseEntity.ok(reservations);
    }

    @Operation(
        summary = "My invitations",
        description = "Returns pending invitations (seats held for the user) for upcoming slots and events."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of invitations",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = MyInvitationDto.class)))),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @GetMapping("/my/invitations")
    public ResponseEntity<List<MyInvitationDto>> getMyInvitations(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        return ResponseEntity.ok(reservationService.getMyInvitations(userId));
    }

    @Operation(
        summary = "Register for event",
        description = "Creates reservations for all active slots of the event. Requires login."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Registered for the event",
            content = @Content(schema = @Schema(implementation = EventReservationResultDto.class))),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "400", description = "No free seats or event inactive"),
        @ApiResponse(responseCode = "409", description = "User is already registered for this event")
    })
    @PostMapping("/event/{eventId}")
    public ResponseEntity<EventReservationResultDto> createEventReservation(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @RequestBody(required = false) CreateReservationRequest body) {

        String comment = body != null ? body.comment() : null;
        int participants = (body != null && body.participants() != null) ? body.participants() : 1;
        return ResponseEntity.ok(reservationService.createEventReservation(eventId, userId, comment, participants));
    }

    @Operation(
        summary = "Cancel event registration",
        description = "Cancels all of the user's reservations for the event."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Registration cancelled"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "404", description = "No reservation found for this event")
    })
    @DeleteMapping("/event/{eventId}")
    public ResponseEntity<Void> cancelEventReservation(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        reservationService.cancelEventReservation(eventId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Update slot reservation participant count",
        description = "Changes the participant count for an existing reservation. Requires login."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Updated",
            content = @Content(schema = @Schema(implementation = ReservationResultDto.class))),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "400", description = "Not enough free seats")
    })
    @PutMapping("/{reservationId}/participants")
    public ResponseEntity<ReservationResultDto> updateSlotParticipants(
            @Parameter(description = "Reservation UUID") @PathVariable UUID reservationId,
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @RequestBody UpdateParticipantsRequest body) {

        return ResponseEntity.ok(reservationService.updateSlotParticipants(reservationId, userId, body.participants()));
    }

    @Operation(
        summary = "Update event registration participant count",
        description = "Changes the participant count for all of the user's reservations on an event. Requires login."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Updated",
            content = @Content(schema = @Schema(implementation = EventReservationResultDto.class))),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "400", description = "Not enough free seats")
    })
    @PutMapping("/event/{eventId}/participants")
    public ResponseEntity<EventReservationResultDto> updateEventParticipants(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @RequestBody UpdateParticipantsRequest body) {

        return ResponseEntity.ok(reservationService.updateEventParticipants(eventId, userId, body.participants()));
    }

    // -------------------------------------------------------------------------
    // Waitlist endpoints
    // -------------------------------------------------------------------------

    @Operation(summary = "Join waitlist", description = "Joins the queue when the slot is full. Requires login.")
    @PostMapping("/slot/{slotId}/waitlist")
    public ResponseEntity<WaitlistResultDto> joinWaitlist(
            @PathVariable UUID slotId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        return ResponseEntity.ok(waitlistService.joinWaitlist(slotId, userId));
    }

    @Operation(summary = "Leave waitlist", description = "Removes the user from the queue.")
    @DeleteMapping("/slot/{slotId}/waitlist")
    public ResponseEntity<Void> leaveWaitlist(
            @PathVariable UUID slotId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        waitlistService.leaveWaitlist(slotId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Confirm waitlist offer", description = "Confirms the reservation after receiving an offer (one click).")
    @PostMapping("/waitlist/{waitlistId}/confirm")
    public ResponseEntity<ReservationResultDto> confirmWaitlistOffer(
            @PathVariable UUID waitlistId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        return ResponseEntity.ok(waitlistService.confirmOffer(waitlistId, userId));
    }

    @Operation(summary = "My waitlist entries", description = "Returns active entries (WAITING + PENDING_CONFIRMATION) for the logged-in user.")
    @GetMapping("/my/waitlist")
    public ResponseEntity<List<WaitlistEntryDto>> getMyWaitlist(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        return ResponseEntity.ok(waitlistService.getUserWaitlist(userId));
    }

    // -------------------------------------------------------------------------
    // Event waitlist endpoints
    // -------------------------------------------------------------------------

    @Operation(summary = "Join event waitlist", description = "Joins the queue when the event is full. Requires login.")
    @PostMapping("/event/{eventId}/waitlist")
    public ResponseEntity<WaitlistResultDto> joinEventWaitlist(
            @PathVariable UUID eventId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        return ResponseEntity.ok(eventWaitlistService.joinEventWaitlist(eventId, userId));
    }

    @Operation(summary = "Leave event waitlist", description = "Removes the user from the event queue.")
    @DeleteMapping("/event/{eventId}/waitlist")
    public ResponseEntity<Void> leaveEventWaitlist(
            @PathVariable UUID eventId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        eventWaitlistService.leaveEventWaitlist(eventId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Confirm event waitlist offer", description = "Confirms the event registration after receiving an offer (first come, first served).")
    @PostMapping("/event-waitlist/{waitlistId}/confirm")
    public ResponseEntity<EventReservationResultDto> confirmEventWaitlistOffer(
            @PathVariable UUID waitlistId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        return ResponseEntity.ok(eventWaitlistService.confirmEventOffer(waitlistId, userId));
    }

    @Operation(summary = "My event waitlist entries", description = "Returns active entries (WAITING + PENDING_CONFIRMATION) for the logged-in user.")
    @GetMapping("/my/event-waitlist")
    public ResponseEntity<List<EventWaitlistEntryDto>> getMyEventWaitlist(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        return ResponseEntity.ok(eventWaitlistService.getUserEventWaitlist(userId));
    }
}
