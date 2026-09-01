package pl.nextsteppro.climbing.domain.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One payout flattened for reading, carrying its source's name so the tab does not dereference a
 * lazy association per row — the same reason {@code SettlementRow} exists.
 */
public record PayoutRow(
    UUID id,
    UUID sourceId,
    String sourceName,
    LocalDate periodMonth,
    BigDecimal amount,
    LocalDate receivedOn
) {}
