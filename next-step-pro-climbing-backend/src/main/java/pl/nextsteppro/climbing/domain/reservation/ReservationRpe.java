package pl.nextsteppro.climbing.domain.reservation;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * An athlete's RPE rating (1-10) for an attended reservation, with an optional note. Complements
 * the RPE stored on personal trainings so the intensity stats cover the athlete's whole activity.
 * One per reservation (unique); editable via upsert.
 */
@Entity
@Table(name = "reservation_rpe")
public class ReservationRpe {

    public static final int MAX_NOTE_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @Column(nullable = false)
    private int rpe;

    @Column(length = MAX_NOTE_LENGTH)
    @Nullable
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReservationRpe() {}

    public ReservationRpe(Reservation reservation, int rpe, @Nullable String note) {
        this.reservation = reservation;
        this.rpe = rpe;
        this.note = note;
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

    /** Same UTF-8 HTML-escape as elsewhere (keeps Polish diacritics); null/blank allowed. */
    @Nullable
    public static String sanitizeNote(@Nullable String note) {
        if (note == null || note.isBlank()) return null;
        String escaped = HtmlUtils.htmlEscape(note.trim(), java.nio.charset.StandardCharsets.UTF_8.name());
        return escaped.length() > MAX_NOTE_LENGTH ? escaped.substring(0, MAX_NOTE_LENGTH) : escaped;
    }

    public void update(int rpe, @Nullable String note) {
        this.rpe = rpe;
        this.note = note;
    }

    public UUID getId() {
        return id;
    }

    /** FK access without initialising the lazy proxy. NOT named getReservationId() — a
     * "reservationId" bean property would break the derived findByReservationId query. */
    public UUID reservationId() {
        return reservation.getId();
    }

    public int getRpe() {
        return rpe;
    }

    @Nullable
    public String getNote() {
        return note;
    }
}
