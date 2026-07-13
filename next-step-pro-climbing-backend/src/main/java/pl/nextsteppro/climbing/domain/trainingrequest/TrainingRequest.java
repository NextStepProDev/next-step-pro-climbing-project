package pl.nextsteppro.climbing.domain.trainingrequest;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.course.Course;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * A training time request submitted by a logged-in user.
 *
 * <p>Can be submitted inside an availability window ({@code windowSlot}) or freely
 * (an empty day in the calendar). The admin responds by creating a slot/event from the
 * request (status {@link TrainingRequestStatus#ACCEPTED} + link {@code createdSlot}/{@code createdEvent},
 * the requester gets an invitation-held seat), by marking phone contact
 * ({@code CONTACTED}), or by rejecting it ({@code REJECTED}).
 */
@Entity
@Table(name = "training_requests")
public class TrainingRequest {

    public static final int MAX_COMMENT_LENGTH = 1000;
    public static final int MAX_ADMIN_NOTE_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "window_slot_id")
    @Nullable
    private TimeSlot windowSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    @Nullable
    private Course course;

    @Column(name = "requested_date", nullable = false)
    private LocalDate requestedDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private int participants = 1;

    @Column(length = MAX_COMMENT_LENGTH)
    @Nullable
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrainingRequestStatus status = TrainingRequestStatus.PENDING;

    @Column(name = "admin_note", length = MAX_ADMIN_NOTE_LENGTH)
    @Nullable
    private String adminNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_slot_id")
    @Nullable
    private TimeSlot createdSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_event_id")
    @Nullable
    private Event createdEvent;

    @Column(name = "resolved_at")
    @Nullable
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TrainingRequest() {}

    public TrainingRequest(User user, LocalDate requestedDate, LocalTime startTime, LocalTime endTime, int participants) {
        this.user = user;
        this.requestedDate = requestedDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.participants = participants;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /**
     * HTML-escapes and trims the user comment (same pattern as Reservation.sanitizeComment).
     * The UTF-8 variant escapes only dangerous characters (&lt; &gt; " &amp; '); the one-arg variant
     * assumes ISO-8859-1 and would turn diacritics into entities (ó → &amp;oacute;), mangling Polish comments.
     */
    @Nullable
    public static String sanitizeComment(@Nullable String comment) {
        if (comment == null || comment.isBlank()) return null;
        String escaped = HtmlUtils.htmlEscape(comment.trim(), java.nio.charset.StandardCharsets.UTF_8.name());
        return escaped.length() > MAX_COMMENT_LENGTH ? escaped.substring(0, MAX_COMMENT_LENGTH) : escaped;
    }

    public void resolve(TrainingRequestStatus newStatus) {
        this.status = newStatus;
        this.resolvedAt = Instant.now();
    }

    public void reopen() {
        this.status = TrainingRequestStatus.PENDING;
        this.resolvedAt = null;
        this.createdSlot = null;
        this.createdEvent = null;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    @Nullable
    public TimeSlot getWindowSlot() {
        return windowSlot;
    }

    public void setWindowSlot(@Nullable TimeSlot windowSlot) {
        this.windowSlot = windowSlot;
    }

    @Nullable
    public Course getCourse() {
        return course;
    }

    public void setCourse(@Nullable Course course) {
        this.course = course;
    }

    public LocalDate getRequestedDate() {
        return requestedDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public int getParticipants() {
        return participants;
    }

    @Nullable
    public String getComment() {
        return comment;
    }

    public void setComment(@Nullable String comment) {
        this.comment = comment;
    }

    public TrainingRequestStatus getStatus() {
        return status;
    }

    public void setStatus(TrainingRequestStatus status) {
        this.status = status;
    }

    @Nullable
    public String getAdminNote() {
        return adminNote;
    }

    public void setAdminNote(@Nullable String adminNote) {
        this.adminNote = adminNote;
    }

    @Nullable
    public TimeSlot getCreatedSlot() {
        return createdSlot;
    }

    public void setCreatedSlot(@Nullable TimeSlot createdSlot) {
        this.createdSlot = createdSlot;
    }

    @Nullable
    public Event getCreatedEvent() {
        return createdEvent;
    }

    public void setCreatedEvent(@Nullable Event createdEvent) {
        this.createdEvent = createdEvent;
    }

    @Nullable
    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
