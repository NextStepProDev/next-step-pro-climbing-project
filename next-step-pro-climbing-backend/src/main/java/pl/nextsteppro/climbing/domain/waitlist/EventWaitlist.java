package pl.nextsteppro.climbing.domain.waitlist;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.user.User;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_waitlist", uniqueConstraints = {
    @UniqueConstraint(name = "uq_event_waitlist_user_event", columnNames = {"user_id", "event_id"})
})
public class EventWaitlist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private WaitlistStatus status = WaitlistStatus.WAITING;

    @Column(name = "offered_at")
    @Nullable
    private Instant offeredAt;

    @Column(name = "confirmation_deadline")
    @Nullable
    private Instant confirmationDeadline;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EventWaitlist() {}

    public EventWaitlist(User user, Event event, int position) {
        this.user = user;
        this.event = event;
        this.position = position;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void offerSpot(Instant deadline) {
        this.status = WaitlistStatus.PENDING_CONFIRMATION;
        this.offeredAt = Instant.now();
        this.confirmationDeadline = deadline;
    }

    public void expire() {
        this.status = WaitlistStatus.EXPIRED;
    }

    public void returnToWaiting() {
        this.status = WaitlistStatus.WAITING;
        this.offeredAt = null;
        this.confirmationDeadline = null;
    }

    public boolean isPendingConfirmation() { return status == WaitlistStatus.PENDING_CONFIRMATION; }
    public boolean isWaiting() { return status == WaitlistStatus.WAITING; }
    public boolean isDeadlinePassed() {
        return confirmationDeadline != null && confirmationDeadline.isBefore(Instant.now());
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public Event getEvent() { return event; }
    public int getPosition() { return position; }
    public WaitlistStatus getStatus() { return status; }
    @Nullable public Instant getOfferedAt() { return offeredAt; }
    @Nullable public Instant getConfirmationDeadline() { return confirmationDeadline; }
    public Instant getCreatedAt() { return createdAt; }
}
