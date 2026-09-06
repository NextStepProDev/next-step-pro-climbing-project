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
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.config.CurrentUserId;
import pl.nextsteppro.climbing.domain.athleteweight.WeightRange;

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
    private final AthleteWeightService athleteWeightService;

    public TrainingCalendarController(TrainingCalendarService trainingCalendarService,
                                      TrainingStatsService trainingStatsService,
                                      AthleteGoalService athleteGoalService,
                                      AthleteWeightService athleteWeightService) {
        this.trainingCalendarService = trainingCalendarService;
        this.trainingStatsService = trainingStatsService;
        this.athleteGoalService = athleteGoalService;
        this.athleteWeightService = athleteWeightService;
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

    @Operation(summary = "My weight series",
        description = "Morning readings + trailing 7-day trend + week-over-week change. The range is a closed set of named windows (RECENT = 120 days, default), so no request can ask for an unbounded history. trendSampleCount says how many readings back the trend; below 3 it is shown but cannot close a weight goal.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Weight series",
            content = @Content(schema = @Schema(implementation = AthleteWeightSeriesDto.class))),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "409", description = "User is not a designated athlete")
    })
    @GetMapping("/weights")
    public ResponseEntity<AthleteWeightSeriesDto> getWeights(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @RequestParam(required = false) WeightRange range) {
        return ResponseEntity.ok(athleteWeightService.getMySeries(userId, range));
    }

    @Operation(summary = "Record my morning weight",
        description = "Upsert on the measurement date — weighing again the same day is a correction, not a second reading. Also closes any weight goal the new confirmed trend has reached. Returns the recomputed series.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recomputed series",
            content = @Content(schema = @Schema(implementation = AthleteWeightSeriesDto.class))),
        @ApiResponse(responseCode = "400", description = "Future date or weight outside 20-300 kg"),
        @ApiResponse(responseCode = "409", description = "User is not a designated athlete")
    })
    @PutMapping("/weights")
    public ResponseEntity<AthleteWeightSeriesDto> recordWeight(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody SaveWeightRequest request) {
        return ResponseEntity.ok(athleteWeightService.recordMyWeight(userId, request));
    }

    @Operation(summary = "Delete one of my readings",
        description = "Idempotent. Never re-opens an achieved weight goal — removing data must not take a trophy away.")
    @DeleteMapping("/weights/{measuredOn}")
    public ResponseEntity<AthleteWeightSeriesDto> deleteWeight(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate measuredOn) {
        return ResponseEntity.ok(athleteWeightService.deleteMyWeight(userId, measuredOn));
    }

    @Operation(summary = "My goals", description = "Active goals (banner cards, one per kind + horizon) + achieved goals (trophy chest). Read-only — the coach manages goals.")
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

    @Operation(summary = "Add comment with attachments",
        description = "Multipart alternative to the JSON endpoint. Text is optional here — a photo is a whole message.")
    @PostMapping(value = "/trainings/{trainingId}/comments/attachments", consumes = "multipart/form-data")
    public ResponseEntity<TrainingCommentDto> addCommentWithFiles(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID trainingId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "body", required = false) @Nullable String body) {
        return ResponseEntity.ok(trainingCalendarService.addMyCommentWithFiles(userId, trainingId, body, files));
    }

    @Operation(summary = "Thread of a booked session",
        description = "Same conversation, under a session booked in the public calendar. Addressed by "
            + "RESERVATION id because that is the one id carrying both halves of a thread's address — "
            + "who booked and what they booked. The thread itself is stored against the slot or the "
            + "event, so a multi-day course is one conversation and cancelling plus re-booking keeps it.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Messages",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = TrainingCommentDto.class))))
    })
    @GetMapping("/reservations/{reservationId}/comments")
    public ResponseEntity<List<TrainingCommentDto>> getSessionComments(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID reservationId) {
        return ResponseEntity.ok(trainingCalendarService.getMySessionComments(userId, reservationId));
    }

    @Operation(summary = "Add a message under a booked session")
    @PostMapping("/reservations/{reservationId}/comments")
    public ResponseEntity<TrainingCommentDto> addSessionComment(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody CreateTrainingCommentRequest request) {
        return ResponseEntity.ok(
            trainingCalendarService.addMySessionComment(userId, reservationId, request.body()));
    }

    @Operation(summary = "Add a message with attachments under a booked session")
    @PostMapping(value = "/reservations/{reservationId}/comments/attachments", consumes = "multipart/form-data")
    public ResponseEntity<TrainingCommentDto> addSessionCommentWithFiles(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID reservationId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "body", required = false) @Nullable String body) {
        return ResponseEntity.ok(
            trainingCalendarService.addMySessionCommentWithFiles(userId, reservationId, body, files));
    }

    @Operation(summary = "Download a comment attachment",
        description = "Authenticated stream. Serves both roles: the owning athlete and the coach. "
            + "These files are health-adjacent and never appear under the public /api/files namespace.")
    @GetMapping("/comment-files/{fileId}")
    public ResponseEntity<Resource> getCommentFile(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            Authentication authentication,
            @PathVariable UUID fileId) {
        CommentFileStream file = trainingCalendarService.openCommentFile(userId, isAdmin(authentication), fileId);
        return PrivateFileResponses.stream(file.inputStream(), file.size(), file.mimeType(), file.fileName());
    }

    @Operation(summary = "Delete a comment attachment",
        description = "The author may withdraw what they sent; the coach may remove anything in the thread.")
    @DeleteMapping("/comment-files/{fileId}")
    public ResponseEntity<Void> deleteCommentFile(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            Authentication authentication,
            @PathVariable UUID fileId) {
        trainingCalendarService.deleteCommentFile(userId, isAdmin(authentication), fileId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Edit a message",
        description = "Corrects the text of a message the caller wrote. Author only — the coach may remove "
            + "what is in the thread but never rewrites somebody else's words. Attachments are untouched, "
            + "and the text may not be emptied. Serves both roles from one route, like the file endpoints. "
            + "An edit re-raises the other side's unread mark: nothing else would tell a reader who has "
            + "already seen the message that it now says something different.")
    @PutMapping("/comments/{commentId}")
    public ResponseEntity<TrainingCommentDto> editComment(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            Authentication authentication,
            @PathVariable UUID commentId,
            @Valid @RequestBody CreateTrainingCommentRequest request) {
        return ResponseEntity.ok(
            trainingCalendarService.editComment(userId, isAdmin(authentication), commentId, request.body()));
    }

    @Operation(summary = "Download a training material",
        description = "Authenticated stream for the coach's materials (PDF/image). Replaced the public "
            + "/api/files/training/{filename} route, whose only protection was an unguessable name.")
    @GetMapping("/files/{attachmentId}")
    public ResponseEntity<Resource> getMaterial(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            Authentication authentication,
            @PathVariable UUID attachmentId) {
        CommentFileStream file = trainingCalendarService.openMaterial(userId, isAdmin(authentication), attachmentId);
        return PrivateFileResponses.stream(file.inputStream(), file.size(), file.mimeType(), file.fileName());
    }

    /**
     * One route serves both roles, so the role has to come from the token rather than the path.
     * A mirrored admin route would be a second copy of an access check on other people's health
     * data — the twin-divergence pattern this codebase has already been bitten by.
     */
    private static boolean isAdmin(@Nullable Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    @Operation(summary = "Grant data-processing consent",
        description = "Records the athlete's explicit consent (GDPR art. 9(2)(a)) to processing training-calendar data "
            + "— weigh-ins, weight goals, RPE and feedback. Every other endpoint here returns 409 until it is given. Idempotent.")
    @PostMapping("/consent")
    public ResponseEntity<Void> grantConsent(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {
        trainingCalendarService.grantConsent(userId);
        return ResponseEntity.noContent().build();
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
