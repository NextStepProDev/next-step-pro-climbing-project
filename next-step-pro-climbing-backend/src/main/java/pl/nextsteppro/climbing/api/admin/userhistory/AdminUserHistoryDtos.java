package pl.nextsteppro.climbing.api.admin.userhistory;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/*
 * Read-only shapes for the admin user card. There is no request record here on purpose: the card
 * has no write path at all, mirroring the coach's read-only view of weigh-ins and the logbook.
 */

/** Header, the Account tab, and the headline tiles, in one response. */
record UserDetailDto(
    UUID id,
    String firstName,
    String lastName,
    String nickname,
    String email,
    String phone,
    @Nullable String avatarUrl,
    String role,
    /** Drives whether the card offers a Training tab at all — see {@link UserCountsDto}. */
    boolean athlete,

    // ---- Account ----
    boolean emailVerified,
    @Nullable Instant emailVerifiedAt,
    boolean hasPassword,
    @Nullable String oauthProvider,
    String preferredLanguage,
    boolean emailNotificationsEnabled,
    boolean newsletterSubscribed,
    boolean newsletterChoiceMade,
    @Nullable Instant newsletterSubscribedAt,
    boolean ascentsPublic,
    @Nullable Instant trainingConsentAt,
    int failedLoginAttempts,
    @Nullable Instant lockedUntil,
    boolean accountLocked,
    Instant createdAt,
    Instant updatedAt,

    UserCountsDto counts
) {}

/**
 * Headline tiles. The two training numbers are {@code null} — not zero — for anyone without the
 * athlete flag: the calendar and the logbook are unreadable for the admin in that case (the
 * logbook of a plain user is private), so "no data to show" and "a genuine zero" must not render
 * the same. Null here is what removes the tile, the same way it removes the Training tab.
 */
record UserCountsDto(
    long reservationsConfirmed,
    long reservationsCancelled,
    @Nullable Long trainingsCompleted,
    @Nullable Long ascents
) {}

/**
 * Everything booking-shaped about one person, in the order the Reservations tab renders it.
 *
 * <p>{@code past} is the only paged section — see {@code AdminUserHistoryService}. The total ships
 * alongside it so the UI can say "25 of 340" rather than guessing from a full page whether more
 * exists, which is wrong exactly when the count is an exact multiple of the page size.
 */
record UserReservationHistoryDto(
    List<HistoryReservationDto> upcoming,
    List<HistoryReservationDto> past,
    long pastTotal,
    int pastPage,
    int pastSize,
    List<HistoryWaitlistDto> waitlist,
    List<HistoryInviteDto> invitations,
    List<HistoryRequestDto> trainingRequests
) {}

/**
 * Deliberately without {@code spotsAvailable}: the athlete-facing DTO carries it and pays for it
 * with extra per-slot COUNT batches, which buy nothing in a read-only admin view.
 */
record HistoryReservationDto(
    UUID id,
    UUID slotId,
    @Nullable UUID eventId,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    String title,
    @Nullable String eventTitle,
    String status,
    int participants,
    @Nullable String comment,
    boolean createdByAdmin,
    Instant createdAt
) {}

/** One entry on a slot or an event queue. {@code kind} says which — the two are separate tables. */
record HistoryWaitlistDto(
    UUID id,
    String kind,
    UUID targetId,
    String title,
    @Nullable LocalDate date,
    @Nullable LocalTime startTime,
    int position,
    String status,
    @Nullable Instant confirmationDeadline,
    Instant createdAt
) {}

/** A seat held by name that has not been turned into a booking yet. */
record HistoryInviteDto(
    UUID id,
    String kind,
    UUID targetId,
    String title,
    @Nullable LocalDate date,
    @Nullable LocalTime startTime,
    /** When an admin manually sent the invitation email; never set automatically. */
    @Nullable Instant notifiedAt,
    Instant createdAt
) {}

record HistoryRequestDto(
    UUID id,
    LocalDate requestedDate,
    LocalTime startTime,
    LocalTime endTime,
    int participants,
    @Nullable String comment,
    String status,
    @Nullable String adminNote,
    @Nullable String courseTitle,
    @Nullable Instant resolvedAt,
    Instant createdAt
) {}
