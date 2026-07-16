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
}
