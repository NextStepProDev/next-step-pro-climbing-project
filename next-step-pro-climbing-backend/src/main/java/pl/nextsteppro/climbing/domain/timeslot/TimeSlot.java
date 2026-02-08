package pl.nextsteppro.climbing.domain.timeslot;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.event.Event;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "time_slots")
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    @Nullable
    private Event event;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    @Column(length = 200)
    @Nullable
    private String title;

    @Column(name = "is_blocked", nullable = false)
    private boolean blocked = false;

    @Column(name = "block_reason")
    @Nullable
    private String blockReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TimeSlot() {}

    public TimeSlot(LocalDate date, LocalTime startTime, LocalTime endTime, int maxParticipants) {
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.maxParticipants = maxParticipants;
    }

    public TimeSlot(Event event, LocalDate date, LocalTime startTime, LocalTime endTime, int maxParticipants) {
        this(date, startTime, endTime, maxParticipants);
        this.event = event;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    @Nullable
    public Event getEvent() {
        return event;
    }

    public void setEvent(@Nullable Event event) {
        this.event = event;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public int getMaxParticipants() {
        return maxParticipants;
    }

    public void setMaxParticipants(int maxParticipants) {
        this.maxParticipants = maxParticipants;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void block(@Nullable String reason) {
        this.blocked = true;
        this.blockReason = reason;
    }

    public void unblock() {
        this.blocked = false;
        this.blockReason = null;
    }

    @Nullable
    public String getBlockReason() {
        return blockReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    public void setTitle(@Nullable String title) {
        this.title = title;
    }

    /**
     * Returns display title: slot's own title, or linked event's title, or null.
     */
    @Nullable
    public String getDisplayTitle() {
        if (title != null) return title;
        if (event != null) return event.getTitle();
        return null;
    }

    public boolean belongsToEvent() {
        return event != null;
    }
}
