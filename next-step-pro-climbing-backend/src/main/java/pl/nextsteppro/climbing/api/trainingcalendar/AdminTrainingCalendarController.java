package pl.nextsteppro.climbing.api.trainingcalendar;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.config.CurrentUserId;
import pl.nextsteppro.climbing.domain.athleteweight.WeightRange;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Coach (admin) side of the personal training calendar. Path is under /api/admin/** —
 * guarded by SecurityConfig + class-level @PreAuthorize. Lives in this package
 * (not api/admin/*) to share the package-private DTOs with the athlete controller.
 */
@RestController
@RequestMapping("/api/admin/training-calendar")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Training Calendar", description = "Coach view of athletes' personal training calendars")
public class AdminTrainingCalendarController {

    private final AdminTrainingCalendarService adminTrainingCalendarService;
    private final TrainingTemplateService templateService;
    private final AthleteWeightService athleteWeightService;

    public AdminTrainingCalendarController(AdminTrainingCalendarService adminTrainingCalendarService,
                                           TrainingTemplateService templateService,
                                           AthleteWeightService athleteWeightService) {
        this.adminTrainingCalendarService = adminTrainingCalendarService;
        this.templateService = templateService;
        this.athleteWeightService = athleteWeightService;
    }

    @Operation(summary = "Athlete roster", description = "Flagged athletes with per-athlete unread badges, unread-first.")
    @GetMapping("/athletes")
    public ResponseEntity<List<AthleteSummaryDto>> getAthletes(
            @Parameter(hidden = true) @CurrentUserId UUID adminId) {
        return ResponseEntity.ok(adminTrainingCalendarService.getAthleteSummaries(adminId));
    }

    @Operation(summary = "Athlete's calendar range", description = "Same shape as the athlete's own range endpoint.")
    @GetMapping("/athletes/{athleteId}")
    public ResponseEntity<CalendarRangeDto> getRange(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @PathVariable UUID athleteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(adminTrainingCalendarService.getRangeForAthlete(adminId, athleteId, from, to));
    }

    @Operation(summary = "Athlete statistics", description = "Same live-derived stats the athlete sees under their own calendar.")
    @GetMapping("/athletes/{athleteId}/stats")
    public ResponseEntity<AthleteStatsDto> getStats(
            @PathVariable UUID athleteId) {
        return ResponseEntity.ok(adminTrainingCalendarService.getStatsForAthlete(athleteId));
    }

    @Operation(summary = "Upload a material file", description = "Stores a PDF/image; reference the returned filename as a FILE attachment when saving the training.")
    @PostMapping(value = "/attachments/upload", consumes = "multipart/form-data")
    public ResponseEntity<AttachmentUploadResponse> uploadAttachment(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(adminTrainingCalendarService.uploadAttachment(file));
    }

    @Operation(summary = "Add training for athlete")
    @PostMapping("/athletes/{athleteId}/trainings")
    public ResponseEntity<PersonalTrainingDto> create(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @PathVariable UUID athleteId,
            @Valid @RequestBody CreatePersonalTrainingRequest request) {
        return ResponseEntity.ok(adminTrainingCalendarService.createForAthlete(adminId, athleteId, request));
    }

    @Operation(summary = "Edit training", description = "Coach may edit any training in any athlete's calendar.")
    @PutMapping("/trainings/{trainingId}")
    public ResponseEntity<PersonalTrainingDto> update(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @PathVariable UUID trainingId,
            @Valid @RequestBody CreatePersonalTrainingRequest request) {
        return ResponseEntity.ok(adminTrainingCalendarService.update(adminId, trainingId, request));
    }

