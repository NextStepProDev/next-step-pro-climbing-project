package pl.nextsteppro.climbing.domain.reservedseat;

import java.util.UUID;

/** Number of pending invitations per slot or per event (batched in calendar views). */
public record ReservedSeatCount(UUID targetId, long count) {
    public int countAsInt() {
        return (int) count;
    }
}
