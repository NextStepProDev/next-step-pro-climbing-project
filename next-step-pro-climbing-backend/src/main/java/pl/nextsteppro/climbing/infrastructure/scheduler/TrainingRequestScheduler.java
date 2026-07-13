package pl.nextsteppro.climbing.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequestRepository;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class TrainingRequestScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrainingRequestScheduler.class);
    // Daty propozycji to czas lokalny PL (kontener prod = UTC) — patrz BookingTimeValidator.
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final TrainingRequestRepository trainingRequestRepository;

    public TrainingRequestScheduler(TrainingRequestRepository trainingRequestRepository) {
        this.trainingRequestRepository = trainingRequestRepository;
    }

    /**
     * Expires pending requests whose proposed date has already passed (hourly at :10 —
     * offset from TokenCleanupScheduler so the jobs do not start at the same time).
     */
    @Scheduled(cron = "0 10 * * * *")
    @Transactional
    public void expireOverdueRequests() {
        int expired = trainingRequestRepository.expirePendingBefore(LocalDate.now(WARSAW));
        if (expired > 0) {
            log.info("Expired {} overdue training requests", expired);
        }
    }
}
