package pl.nextsteppro.climbing.domain.activitylog;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity_logs")
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private ActivityActionType actionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id")
    @Nullable
    private TimeSlot timeSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    @Nullable
    private Event event;

    @Nullable
    private Integer participants;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ActivityLog() {}

    public ActivityLog(User user, ActivityActionType actionType) {
        this.user = user;
        this.actionType = actionType;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public ActivityActionType getActionType() {
        return actionType;
    }

    @Nullable
    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(@Nullable TimeSlot timeSlot) {
        this.timeSlot = timeSlot;
    }

    @Nullable
    public Event getEvent() {
        return event;
    }

    public void setEvent(@Nullable Event event) {
        this.event = event;
    }

    @Nullable
    public Integer getParticipants() {
        return participants;
    }

    public void setParticipants(@Nullable Integer participants) {
        this.participants = participants;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
