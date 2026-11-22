package pl.nextsteppro.climbing.api.contact;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Hourly caps on sending, on top of the per-minute {@code RateLimitFilter} bucket.
 *
 * <p>The filter's window is one minute, so on its own it allows a few hundred messages an hour
 * from one address - each of them a mail in the team's inbox. A person writes to a coach a few
 * times a day at most; a script writes until somebody stops it. Guests are keyed on their address,
 * accounts on their id (an office NAT must not throttle a signed-in client because a guest at the
 * next desk wrote too).
 *
 * <p><b>An IPv6 guest is keyed on their /64</b>, not the full address: a subscriber is handed the
 * whole prefix, so a per-address key lets one machine rotate past the cap with every request.
 *
 * <p><b>And there is a ceiling on guest MAIL across all addresses</b>, because rotating addresses
 * (a botnet, a cloud range) defeats any per-address cap. Past it the message is still stored and
 * shown in the panel; only the forwarding stops, so a flood costs the team a long list rather
 * than a buried inbox.
 *
 * <p>In memory, like the filter: a restart resets it, which costs nothing worth a table.
 */
@Component
public class ContactSendThrottle {

    private static final Logger log = LoggerFactory.getLogger(ContactSendThrottle.class);

    static final int GUEST_PER_HOUR = 5;
    static final int ACCOUNT_PER_HOUR = 30;
    private static final Duration GUEST_MAIL_WINDOW = Duration.ofHours(1);
    /** Only these characters are parsed as an address; anything else is a plain key, never a DNS lookup. */
    private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9a-fA-F:.]+");

    private final int guestMailPerHour;
    private long guestMailWindowStart = System.currentTimeMillis();
    private int guestMailCount;

    private final Cache<String, AtomicInteger> counts = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofHours(1))
        .maximumSize(10_000)
        .build();

    public ContactSendThrottle(@Value("${app.contact.guest-mail-per-hour:40}") int guestMailPerHour) {
        this.guestMailPerHour = guestMailPerHour;
    }

    /** Counts one send and answers whether it is still within the cap. */
    public boolean tryGuest(String clientIp) {
        return tryAcquire("ip:" + senderKey(clientIp), GUEST_PER_HOUR);
    }

    public boolean tryAccount(String userId) {
        return tryAcquire("user:" + userId, ACCOUNT_PER_HOUR);
    }

    /**
     * Whether this guest message may still go out by mail this hour. Logs once when the ceiling is hit.
     *
     * <p>Its own fixed window rather than an entry in {@link #counts}: that cache is size-bounded, and
     * the flood this ceiling exists for — thousands of rotating addresses — is what fills it. An
     * evicted ceiling would start again from zero. Caffeine's frequency-based eviction keeps a key
     * this hot in practice (a 20 000-address flood did not evict it), but a ceiling should not rest
     * on an eviction policy at all.
     */
    public synchronized boolean tryForwardGuestMail() {
        long nowMillis = System.currentTimeMillis();
        if (nowMillis - guestMailWindowStart >= GUEST_MAIL_WINDOW.toMillis()) {
            guestMailWindowStart = nowMillis;
            guestMailCount = 0;
        }
        int count = ++guestMailCount;
        if (count == guestMailPerHour + 1) {
            log.warn("Contact form: more than {} guest messages this hour; the rest are kept in the panel without mail",
                guestMailPerHour);
        }
        return count <= guestMailPerHour;
    }

    private boolean tryAcquire(String key, int limit) {
        AtomicInteger counter = counts.get(key, k -> new AtomicInteger(0));
        return counter.incrementAndGet() <= limit;
    }

    /** The /64 prefix of an IPv6 address; any other value unchanged. */
    static String senderKey(String clientIp) {
        if (!clientIp.contains(":") || !IPV6_LITERAL.matcher(clientIp).matches()) {
            return clientIp;
        }
        try {
            // A literal of hex digits, colons and dots: parsed in place, no name resolution.
            byte[] bytes = InetAddress.getByName(clientIp).getAddress();
            if (bytes.length != 16) return clientIp;
            return HexFormat.of().formatHex(bytes, 0, 8) + "/64";
        } catch (UnknownHostException e) {
            return clientIp;
        }
    }
}
