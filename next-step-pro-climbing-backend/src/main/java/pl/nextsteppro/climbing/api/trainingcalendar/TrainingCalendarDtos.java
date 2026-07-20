package pl.nextsteppro.climbing.api.trainingcalendar;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.athletegoal.AthleteGoal;
import pl.nextsteppro.climbing.domain.athletegoal.GoalHorizon;
import pl.nextsteppro.climbing.domain.personaltraining.AttachmentKind;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTraining;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingAttachment;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingComment;
import pl.nextsteppro.climbing.domain.trainingtemplate.TrainingTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Shared by the athlete controller AND the admin (coach) slice — the admin controller/service
// live in this same package (deviation from the api/admin/* layout) so these records can stay
// package-private per project convention instead of being duplicated.

record CreatePersonalTrainingRequest(
    @NotNull LocalDate date,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @NotBlank @Size(max = PersonalTraining.MAX_TITLE_LENGTH) String title,
    @Nullable @Size(max = PersonalTraining.MAX_DESCRIPTION_LENGTH) String description,
    // null = leave attachments untouched (so a move/drag PUT keeps them);
    // [] = clear; a list = replace. Max 3.
    @Nullable @Size(max = TrainingAttachment.MAX_PER_TRAINING) List<@Valid AttachmentRequest> attachments
) {
    // Convenience for callers that don't touch attachments (null = leave untouched)
    CreatePersonalTrainingRequest(LocalDate date, LocalTime startTime, LocalTime endTime,
                                  String title, @Nullable String description) {
        this(date, startTime, endTime, title, description, null);
    }
}

/**
 * One material on a training: a LINK (url) or a FILE (previously uploaded filename + metadata).
 * kind == null is treated as LINK for backward compatibility. Per-kind fields are validated
 * in the service.
 */
record AttachmentRequest(
    @Nullable AttachmentKind kind,
    @Nullable @Size(max = TrainingAttachment.MAX_URL_LENGTH) String url,
    @Nullable @Size(max = 255) String filename,
    @Nullable @Size(max = 255) String originalName,
    @Nullable @Size(max = 100) String mimeType,
    @Nullable Long sizeBytes,
    @Nullable @Size(max = TrainingAttachment.MAX_LABEL_LENGTH) String label
) {
    // Link convenience (used by tests and any client sending only url+label)
    AttachmentRequest(String url, String label) {
        this(AttachmentKind.LINK, url, null, null, null, null, label);
    }

    boolean isFile() {
        return kind == AttachmentKind.FILE;
    }
}

/**
 * embedUrl is non-null for supported YouTube/Instagram LINKs → the UI renders an iframe.
 * For FILEs, url points at /api/files/training/{filename}; fileName/mimeType drive the card.
 */
record TrainingAttachmentDto(
    UUID id,
    // "LINK" | "FILE"
    String kind,
    @Nullable String url,
    @Nullable String label,
    @Nullable String embedUrl,
    // FILE only: stored filename (re-sent on edit to keep the file), display name, type, size
    @Nullable String filename,
    @Nullable String fileName,
    @Nullable String mimeType,
    @Nullable Long sizeBytes
) {}

/** Returned by the file-upload endpoint; the client echoes these fields back as a FILE attachment. */
record AttachmentUploadResponse(
    String filename,
    String originalName,
    String mimeType,
    long sizeBytes,
    String url
) {}

/** Coach creates/edits a reusable training template. */
record SaveTemplateRequest(
    @NotBlank @Size(max = TrainingTemplate.MAX_TITLE_LENGTH) String title,
    @Nullable @Size(max = TrainingTemplate.MAX_DESCRIPTION_LENGTH) String description,
    @NotNull @Min(TrainingTemplate.MIN_DURATION_MINUTES) @Max(TrainingTemplate.MAX_DURATION_MINUTES)
    Integer defaultDurationMinutes,
    @Nullable @Size(max = TrainingAttachment.MAX_PER_TRAINING) List<@Valid AttachmentRequest> attachments
) {}

record TrainingTemplateDto(
    UUID id,
    String title,
    @Nullable String description,
    int defaultDurationMinutes,
    List<TrainingAttachmentDto> attachments,
    Instant updatedAt
) {}

/** One uploaded file for the admin materials-management list (central cleanup view). */
record MaterialDto(
    // Attachment id (used to delete this specific material)
    UUID id,
    @Nullable String fileName,
    @Nullable String mimeType,
    @Nullable Long sizeBytes,
    String url,
    // "TRAINING" | "TEMPLATE"
    String ownerType,
    // e.g. "16.07.2026 — Trening siłowy" or a template title (HTML-escaped; UI decodes)
    String ownerLabel,
    Instant createdAt
) {}

record CompleteTrainingRequest(
    @Nullable @Size(max = PersonalTraining.MAX_FEEDBACK_LENGTH) String feedback,
    @Nullable @Min(1) @Max(10) Integer rpe
) {}

record CreateTrainingCommentRequest(
    @NotBlank @Size(max = TrainingComment.MAX_BODY_LENGTH) String body
) {}

