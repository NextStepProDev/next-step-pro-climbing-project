package pl.nextsteppro.climbing.domain.settlement;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * "This session is work for that payer."
 *
 * <p>It carries no money at all, and that is the point: it is what lets the app count how many
 * sessions a month held for one source, so that a single transfer can be divided by them into a
 * real rate. Without it a payout is a number with no denominator.
 *
 * <p>A session marked this way is not priced per participant, so it stays out of the "to be priced"
 * queue. That is the whole reason the mark exists: without it a session covered by a retainer hangs
 * in the queue for ever, and the only way out is to type a zero — which destroys the difference
 * between "free of charge" and "already paid for", and makes the revenue split claim that one-to-one
 * work earns nothing.
 *
 * <p>Its own table rather than a column on {@code time_slots} / {@code events}, for the same reason
 * settlements are: those shapes are served to anonymous visitors and cached, and a payer's name on
 * a public calendar entry is exactly the kind of obliging field that leaks by default.
 */
@Entity
@Table(name = "session_payouts")
public class SessionPayout {

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

    /**
     * Who settles this session in bulk. XOR with {@link #user}: an institution paying a lump for a
     * month, or a client whose standing subscription covers it. Two sides of the same phenomenon —
     * somebody paid for a period, and this session is part of what that payment bought.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_source_id")
    @Nullable
    private PayoutSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @Nullable
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SessionPayout() {}

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
