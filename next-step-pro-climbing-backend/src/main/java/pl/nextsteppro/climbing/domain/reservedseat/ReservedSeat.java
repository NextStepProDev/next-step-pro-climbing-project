package pl.nextsteppro.climbing.domain.reservedseat;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Miejsce trzymane "na zaproszenie" dla konkretnego użytkownika — w pojedynczym slocie
 * albo w wydarzeniu (dokładnie jedno z {@code timeSlot}/{@code event} jest ustawione).
 *
 * <p>Trzymane miejsce liczy się jako zajęte dla wszystkich poza wskazaną osobą. Rekord jest
 * "wisz[ą]cym" zaproszeniem dopóki adresat nie zarezerwuje miejsca — dlatego przy liczeniu
 * dostępności pomijamy zaproszenia osób, które mają już potwierdzoną rezerwację (patrz
 * {@link ReservedSeatRepository}), zamiast usuwać rekord przy rezerwacji.
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
}
