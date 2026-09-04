package pl.nextsteppro.climbing.domain.reservedseat;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * A seat held "by invitation" for a specific user — on a single slot or on an event
 * (exactly one of {@code timeSlot}/{@code event} is set).
 *
 * <p>A held seat counts as taken for everyone except the designated person. The record is
 * a "pending" invitation until the recipient books the seat — that is why, when counting
 * availability, we skip invitations of people who already have a confirmed reservation (see
 * {@link ReservedSeatRepository}) instead of deleting the record on booking.
 */
@Entity
@Table(name = "reserved_seats")
public class ReservedSeat {

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // When the admin manually sent the invitation email (null = not notified yet).
    @Column(name = "notified_at")
    @Nullable
    private Instant notifiedAt;

    protected ReservedSeat() {}

    public ReservedSeat(TimeSlot timeSlot, User user) {
        this.timeSlot = timeSlot;
        this.user = user;
    }

    public ReservedSeat(Event event, User user) {
        this.event = event;
        this.user = user;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    @Nullable
    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    @Nullable
    public Event getEvent() {
        return event;
    }

    public User getUser() {
        return user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public Instant getNotifiedAt() {
        return notifiedAt;
    }

    public void markNotified() {
        this.notifiedAt = Instant.now();
    }

    /**
     * Forgets that the invitation was mailed — called when the slot/event moves to a different
     * date or time. The message the recipient is holding (and the calendar entry its ICS
     * attachment created) now describes a term that no longer exists, so "sent" would be a claim
     * about a mail that is no longer true. Clearing it makes the admin's list say "not sent",
     * which is the honest state, and brings the send button back with this person counted in it.
     *
     * <p>Deliberately not paired with an automatic re-send: the invitation mail says "a seat is
     * held for you", not "the time changed", so mailing it again unprompted delivers a near
     * duplicate the recipient can easily skim past. The admin re-sends with one click instead.
     */
    public void clearNotified() {
        this.notifiedAt = null;
    }
}
