package pl.nextsteppro.climbing.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.nextsteppro.climbing.api.reservation.EventWaitlistService;
import pl.nextsteppro.climbing.api.reservation.WaitlistService;

@Component
public class WaitlistScheduler {

    private static final Logger log = LoggerFactory.getLogger(WaitlistScheduler.class);

    private final WaitlistService waitlistService;
    private final EventWaitlistService eventWaitlistService;

    public WaitlistScheduler(WaitlistService waitlistService, EventWaitlistService eventWaitlistService) {
        this.waitlistService = waitlistService;
        this.eventWaitlistService = eventWaitlistService;
    }

    // Co 5 minut expiruje przeterminowane oferty i re-powiadamia jeśli miejsce nadal wolne
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void processExpiredOffers() {
        log.debug("WaitlistScheduler: checking for expired pending confirmations");
        waitlistService.expireAndNotify();
        eventWaitlistService.expireAndNotify();
    }
}
