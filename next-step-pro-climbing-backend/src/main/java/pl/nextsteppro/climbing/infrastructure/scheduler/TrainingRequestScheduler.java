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
     * Wygasza oczekujące propozycje, których proponowana data już minęła (co godzinę o :10 —
     * przesunięcie względem TokenCleanupScheduler, żeby joby nie startowały jednocześnie).
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
