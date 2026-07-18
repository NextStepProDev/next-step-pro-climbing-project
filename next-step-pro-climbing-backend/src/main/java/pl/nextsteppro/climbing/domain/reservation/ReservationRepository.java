package pl.nextsteppro.climbing.domain.reservation;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pl.nextsteppro.climbing.domain.personaltraining.AthleteActivityCount;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @Query("SELECT r FROM Reservation r JOIN FETCH r.timeSlot WHERE r.user.id = :userId")
    List<Reservation> findByUserId(UUID userId);

    /** Personal training calendar: read-only overlay of the user's booked sessions in a date range.
     * Event fetched for the display title (slot title -> event title). */
    @Query("""
        SELECT r FROM Reservation r
        JOIN FETCH r.timeSlot ts
        LEFT JOIN FETCH ts.event
        WHERE r.user.id = :userId AND r.status = 'CONFIRMED' AND ts.date BETWEEN :from AND :to
        ORDER BY ts.date, ts.startTime
        """)
    List<Reservation> findConfirmedByUserIdInRange(UUID userId, LocalDate from, LocalDate to);

    /** Coach's per-athlete badge: athletes' own confirmed bookings the admin hasn't seen yet.
     * Manual admin bookings are excluded — no alerts for the coach's own actions. LEFT JOIN on
     * the read-marker entity like the PersonalTraining counters; no row = never seen = count all. */
    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.personaltraining.AthleteActivityCount(r.user.id, COUNT(r))
        FROM Reservation r
        LEFT JOIN TrainingCalendarRead m ON m.userId = :adminId AND m.athleteId = r.user.id
        WHERE r.status = 'CONFIRMED' AND r.createdByAdmin = false AND r.user.athlete = true
          AND (m.seenAt IS NULL OR r.createdAt > m.seenAt)
        GROUP BY r.user.id
        """)
    List<AthleteActivityCount> countNewReservationsPerAthlete(UUID adminId);

    List<Reservation> findByTimeSlotId(UUID timeSlotId);

    @Query("SELECT r FROM Reservation r WHERE r.timeSlot.id = :timeSlotId AND r.status = 'CONFIRMED'")
    List<Reservation> findConfirmedByTimeSlotId(UUID timeSlotId);

    @Query("SELECT COALESCE(SUM(r.participants), 0) FROM Reservation r WHERE r.timeSlot.id = :timeSlotId AND r.status = 'CONFIRMED'")
    int countConfirmedByTimeSlotId(UUID timeSlotId);

    @Query("SELECT new pl.nextsteppro.climbing.domain.reservation.SlotParticipantCount(r.timeSlot.id, COALESCE(SUM(r.participants), 0)) FROM Reservation r WHERE r.timeSlot.id IN :slotIds AND r.status = 'CONFIRMED' GROUP BY r.timeSlot.id")
    List<SlotParticipantCount> countConfirmedByTimeSlotIds(Collection<UUID> slotIds);

    @Query("SELECT r FROM Reservation r JOIN FETCH r.user WHERE r.timeSlot.id IN :slotIds AND r.status = 'CONFIRMED' ORDER BY r.timeSlot.date, r.timeSlot.startTime")
    List<Reservation> findConfirmedByTimeSlotIds(Collection<UUID> slotIds);

    @Nullable
    Reservation findByUserIdAndTimeSlotId(UUID userId, UUID timeSlotId);

    @Query("SELECT r FROM Reservation r WHERE r.user.id = :userId AND r.timeSlot.date >= :fromDate AND r.status = 'CONFIRMED' ORDER BY r.timeSlot.date, r.timeSlot.startTime")
    List<Reservation> findUpcomingByUserId(UUID userId, LocalDate fromDate);

    @Query("SELECT r FROM Reservation r WHERE r.user.id = :userId AND (r.timeSlot.date > :today OR (r.timeSlot.date = :today AND r.timeSlot.endTime > :now)) AND r.status IN ('CONFIRMED', 'CANCELLED_BY_ADMIN') ORDER BY r.timeSlot.date, r.timeSlot.startTime")
    List<Reservation> findUpcomingByUserIdIncludingAdminCancelled(UUID userId, LocalDate today, LocalTime now);

    @Query("SELECT r FROM Reservation r WHERE r.user.id = :userId AND (r.timeSlot.date < :today OR (r.timeSlot.date = :today AND r.timeSlot.endTime <= :now)) AND r.status IN ('CONFIRMED', 'CANCELLED', 'CANCELLED_BY_ADMIN') ORDER BY r.timeSlot.date DESC, r.timeSlot.startTime DESC")
    List<Reservation> findPastByUserId(UUID userId, LocalDate today, LocalTime now);

    /** Athlete statistics: attended reservations (confirmed + slot already over) reduced to
     * (date, eventType, location). Same past-predicate as {@link #findPastByUserId}. */
    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.reservation.ReservationStatsRow(ts.date, e.eventType, e.location)
        FROM Reservation r
        JOIN r.timeSlot ts
        LEFT JOIN ts.event e
        WHERE r.user.id = :userId AND r.status = 'CONFIRMED'
          AND (ts.date < :today OR (ts.date = :today AND ts.endTime <= :now))
        """)
    List<ReservationStatsRow> findPastConfirmedStatsRows(UUID userId, LocalDate today, LocalTime now);

    boolean existsByUserIdAndTimeSlotIdAndStatus(UUID userId, UUID timeSlotId, ReservationStatus status);

    @Query("SELECT r.timeSlot.id FROM Reservation r WHERE r.user.id = :userId AND r.timeSlot.id IN :slotIds AND r.status = 'CONFIRMED'")
    List<UUID> findUserConfirmedSlotIds(UUID userId, Collection<UUID> slotIds);

    @Query("SELECT r FROM Reservation r WHERE r.user.id = :userId AND r.timeSlot.id IN :slotIds AND r.status = 'CONFIRMED'")
    List<Reservation> findConfirmedByUserIdAndSlotIds(UUID userId, Collection<UUID> slotIds);

    @Query("SELECT r.participants FROM Reservation r WHERE r.user.id = :userId AND r.timeSlot.event.id = :eventId AND r.status = 'CONFIRMED'")
    List<Integer> findUserParticipantsForEvent(UUID userId, UUID eventId);

    // Invitees who already booked are counted among confirmed — the invitation limit guard
    // must filter them out of the invitation list to avoid double-counting.
    @Query("SELECT r.user.id FROM Reservation r WHERE r.timeSlot.id = :timeSlotId AND r.status = 'CONFIRMED'")
    List<UUID> findConfirmedUserIdsByTimeSlotId(UUID timeSlotId);

    @Query("SELECT DISTINCT r.user.id FROM Reservation r WHERE r.timeSlot.event.id = :eventId AND r.status = 'CONFIRMED'")
    List<UUID> findConfirmedUserIdsByEventId(UUID eventId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Reservation r WHERE r.timeSlot.id IN :slotIds")
    void deleteByTimeSlotIds(Collection<UUID> slotIds);

    @Modifying
    @Query("UPDATE Reservation r SET r.status = 'CANCELLED' WHERE r.user.id = :userId AND r.status = 'CONFIRMED'")
    void cancelConfirmedByUserId(UUID userId);

    // Projections (do not load Reservation entities into the session) — used during account
    // deletion to collect affected slots/events BEFORE reservations are cancelled/cascade-deleted.
    @Query("SELECT r.timeSlot.id FROM Reservation r WHERE r.user.id = :userId AND r.status = 'CONFIRMED'")
    List<UUID> findConfirmedSlotIdsByUserId(UUID userId);

    @Query("SELECT DISTINCT r.timeSlot.event.id FROM Reservation r WHERE r.user.id = :userId AND r.status = 'CONFIRMED' AND r.timeSlot.event IS NOT NULL")
    List<UUID> findConfirmedEventIdsByUserId(UUID userId);

    // Admin notifications: new reservations since last "read" (panel badge).
    // Skips reservations added manually by the admin — the dot lights up only for client actions.
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.status = 'CONFIRMED' AND r.createdAt > :since AND r.createdByAdmin = false")
    int countConfirmedCreatedAfter(Instant since);
}
