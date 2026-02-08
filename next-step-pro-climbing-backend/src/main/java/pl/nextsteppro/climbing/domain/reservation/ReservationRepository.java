package pl.nextsteppro.climbing.domain.reservation;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByUserId(UUID userId);

    List<Reservation> findByTimeSlotId(UUID timeSlotId);

    @Query("SELECT r FROM Reservation r WHERE r.timeSlot.id = :timeSlotId AND r.status = 'CONFIRMED'")
    List<Reservation> findConfirmedByTimeSlotId(UUID timeSlotId);

    @Query("SELECT COALESCE(SUM(r.participants), 0) FROM Reservation r WHERE r.timeSlot.id = :timeSlotId AND r.status = 'CONFIRMED'")
    int countConfirmedByTimeSlotId(UUID timeSlotId);

    @Query("SELECT r.timeSlot.id, COALESCE(SUM(r.participants), 0) FROM Reservation r WHERE r.timeSlot.id IN :slotIds AND r.status = 'CONFIRMED' GROUP BY r.timeSlot.id")
    List<Object[]> countConfirmedByTimeSlotIds(Collection<UUID> slotIds);

    @Query("SELECT r FROM Reservation r JOIN FETCH r.user WHERE r.timeSlot.id IN :slotIds AND r.status = 'CONFIRMED'")
    List<Reservation> findConfirmedByTimeSlotIds(Collection<UUID> slotIds);

    @Nullable
    Reservation findByUserIdAndTimeSlotId(UUID userId, UUID timeSlotId);

    @Query("SELECT r FROM Reservation r WHERE r.user.id = :userId AND r.timeSlot.date >= :fromDate AND r.status = 'CONFIRMED' ORDER BY r.timeSlot.date, r.timeSlot.startTime")
    List<Reservation> findUpcomingByUserId(UUID userId, LocalDate fromDate);

    boolean existsByUserIdAndTimeSlotIdAndStatus(UUID userId, UUID timeSlotId, ReservationStatus status);

    @Query("SELECT r.timeSlot.id FROM Reservation r WHERE r.user.id = :userId AND r.timeSlot.id IN :slotIds AND r.status = 'CONFIRMED'")
    List<UUID> findUserConfirmedSlotIds(UUID userId, Collection<UUID> slotIds);
}
