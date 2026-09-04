package pl.nextsteppro.climbing.api.admin;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

// Time Slot DTOs

record CreateTimeSlotRequest(
    @NotNull LocalDate date,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @Min(0) @Max(100) int maxParticipants,
    @Nullable String title,
    @Nullable UUID eventId,
    boolean isAvailabilityWindow,
    // Instructor absence — created closed: no seats, no invitations, no waitlist
    boolean isUnavailable,
    // Users for whom seats are held "by invitation"
    @Nullable List<UUID> invitedUserIds,
    // Training request this slot is created from (→ status ACCEPTED + link)
    @Nullable UUID trainingRequestId
) {}

// Held seat + invited person data (prefill for the slot/event edit form)
//
// The last two fields exist so the invite list can say why someone will get no mail instead of
// flatly reporting "not sent". Both are reasons the send loop skips a person, so the count on the
// send button equals the set that will actually be written to — the button used to offer "send to
// 1" for someone the backend had always skipped, and answer with "sent 0".
record InvitedUserDto(
    UUID userId,
    String fullName,
    String email,
    // When the admin manually sent the invitation email (null = not yet)
    @Nullable Instant notifiedAt,
    // false = this person turned emails off in their profile; the invitation still holds their
    // seat and still shows up in their in-app "Invitations" section, we just do not write to them
    boolean emailNotificationsEnabled,
    // true = they already booked this slot/event, so they got the ordinary confirmation instead
    boolean alreadyBooked
) {}

record TimeSlotAdminDto(
    UUID id,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    int maxParticipants,
    int currentParticipants,
    boolean blocked,
    @Nullable String blockReason,
    @Nullable String title,
    @Nullable UUID eventId,
    boolean isAvailabilityWindow,
    boolean isUnavailable
) {}

record SlotParticipantsDto(
    UUID slotId,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    int maxParticipants,
    List<ParticipantDto> participants,
    List<GuestParticipantDto> guestParticipants
) {}

record ParticipantDto(
    UUID reservationId,
    UUID userId,
    String fullName,
    String email,
    String phone,
    @Nullable String comment,
    int participants,
    Instant registeredAt
) {}

record GuestParticipantDto(
    UUID id,
    String note,
    int participants,
    Instant createdAt
) {}

record AddRegisteredParticipantRequest(
    @NotNull UUID userId,
    @Min(1) @Max(20) int participants,
    @Nullable String comment
) {}

record AddGuestParticipantRequest(
    @NotBlank String note,
    @Min(1) @Max(20) int participants
) {}

record SlotWaitlistDto(
    UUID slotId,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    List<WaitlistAdminEntryDto> entries
) {}

record WaitlistAdminEntryDto(
    UUID waitlistId,
    UUID userId,
    String fullName,
    String email,
    String phone,
    int position,
    String status,
    @Nullable Instant confirmationDeadline,
    Instant joinedAt,
    // True in the global "Waitlists" view when this entry joined after the admin's previous
    // read (one of the joins the reservations-tab badge was alerting about). Always false in
    // the per-slot / per-event waitlist views.
    boolean isNew
) {}

record EventWaitlistAdminDto(
    UUID eventId,
    String title,
    LocalDate startDate,
    LocalDate endDate,
    List<WaitlistAdminEntryDto> entries
) {}

// Global "Waitlists" view in the Reservations tab — all upcoming slots/events someone is
// currently waiting for (WAITING/PENDING_CONFIRMATION), grouped per slot/event
record AdminWaitlistsDto(
    List<SlotWaitlistGroupDto> slotWaitlists,
    List<EventWaitlistAdminDto> eventWaitlists
) {}

record SlotWaitlistGroupDto(
    UUID slotId,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    @Nullable String title,
    List<WaitlistAdminEntryDto> entries
) {}

record UpdateTimeSlotRequest(
    @Nullable LocalDate date,
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime,
    @Nullable @Min(0) @Max(100) Integer maxParticipants,
    @Nullable String title,
    @Nullable Boolean isAvailabilityWindow,
    @Nullable Boolean isUnavailable,
    @Nullable Boolean sendNotifications,
    // null = leave invitations unchanged; a list (even empty) = set exactly this set of invitees
    @Nullable List<UUID> invitedUserIds
) {}

record UpdateReservationParticipantsRequest(
    @Min(1) @Max(20) int participants
) {}

// Event DTOs

record CreateEventRequest(
    @NotBlank String title,
    @Nullable String description,
    @Nullable String location,
    @NotBlank String eventType,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @Min(0) @Max(100) int maxParticipants,
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime,
    @Nullable UUID courseId,
    @Nullable List<UUID> invitedUserIds,
    // Training request this event is created from (→ status ACCEPTED + link)
    @Nullable UUID trainingRequestId
) {
    @AssertTrue(message = "{validation.event.date.range}")
    boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    /* Only when the event lives inside ONE day. Across a range the times belong to different
     * days ("from 18:00 on Friday until 08:00 on Sunday"), so end before start is normal there;
     * on a single day it is a window of negative length, which every renderer draws as nothing —
     * an entry the admin believes they created and then cannot find. */
    @AssertTrue(message = "{validation.event.time.range}")
    boolean isSameDayTimeRangeValid() {
        if (startDate == null || !startDate.equals(endDate)) return true;
        if (startTime == null || endTime == null) return true;
        return endTime.isAfter(startTime);
    }
}

