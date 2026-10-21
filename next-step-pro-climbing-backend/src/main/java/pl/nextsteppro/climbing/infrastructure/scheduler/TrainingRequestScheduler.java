package pl.nextsteppro.climbing.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.reschedule.RescheduleRequestRepository;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequestRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class TrainingRequestScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrainingRequestScheduler.class);
    // Daty propozycji to czas lokalny PL (kontener prod = UTC) — patrz BookingTimeValidator.
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final TrainingRequestRepository trainingRequestRepository;

    private final RescheduleRequestRepository rescheduleRequestRepository;

    public TrainingRequestScheduler(TrainingRequestRepository trainingRequestRepository,
                                    RescheduleRequestRepository rescheduleRequestRepository) {
        this.trainingRequestRepository = trainingRequestRepository;
        this.rescheduleRequestRepository = rescheduleRequestRepository;
    }

    /**
     * Expires pending requests whose proposed date has already passed (hourly at :10 —
     * offset from TokenCleanupScheduler so the jobs do not start at the same time).
     */
    @Scheduled(cron = "0 10 * * * *", zone = "UTC")
    @Transactional
    public void expireOverdueRequests() {
        int expired = trainingRequestRepository.expirePendingBefore(LocalDate.now(WARSAW));
        if (expired > 0) {
            log.info("Expired {} overdue training requests", expired);
        }
        // A request to move a term that has already taken place answers nothing any more.
        LocalDateTime now = LocalDateTime.now(WARSAW);
        int expiredReschedules = rescheduleRequestRepository.expireOverdue(now.toLocalDate(), now.toLocalTime(), Instant.now());
        if (expiredReschedules > 0) {
            log.info("Expired {} overdue reschedule requests", expiredReschedules);
        }
    }
}
