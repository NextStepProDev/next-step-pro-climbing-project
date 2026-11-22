package pl.nextsteppro.climbing.api.contact;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An hourly cap on sending, on top of the per-minute {@code RateLimitFilter} bucket.
 *
 * <p>The filter's window is one minute, so on its own it allows a few hundred messages an hour
 * from one address - each of them a mail in the team's inbox. A person writes to a coach a few
 * times a day at most; a script writes until somebody stops it. Guests are keyed on their IP,
 * accounts on their id (an office NAT must not throttle a signed-in client because a guest at the
 * next desk wrote too).
 *
 * <p>In memory, like the filter: a restart resets it, which costs nothing worth a table.
 */
@Component
public class ContactSendThrottle {

    static final int GUEST_PER_HOUR = 5;
    static final int ACCOUNT_PER_HOUR = 30;

    private final Cache<String, AtomicInteger> counts = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofHours(1))
        .maximumSize(10_000)
        .build();

    /** Counts one send and answers whether it is still within the cap. */
    public boolean tryGuest(String clientIp) {
        return tryAcquire("ip:" + clientIp, GUEST_PER_HOUR);
    }

    public boolean tryAccount(String userId) {
        return tryAcquire("user:" + userId, ACCOUNT_PER_HOUR);
    }

    private boolean tryAcquire(String key, int limit) {
        AtomicInteger counter = counts.get(key, k -> new AtomicInteger(0));
        return counter.incrementAndGet() <= limit;
    }
}
