package pl.nextsteppro.climbing.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.auth.AuthTokenRepository;

import java.time.Instant;

@Component
public class TokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupScheduler.class);

    private final AuthTokenRepository authTokenRepository;

    public TokenCleanupScheduler(AuthTokenRepository authTokenRepository) {
        this.authTokenRepository = authTokenRepository;
    }

    /**
     * Cleans up expired auth tokens every hour.
     * Runs at minute 0 of every hour (e.g., 1:00, 2:00, etc.)
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        Instant cutoff = Instant.now();
        int deletedCount = authTokenRepository.deleteExpiredTokens(cutoff);

        if (deletedCount > 0) {
            log.info("Cleaned up {} expired auth tokens", deletedCount);
        } else {
            log.debug("No expired auth tokens to clean up");
        }
    }
}
