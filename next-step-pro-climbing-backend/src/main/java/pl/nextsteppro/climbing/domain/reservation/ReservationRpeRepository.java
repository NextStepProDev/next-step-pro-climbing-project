package pl.nextsteppro.climbing.domain.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRpeRepository extends JpaRepository<ReservationRpe, UUID> {

    Optional<ReservationRpe> findByReservationId(UUID reservationId);

    /** Batch load for the calendar overlay: rpe rows for the given reservations. */
    List<ReservationRpe> findByReservationIdIn(Collection<UUID> reservationIds);
}
