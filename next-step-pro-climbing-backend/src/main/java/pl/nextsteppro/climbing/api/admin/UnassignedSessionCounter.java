package pl.nextsteppro.climbing.api.admin;

import java.time.LocalDate;

/**
 * How many closed sessions (zero seats) have nobody named to bill — the admin-nav dot.
 *
 * <p>⚠️ An interface, and it lives HERE rather than in the settlement package, because the dependency
 * has to point the other way: {@code AdminService} may not name a money type
 * ({@code SettlementIsolationTest}), while the settlement package may name this one freely. What
 * crosses is a bare count — no id, no date, no payer — so nothing about money can ride along on the
 * notifications DTO.
 *
 * <p>A <b>state</b> count, like pending proposals, not a "since last seen" one: the dot stays lit
 * until the payer is assigned. Opening the tab cannot clear it, because looking at a missing payer
 * does not supply one — and the whole point is that a forgotten one cannot go unnoticed.
 */
public interface UnassignedSessionCounter {

    /** @param today a Warsaw date — the same window the settlements tab's list applies. */
    int countUnassigned(LocalDate today);
}
