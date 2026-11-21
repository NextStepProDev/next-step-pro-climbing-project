package pl.nextsteppro.climbing.api.admin;

import java.util.UUID;

/**
 * Moves a guest's charge and payments onto a registered account.
 *
 * <p>⚠️ An interface living HERE, like {@link UnassignedSessionCounter}, because the dependency must
 * point the other way: {@code GuestAssignmentService} may not name a money type
 * ({@code SettlementIsolationTest}), while the settlement package may implement this freely. Only
 * ids cross — no amount comes back.
 *
 * <p>Must run BEFORE the guest row is deleted: a guest's charge and payments go with that row
 * (ON DELETE CASCADE), so the opposite order would erase revenue.
 */
public interface GuestMoneyHandover {

    /**
     * The refusal of {@link #handOver}, as a read only — so the caller can ask before writing
     * anything, instead of leaning on the rollback to undo the bookings it already made.
     */
    void requireHandoverPossible(UUID guestId, UUID userId);

    /**
     * Repoints every settlement and payment of the guest at the account, keeping target, amount and
     * dates. Refuses (409) when the account already holds its own charge for the same target — the
     * two would collide on the per-person unique index, and merging them is not this call's decision.
     */
    void handOver(UUID guestId, UUID userId);
}
