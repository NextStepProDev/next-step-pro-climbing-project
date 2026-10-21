package pl.nextsteppro.climbing.api.admin.userexport;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/*
 * Records of their own rather than the API's DTOs, on purpose: a field added to a screen's DTO must
 * not land in a file handed to somebody outside the system, and a field added here must not start
 * travelling to a screen. Everything a reader cannot interpret (internal flags, read markers,
 * version counters) is left out; everything that is the person's own data is in.
 */

/**
 * Everything held about one person, as handed over on a GDPR access / portability request.
 *
 * <p>Money is NOT here: the settlement types are unreachable from outside their packages by design
 * ({@code SettlementIsolationTest}), so the browser fetches that part from the settlement endpoint
 * and adds it to the same file — the same split the user card's money block already makes.
 *
 * @param schemaVersion bumped when a section changes shape, so a file read years later says what
 *                      it was written against
 */
record UserDataExportDto(
    int schemaVersion,
    Instant exportedAt,
    ExportAccountDto account,
    List<ExportConsentEventDto> newsletterConsentHistory,
    List<ExportReservationDto> reservations,
    List<ExportWaitlistDto> waitlists,
    List<ExportInvitationDto> invitations,
    List<ExportTrainingRequestDto> trainingRequests,
    List<ExportRescheduleRequestDto> rescheduleRequests,
    List<ExportStarredNewsDto> starredNews,
    List<ExportActivityDto> activity,
    List<ExportTrainingDto> trainingPlan,
    List<ExportCommentDto> messages,
    List<ExportGoalDto> goals,
    List<ExportWeightDto> weights,
    List<ExportAscentDto> ascents
) {}

/**
 * The account. Secrets travel only as their harmless stand-ins, the same rule as the user card:
 * {@code hasPassword} instead of the hash, the provider without its id, and no unsubscribe token —
 * that one is permanent and cannot be revoked, so a file mailed around must never carry it.
 */
record ExportAccountDto(
    UUID id,
    String firstName,
    String lastName,
    @Nullable String nickname,
    String email,
    @Nullable String phone,
    @Nullable String avatarFile,
    String role,
    String preferredLanguage,
    boolean hasPassword,
    @Nullable String oauthProvider,
    @Nullable Instant emailVerifiedAt,
    boolean emailNotificationsEnabled,
    boolean newsletterSubscribed,
    @Nullable Instant newsletterSubscribedAt,
    boolean ascentsPublic,
    boolean athlete,
    @Nullable Instant trainingConsentAt,
    Instant createdAt,
    Instant updatedAt
) {}

record ExportConsentEventDto(String action, String source, Instant at) {}

/**
 * @param slotId  the session; messages under a booked session point at it
 * @param eventId set when the session is a day of an event
 */
record ExportReservationDto(
    UUID id,
    UUID slotId,
    @Nullable UUID eventId,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    @Nullable String title,
    String status,
    int participants,
    @Nullable String comment,
    boolean createdByAdmin,
    @Nullable Integer rpe,
    @Nullable String rpeNote,
    Instant createdAt
) {}

record ExportWaitlistDto(
    String targetType,
    UUID targetId,
    @Nullable String title,
    LocalDate date,
    @Nullable LocalTime startTime,
    int position,
    String status,
    Instant createdAt
) {}

record ExportInvitationDto(
    String targetType,
    UUID targetId,
    @Nullable String title,
    LocalDate date,
    @Nullable LocalTime startTime,
    @Nullable Instant notifiedAt,
    Instant createdAt
) {}

record ExportTrainingRequestDto(
    UUID id,
    LocalDate requestedDate,
    LocalTime startTime,
    LocalTime endTime,
    int participants,
    @Nullable String comment,
    String status,
    @Nullable String adminNote,
    @Nullable String course,
    @Nullable Instant resolvedAt,
    Instant createdAt
) {}

/** A request to move a booked term; {@code oldTerm}/{@code newTerm} are "dd.MM.yyyy, HH:mm – HH:mm". */
record ExportRescheduleRequestDto(
    UUID id,
    String targetType,
    @Nullable String title,
    String oldTerm,
    String newTerm,
    @Nullable String comment,
    String status,
    @Nullable String adminNote,
    @Nullable Instant resolvedAt,
    Instant createdAt
) {}

record ExportStarredNewsDto(UUID newsId, String title, Instant starredAt) {}

/** An entry of the activity log in which this person is the actor (see {@code ActivityLogService}). */
record ExportActivityDto(
    String action,
    @Nullable UUID slotId,
    @Nullable UUID eventId,
    @Nullable Integer participants,
    @Nullable String description,
    Instant at
) {}

/**
 * One plan entry. {@code materials} lists files by name and metadata only — the bytes are not in a
 * JSON file and go out separately if asked for.
 */
record ExportTrainingDto(
    UUID id,
    String kind,
    LocalDate date,
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime,
    String title,
    @Nullable String description,
    @Nullable Integer targetCalories,
    boolean createdByCoach,
    @Nullable Instant completedAt,
    @Nullable Integer rpe,
    @Nullable String feedback,
    List<ExportFileDto> materials,
    Instant createdAt,
    Instant updatedAt
) {}

/**
 * @param url  a link material; null for a file
 * @param name the original file name for a file, the label for a link
 */
record ExportFileDto(
    String kind,
    @Nullable String url,
    @Nullable String name,
    @Nullable String mimeType,
    @Nullable Long sizeBytes,
    @Nullable Instant expiresAt,
    Instant createdAt
) {}

/**
 * One message in a thread. The coach's messages are included — the conversation is with this
 * person — but named only by role: the coach's account is not the subject of this export.
 *
 * @param author  {@code "ME"} or {@code "COACH"}
 * @param trainingId set for a message under a plan entry; otherwise slot or event of a booking
 */
record ExportCommentDto(
    UUID id,
    String author,
    @Nullable UUID trainingId,
    @Nullable UUID slotId,
    @Nullable UUID eventId,
    @Nullable String body,
    List<ExportFileDto> attachments,
    Instant createdAt,
    @Nullable Instant editedAt
) {}

record ExportGoalDto(
    String kind,
    String horizon,
    @Nullable String content,
    @Nullable BigDecimal startWeightKg,
    @Nullable BigDecimal targetWeightKg,
    @Nullable LocalDate targetDate,
    @Nullable Instant achievedAt,
    boolean achievedAutomatically,
    Instant createdAt
) {}

record ExportWeightDto(LocalDate measuredOn, BigDecimal weightKg, Instant createdAt) {}

/** Grade as the label people read ("7a+"), not the enum constant the database stores. */
record ExportAscentDto(
    LocalDate climbedOn,
    String terrain,
    @Nullable String discipline,
    String grade,
    @Nullable String originalGrade,
    String style,
    @Nullable Boolean winter,
    @Nullable Integer lengthMeters,
    @Nullable Integer pitches,
    @Nullable Integer durationMinutes,
    @Nullable String ledGrade,
    @Nullable Integer ledPitches,
    @Nullable String partners,
    String area,
    String crag,
    String routeName,
    @Nullable Integer attempts,
    @Nullable Integer qualityStars,
    @Nullable String comment,
    boolean hiddenFromPublic,
    Instant createdAt
) {}
