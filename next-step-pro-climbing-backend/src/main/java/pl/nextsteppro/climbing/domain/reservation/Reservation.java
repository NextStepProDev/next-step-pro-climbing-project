package pl.nextsteppro.climbing.domain.reservation;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "time_slot_id"})
})
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "time_slot_id", nullable = false)
    private TimeSlot timeSlot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    @Column(nullable = false)
    private int participants = 1;

    @Column(length = 500)
    @Nullable
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reservation() {}

    public Reservation(User user, TimeSlot timeSlot) {
        this.user = user;
        this.timeSlot = timeSlot;
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

    public User getUser() {
        return user;
    }

    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
    }

    public void cancelByAdmin() {
        this.status = ReservationStatus.CANCELLED_BY_ADMIN;
    }

    public void confirm() {
        this.status = ReservationStatus.CONFIRMED;
    }

    public boolean isConfirmed() {
        return status == ReservationStatus.CONFIRMED;
    }

    public boolean isCancelled() {
        return status == ReservationStatus.CANCELLED || status == ReservationStatus.CANCELLED_BY_ADMIN;
    }

    public boolean isCancelledByAdmin() {
        return status == ReservationStatus.CANCELLED_BY_ADMIN;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getParticipants() {
        return participants;
    }

    public void setParticipants(int participants) {
        this.participants = participants;
    }

    @Nullable
    public String getComment() {
        return comment;
    }

    public void setComment(@Nullable String comment) {
        this.comment = comment;
    }

    @Nullable
    public static String sanitizeComment(@Nullable String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.length() > 500 ? comment.substring(0, 500) : comment;
    }
}
