package pl.nextsteppro.climbing.domain.reservation;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "guest_reservations")
public class GuestReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id")
    @Nullable
    private TimeSlot timeSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    @Nullable
    private Event event;

    @Column(nullable = false, length = 500)
    private String note;

    @Column(nullable = false)
    private int participants = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GuestReservation() {}

    public GuestReservation(TimeSlot timeSlot, String note, int participants) {
        this.timeSlot = timeSlot;
        this.note = note;
        this.participants = participants;
    }

    public GuestReservation(Event event, String note, int participants) {
        this.event = event;
        this.note = note;
        this.participants = participants;
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

    public UUID getId() { return id; }

    @Nullable
    public TimeSlot getTimeSlot() { return timeSlot; }

    @Nullable
    public Event getEvent() { return event; }

    public String getNote() { return note; }

    public int getParticipants() { return participants; }

    public Instant getCreatedAt() { return createdAt; }
}
