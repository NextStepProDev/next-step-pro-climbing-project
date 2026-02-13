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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogDto;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final ActivityLogService activityLogService;

    public AdminController(AdminService adminService, ActivityLogService activityLogService) {
        this.adminService = adminService;
        this.activityLogService = activityLogService;
    }

    // ==================== Time Slots Management ====================

    @Tag(name = "Admin - Slots", description = "Zarządzanie terminami (tylko admin)")
    @Operation(
        summary = "Utwórz termin",
        description = "Tworzy nowy termin zajęć. Można opcjonalnie powiązać z wydarzeniem."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Termin utworzony",
            content = @Content(schema = @Schema(implementation = TimeSlotAdminDto.class))),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe dane"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PostMapping("/slots")
    public ResponseEntity<TimeSlotAdminDto> createTimeSlot(
            @Valid @RequestBody CreateTimeSlotRequest request) {
        TimeSlotAdminDto slot = adminService.createTimeSlot(request);
        return ResponseEntity.ok(slot);
    }

    @Tag(name = "Admin - Slots")
    @Operation(
        summary = "Edytuj termin",
        description = "Aktualizuje dane terminu (godziny, maks. uczestników, tytuł)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Termin zaktualizowany",
            content = @Content(schema = @Schema(implementation = TimeSlotAdminDto.class))),
        @ApiResponse(responseCode = "404", description = "Termin nie istnieje"),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe dane"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PutMapping("/slots/{slotId}")
    public ResponseEntity<TimeSlotAdminDto> updateTimeSlot(
            @Parameter(description = "UUID terminu") @PathVariable UUID slotId,
            @Valid @RequestBody UpdateTimeSlotRequest request) {
        TimeSlotAdminDto slot = adminService.updateTimeSlot(slotId, request);
        return ResponseEntity.ok(slot);
    }

    @Tag(name = "Admin - Slots")
    @Operation(
        summary = "Zablokuj termin",
        description = "Blokuje termin bez podawania danych rezerwującego. Użytkownicy zobaczą 'zarezerwowane'."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Termin zablokowany"),
        @ApiResponse(responseCode = "404", description = "Termin nie istnieje"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PostMapping("/slots/{slotId}/block")
    public ResponseEntity<Void> blockTimeSlot(
            @Parameter(description = "UUID terminu") @PathVariable UUID slotId,
            @Parameter(description = "Opcjonalny powód blokady") @RequestParam(required = false) String reason) {
        adminService.blockTimeSlot(slotId, reason);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Slots")
    @Operation(
        summary = "Odblokuj termin",
        description = "Odblokowuje wcześniej zablokowany termin"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Termin odblokowany"),
        @ApiResponse(responseCode = "404", description = "Termin nie istnieje"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PostMapping("/slots/{slotId}/unblock")
    public ResponseEntity<Void> unblockTimeSlot(
            @Parameter(description = "UUID terminu") @PathVariable UUID slotId) {
        adminService.unblockTimeSlot(slotId);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Slots")
    @Operation(
        summary = "Usuń termin",
        description = "Usuwa termin wraz ze wszystkimi rezerwacjami i wpisami na liście rezerwowej"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Termin usunięty"),
        @ApiResponse(responseCode = "404", description = "Termin nie istnieje"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @DeleteMapping("/slots/{slotId}")
    public ResponseEntity<Void> deleteTimeSlot(
            @Parameter(description = "UUID terminu") @PathVariable UUID slotId) {
        adminService.deleteTimeSlot(slotId);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Slots")
    @Operation(
        summary = "Lista uczestników terminu",
        description = "Zwraca pełne dane wszystkich osób zapisanych na termin oraz listę rezerwową"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista uczestników",
            content = @Content(schema = @Schema(implementation = SlotParticipantsDto.class))),
        @ApiResponse(responseCode = "404", description = "Termin nie istnieje"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping("/slots/{slotId}/participants")
    public ResponseEntity<SlotParticipantsDto> getSlotParticipants(
            @Parameter(description = "UUID terminu") @PathVariable UUID slotId) {
        SlotParticipantsDto participants = adminService.getSlotParticipants(slotId);
        return ResponseEntity.ok(participants);
    }

    // ==================== Events Management ====================

    @Tag(name = "Admin - Events", description = "Zarządzanie wydarzeniami (tylko admin)")
    @Operation(
        summary = "Utwórz wydarzenie",
        description = "Tworzy nowe wydarzenie (kurs, trening, warsztat). Automatycznie generuje terminy dla każdego dnia."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Wydarzenie utworzone",
            content = @Content(schema = @Schema(implementation = EventAdminDto.class))),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe dane"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PostMapping("/events")
    public ResponseEntity<EventAdminDto> createEvent(
            @Valid @RequestBody CreateEventRequest request) {
        EventAdminDto event = adminService.createEvent(request);
        return ResponseEntity.ok(event);
    }

    @Tag(name = "Admin - Events")
    @Operation(
        summary = "Aktualizuj wydarzenie",
        description = "Aktualizuje dane wydarzenia (tytuł, opis, liczba miejsc)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Wydarzenie zaktualizowane",
            content = @Content(schema = @Schema(implementation = EventAdminDto.class))),
        @ApiResponse(responseCode = "404", description = "Wydarzenie nie istnieje"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PutMapping("/events/{eventId}")
    public ResponseEntity<EventAdminDto> updateEvent(
            @Parameter(description = "UUID wydarzenia") @PathVariable UUID eventId,
            @Valid @RequestBody UpdateEventRequest request) {
        EventAdminDto event = adminService.updateEvent(eventId, request);
        return ResponseEntity.ok(event);
    }

    @Tag(name = "Admin - Events")
    @Operation(
        summary = "Usuń wydarzenie",
        description = "Usuwa wydarzenie wraz ze wszystkimi powiązanymi terminami"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Wydarzenie usunięte"),
        @ApiResponse(responseCode = "404", description = "Wydarzenie nie istnieje"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<Void> deleteEvent(
            @Parameter(description = "UUID wydarzenia") @PathVariable UUID eventId) {
        adminService.deleteEvent(eventId);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Events")
    @Operation(
        summary = "Lista wszystkich wydarzeń",
        description = "Zwraca listę wszystkich wydarzeń z podstawowymi informacjami"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista wydarzeń",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = EventAdminDto.class)))),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping("/events")
    public ResponseEntity<List<EventAdminDto>> getAllEvents() {
        List<EventAdminDto> events = adminService.getAllEvents();
        return ResponseEntity.ok(events);
    }

    @Tag(name = "Admin - Events")
    @Operation(
        summary = "Szczegóły wydarzenia",
        description = "Zwraca pełne dane wydarzenia wraz z listą terminów i uczestników"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Szczegóły wydarzenia",
            content = @Content(schema = @Schema(implementation = EventDetailAdminDto.class))),
        @ApiResponse(responseCode = "404", description = "Wydarzenie nie istnieje"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping("/events/{eventId}")
    public ResponseEntity<EventDetailAdminDto> getEventDetails(
            @Parameter(description = "UUID wydarzenia") @PathVariable UUID eventId) {
        EventDetailAdminDto event = adminService.getEventDetails(eventId);
        return ResponseEntity.ok(event);
    }

    @Tag(name = "Admin - Events")
    @Operation(
        summary = "Lista uczestników wydarzenia",
        description = "Zwraca listę unikalnych uczestników zapisanych na wydarzenie (deduplikacja po użytkowniku)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista uczestników",
            content = @Content(schema = @Schema(implementation = EventParticipantsDto.class))),
        @ApiResponse(responseCode = "404", description = "Wydarzenie nie istnieje"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping("/events/{eventId}/participants")
    public ResponseEntity<EventParticipantsDto> getEventParticipants(
            @Parameter(description = "UUID wydarzenia") @PathVariable UUID eventId) {
        EventParticipantsDto participants = adminService.getEventParticipants(eventId);
        return ResponseEntity.ok(participants);
    }

    // ==================== Reservations Overview ====================

    @Tag(name = "Admin - Reservations", description = "Przegląd rezerwacji (tylko admin)")
    @Operation(
        summary = "Wszystkie nadchodzące rezerwacje",
        description = "Zwraca wszystkie nadchodzące rezerwacje od dzisiaj z pełnymi danymi uczestników"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista rezerwacji",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReservationAdminDto.class)))),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping("/reservations/upcoming")
    public ResponseEntity<List<ReservationAdminDto>> getAllUpcomingReservations() {
        List<ReservationAdminDto> reservations = adminService.getAllUpcomingReservations();
        return ResponseEntity.ok(reservations);
    }

    @Tag(name = "Admin - Reservations")
    @Operation(
        summary = "Wszystkie minione rezerwacje",
        description = "Zwraca wszystkie przeszłe rezerwacje z pełnymi danymi uczestników"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista rezerwacji",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReservationAdminDto.class)))),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping("/reservations/past")
    public ResponseEntity<List<ReservationAdminDto>> getAllPastReservations() {
        List<ReservationAdminDto> reservations = adminService.getAllPastReservations();
        return ResponseEntity.ok(reservations);
    }

    @Tag(name = "Admin - Reservations")
    @Operation(
        summary = "Rezerwacje na dzień",
        description = "Zwraca wszystkie rezerwacje na wybrany dzień z pełnymi danymi uczestników"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista rezerwacji",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReservationAdminDto.class)))),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping("/reservations/date/{date}")
    public ResponseEntity<List<ReservationAdminDto>> getReservationsByDate(
            @Parameter(description = "Data w formacie yyyy-MM-dd", example = "2026-02-07")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<ReservationAdminDto> reservations = adminService.getReservationsByDate(date);
        return ResponseEntity.ok(reservations);
    }

    // ==================== Users Management ====================

    @Tag(name = "Admin - Users", description = "Zarządzanie użytkownikami (tylko admin)")
    @Operation(
        summary = "Lista użytkowników",
        description = "Zwraca listę wszystkich zarejestrowanych użytkowników z ich danymi"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista użytkowników",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserAdminDto.class)))),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping("/users")
    public ResponseEntity<List<UserAdminDto>> getAllUsers() {
        List<UserAdminDto> users = adminService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @Tag(name = "Admin - Users")
    @Operation(
        summary = "Nadaj uprawnienia admina",
        description = "Nadaje użytkownikowi uprawnienia administratora"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Uprawnienia nadane"),
        @ApiResponse(responseCode = "404", description = "Użytkownik nie istnieje"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PostMapping("/users/{userId}/make-admin")
    public ResponseEntity<Void> makeAdmin(
            @Parameter(description = "UUID użytkownika") @PathVariable UUID userId) {
        adminService.makeAdmin(userId);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Users")
    @Operation(
        summary = "Odbierz uprawnienia administratora",
        description = "Zmienia rolę użytkownika z ADMIN na USER."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Uprawnienia odebrane"),
        @ApiResponse(responseCode = "404", description = "Użytkownik nie istnieje"),
        @ApiResponse(responseCode = "400", description = "Użytkownik nie jest administratorem"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PostMapping("/users/{userId}/remove-admin")
    public ResponseEntity<Void> removeAdmin(
            @Parameter(description = "UUID użytkownika") @PathVariable UUID userId) {
        adminService.removeAdmin(userId);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin - Users")
    @Operation(
        summary = "Usuń użytkownika",
        description = "Usuwa użytkownika wraz ze wszystkimi jego rezerwacjami i wpisami na liście rezerwowej. Nie można usunąć administratora."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Użytkownik usunięty"),
        @ApiResponse(responseCode = "400", description = "Nie można usunąć administratora"),
        @ApiResponse(responseCode = "404", description = "Użytkownik nie istnieje"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "UUID użytkownika") @PathVariable UUID userId) {
        adminService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    // ==================== Activity Logs ====================

    @Tag(name = "Admin - Activity", description = "Logi aktywności użytkowników (tylko admin)")
    @Operation(
        summary = "Ostatnie logi aktywności",
        description = "Zwraca ostatnie akcje użytkowników (zapisy, anulacje, blokady)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista logów",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ActivityLogDto.class)))),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping("/activity-logs")
    public ResponseEntity<List<ActivityLogDto>> getActivityLogs(
            @Parameter(description = "Numer strony (od 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Rozmiar strony (domyślnie 20)") @RequestParam(defaultValue = "20") int size) {
        List<ActivityLogDto> logs = activityLogService.getRecentLogs(page, Math.min(size, 100));
        return ResponseEntity.ok(logs);
    }
}
