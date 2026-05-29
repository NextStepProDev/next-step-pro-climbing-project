package pl.nextsteppro.climbing.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.reservation.GuestReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;

import java.time.LocalDate;

@Component
public class ReservationCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReservationCleanupScheduler.class);
    private static final int RETENTION_YEARS = 3;
    private final ReservationRepository reservationRepository;
    private final GuestReservationRepository guestReservationRepository;

    public ReservationCleanupScheduler(ReservationRepository reservationRepository,
                                       GuestReservationRepository guestReservationRepository) {
        this.reservationRepository = reservationRepository;
        this.guestReservationRepository = guestReservationRepository;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void cleanupOldReservations() {
        LocalDate cutoffDate = LocalDate.now().minusYears(RETENTION_YEARS);

        int cancelledDeleted = reservationRepository.deleteCancelledBefore(cutoffDate);
        if (cancelledDeleted > 0) {
            log.info("RODO cleanup: deleted {} cancelled reservations older than {}", cancelledDeleted, cutoffDate);
        }

        int guestDeleted = guestReservationRepository.deleteBySlotDateBefore(cutoffDate);
        if (guestDeleted > 0) {
            log.info("RODO cleanup: deleted {} guest reservations older than {}", guestDeleted, cutoffDate);
        }
    }
}
