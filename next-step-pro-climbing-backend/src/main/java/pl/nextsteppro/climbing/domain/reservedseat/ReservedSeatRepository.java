package pl.nextsteppro.climbing.domain.reservedseat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Zaproszenia (trzymane miejsca). "Wiszące" zaproszenie = takie, którego adresat nie ma jeszcze
 * potwierdzonej rezerwacji na danym slocie/wydarzeniu — tylko takie odejmują dostępność, bo gdy
 * zaproszony już zarezerwuje, jego miejsce jest liczone w potwierdzonych (nie podwójnie).
 */
public interface ReservedSeatRepository extends JpaRepository<ReservedSeat, UUID> {

    // ---- Sloty ----

    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.reservedseat.ReservedSeatCount(rs.timeSlot.id, COUNT(rs))
        FROM ReservedSeat rs
        WHERE rs.timeSlot.id IN :slotIds
          AND NOT EXISTS (SELECT 1 FROM Reservation r
                          WHERE r.user.id = rs.user.id AND r.timeSlot.id = rs.timeSlot.id AND r.status = 'CONFIRMED')
        GROUP BY rs.timeSlot.id
        """)
    List<ReservedSeatCount> countPendingBySlotIds(Collection<UUID> slotIds);

    @Query("""
        SELECT rs.timeSlot.id FROM ReservedSeat rs
        WHERE rs.user.id = :userId AND rs.timeSlot.id IN :slotIds
          AND NOT EXISTS (SELECT 1 FROM Reservation r
                          WHERE r.user.id = :userId AND r.timeSlot.id = rs.timeSlot.id AND r.status = 'CONFIRMED')
        """)
    List<UUID> findUserPendingSlotInviteIds(UUID userId, Collection<UUID> slotIds);

    @Query("""
        SELECT COUNT(rs) FROM ReservedSeat rs
        WHERE rs.timeSlot.id = :slotId
          AND NOT EXISTS (SELECT 1 FROM Reservation r
                          WHERE r.user.id = rs.user.id AND r.timeSlot.id = :slotId AND r.status = 'CONFIRMED')
        """)
    int countPendingBySlotId(UUID slotId);

    @Query("""
        SELECT COUNT(rs) FROM ReservedSeat rs
        WHERE rs.timeSlot.id = :slotId AND rs.user.id <> :userId
          AND NOT EXISTS (SELECT 1 FROM Reservation r
                          WHERE r.user.id = rs.user.id AND r.timeSlot.id = :slotId AND r.status = 'CONFIRMED')
        """)
    int countPendingBySlotIdExcludingUser(UUID slotId, UUID userId);

    @Query("""
        SELECT COUNT(rs) > 0 FROM ReservedSeat rs
        WHERE rs.timeSlot.id = :slotId AND rs.user.id = :userId
          AND NOT EXISTS (SELECT 1 FROM Reservation r
                          WHERE r.user.id = :userId AND r.timeSlot.id = :slotId AND r.status = 'CONFIRMED')
        """)
    boolean existsPendingBySlotIdAndUserId(UUID slotId, UUID userId);

    @Query("SELECT rs FROM ReservedSeat rs JOIN FETCH rs.user WHERE rs.timeSlot.id = :slotId ORDER BY rs.createdAt")
    List<ReservedSeat> findBySlotIdWithUser(UUID slotId);

    // Zaproszenia widziane od strony adresata (sekcja "Zaproszenia" + badge w navbarze).
    // Pokazujemy aż do godziny startu — zaproszeni omijają okno 12 h przed terminem.
    @Query("""
        SELECT rs FROM ReservedSeat rs
        JOIN FETCH rs.timeSlot ts
        LEFT JOIN FETCH ts.event
        WHERE rs.user.id = :userId
          AND ts.blocked = false
          AND (ts.date > :today OR (ts.date = :today AND ts.startTime > :now))
          AND NOT EXISTS (SELECT 1 FROM Reservation r
                          WHERE r.user.id = :userId AND r.timeSlot.id = ts.id AND r.status = 'CONFIRMED')
        ORDER BY ts.date, ts.startTime
        """)
    List<ReservedSeat> findUpcomingPendingSlotInvitesByUserId(UUID userId, LocalDate today, LocalTime now);

    boolean existsByTimeSlotIdAndUserId(UUID slotId, UUID userId);

    void deleteByTimeSlotIdAndUserId(UUID slotId, UUID userId);

    // ---- Wydarzenia ----

    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.reservedseat.ReservedSeatCount(rs.event.id, COUNT(rs))
        FROM ReservedSeat rs
        WHERE rs.event.id IN :eventIds
          AND NOT EXISTS (SELECT 1 FROM Reservation r
                          WHERE r.user.id = rs.user.id AND r.timeSlot.event.id = rs.event.id AND r.status = 'CONFIRMED')
        GROUP BY rs.event.id
        """)
    List<ReservedSeatCount> countPendingByEventIds(Collection<UUID> eventIds);

    @Query("""
        SELECT rs.event.id FROM ReservedSeat rs
        WHERE rs.user.id = :userId AND rs.event.id IN :eventIds
          AND NOT EXISTS (SELECT 1 FROM Reservation r
                          WHERE r.user.id = :userId AND r.timeSlot.event.id = rs.event.id AND r.status = 'CONFIRMED')
        """)
    List<UUID> findUserPendingEventInviteIds(UUID userId, Collection<UUID> eventIds);

    @Query("""
        SELECT COUNT(rs) FROM ReservedSeat rs
        WHERE rs.event.id = :eventId
          AND NOT EXISTS (SELECT 1 FROM Reservation r
                          WHERE r.user.id = rs.user.id AND r.timeSlot.event.id = :eventId AND r.status = 'CONFIRMED')
        """)
    int countPendingByEventId(UUID eventId);

    @Query("""
        SELECT COUNT(rs) FROM ReservedSeat rs
        WHERE rs.event.id = :eventId AND rs.user.id <> :userId
          AND NOT EXISTS (SELECT 1 FROM Reservation r
                          WHERE r.user.id = rs.user.id AND r.timeSlot.event.id = :eventId AND r.status = 'CONFIRMED')
        """)
    int countPendingByEventIdExcludingUser(UUID eventId, UUID userId);

    @Query("""
        SELECT COUNT(rs) > 0 FROM ReservedSeat rs
        WHERE rs.event.id = :eventId AND rs.user.id = :userId
          AND NOT EXISTS (SELECT 1 FROM Reservation r
                          WHERE r.user.id = :userId AND r.timeSlot.event.id = :eventId AND r.status = 'CONFIRMED')
        """)
    boolean existsPendingByEventIdAndUserId(UUID eventId, UUID userId);

    @Query("SELECT rs FROM ReservedSeat rs JOIN FETCH rs.user WHERE rs.event.id = :eventId ORDER BY rs.createdAt")
    List<ReservedSeat> findByEventIdWithUser(UUID eventId);

    @Query("""
        SELECT rs FROM ReservedSeat rs
        JOIN FETCH rs.event e
        WHERE rs.user.id = :userId
          AND e.active = true
          AND e.endDate >= :today
          AND NOT EXISTS (SELECT 1 FROM Reservation r
                          WHERE r.user.id = :userId AND r.timeSlot.event.id = e.id AND r.status = 'CONFIRMED')
        ORDER BY e.startDate
        """)
    List<ReservedSeat> findUpcomingPendingEventInvitesByUserId(UUID userId, LocalDate today);

    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);

    void deleteByEventIdAndUserId(UUID eventId, UUID userId);
}
