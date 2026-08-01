package pl.nextsteppro.climbing.api.trainingcalendar;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.athletegoal.AthleteGoal;
import pl.nextsteppro.climbing.domain.athletegoal.GoalHorizon;
import pl.nextsteppro.climbing.domain.athletegoal.GoalKind;
import pl.nextsteppro.climbing.domain.personaltraining.AttachmentKind;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTraining;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingAttachment;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingComment;
import pl.nextsteppro.climbing.domain.trainingtemplate.TrainingTemplate;

import java.math.BigDecimal;
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
    // Untimed ("all-day") training: both null. Otherwise both set (validated in the service).
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime,
    @NotBlank @Size(max = PersonalTraining.MAX_TITLE_LENGTH) String title,
    @Nullable @Size(max = PersonalTraining.MAX_DESCRIPTION_LENGTH) String description,
    // null = leave attachments untouched (so a move/drag PUT keeps them);
    // [] = clear; a list = replace. Max 3.
    @Nullable @Size(max = TrainingAttachment.MAX_PER_TRAINING) List<@Valid AttachmentRequest> attachments
) {
    // Convenience for callers that don't touch attachments (null = leave untouched)
    CreatePersonalTrainingRequest(LocalDate date, @Nullable LocalTime startTime, @Nullable LocalTime endTime,
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
    // RPE is required on completion (older completions with a null value are left untouched)
    @NotNull @Min(1) @Max(10) Integer rpe
) {}

/** Athlete rates an attended reservation (idempotent upsert). */
record RateReservationRequest(
    @NotNull @Min(1) @Max(10) Integer rpe,
    @Nullable @Size(max = 500) String note
) {}

record CreateTrainingCommentRequest(
    @NotBlank @Size(max = TrainingComment.MAX_BODY_LENGTH) String body
) {}

record PersonalTrainingDto(
    UUID id,
    LocalDate date,
    // Null for untimed ("all-day") trainings.
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime,
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
    boolean isNew,
    // Athlete RPE rating for this attended booking (null = not rated); canRate = booking is past
    @Nullable Integer rpe,
    @Nullable String rpeNote,
    boolean canRate
) {}

/** A future training removed by the OTHER side since the viewer's last visit ("deleted" strip). */
record TrainingDeletionDto(
    LocalDate date,
    // Null when the deleted training was untimed ("all-day").
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime,
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
    // Personal trainings only, last 90 days: completed / (completed + missed). Reservations are
    // excluded on purpose — a cancelled booking is a choice, not a no-show
    @Nullable Integer attendanceRatePercent,
    // Average RPE now merges completed trainings AND rated reservations
    @Nullable Double avgRpeOverall,
    @Nullable Double avgRpeLast30Days,
    List<LocationCountDto> topLocations,
    // Intensity balance over the last 90 days across both sources (session counts per band)
    RpeDistributionDto rpeDistribution,
    // Last 5 ratings (both sources) all >= 9 → possible overtraining / inflated scoring
    boolean sustainedHighRpe,
    // Past attended reservations with no RPE yet (nudge to rate; personal trainings excluded)
    int unratedActivitiesCount
) {}

/** Session counts by RPE band over the last 90 days: light 1-4, medium 5-7, hard 8-10. */
record RpeDistributionDto(int light, int medium, int hard) {}

record TypeBreakdownDto(long personal, long individualSlot, long course, long training, long workshop) {}

record LocationCountDto(String name, long count) {}

/**
 * Coach creates/edits an athlete's goal. On update the horizon and the kind are IGNORED —
 * both are fixed for an active goal (changing either means deleting + creating a new goal).
 *
 * <p>{@code kind} is nullable so an omitted field still means a training goal;
 * {@code targetWeightKg} is required for (and only allowed on) WEIGHT goals.
 */
record SaveGoalRequest(
    @Nullable GoalKind kind,
    @NotNull GoalHorizon horizon,
    @NotBlank @Size(max = AthleteGoal.MAX_CONTENT_LENGTH) String content,
    @NotNull LocalDate targetDate,
    @Nullable @DecimalMin("20.0") @DecimalMax("300.0") BigDecimal targetWeightKg
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
    // GENERAL | WEIGHT — picks the banner row
    String kind,
    // SHORT | MEDIUM | LONG — also picks the trophy size in the trophy chest
    String horizon,
    String content,
    LocalDate targetDate,
    // WEIGHT only: the target, and the trend snapshot the progress bar starts from
    @Nullable BigDecimal targetWeightKg,
    @Nullable BigDecimal startWeightKg,
    // Closed by a weigh-in rather than by the coach — the only case that may be reopened
    boolean achievedAutomatically,
    @Nullable Instant achievedAt,
    Instant createdAt
) {}

/**
 * One morning reading on the chart, with the trend as of that day so the frontend draws the
 * line without re-implementing the arithmetic. Never null — a reading is always in its own
 * trailing window.
 */
record AthleteWeightEntryDto(
    LocalDate measuredOn,
    BigDecimal weightKg,
    BigDecimal trendKg
) {}

/**
 * The weight panel in one payload: raw readings for the dots, plus everything derived so the
 * frontend never re-implements the trend arithmetic.
 */
record AthleteWeightSeriesDto(
    // Ascending by date
    List<AthleteWeightEntryDto> entries,
    // Trailing 7-day average ending today; shown from the very first reading
    @Nullable BigDecimal currentTrendKg,
    // How many readings back that average (0-7) — drives the "will not close goals" hint
    int trendSampleCount,
    boolean trendConfirmed,
    @Nullable BigDecimal weeklyChangePercent,
    // Losing faster than 1%/week. Always present; the frontend shows it to the coach only
    boolean rapidLoss,
    @Nullable BigDecimal latestWeightKg,
    @Nullable LocalDate latestMeasuredOn,
    // How far back a reading may be backfilled. A FIXED policy, independent of the selected
    // range: viewing a year must not let the date picker offer days the server would refuse
    int backfillDays
) {}

/**
 * Athlete records (or corrects) a morning weight. Same date twice is an upsert — weighing
 * again is a correction, not a second data point.
 */
record SaveWeightRequest(
    @NotNull LocalDate measuredOn,
    @NotNull @DecimalMin("20.0") @DecimalMax("300.0") BigDecimal weightKg
) {}

/** Banner cards (active, sorted short → medium → long) + trophy chest (achieved, newest first). */
record GoalsDto(
    List<AthleteGoalDto> active,
    List<AthleteGoalDto> achieved
) {}
