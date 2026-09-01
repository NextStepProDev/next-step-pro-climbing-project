package pl.nextsteppro.climbing.domain.settlement;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Where one payer's account stands: positive when we are holding their money.
 *
 * <p>Fetched for a whole section at once rather than per person. Asking per payer reads perfectly
 * well and costs one query each — the exact shape {@code AdminSettlementQueryCountTest} exists to
 * keep out, and which it caught here.
 */
public record PayerBalance(UUID payerId, BigDecimal balance) {}
