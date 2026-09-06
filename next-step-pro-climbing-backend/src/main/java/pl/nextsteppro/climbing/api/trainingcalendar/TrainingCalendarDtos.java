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
import pl.nextsteppro.climbing.domain.personaltraining.TrainingKind;
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
    // null = TRAINING. IGNORED on update: an entry is a training or a task from birth (see
    // TrainingKind), so a PUT carrying a different kind changes nothing rather than erroring —
    // the frontend never sends one.
    @Nullable TrainingKind kind,
    @NotNull LocalDate date,
    // Untimed ("all-day") training: both null. Otherwise both set (validated in the service).
    // A TASK must leave both null — a commitment held across a day has no hour.
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime,
    @NotBlank @Size(max = PersonalTraining.MAX_TITLE_LENGTH) String title,
    @Nullable @Size(max = PersonalTraining.MAX_DESCRIPTION_LENGTH) String description,
    // TASK only, and optional there too: "drink 3 litres" carries its number in the title.
    @Nullable @Min(PersonalTraining.MIN_TARGET_CALORIES) @Max(PersonalTraining.MAX_TARGET_CALORIES)
    Integer targetCalories,
    // null = leave attachments untouched (so a move/drag PUT keeps them);
    // [] = clear; a list = replace. Max 3.
    @Nullable @Size(max = TrainingAttachment.MAX_PER_TRAINING) List<@Valid AttachmentRequest> attachments,
    // Optimistic lock, IGNORED on create. null = "do not check", and that is load-bearing rather
    // than lax: the PUTs that carry no version are real ones — a drag, a paste of a cut entry, and
    // any client written before this field existed. Those move an entry the user is looking at
    // right now, so there is nothing stale to protect. The edit FORM sends it, because that is the
    // one flow with a gap between reading the row and writing it back.
    @Nullable Long version
) {
    // Convenience for callers that don't touch attachments (null = leave untouched)
    CreatePersonalTrainingRequest(LocalDate date, @Nullable LocalTime startTime, @Nullable LocalTime endTime,
                                  String title, @Nullable String description) {
        this(date, startTime, endTime, title, description, null);
    }

    // Pre-version shape, kept so every caller that does not care about the lock reads unchanged
    CreatePersonalTrainingRequest(@Nullable TrainingKind kind, LocalDate date,
                                  @Nullable LocalTime startTime, @Nullable LocalTime endTime,
                                  String title, @Nullable String description,
                                  @Nullable Integer targetCalories,
                                  @Nullable List<AttachmentRequest> attachments) {
        this(kind, date, startTime, endTime, title, description, targetCalories, attachments, null);
    }

    // Convenience for the ordinary TRAINING case, which is everything except the task form
    CreatePersonalTrainingRequest(LocalDate date, @Nullable LocalTime startTime, @Nullable LocalTime endTime,
                                  String title, @Nullable String description,
                                  @Nullable List<AttachmentRequest> attachments) {
        this(null, date, startTime, endTime, title, description, null, attachments);
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
 *
 * <p>For FILEs, url points at the AUTHENTICATED route /api/training-calendar/files/{attachmentId}.
 * It is deliberately NOT under the public /api/files namespace: that route existed until V80 and
 * its only protection was an unguessable name, so a link that ever escaped worked for anyone,
 * forever. The client therefore fetches the bytes with its session rather than pointing an
 * &lt;img src&gt; at them. fileName/mimeType drive the card.
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

/**
 * Returned by the file-upload endpoint; the client echoes these fields back as a FILE attachment.
 * Carries no URL: the upload is two-phase, so until the file is saved onto a training or template
 * there is no attachment row to address it by — and the filename alone is no longer an address.
 */
record AttachmentUploadResponse(
    String filename,
    String originalName,
    String mimeType,
    long sizeBytes
) {}

/** Coach creates/edits a reusable training template. */
record SaveTemplateRequest(
    // null = TRAINING, so a request that predates tasks still makes what it always made. Unlike a
    // training's kind this one IS honoured on update — a template has no completion to invalidate.
    @Nullable TrainingKind kind,
    @NotBlank @Size(max = TrainingTemplate.MAX_TITLE_LENGTH) String title,
    @Nullable @Size(max = TrainingTemplate.MAX_DESCRIPTION_LENGTH) String description,
    // Required for a TRAINING, rejected for a TASK — a cross-field rule these annotations cannot
    // state, so the service decides and they only keep an out-of-range number from reaching it.
    @Nullable @Min(TrainingTemplate.MIN_DURATION_MINUTES) @Max(TrainingTemplate.MAX_DURATION_MINUTES)
    Integer defaultDurationMinutes,
    // TASK only, and optional there too — same rule, same bounds as an entry's own target.
    @Nullable @Min(PersonalTraining.MIN_TARGET_CALORIES) @Max(PersonalTraining.MAX_TARGET_CALORIES)
    Integer targetCalories,
    @Nullable @Size(max = TrainingAttachment.MAX_PER_TRAINING) List<@Valid AttachmentRequest> attachments
) {}

record TrainingTemplateDto(
    UUID id,
    TrainingKind kind,
    String title,
    @Nullable String description,
    // Set for a TRAINING, null for a TASK
    @Nullable Integer defaultDurationMinutes,
    @Nullable Integer targetCalories,
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
    // Required when completing a TRAINING, rejected when completing a TASK — "how hard was staying
    // under 2200 kcal, 1-10" is a question about nothing, and an answer would poison the RPE
    // averages. Nullable here because bean validation cannot see the kind; the service decides.
    // (Older completions with a null value are left untouched.)
    @Nullable @Min(1) @Max(10) Integer rpe
) {}

/** Athlete rates an attended reservation (idempotent upsert). */
record RateReservationRequest(
    @NotNull @Min(1) @Max(10) Integer rpe,
    @Nullable @Size(max = 500) String note
) {}

/**
 * Text-only message. The multipart endpoint takes its optional text as a plain request part
 * instead, because a message carrying an attachment is allowed to have no words at all.
 *
 * <p>Also carries an edit: same single field, same validation, so a twin
 * {@code UpdateTrainingCommentRequest} would only be a second copy waiting to drift. Here
 * {@code @NotBlank} is a domain rule rather than hygiene — an edit corrects the text and may never
 * empty it, because a message that lost its words would be an empty bubble (or a silent delete,
 * which is not what this endpoint is for).
 */
record CreateTrainingCommentRequest(
    @NotBlank @Size(max = TrainingComment.MAX_BODY_LENGTH) String body
) {}

record PersonalTrainingDto(
    UUID id,
    // TRAINING | TASK — fixed at creation
    TrainingKind kind,
    LocalDate date,
    // Null for untimed ("all-day") trainings, and always null for a task.
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime,
    String title,
    @Nullable String description,
    // Task only, and optional there too
    @Nullable Integer targetCalories,
    boolean createdByAdmin,
    // PLANNED | COMPLETED | MISSED (missed is derived, never stored)
    String status,
    @Nullable Instant completedAt,
    @Nullable String feedback,
    @Nullable Integer rpe,
    // Unread activity from the OTHER side (viewer-dependent): new/edited entry or new comments
    boolean hasUnreadActivity,
    Instant createdAt,
    List<TrainingAttachmentDto> attachments,
    // Optimistic lock. Echoed back by the edit form so a save built on a stale read is refused
    // (409) instead of quietly overwriting what the other side wrote in the meantime — the plan is
    // shared, and this is the only entity in the app two people really edit in parallel.
    long version
) {}

/**
 * The athlete's confirmed booking from the public reservation system, overlaid on the plan.
 *
 * <p>Read-only about the BOOKING — the calendar cannot move or cancel it — but no longer silent:
 * the pair can hold the same conversation here as under a plan entry, addressed by {@code id}.
 */
record ReservationOverlayDto(
    UUID id,
    // Slot behind the booking — lets the UI open the full slot-detail modal in place
    UUID slotId,
    // Set when the booking belongs to an event. Carried so the UI can say which conversation this
    // is: every day of a multi-day course shares one thread, hung on the event.
    @Nullable UUID eventId,
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
    boolean canRate,
    // Messages from the OTHER side since the viewer's marker — same signal PersonalTrainingDto
    // carries, so a booked session cannot become the one place a message goes unnoticed
    boolean hasUnreadActivity
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
    // Null when the message is nothing but attachments
    @Nullable String body,
    boolean authorIsAdmin,
    String authorName,
    @Nullable String authorAvatarUrl,
    Instant createdAt,
    // Null until the author corrected their own words. Drives the "(edited)" badge — in a two-person
    // thread the record of what was agreed is the only record there is, so a rewrite has to show.
    @Nullable Instant editedAt,
    // Whether the viewer wrote this message (chat alignment left/right)
    boolean mine,
    List<TrainingCommentFileDto> files
) {}

/**
 * One file attached to a message. {@code url} needs the session — these never enter the public
 * {@code /api/files} namespace — so the client fetches the bytes itself rather than pointing an
 * {@code <img src>} at it.
 */
record TrainingCommentFileDto(
    UUID id,
    String url,
    String mimeType,
    // Original name as uploaded (escaped, display-only) — the stored name is a bare UUID
    @Nullable String fileName,
    long sizeBytes,
    // Null for a PDF; present for images so the thread reserves space before the bytes arrive
    @Nullable Integer width,
    @Nullable Integer height,
    // Shown in the UI, so the file disappearing is never a surprise
    Instant expiresAt,
    boolean canDelete
) {}

/**
 * Not a JSON payload — what the streaming endpoint needs to hand the bytes back: the open stream
 * plus the facts (type, size, name) taken from the stored file rather than from the request.
 */
record CommentFileStream(java.io.InputStream inputStream, long size, String mimeType,
                         @Nullable String fileName) {}

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
    // Intensity balance over the last 90 days across both sources (session counts per band)
    RpeDistributionDto rpeDistribution,
    // Last 5 ratings (both sources) all >= 9 → possible overtraining / inflated scoring
    boolean sustainedHighRpe,
    // Past attended reservations with no RPE yet (nudge to rate; personal trainings excluded)
    int unratedActivitiesCount,
    // Tasks, counted apart from every training number above
    TaskStatsDto tasks
) {}

/**
 * Tasks held vs tasks that came due. Every count ships the denominator it belongs to, because
 * "3 done" cannot tell three-of-three from three-of-twelve, and a bare 0 cannot tell a month of
 * blown ceilings from a month where none were set.
 */
record TaskStatsDto(
    int thisMonthDone,
    int thisMonthDue,
    // Rolling 30 days
    int windowDone,
    int windowDue,
    // Over the rolling window; null until something has come due — 0% would claim a failure
    // that never happened
    @Nullable Integer completionPercent
) {}

/** Session counts by RPE band over the last 90 days: light 1-4, medium 5-7, hard 8-10. */
record RpeDistributionDto(int light, int medium, int hard) {}

record TypeBreakdownDto(long personal, long individualSlot, long course, long training, long workshop) {}


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
    // Lowest CONFIRMED trend of the last 90 days, and the day it was reached. Confirmed on
    // purpose: a personal best shown next to a goal that stayed open would contradict it.
    // Null when no day in that window ever carried enough readings — the tile then hides
    // rather than claiming a number nobody earned
    @Nullable BigDecimal lowestTrendKg,
    @Nullable LocalDate lowestTrendOn,
    // The window behind lowestTrendKg. A FIXED policy like backfillDays, so the label cannot
    // drift from what was measured when the chart range changes
    int lowestWindowDays,
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
