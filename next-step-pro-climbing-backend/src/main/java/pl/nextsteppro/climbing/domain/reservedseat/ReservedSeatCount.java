package pl.nextsteppro.climbing.domain.reservedseat;

import java.util.UUID;

/** Liczba wiszących zaproszeń per slot lub per wydarzenie (batch w widokach kalendarza). */
public record ReservedSeatCount(UUID targetId, long count) {
    public int countAsInt() {
        return (int) count;
    }
}
