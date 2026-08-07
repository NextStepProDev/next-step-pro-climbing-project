package pl.nextsteppro.climbing.domain.event;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    /* Serializes waitlist confirmations on one event, the way findByIdForUpdate does for slots.
     * Without it two confirmations can both read capacity before either writes its reservations. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000")})
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdForUpdate(UUID id);

    List<Event> findAllByOrderByStartDateAsc();

    @Query("SELECT e FROM Event e WHERE e.active = true AND e.startDate <= :date AND e.endDate >= :date ORDER BY e.startDate ASC")
    List<Event> findActiveEventsOnDate(LocalDate date);

    @Query("SELECT e FROM Event e WHERE e.active = true AND e.startDate <= :endDate AND e.endDate >= :startDate ORDER BY e.startDate ASC")
    List<Event> findActiveEventsBetween(LocalDate startDate, LocalDate endDate);

    @Query("SELECT e FROM Event e WHERE e.course.id = :courseId AND e.active = true AND e.endDate >= :today ORDER BY e.startDate ASC")
    List<Event> findUpcomingByCourseId(UUID courseId, LocalDate today);

    @Query("SELECT e FROM Event e WHERE e.course.translationGroupId = :translationGroupId AND e.active = true AND e.endDate >= :today ORDER BY e.startDate ASC")
    List<Event> findUpcomingByTranslationGroupId(UUID translationGroupId, LocalDate today);

    @Query("SELECT e FROM Event e WHERE e.course.id = :courseId")
    List<Event> findByCourseId(UUID courseId);
}
