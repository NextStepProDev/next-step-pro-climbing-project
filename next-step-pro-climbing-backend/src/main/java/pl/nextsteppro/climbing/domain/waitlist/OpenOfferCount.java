package pl.nextsteppro.climbing.domain.waitlist;

import java.util.UUID;

/** Open waitlist offers per slot or per event (batched in calendar views). */
public record OpenOfferCount(UUID targetId, long count) {
    public int countAsInt() {
        return (int) count;
    }
}