record PersonalTrainingDto(
    UUID id,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    String title,
    @Nullable String description,
    boolean createdByAdmin,
    // PLANNED | COMPLETED | MISSED (missed is derived, never stored)
    String status,
    @Nullable Instant completedAt,
    @Nullable String feedback,
    @Nullable Integer rpe,
    // Unread activity from the OTHER side (viewer-dependent): new/edited entry or new comments
    boolean hasUnreadActivity,
    Instant createdAt,
    List<TrainingAttachmentDto> attachments
) {}

/** Read-only overlay: the athlete's confirmed booking from the public reservation system. */
record ReservationOverlayDto(
    UUID id,
    // Slot behind the booking — lets the UI open the full slot-detail modal in place
    UUID slotId,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    @Nullable String title,
    // Coach viewer only: the athlete booked this after the coach's last visit (unread dot);
    // always false for the athlete (own action) and for bookings made by an admin
    boolean isNew
) {}

/** A future training removed by the OTHER side since the viewer's last visit ("deleted" strip). */
record TrainingDeletionDto(
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    String title,
    boolean deletedByAdmin,
    Instant deletedAt
) {}

/**
 * Pending invitation (a seat held for the athlete who has NOT booked yet) — rendered
 * loudly (amber, "book now!") so it cannot be mistaken for a confirmed reservation.
 * Exactly one of slotId/eventId is set; multi-day events emit one entry per day.
 */
record InvitationOverlayDto(
    @Nullable UUID slotId,
    @Nullable UUID eventId,
    LocalDate date,
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime,
    @Nullable String title
) {}

record CalendarRangeDto(
    List<PersonalTrainingDto> trainings,
    List<ReservationOverlayDto> reservations,
    // Held seats awaiting booking — visually distinct call-to-action, not a reservation
    List<InvitationOverlayDto> invitations,
    // Unseen deletions of FUTURE trainings made by the other side (any date, newest first)
    List<TrainingDeletionDto> deletions
) {}

record TrainingCommentDto(
    UUID id,
    String body,
    boolean authorIsAdmin,
    String authorName,
    @Nullable String authorAvatarUrl,
    Instant createdAt,
    // Whether the viewer wrote this message (chat alignment left/right)
    boolean mine
) {}

record TrainingNotificationsDto(long newCount) {}

/** Coach's roster entry: one flagged athlete with unread-activity badge data. */
record AthleteSummaryDto(
    UUID id,
    String firstName,
    String lastName,
    String nickname,
    @Nullable String avatarUrl,
    long newCount,
    @Nullable Instant lastActivityAt
) {}

/**
 * Live-derived athlete statistics — never cached, never stored: every request recomputes
 * from the current DB state, so uncompleting/cancelling/deleting past entries is reflected
 * instantly. An "activity" = completed personal training OR attended reservation
 * (confirmed, slot already over). Nullable fields mean "no data — hide the tile".
 */
record AthleteStatsDto(
    int thisMonthCount,
    int prevMonthCount,
    long totalCount,
    @Nullable LocalDate firstActivityDate,
    // Consecutive ISO weeks (Mon-Sun) with >=1 activity; an empty current week does not
    // break the streak while it is still in progress (grace period)
    int currentStreakWeeks,
    int bestStreakWeeks,
    // Average over the last 6 FULL months (shortened to the first-activity month);
    // null until one full month of history exists
    @Nullable Double avgPerMonth,
    // Last 365 days, non-zero days only; keys serialize as yyyy-MM-dd
    Map<LocalDate, Integer> heatmap,
    TypeBreakdownDto byType,
    // Personal trainings only: completed / (completed + missed). Reservations are excluded
    // on purpose — a cancelled booking is a choice, not a no-show
    @Nullable Integer attendanceRatePercent,
    @Nullable Double avgRpeOverall,
    @Nullable Double avgRpeLast30Days,
    List<LocationCountDto> topLocations
) {}

record TypeBreakdownDto(long personal, long individualSlot, long course, long training, long workshop) {}

record LocationCountDto(String name, long count) {}

/**
 * Coach creates/edits an athlete's goal. On update the horizon is IGNORED — an active
 * goal's horizon is fixed (replacing the horizon means deleting + creating a new goal).
 */
record SaveGoalRequest(
    @NotNull GoalHorizon horizon,
    @NotBlank @Size(max = AthleteGoal.MAX_CONTENT_LENGTH) String content,
    @NotNull LocalDate targetDate
) {}

/**
 * Marking a goal achieved: the coach may backdate the achievement (a goal usually falls
 * days before the coach sits at the panel). Null = now; future dates are rejected.
 */
record AchieveGoalRequest(
    @Nullable LocalDate achievedDate
) {}

record AthleteGoalDto(
    UUID id,
    // SHORT | MEDIUM | LONG — also picks the trophy size in the trophy chest
    String horizon,
    String content,
    LocalDate targetDate,
    @Nullable Instant achievedAt,
    Instant createdAt
) {}

/** Banner cards (active, sorted short → medium → long) + trophy chest (achieved, newest first). */
record GoalsDto(
    List<AthleteGoalDto> active,
    List<AthleteGoalDto> achieved
) {}
