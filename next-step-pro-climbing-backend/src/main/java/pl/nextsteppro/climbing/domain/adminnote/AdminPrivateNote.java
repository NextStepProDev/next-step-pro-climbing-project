package pl.nextsteppro.climbing.domain.adminnote;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTraining;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * The owner's private note about one session — a time slot, an event, or an entry in an athlete's
 * training calendar.
 *
 * <p><b>Only the author ever reads it.</b> Not the client who booked the slot, not the athlete
 * whose plan it hangs on, and not a second admin. That is why {@code author} is half of every
 * unique key and half of every lookup: this is one person's notebook, not a shared back-office
 * annotation.
 *
 * <p>Exactly one of {@code timeSlot} / {@code event} / {@code training} is set (CHECK in V89).
 * An event carries a single note however many days it spans, so an event's note hangs on the
 * event and never on the per-day slots that the first booking silently creates for it — the
 * service rejects a slot that {@code belongsToEvent()}.
 *
 * <p>Writes go through the repository's upsert rather than this entity, so there is no
 * {@code update()} here; the entity exists for reads and for the schema.
 */
@Entity
@Table(name = "admin_private_notes")
public class AdminPrivateNote {

    public static final int MAX_BODY_LENGTH = 4000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id")
    @Nullable
    private TimeSlot timeSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    @Nullable
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id")
    @Nullable
    private PersonalTraining training;

    @Column(nullable = false, length = MAX_BODY_LENGTH)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AdminPrivateNote() {}

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Trims and truncates. Deliberately <b>not</b> HTML-escaped, unlike
     * {@code TrainingComment.sanitizeBody}: escaping at write turns the author's own quotes and
     * apostrophes into entities, which is why the rest of the app needs {@code decodeHtmlEntities}
     * on render. This text never reaches an email or an {@code innerHTML} sink — React renders it
     * as a text node — and the only writer and the only reader are the same admin.
     *
     * <p>Returns {@code null} for blank input: an empty note is a deleted note (CHECK in V89).
     */
    @Nullable
    public static String sanitizeBody(@Nullable String body) {
        if (body == null || body.isBlank()) return null;
        String trimmed = body.trim();
        return trimmed.length() > MAX_BODY_LENGTH ? trimmed.substring(0, MAX_BODY_LENGTH) : trimmed;
    }

    public UUID getId() {
        return id;
    }

    public String getBody() {
        return body;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
