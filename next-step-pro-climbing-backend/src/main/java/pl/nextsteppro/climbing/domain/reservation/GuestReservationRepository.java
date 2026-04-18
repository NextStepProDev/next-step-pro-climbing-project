package pl.nextsteppro.climbing.domain.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GuestReservationRepository extends JpaRepository<GuestReservation, UUID> {

    List<GuestReservation> findByTimeSlotId(UUID slotId);

    List<GuestReservation> findByEventId(UUID eventId);

    void deleteByTimeSlotId(UUID slotId);

    void deleteByEventId(UUID eventId);

    @Query("SELECT COALESCE(SUM(g.participants), 0) FROM GuestReservation g WHERE g.timeSlot.id = :slotId")
    int sumParticipantsByTimeSlotId(@Param("slotId") UUID slotId);

    @Query("SELECT COALESCE(SUM(g.participants), 0) FROM GuestReservation g WHERE g.event.id = :eventId")
    int sumParticipantsByEventId(@Param("eventId") UUID eventId);
}
