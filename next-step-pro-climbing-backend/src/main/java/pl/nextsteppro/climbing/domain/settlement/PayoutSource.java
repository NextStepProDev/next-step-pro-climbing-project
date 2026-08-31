package pl.nextsteppro.climbing.domain.settlement;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Somebody who pays for a batch of work rather than for a seat — a school, a club, a gym.
 *
 * <p>This is the other side of the money model. A {@code Settlement} says what one participant owes
 * for one session, decided by the owner at the time. A source says the opposite: the work is done
 * first, somebody else calculates it, and one transfer covers a month of it.
 *
 * <p><b>Archived, never deleted.</b> Payouts and session assignments point here, and a collaboration
 * that ended does not un-earn the money made during it. The unique index covers active names only,
 * so a club can come back next season without colliding with its own dead row.
 */
@Entity
@Table(name = "payout_sources")
public class PayoutSource {

    public static final int MAX_NAME_LENGTH = 120;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "archived_at")
    @Nullable
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PayoutSource() {}

    public PayoutSource(String name) {
        this.name = name;
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

    /** Trims and truncates; {@code null} for blank, mirroring the CHECK in V93. */
    @Nullable
    public static String sanitizeName(@Nullable String name) {
        if (name == null || name.isBlank()) return null;
        String trimmed = name.trim();
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void archive() {
        if (archivedAt == null) {
            archivedAt = Instant.now();
        }
    }

    public void restore() {
        archivedAt = null;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }
}
