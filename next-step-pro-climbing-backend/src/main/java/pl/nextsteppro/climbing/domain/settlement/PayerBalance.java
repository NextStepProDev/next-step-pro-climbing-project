package pl.nextsteppro.climbing.domain.settlement;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Where one payer's account stands, in the two figures that answer different questions.
 *
 * <p>Fetched for a whole section at once rather than per person. Asking per payer reads perfectly
 * well and costs one query each — the exact shape {@code AdminSettlementQueryCountTest} exists to
 * keep out, and which it caught here. Both figures come from one read for the same reason.
 *
 * @param balance the net position: positive when we are holding their money. This is the summary of
 *                the account and the figure the screens label.
 * @param credit  ⚠️ what is actually parked on their overpaid rows, which is NOT the same number and
 *                is the one that can be spent. A client who overpaid fifty and then attended a
 *                fifty session he has not paid for nets to <b>zero</b> while fifty of his money sits
 *                on the older row — reading the net figure there says "no credit" about somebody
 *                whose next session is already covered, which is precisely the case this exists for.
 *                Mirrors what {@code AdminSettlementService.settleOutstanding} pulls into its pool.
 */
public record PayerBalance(UUID payerId, BigDecimal balance, BigDecimal credit) {}
