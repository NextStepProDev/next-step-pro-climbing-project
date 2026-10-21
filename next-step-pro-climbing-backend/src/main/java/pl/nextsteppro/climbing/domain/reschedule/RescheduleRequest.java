package pl.nextsteppro.climbing.domain.reschedule;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.StoredText;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A booked client asking to move their term. Accepting moves the WHOLE slot or event, everyone on
 * it included (decision 2026-10-21) — which is why the coach's card names the other participants.
 *
 * <p>The target is the pair (term, person), never a reservation: an event booking is one
 * reservation row per day, and a cancel + rebook is a new row. See V105.
 *
 * <p>{@code old*} is a snapshot of the term as the client saw it. Accepting compares it with the
 * live term: if the coach moved the term in the meantime, "from Tuesday to Thursday" describes a
 * Tuesday that no longer exists, and the answer must be a refusal, not a silent second move.
 */
@Entity
@Table(name = "reschedule_requests")
public class RescheduleRequest {

    public static final int MAX_COMMENT_LENGTH = 1000;
    public static final int MAX_ADMIN_NOTE_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id")
    @Nullable
    private TimeSlot timeSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    @Nullable
    private Event event;

    @Column(name = "old_date", nullable = false)
    private LocalDate oldDate;

    @Column(name = "old_end_date")
    @Nullable
    private LocalDate oldEndDate;

    @Column(name = "old_start_time")
    @Nullable
    private LocalTime oldStartTime;

    @Column(name = "old_end_time")
    @Nullable
    private LocalTime oldEndTime;

    @Column(name = "new_date", nullable = false)
    private LocalDate newDate;

    @Column(name = "new_end_date")
    @Nullable
    private LocalDate newEndDate;

    @Column(name = "new_start_time")
    @Nullable
    private LocalTime newStartTime;

    @Column(name = "new_end_time")
    @Nullable
    private LocalTime newEndTime;

    @Column(length = MAX_COMMENT_LENGTH)
    @Nullable
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RescheduleRequestStatus status = RescheduleRequestStatus.PENDING;

    @Column(name = "admin_note", length = MAX_ADMIN_NOTE_LENGTH)
    @Nullable
    private String adminNote;

    @Column(name = "resolved_at")
    @Nullable
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RescheduleRequest() {}

    public static RescheduleRequest forSlot(User user, TimeSlot slot, RescheduleTerm proposed) {
        RescheduleRequest r = new RescheduleRequest(user, RescheduleTerm.of(slot), proposed);
        r.timeSlot = slot;
        return r;
    }

    public static RescheduleRequest forEvent(User user, Event event, RescheduleTerm proposed) {
        RescheduleRequest r = new RescheduleRequest(user, RescheduleTerm.of(event), proposed);
        r.event = event;
        return r;
    }

    private RescheduleRequest(User user, RescheduleTerm old, RescheduleTerm proposed) {
        this.user = user;
        this.oldDate = old.date();
        this.oldEndDate = old.endDate();
        this.oldStartTime = old.startTime();
        this.oldEndTime = old.endTime();
        this.newDate = proposed.date();
        this.newEndDate = proposed.endDate();
        this.newStartTime = proposed.startTime();
        this.newEndTime = proposed.endTime();
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @Nullable
    public static String sanitizeComment(@Nullable String comment) {
        return StoredText.escapeAndCap(comment, MAX_COMMENT_LENGTH);
    }

    public void resolve(RescheduleRequestStatus newStatus, @Nullable String note) {
        this.status = newStatus;
        this.adminNote = note;
        this.resolvedAt = Instant.now();
    }

    public boolean isPending() {
        return status == RescheduleRequestStatus.PENDING;
    }

    public RescheduleTerm oldTerm() {
        return new RescheduleTerm(oldDate, oldEndDate, oldStartTime, oldEndTime);
    }

    public RescheduleTerm newTerm() {
        return new RescheduleTerm(newDate, newEndDate, newStartTime, newEndTime);
    }

    /** The term as it stands right now — the snapshot is compared against this. */
    public RescheduleTerm liveTerm() {
        // Exactly one of the two is set (CHECK in V105).
        return timeSlot != null ? RescheduleTerm.of(timeSlot) : RescheduleTerm.of(Objects.requireNonNull(event));
    }

    /** False once the term was moved by something other than this request. */
    public boolean termStillMatches() {
        return oldTerm().sameAs(liveTerm());
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    @Nullable
    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    @Nullable
    public Event getEvent() {
        return event;
    }

    @Nullable
    public String getComment() {
        return comment;
    }

    public void setComment(@Nullable String comment) {
        this.comment = comment;
    }

    public RescheduleRequestStatus getStatus() {
        return status;
    }

    @Nullable
    public String getAdminNote() {
        return adminNote;
    }

    @Nullable
    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
