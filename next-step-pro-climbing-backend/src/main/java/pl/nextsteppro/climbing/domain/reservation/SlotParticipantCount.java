package pl.nextsteppro.climbing.domain.reservation;

import java.util.UUID;

public record SlotParticipantCount(UUID slotId, long count) {
    public int countAsInt() {
        return (int) count;
    }
}
