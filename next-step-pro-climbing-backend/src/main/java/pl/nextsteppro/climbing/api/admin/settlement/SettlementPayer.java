package pl.nextsteppro.climbing.api.admin.settlement;

import java.util.Locale;
import java.util.Optional;

/**
 * Who owes the money: a registered user, or a guest the admin wrote in by hand.
 *
 * <p>Guests count towards revenue — the owner's decision — because leaving them out would make
 * every monthly total quietly lower than what actually came in. They have no account, so they
 * appear in the per-person ranking under the written name and without a link to a user card.
 */
public enum SettlementPayer {
    USER,
    GUEST;

    /** Same hand-rolled parse as {@link SettlementTarget#tryFrom}, and for the same reason. */
    public static Optional<SettlementPayer> tryFrom(String segment) {
        try {
            return Optional.of(valueOf(segment.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
