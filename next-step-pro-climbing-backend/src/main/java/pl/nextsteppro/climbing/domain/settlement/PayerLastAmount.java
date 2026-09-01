package pl.nextsteppro.climbing.domain.settlement;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What this person was last charged, used to prefill the amount field.
 *
 * <p>This is what stands in for a "default rate" column on {@code time_slots} / {@code events}, and
 * it is the better answer twice over. A rate on the entry would be a money column on a shape that
 * is served to anonymous visitors and cached — the exact kind of obliging field this whole feature
 * is arranged to avoid. And it would be wrong more often: the real price follows the <em>person</em>
 * (a pass, a discount, gear), not the slot, so the last thing you charged them beats a number
 * attached to the hour.
 */
public record PayerLastAmount(UUID userId, BigDecimal amount) {}
