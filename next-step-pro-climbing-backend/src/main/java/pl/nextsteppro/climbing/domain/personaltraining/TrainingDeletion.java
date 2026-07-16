package pl.nextsteppro.climbing.domain.personaltraining;

import jakarta.persistence.*;
import pl.nextsteppro.climbing.domain.user.User;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Deletion record of a FUTURE personal training — the source row is gone, so the
 * unread counters and the "deleted trainings" strip need this snapshot instead.
 * Written only when the deleted training had not started yet (Warsaw time):
 * {@code deletedByAdmin} decides which side gets alerted (the other one).
 */
@Entity
@Table(name = "training_deletions")
public class TrainingDeletion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private User athlete;

    @Column(nullable = false, length = PersonalTraining.MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "training_date", nullable = false)
    private LocalDate trainingDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "deleted_by_admin", nullable = false)
    private boolean deletedByAdmin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TrainingDeletion() {}

    public TrainingDeletion(PersonalTraining training, boolean deletedByAdmin) {
        this.athlete = training.getAthlete();
        this.title = training.getTitle();
        this.trainingDate = training.getTrainingDate();
        this.startTime = training.getStartTime();
        this.endTime = training.getEndTime();
        this.deletedByAdmin = deletedByAdmin;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public User getAthlete() {
        return athlete;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getTrainingDate() {
        return trainingDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public boolean isDeletedByAdmin() {
        return deletedByAdmin;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