record UpdateEventRequest(
    @Nullable String title,
    @Nullable String description,
    @Nullable String location,
    @Nullable String eventType,
    @Nullable LocalDate startDate,
    @Nullable LocalDate endDate,
    @Nullable Integer maxParticipants,
    @Nullable Boolean active,
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime,
    @Nullable UUID courseId,
    @Nullable Boolean removeCourse,
    // null = leave invitations unchanged; a list (even empty) = set exactly this set of invitees
    @Nullable List<UUID> invitedUserIds
) {}

record EventAdminDto(
    UUID id,
    String title,
    @Nullable String description,
    @Nullable String location,
    String eventType,
    LocalDate startDate,
    LocalDate endDate,
    int maxParticipants,
    int currentParticipants,
    boolean active,
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime,
    @Nullable UUID courseId,
    @Nullable String courseTitle
) {}

record EventDetailAdminDto(
    UUID id,
    String title,
    @Nullable String description,
    @Nullable String location,
    String eventType,
    LocalDate startDate,
    LocalDate endDate,
    int maxParticipants,
    int currentParticipants,
    boolean active,
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime,
    @Nullable UUID courseId,
    @Nullable String courseTitle,
    List<TimeSlotAdminDto> slots
) {}

record EventParticipantsDto(
    UUID eventId,
    int maxParticipants,
    List<ParticipantDto> participants,
    List<GuestParticipantDto> guestParticipants
) {}

// Reservation DTOs

record ReservationAdminDto(
    UUID id,
    String userFullName,
    String userEmail,
    String userPhone,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    @Nullable String title,
    @Nullable String comment,
    int participants,
    @Nullable LocalDate eventStartDate,
    @Nullable LocalDate eventEndDate,
    @Nullable UUID eventId,
    // True when this reservation was created after the admin's previous "read" marker
    // (i.e. it is one of the reservations the navbar/tab badge was alerting about).
    // Always false for past / by-date listings and for admin-created reservations.
    boolean isNew
) {}

// User DTOs

record UserAdminDto(
    UUID id,
    String firstName,
    String lastName,
    String email,
    String phone,
    String role,
    Instant createdAt,
    boolean newsletterSubscribed,
    boolean isAthlete,
    // Whether the address was ever confirmed. Every picker in the panel is fed by this one
    // listing, so without the flag an account nobody can log into looks exactly like a real one —
    // and the actions that refuse it (invites, manual sign-up, bulk mail) would refuse it silently.
    boolean emailVerified
) {}

record SetAthleteRequest(boolean isAthlete) {}

// Mail DTOs

enum RecipientType { ALL, NEWSLETTER, SELECTED }

record SendMailRequest(
    @NotNull RecipientType recipientType,
    @Nullable List<UUID> userIds,
    @NotBlank String subject,
    @NotBlank String body
) {}

record MailSendResponse(int recipientCount) {}

// Admin panel notifications: badges on the tabs (Requests/Reservations) and on the Admin navbar link
record AdminNotificationsDto(
    int pendingRequests,
    int newReservations,
    // New waitlist joins (slots + events) since last "read" —
    // same marker as newReservations; entering the Reservations tab resets both
    int newWaitlistEntries,
    // Athlete training-calendar activity (new trainings/completions/comments)
    // across all athletes, per this admin's read markers (training_calendar_reads)
    long athleteActivity,
    // Accounts confirmed since last "read" — its own marker (admin_users_seen_at), cleared by
    // opening the Users list, not the Reservations tab
    int newUsers
) {}

record NotifyParticipantsResult(int notifiedCount) {}

/**
 * Result of sending invitation mails. Carries the skipped count alongside the sent one because
 * "sent 0" has two very different meanings: everyone was already invited (nothing to do) and
 * everyone left has emails switched off (there is nobody we are allowed to write to). Reporting
 * only the first number would let the admin read the second case as a failure.
 *
 * <p>Separate from {@link NotifyParticipantsResult} on purpose: the participant bell has no
 * equivalent skip to report, and widening its shape would mean answering a question nobody asked.
 */
record NotifyInvitesResult(int notifiedCount, int skippedNotificationsOff) {}

/**
 * An edit sends its modification mails silently — the admin used to save a moved slot and get no
 * sign that anyone was told. How many people were mailed is a property of the operation, not of the
 * slot, so it travels alongside the entity instead of inside it (a listing has no such number).
 * The count matches what actually went out: participants with mail notifications switched off are
 * skipped by {@code MailService}, so they are skipped here too.
 *
 * <p>{@code hadParticipants} separates the two ways of sending nothing, which are different
 * answers: an empty slot had nobody to write to, while a booked one that mailed no-one means the
 * edit did not concern the participants (or they all opted out). A boolean rather than a second
 * count on purpose — the two sides count different units (a slot counts seats, an event counts
 * people), and the panel only asks whether the silence had an audience.
 */
record SlotUpdateResultDto(TimeSlotAdminDto slot, int notifiedCount, boolean hadParticipants) {}

/** Event twin of {@link SlotUpdateResultDto}. */
record EventUpdateResultDto(EventAdminDto event, int notifiedCount, boolean hadParticipants) {}

record NotifySlotParticipantsRequest(
    @Nullable LocalDate previousDate,
    @Nullable LocalTime previousStartTime,
    @Nullable LocalTime previousEndTime
) {}

