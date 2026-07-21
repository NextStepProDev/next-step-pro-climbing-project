package pl.nextsteppro.climbing.api.trainingcalendar;

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
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.config.CurrentUserId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Athlete side of the personal training calendar. Requires login (anyRequest().authenticated());
 * every operation additionally requires the coach-set athlete flag (users.is_athlete).
 */
@RestController
@RequestMapping("/api/training-calendar")
@Tag(name = "Training Calendar", description = "Personal training calendar of a coach-designated athlete")
public class TrainingCalendarController {

    private final TrainingCalendarService trainingCalendarService;
    private final TrainingStatsService trainingStatsService;
    private final AthleteGoalService athleteGoalService;

    public TrainingCalendarController(TrainingCalendarService trainingCalendarService,
                                      TrainingStatsService trainingStatsService,
                                      AthleteGoalService athleteGoalService) {
        this.trainingCalendarService = trainingCalendarService;
        this.trainingStatsService = trainingStatsService;
        this.athleteGoalService = athleteGoalService;
    }

    @Operation(summary = "Calendar range", description = "Trainings + read-only reservation overlay for a date range (max 62 days).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Calendar data",
            content = @Content(schema = @Schema(implementation = CalendarRangeDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid range"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "409", description = "User is not a designated athlete")
    })
    @GetMapping
    public ResponseEntity<CalendarRangeDto> getRange(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(trainingCalendarService.getMyRange(userId, from, to));
    }

    @Operation(summary = "Athlete statistics", description = "Live-derived stats (totals, streaks, heatmap, RPE...) over completed trainings + attended reservations.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Statistics",
            content = @Content(schema = @Schema(implementation = AthleteStatsDto.class))),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "409", description = "User is not a designated athlete")
    })
    @GetMapping("/stats")
    public ResponseEntity<AthleteStatsDto> getStats(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {
        return ResponseEntity.ok(trainingStatsService.getMyStats(userId));
    }

    @Operation(summary = "My goals", description = "Active goals (banner cards, one per horizon) + achieved goals (trophy chest). Read-only — the coach manages goals.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Goals",
            content = @Content(schema = @Schema(implementation = GoalsDto.class))),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "409", description = "User is not a designated athlete")
    })
    @GetMapping("/goals")
    public ResponseEntity<GoalsDto> getGoals(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {
        return ResponseEntity.ok(athleteGoalService.getMyGoals(userId));
    }

    @Operation(summary = "Upload a material file", description = "Stores a PDF/image; reference the returned filename as a FILE attachment when saving the training.")
    @PostMapping(value = "/attachments/upload", consumes = "multipart/form-data")
    public ResponseEntity<AttachmentUploadResponse> uploadAttachment(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(trainingCalendarService.uploadMyAttachment(userId, file));
    }

    @Operation(summary = "Rate an attended reservation", description = "Idempotent upsert of the athlete's RPE (1-10) for a confirmed, already-finished booking.")
    @PutMapping("/reservations/{reservationId}/rpe")
    public ResponseEntity<Void> rateReservation(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody RateReservationRequest request) {
        trainingCalendarService.rateReservation(userId, reservationId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add training", description = "Creates a training in the athlete's own calendar.")
    @PostMapping("/trainings")
    public ResponseEntity<PersonalTrainingDto> create(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody CreatePersonalTrainingRequest request) {
        return ResponseEntity.ok(trainingCalendarService.createMy(userId, request));
    }

    @Operation(summary = "Edit training", description = "Edits any training in the athlete's own calendar (shared plan — coach-created included).")
    @PutMapping("/trainings/{trainingId}")
    public ResponseEntity<PersonalTrainingDto> update(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID trainingId,
            @Valid @RequestBody CreatePersonalTrainingRequest request) {
        return ResponseEntity.ok(trainingCalendarService.updateMy(userId, trainingId, request));
    }

    @Operation(summary = "Delete training")
    @DeleteMapping("/trainings/{trainingId}")
    public ResponseEntity<Void> delete(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID trainingId) {
        trainingCalendarService.deleteMy(userId, trainingId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mark training completed", description = "Optional feedback and RPE (1-10). Allowed regardless of date.")
    @PostMapping("/trainings/{trainingId}/complete")
    public ResponseEntity<PersonalTrainingDto> complete(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID trainingId,
            @Valid @RequestBody CompleteTrainingRequest request) {
        return ResponseEntity.ok(trainingCalendarService.complete(userId, trainingId, request));
    }

    @Operation(summary = "Undo completion", description = "Clears completion, feedback and RPE.")
    @PostMapping("/trainings/{trainingId}/uncomplete")
    public ResponseEntity<PersonalTrainingDto> uncomplete(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID trainingId) {
        return ResponseEntity.ok(trainingCalendarService.uncomplete(userId, trainingId));
    }

    @Operation(summary = "Training comment thread", description = "Chronological athlete <-> coach thread of one training.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Messages",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = TrainingCommentDto.class))))
    })
    @GetMapping("/trainings/{trainingId}/comments")
    public ResponseEntity<List<TrainingCommentDto>> getComments(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID trainingId) {
        return ResponseEntity.ok(trainingCalendarService.getMyComments(userId, trainingId));
    }

    @Operation(summary = "Add comment")
    @PostMapping("/trainings/{trainingId}/comments")
    public ResponseEntity<TrainingCommentDto> addComment(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID trainingId,
            @Valid @RequestBody CreateTrainingCommentRequest request) {
        return ResponseEntity.ok(trainingCalendarService.addMyComment(userId, trainingId, request.body()));
    }

    @Operation(summary = "Unread counter", description = "New coach activity (trainings/edits/comments) since the athlete's last visit. Drives the navbar badge.")
    @GetMapping("/notifications")
    public ResponseEntity<TrainingNotificationsDto> getNotifications(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {
        return ResponseEntity.ok(trainingCalendarService.getAthleteNotifications(userId));
    }

    @Operation(summary = "Mark seen", description = "Called when the athlete opens the calendar tab; resets the unread counter.")
    @PostMapping("/notifications/seen")
    public ResponseEntity<Void> markSeen(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {
        trainingCalendarService.markAthleteSeen(userId);
        return ResponseEntity.noContent().build();
    }
}
