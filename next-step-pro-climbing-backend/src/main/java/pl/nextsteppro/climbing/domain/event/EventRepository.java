package pl.nextsteppro.climbing.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByActiveTrue();

    @Query("SELECT e FROM Event e WHERE e.active = true AND e.startDate <= :date AND e.endDate >= :date")
    List<Event> findActiveEventsOnDate(LocalDate date);

    @Query("SELECT e FROM Event e WHERE e.active = true AND e.startDate <= :endDate AND e.endDate >= :startDate")
    List<Event> findActiveEventsBetween(LocalDate startDate, LocalDate endDate);
}
