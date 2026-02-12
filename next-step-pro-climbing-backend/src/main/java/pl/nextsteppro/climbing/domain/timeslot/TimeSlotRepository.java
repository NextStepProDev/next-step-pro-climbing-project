package pl.nextsteppro.climbing.domain.timeslot;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ts FROM TimeSlot ts WHERE ts.id = :id")
    Optional<TimeSlot> findByIdForUpdate(UUID id);

    List<TimeSlot> findByDate(LocalDate date);

    List<TimeSlot> findByDateOrderByStartTimeAsc(LocalDate date);

    List<TimeSlot> findByDateBetween(LocalDate startDate, LocalDate endDate);

    List<TimeSlot> findByEventId(UUID eventId);

    @Query("SELECT ts FROM TimeSlot ts WHERE ts.date = :date AND ts.blocked = false")
    List<TimeSlot> findAvailableByDate(LocalDate date);

    @Query("SELECT ts FROM TimeSlot ts WHERE ts.date BETWEEN :startDate AND :endDate ORDER BY ts.date, ts.startTime")
    List<TimeSlot> findByDateRangeOrdered(LocalDate startDate, LocalDate endDate);

    @Query("SELECT ts FROM TimeSlot ts WHERE ts.event.id IN :eventIds")
    List<TimeSlot> findByEventIdIn(Collection<UUID> eventIds);
}