    @Operation(summary = "Delete training")
    @DeleteMapping("/trainings/{trainingId}")
    public ResponseEntity<Void> delete(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @PathVariable UUID trainingId) {
        adminTrainingCalendarService.delete(adminId, trainingId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Training comment thread")
    @GetMapping("/trainings/{trainingId}/comments")
    public ResponseEntity<List<TrainingCommentDto>> getComments(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @PathVariable UUID trainingId) {
        return ResponseEntity.ok(adminTrainingCalendarService.getComments(adminId, trainingId));
    }

    @Operation(summary = "Add comment as coach")
    @PostMapping("/trainings/{trainingId}/comments")
    public ResponseEntity<TrainingCommentDto> addComment(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @PathVariable UUID trainingId,
            @Valid @RequestBody CreateTrainingCommentRequest request) {
        return ResponseEntity.ok(adminTrainingCalendarService.addComment(adminId, trainingId, request.body()));
    }

    @Operation(summary = "Mark athlete seen", description = "Called when the coach opens an athlete's calendar; resets that athlete's badge for this admin.")
    @PostMapping("/athletes/{athleteId}/seen")
    public ResponseEntity<Void> markSeen(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @PathVariable UUID athleteId) {
        adminTrainingCalendarService.markSeen(adminId, athleteId);
        return ResponseEntity.noContent().build();
    }

    // ---------- athlete goals (banner above the calendar + trophy chest) ----------

    @Operation(summary = "Athlete's goals", description = "Active (one per kind + horizon) + achieved (trophy chest), same shape the athlete sees.")
    @GetMapping("/athletes/{athleteId}/goals")
    public ResponseEntity<GoalsDto> getGoals(
            @PathVariable UUID athleteId) {
        return ResponseEntity.ok(adminTrainingCalendarService.getGoals(athleteId));
    }

    @Operation(summary = "Set a goal", description = "One active goal per kind + horizon; a taken slot returns 409. A WEIGHT goal needs targetWeightKg and at least one weigh-in to snapshot the start from.")
    @PostMapping("/athletes/{athleteId}/goals")
    public ResponseEntity<AthleteGoalDto> createGoal(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @PathVariable UUID athleteId,
            @Valid @RequestBody SaveGoalRequest request) {
        return ResponseEntity.ok(adminTrainingCalendarService.createGoal(adminId, athleteId, request));
    }

    @Operation(summary = "Edit an active goal", description = "Horizon is fixed; achieved goals are immutable (409).")
    @PutMapping("/goals/{goalId}")
    public ResponseEntity<AthleteGoalDto> updateGoal(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @PathVariable UUID goalId,
            @Valid @RequestBody SaveGoalRequest request) {
        return ResponseEntity.ok(adminTrainingCalendarService.updateGoal(adminId, goalId, request));
    }

    @Operation(summary = "Delete an active goal", description = "Achieved goals cannot be deleted — they stay in the trophy chest (409).")
    @DeleteMapping("/goals/{goalId}")
    public ResponseEntity<Void> deleteGoal(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @PathVariable UUID goalId) {
        adminTrainingCalendarService.deleteGoal(adminId, goalId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mark goal achieved", description = "Moves the goal to the trophy chest; irreversible, frees its horizon slot. The achievement date is backdatable (null = today); a future date is rejected.")
    @PostMapping("/goals/{goalId}/achieve")
    public ResponseEntity<AthleteGoalDto> achieveGoal(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @PathVariable UUID goalId,
            @Valid @RequestBody(required = false) AchieveGoalRequest request) {
        AchieveGoalRequest body = request != null ? request : new AchieveGoalRequest(null);
        return ResponseEntity.ok(adminTrainingCalendarService.achieveGoal(adminId, goalId, body));
    }

    @Operation(summary = "Reopen an automatically achieved goal",
        description = "Undo for a weight goal closed by a mistyped weigh-in. Only goals closed AUTOMATICALLY can be reopened — a goal the coach marked achieved by hand stays in the trophy chest (409). Also 409 if a new active goal already took the freed slot.")
    @PostMapping("/goals/{goalId}/reopen")
    public ResponseEntity<AthleteGoalDto> reopenGoal(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @PathVariable UUID goalId) {
        return ResponseEntity.ok(adminTrainingCalendarService.reopenGoal(adminId, goalId));
    }

    // ---------- body weight (read-only: only the athlete records their own weight) ----------

    @Operation(summary = "Athlete's weight series",
        description = "Readings + 7-day trend + weekly change. Includes the rapid-loss flag, which the athlete's own endpoint also returns but the athlete UI does not surface. There is deliberately no write endpoint here.")
    @GetMapping("/athletes/{athleteId}/weights")
    public ResponseEntity<AthleteWeightSeriesDto> getWeights(
            @PathVariable UUID athleteId,
            @RequestParam(required = false) WeightRange range) {
        return ResponseEntity.ok(athleteWeightService.getSeriesForAthlete(athleteId, range));
    }

    // ---------- training templates (coach library) ----------

    @Operation(summary = "List training templates", description = "Coach's reusable template library (shared across athletes).")
    @GetMapping("/templates")
    public ResponseEntity<List<TrainingTemplateDto>> listTemplates() {
        return ResponseEntity.ok(templateService.list());
    }

    @Operation(summary = "Create a training template")
    @PostMapping("/templates")
    public ResponseEntity<TrainingTemplateDto> createTemplate(@Valid @RequestBody SaveTemplateRequest request) {
        return ResponseEntity.ok(templateService.create(request));
    }

    @Operation(summary = "Edit a training template", description = "Does not affect trainings already created from it.")
    @PutMapping("/templates/{templateId}")
    public ResponseEntity<TrainingTemplateDto> updateTemplate(
            @PathVariable UUID templateId,
            @Valid @RequestBody SaveTemplateRequest request) {
        return ResponseEntity.ok(templateService.update(templateId, request));
    }

    @Operation(summary = "Delete a training template")
    @DeleteMapping("/templates/{templateId}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID templateId) {
        templateService.delete(templateId);
        return ResponseEntity.noContent().build();
    }

    // ---------- materials management (central cleanup view) ----------

    @Operation(summary = "List uploaded materials", description = "All uploaded files across trainings and templates, newest first — a central place to free disk space.")
    @GetMapping("/materials")
    public ResponseEntity<List<MaterialDto>> listMaterials() {
        return ResponseEntity.ok(adminTrainingCalendarService.listMaterials());
    }

    @Operation(summary = "Delete an uploaded material", description = "Removes the attachment and the file from disk (if no other attachment references it).")
    @DeleteMapping("/materials/{attachmentId}")
    public ResponseEntity<Void> deleteMaterial(@PathVariable UUID attachmentId) {
        adminTrainingCalendarService.deleteMaterial(attachmentId);
        return ResponseEntity.noContent().build();
    }
}
