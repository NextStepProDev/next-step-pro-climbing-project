package pl.nextsteppro.climbing.domain.settlement;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;

import java.time.Instant;
import java.util.UUID;

/**
 * "This session is work for that payer."
 *
 * <p>It carries no money at all, and that is the point: it is what lets the app count how many
 * sessions a month held for one source, so that a single transfer can be divided by them into a
 * real rate. Without it a payout is a number with no denominator.
 *
 * <p>A session marked this way is not priced per participant — there is nobody to charge — so it
 * stays out of the "to be priced" queue.
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payout_source_id", nullable = false)
    private PayoutSource source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SessionPayout() {}

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
