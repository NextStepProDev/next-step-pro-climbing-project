package pl.nextsteppro.climbing.api.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogDto;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.config.CurrentUserId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final ActivityLogService activityLogService;

    public AdminController(AdminService adminService, ActivityLogService activityLogService) {
        this.adminService = adminService;
        this.activityLogService = activityLogService;
    }

    // ==================== Time Slots Management ====================

    @Tag(name = "Admin - Slots", description = "Slot management (admin only)")
    @Operation(
        summary = "Create slot",
        description = "Creates a new class slot. Can optionally be linked to an event."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Slot created",
            content = @Content(schema = @Schema(implementation = TimeSlotAdminDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/slots")
    public ResponseEntity<TimeSlotAdminDto> createTimeSlot(
            @CurrentUserId UUID adminId,
            @Valid @RequestBody CreateTimeSlotRequest request) {
        TimeSlotAdminDto slot = adminService.createTimeSlot(adminId, request);
        return ResponseEntity.ok(slot);
    }

    @Tag(name = "Admin - Slots")
    @Operation(
        summary = "Update slot",
        description = "Updates slot data (times, max participants, title)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Slot updated",
            content = @Content(schema = @Schema(implementation = TimeSlotAdminDto.class))),
        @ApiResponse(responseCode = "404", description = "Slot not found"),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PutMapping("/slots/{slotId}")
    public ResponseEntity<TimeSlotAdminDto> updateTimeSlot(
            @CurrentUserId UUID adminId,
            @Parameter(description = "Slot UUID") @PathVariable UUID slotId,
            @Valid @RequestBody UpdateTimeSlotRequest request) {
        TimeSlotAdminDto slot = adminService.updateTimeSlot(adminId, slotId, request);
        return ResponseEntity.ok(slot);
    }

    @Tag(name = "Admin - Slots")
    @Operation(
        summary = "Block slot",
        description = "Blocks a slot without providing booker details. Users will see 'reserved'."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Slot blocked"),
        @ApiResponse(responseCode = "404", description = "Slot not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/slots/{slotId}/block")
    public ResponseEntity<Void> blockTimeSlot(
            @CurrentUserId UUID adminId,
            @Parameter(description = "Slot UUID") @PathVariable UUID slotId,
            @Parameter(description = "Optional blocking reason") @RequestParam(required = false) String reason) {
        adminService.blockTimeSlot(adminId, slotId, reason);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Slots")
    @Operation(
        summary = "Unblock slot",
        description = "Unblocks a previously blocked slot"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Slot unblocked"),
        @ApiResponse(responseCode = "404", description = "Slot not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/slots/{slotId}/unblock")
    public ResponseEntity<Void> unblockTimeSlot(
            @CurrentUserId UUID adminId,
            @Parameter(description = "Slot UUID") @PathVariable UUID slotId) {
        adminService.unblockTimeSlot(adminId, slotId);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Slots")
    @Operation(
        summary = "Delete slot",
        description = "Deletes a slot along with all its reservations and waitlist entries"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Slot deleted"),
        @ApiResponse(responseCode = "404", description = "Slot not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @DeleteMapping("/slots/{slotId}")
    public ResponseEntity<Void> deleteTimeSlot(
            @CurrentUserId UUID adminId,
            @Parameter(description = "Slot UUID") @PathVariable UUID slotId) {
        adminService.deleteTimeSlot(adminId, slotId);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Slots")
    @Operation(
        summary = "Notify slot participants",
        description = "Sends a slot-change notification email to all registered participants"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Notifications sent"),
        @ApiResponse(responseCode = "404", description = "Slot not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/slots/{slotId}/notify-participants")
    public ResponseEntity<NotifyParticipantsResult> notifySlotParticipants(
            @Parameter(description = "Slot UUID") @PathVariable UUID slotId,
            @RequestBody(required = false) @Nullable NotifySlotParticipantsRequest request) {
        int count = adminService.notifySlotParticipants(slotId, request);
        return ResponseEntity.ok(new NotifyParticipantsResult(count));
    }

    @Tag(name = "Admin - Slots")
    @Operation(
        summary = "Upcoming slots",
        description = "Returns all slots from the given date (today by default) over 90 days, sorted ascending"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of slots",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = TimeSlotAdminDto.class)))),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping("/slots/upcoming")
    public ResponseEntity<List<TimeSlotAdminDto>> getUpcomingSlots(
            @Parameter(description = "Start date (yyyy-MM-dd), defaults to today")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        LocalDate startDate = from != null ? from : LocalDate.now();
        return ResponseEntity.ok(adminService.getUpcomingSlots(startDate));
    }

    @Tag(name = "Admin - Slots")
    @GetMapping("/slots/past")
    public ResponseEntity<List<TimeSlotAdminDto>> getPastSlots() {
        return ResponseEntity.ok(adminService.getPastSlots());
    }

    @Tag(name = "Admin - Slots")
    @Operation(
        summary = "Slot participant list",
        description = "Returns full details of everyone registered for the slot plus its waitlist"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of participants",
            content = @Content(schema = @Schema(implementation = SlotParticipantsDto.class))),
        @ApiResponse(responseCode = "404", description = "Slot not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping("/slots/{slotId}/participants")
    public ResponseEntity<SlotParticipantsDto> getSlotParticipants(
            @Parameter(description = "Slot UUID") @PathVariable UUID slotId) {
        SlotParticipantsDto participants = adminService.getSlotParticipants(slotId);
        return ResponseEntity.ok(participants);
    }

    @Operation(summary = "Slot waitlist", description = "Returns the waiting queue (WAITING + PENDING_CONFIRMATION) for a slot.")
    @GetMapping("/slots/{slotId}/waitlist")
    public ResponseEntity<SlotWaitlistDto> getSlotWaitlist(
            @Parameter(description = "Slot UUID") @PathVariable UUID slotId) {
        return ResponseEntity.ok(adminService.getSlotWaitlist(slotId));
    }

    @Operation(summary = "Event waitlist", description = "Returns the waiting queue (WAITING + PENDING_CONFIRMATION) for an event.")
    @GetMapping("/events/{eventId}/waitlist")
    public ResponseEntity<EventWaitlistAdminDto> getEventWaitlist(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId) {
        return ResponseEntity.ok(adminService.getEventWaitlist(eventId));
    }

    @Operation(summary = "All active waitlists", description = "Upcoming slots and events with someone on the waitlist, grouped per slot/event.")
    @GetMapping("/waitlists")
    public ResponseEntity<AdminWaitlistsDto> getAdminWaitlists() {
        return ResponseEntity.ok(adminService.getAdminWaitlists());
    }

    @Operation(summary = "Slot invitees", description = "Returns users with invitation-held seats (prefill for the edit form).")
    @GetMapping("/slots/{slotId}/invites")
    public ResponseEntity<List<InvitedUserDto>> getSlotInvites(
            @Parameter(description = "Slot UUID") @PathVariable UUID slotId) {
        return ResponseEntity.ok(adminService.getSlotInvites(slotId));
    }

    @Tag(name = "Admin - Slots")
    @Operation(
        summary = "Send invitation emails (slot)",
        description = "Manually sends emails to people with a held seat who have not booked yet. onlyUnnotified=true (default) skips those already notified."
    )
    @PostMapping("/slots/{slotId}/invites/notify")
    public ResponseEntity<NotifyParticipantsResult> notifySlotInvites(
            @Parameter(description = "Slot UUID") @PathVariable UUID slotId,
            @Parameter(description = "Skip people already sent an invitation") @RequestParam(defaultValue = "true") boolean onlyUnnotified) {
        return ResponseEntity.ok(new NotifyParticipantsResult(adminService.notifySlotInvites(slotId, onlyUnnotified)));
    }

    @Tag(name = "Admin - Slots")
    @Operation(summary = "Register an existing user for a slot", description = "Admin books a chosen user onto a slot — with confirmation email and a 'My reservations' entry")
    @PostMapping("/slots/{slotId}/participants/registered")
    public ResponseEntity<Void> addRegisteredParticipantToSlot(
            @Parameter(description = "Slot UUID") @PathVariable UUID slotId,
            @Valid @RequestBody AddRegisteredParticipantRequest request) {
        adminService.addRegisteredParticipantToSlot(slotId, request);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Slots")
    @Operation(summary = "Register a guest for a slot", description = "Admin books a seat for an unregistered person — no notifications, with a note")
    @PostMapping("/slots/{slotId}/participants/guest")
    public ResponseEntity<GuestParticipantDto> addGuestParticipantToSlot(
            @Parameter(description = "Slot UUID") @PathVariable UUID slotId,
            @Valid @RequestBody AddGuestParticipantRequest request) {
        GuestParticipantDto guest = adminService.addGuestParticipantToSlot(slotId, request);
        return ResponseEntity.ok(guest);
    }

    @Tag(name = "Admin - Slots")
    @Operation(summary = "Remove guest from slot")
    @DeleteMapping("/slots/{slotId}/participants/guest/{guestId}")
    public ResponseEntity<Void> deleteGuestParticipantFromSlot(
            @PathVariable UUID slotId,
            @PathVariable UUID guestId) {
        adminService.deleteGuestParticipantFromSlot(slotId, guestId);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Events")
    @Operation(summary = "Register an existing user for an event", description = "Admin books a chosen user onto an event — with confirmation email and a 'My reservations' entry")
    @PostMapping("/events/{eventId}/participants/registered")
    public ResponseEntity<Void> addRegisteredParticipantToEvent(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Valid @RequestBody AddRegisteredParticipantRequest request) {
        adminService.addRegisteredParticipantToEvent(eventId, request);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Events")
    @Operation(summary = "Register a guest for an event", description = "Admin books a seat for an unregistered person — no notifications, with a note")
    @PostMapping("/events/{eventId}/participants/guest")
    public ResponseEntity<GuestParticipantDto> addGuestParticipantToEvent(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Valid @RequestBody AddGuestParticipantRequest request) {
        GuestParticipantDto guest = adminService.addGuestParticipantToEvent(eventId, request);
        return ResponseEntity.ok(guest);
    }

    @Tag(name = "Admin - Events")
    @Operation(summary = "Remove guest from event")
    @DeleteMapping("/events/{eventId}/participants/guest/{guestId}")
    public ResponseEntity<Void> deleteGuestParticipantFromEvent(
            @PathVariable UUID eventId,
            @PathVariable UUID guestId) {
        adminService.deleteGuestParticipantFromEvent(eventId, guestId);
        return ResponseEntity.noContent().build();
    }

    // ==================== Events Management ====================

    @Tag(name = "Admin - Events", description = "Event management (admin only)")
    @Operation(
        summary = "Create event",
        description = "Creates a new event (course, training, workshop). Automatically generates slots for each day."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Event created",
            content = @Content(schema = @Schema(implementation = EventAdminDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/events")
    public ResponseEntity<EventAdminDto> createEvent(
            @CurrentUserId UUID adminId,
            @Valid @RequestBody CreateEventRequest request) {
        EventAdminDto event = adminService.createEvent(adminId, request);
        return ResponseEntity.ok(event);
    }

    @Tag(name = "Admin - Events")
    @Operation(
        summary = "Update event",
        description = "Updates event data (title, description, seat count)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Event updated",
            content = @Content(schema = @Schema(implementation = EventAdminDto.class))),
        @ApiResponse(responseCode = "404", description = "Event not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PutMapping("/events/{eventId}")
    public ResponseEntity<EventAdminDto> updateEvent(
            @CurrentUserId UUID adminId,
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Valid @RequestBody UpdateEventRequest request) {
        EventAdminDto event = adminService.updateEvent(adminId, eventId, request);
        return ResponseEntity.ok(event);
    }

    @Tag(name = "Admin - Events")
    @Operation(
        summary = "Delete event",
        description = "Deletes an event along with all its linked slots"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Event deleted"),
        @ApiResponse(responseCode = "404", description = "Event not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<Void> deleteEvent(
            @CurrentUserId UUID adminId,
            @Parameter(description = "Event UUID") @PathVariable UUID eventId) {
        adminService.deleteEvent(adminId, eventId);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Events")
    @Operation(
        summary = "List of all events",
        description = "Returns a list of all events with basic information"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of events",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = EventAdminDto.class)))),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping("/events")
    public ResponseEntity<List<EventAdminDto>> getAllEvents() {
        List<EventAdminDto> events = adminService.getAllEvents();
        return ResponseEntity.ok(events);
    }

    @Tag(name = "Admin - Events")
    @Operation(
        summary = "Event details",
        description = "Returns full event data along with its slots and participants"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Event details",
            content = @Content(schema = @Schema(implementation = EventDetailAdminDto.class))),
        @ApiResponse(responseCode = "404", description = "Event not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping("/events/{eventId}")
    public ResponseEntity<EventDetailAdminDto> getEventDetails(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId) {
        EventDetailAdminDto event = adminService.getEventDetails(eventId);
        return ResponseEntity.ok(event);
    }

    @Tag(name = "Admin - Events")
    @Operation(
        summary = "Event participant list",
        description = "Returns unique participants registered for the event (deduplicated per user)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of participants",
            content = @Content(schema = @Schema(implementation = EventParticipantsDto.class))),
        @ApiResponse(responseCode = "404", description = "Event not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping("/events/{eventId}/participants")
    public ResponseEntity<EventParticipantsDto> getEventParticipants(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId) {
        EventParticipantsDto participants = adminService.getEventParticipants(eventId);
        return ResponseEntity.ok(participants);
    }

    @Tag(name = "Admin - Events")
    @Operation(summary = "Event invitees", description = "Returns users with invitation-held seats (prefill for the edit form).")
    @GetMapping("/events/{eventId}/invites")
    public ResponseEntity<List<InvitedUserDto>> getEventInvites(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId) {
        return ResponseEntity.ok(adminService.getEventInvites(eventId));
    }

    @Tag(name = "Admin - Events")
    @Operation(
        summary = "Send invitation emails (event)",
        description = "Manually sends emails to people with a held seat who have not booked yet. onlyUnnotified=true (default) skips those already notified."
    )
    @PostMapping("/events/{eventId}/invites/notify")
    public ResponseEntity<NotifyParticipantsResult> notifyEventInvites(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Parameter(description = "Skip people already sent an invitation") @RequestParam(defaultValue = "true") boolean onlyUnnotified) {
        return ResponseEntity.ok(new NotifyParticipantsResult(adminService.notifyEventInvites(eventId, onlyUnnotified)));
    }

    // ==================== Reservations Overview ====================

    @Tag(name = "Admin - Reservations", description = "Reservation overview (admin only)")
    @Operation(
        summary = "All upcoming reservations",
        description = "Returns all upcoming reservations from today with full participant details"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of reservations",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReservationAdminDto.class)))),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping("/reservations/upcoming")
    public ResponseEntity<List<ReservationAdminDto>> getAllUpcomingReservations() {
        List<ReservationAdminDto> reservations = adminService.getAllUpcomingReservations();
        return ResponseEntity.ok(reservations);
    }

    @Tag(name = "Admin - Reservations")
    @Operation(
        summary = "All past reservations",
        description = "Returns all past reservations with full participant details"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of reservations",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReservationAdminDto.class)))),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping("/reservations/past")
    public ResponseEntity<List<ReservationAdminDto>> getAllPastReservations() {
        List<ReservationAdminDto> reservations = adminService.getAllPastReservations();
        return ResponseEntity.ok(reservations);
    }

    @Tag(name = "Admin - Reservations")
    @Operation(
        summary = "Reservations for a day",
        description = "Returns all reservations for the chosen day with full participant details"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of reservations",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReservationAdminDto.class)))),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping("/reservations/date/{date}")
    public ResponseEntity<List<ReservationAdminDto>> getReservationsByDate(
            @Parameter(description = "Date in yyyy-MM-dd format", example = "2026-02-07")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<ReservationAdminDto> reservations = adminService.getReservationsByDate(date);
        return ResponseEntity.ok(reservations);
    }

    // ==================== Reservation Management ====================

    @Tag(name = "Admin - Reservations")
    @Operation(summary = "Cancel a single reservation", description = "Cancels one reservation and emails the user")
    @DeleteMapping("/reservations/{reservationId}")
    public ResponseEntity<Void> cancelReservationByAdmin(
            @Parameter(description = "Reservation UUID") @PathVariable UUID reservationId) {
        adminService.cancelReservationByAdmin(reservationId);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Reservations")
    @Operation(summary = "Permanently delete an archived reservation", description = "Irreversibly deletes a single past reservation (archive cleanup; no emails or waitlist notifications)")
    @DeleteMapping("/reservations/{reservationId}/permanent")
    public ResponseEntity<Void> deleteReservationPermanently(
            @Parameter(description = "Reservation UUID") @PathVariable UUID reservationId) {
        adminService.deleteReservationPermanently(reservationId);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Events")
    @Operation(summary = "Permanently delete an event's archived reservations", description = "Irreversibly deletes all reservations of a finished event (archive cleanup; the event itself remains)")
    @DeleteMapping("/events/{eventId}/reservations/permanent")
    public ResponseEntity<Void> deletePastEventReservations(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId) {
        adminService.deletePastEventReservations(eventId);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Events")
    @Operation(summary = "Change participant count for an event reservation", description = "Changes the user's reserved seat count on an event and sends a notification email")
    @PatchMapping("/events/{eventId}/participants/{userId}")
    public ResponseEntity<Void> updateEventReservationParticipants(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Parameter(description = "User UUID") @PathVariable UUID userId,
            @Valid @RequestBody UpdateReservationParticipantsRequest request) {
        adminService.updateEventReservationParticipants(eventId, userId, request.participants());
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Events")
    @Operation(summary = "Cancel a user's event registrations", description = "Cancels all of the user's reservations within an event")
    @DeleteMapping("/events/{eventId}/participants/{userId}")
    public ResponseEntity<Void> cancelEventParticipantByAdmin(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Parameter(description = "User UUID") @PathVariable UUID userId) {
        adminService.cancelEventParticipantByAdmin(eventId, userId);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Reservations")
    @Operation(summary = "Change reservation participant count", description = "Changes the reserved seat count and emails the user")
    @PatchMapping("/reservations/{reservationId}/participants")
    public ResponseEntity<Void> updateReservationParticipants(
            @Parameter(description = "Reservation UUID") @PathVariable UUID reservationId,
            @Valid @RequestBody UpdateReservationParticipantsRequest request) {
        adminService.updateReservationParticipants(reservationId, request.participants());
        return ResponseEntity.noContent().build();
    }

    // ==================== Users Management ====================

    @Tag(name = "Admin - Users", description = "User management (admin only)")
    @Operation(
        summary = "List of users",
        description = "Returns a list of all registered users with their details"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of users",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserAdminDto.class)))),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping("/users")
    public ResponseEntity<List<UserAdminDto>> getAllUsers() {
        List<UserAdminDto> users = adminService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @Tag(name = "Admin - Users")
    @Operation(
        summary = "Grant admin privileges",
        description = "Grants the user admin privileges"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Privileges granted"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/users/{userId}/make-admin")
    public ResponseEntity<Void> makeAdmin(
            @CurrentUserId UUID adminId,
            @Parameter(description = "User UUID") @PathVariable UUID userId) {
        adminService.makeAdmin(adminId, userId);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Users")
    @Operation(
        summary = "Revoke admin privileges",
        description = "Changes the user's role from ADMIN to USER."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Privileges revoked"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "400", description = "User is not an administrator"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/users/{userId}/remove-admin")
    public ResponseEntity<Void> removeAdmin(
            @CurrentUserId UUID adminId,
            @Parameter(description = "User UUID") @PathVariable UUID userId) {
        adminService.removeAdmin(adminId, userId);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Users")
    @Operation(
        summary = "Delete user",
        description = "Deletes a user along with all their reservations and waitlist entries. Administrators cannot be deleted."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "User deleted"),
        @ApiResponse(responseCode = "400", description = "Administrators cannot be deleted"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(
            @CurrentUserId UUID adminId,
            @Parameter(description = "User UUID") @PathVariable UUID userId) {
        adminService.deleteUser(adminId, userId);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Users")
    @Operation(
        summary = "Log the user out of all devices",
        description = "Invalidates all of the user's refresh tokens (e.g. after account takeover). Logout takes effect within ≤15 min."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "User sessions invalidated"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/users/{userId}/logout-all")
    public ResponseEntity<Void> forceLogout(
            @CurrentUserId UUID adminId,
            @Parameter(description = "User UUID") @PathVariable UUID userId) {
        adminService.forceLogout(adminId, userId);
        return ResponseEntity.noContent().build();
    }

    // ==================== Mail ====================

    @Tag(name = "Admin - Mail", description = "Sending emails to users")
    @Operation(summary = "Send email to users")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Emails sent",
            content = @Content(schema = @Schema(implementation = MailSendResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/mail/send")
    public ResponseEntity<MailSendResponse> sendMail(@Valid @RequestBody SendMailRequest request) {
        int count = adminService.sendMailToUsers(request);
        return ResponseEntity.ok(new MailSendResponse(count));
    }

    // ==================== Activity Logs ====================

    @Tag(name = "Admin - Activity", description = "User activity logs (admin only)")
    @Operation(
        summary = "Recent activity logs",
        description = "Returns recent user actions (bookings, cancellations, blocks)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of log entries",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ActivityLogDto.class)))),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping("/activity-logs")
    public ResponseEntity<List<ActivityLogDto>> getActivityLogs(
            @Parameter(description = "Page number (from 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (default 20)") @RequestParam(defaultValue = "20") int size) {
        List<ActivityLogDto> logs = activityLogService.getRecentLogs(page, Math.min(size, 100));
        return ResponseEntity.ok(logs);
    }

    // ==================== Notifications ====================

    @Tag(name = "Admin - Notifications", description = "Admin panel notification counters")
    @Operation(
        summary = "Admin notifications",
        description = "Badge counters: pending training requests + new reservations since last read."
    )
    @GetMapping("/notifications")
    public ResponseEntity<AdminNotificationsDto> getNotifications(@CurrentUserId UUID adminId) {
        return ResponseEntity.ok(adminService.getNotifications(adminId));
    }

    @Tag(name = "Admin - Notifications")
    @Operation(
        summary = "Mark reservations as read",
        description = "Sets the read marker for new reservations (called on entering the Reservations tab)."
    )
    @PostMapping("/notifications/reservations-seen")
    public ResponseEntity<Void> markReservationsSeen(@CurrentUserId UUID adminId) {
        adminService.markReservationsSeen(adminId);
        return ResponseEntity.noContent().build();
    }
}
