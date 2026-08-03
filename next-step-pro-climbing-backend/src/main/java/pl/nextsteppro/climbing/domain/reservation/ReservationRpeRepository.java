package pl.nextsteppro.climbing.domain.reservation;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRpeRepository extends JpaRepository<ReservationRpe, UUID> {

    Optional<ReservationRpe> findByReservationId(UUID reservationId);

    /** Batch load for the calendar overlay: rpe rows for the given reservations. */
    List<ReservationRpe> findByReservationIdIn(Collection<UUID> reservationIds);

    /**
     * Race-free rating (same reasoning as AthleteWeightRepository.upsertReading): read-then-save
     * on the reservation_id UNIQUE index turned a double-submitted rating into a 500. The rating
     * is idempotent by design, so letting the second writer overwrite is the correct outcome.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO reservation_rpe (reservation_id, rpe, note, updated_at)
        VALUES (:reservationId, :rpe, :note, :updatedAt)
        ON CONFLICT (reservation_id)
        DO UPDATE SET rpe = :rpe, note = :note, updated_at = :updatedAt
        """, nativeQuery = true)
    void upsertRating(@Param("reservationId") UUID reservationId,
                      @Param("rpe") int rpe,
                      @Param("note") @Nullable String note,
                      @Param("updatedAt") Instant updatedAt);
}
