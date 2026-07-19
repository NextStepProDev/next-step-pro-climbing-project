package pl.nextsteppro.climbing.api.trainingcalendar;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.config.CurrentUserId;

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

    public AdminTrainingCalendarController(AdminTrainingCalendarService adminTrainingCalendarService) {
        this.adminTrainingCalendarService = adminTrainingCalendarService;
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

    @Operation(summary = "Athlete's goals", description = "Active (one per horizon) + achieved (trophy chest), same shape the athlete sees.")
    @GetMapping("/athletes/{athleteId}/goals")
    public ResponseEntity<GoalsDto> getGoals(
            @PathVariable UUID athleteId) {
        return ResponseEntity.ok(adminTrainingCalendarService.getGoals(athleteId));
    }

    @Operation(summary = "Set a goal", description = "One active goal per horizon; a taken horizon returns 409.")
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

    @Operation(summary = "Mark goal achieved", description = "Moves the goal to the trophy chest; irreversible, frees its horizon slot.")
    @PostMapping("/goals/{goalId}/achieve")
    public ResponseEntity<AthleteGoalDto> achieveGoal(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @PathVariable UUID goalId) {
        return ResponseEntity.ok(adminTrainingCalendarService.achieveGoal(adminId, goalId));
    }
}
