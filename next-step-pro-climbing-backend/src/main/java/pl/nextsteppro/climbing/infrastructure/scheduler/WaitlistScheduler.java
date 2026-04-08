package pl.nextsteppro.climbing.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.nextsteppro.climbing.api.reservation.WaitlistService;

@Component
public class WaitlistScheduler {

    private static final Logger log = LoggerFactory.getLogger(WaitlistScheduler.class);

    private final WaitlistService waitlistService;

    public WaitlistScheduler(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    // Co 5 minut sprawdza przeterminowane oferty i promuje następną osobę z kolejki
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void processExpiredOffers() {
        log.debug("WaitlistScheduler: checking for expired pending confirmations");
        waitlistService.expireAndPromoteNext();
    }
}
