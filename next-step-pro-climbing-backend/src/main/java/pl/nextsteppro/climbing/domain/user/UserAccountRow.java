package pl.nextsteppro.climbing.domain.user;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * One account, reduced to the fields the admin's user statistics ask about.
 *
 * <p>A projection rather than the entity on purpose: the statistics screen reads every account in
 * the database, and pulling entities would drag the password hash, both OAuth columns and the
 * unsubscribe token through the persistence context to count booleans. A handful of columns per
 * row also keeps the query flat as the base grows — nothing here is lazy, so no row can trigger a
 * second query behind the caller's back.
 *
 * <p>{@code id} is here only so the statistics can tell staff accounts apart from customers
 * without a second query; nothing built from this projection ships an id to the browser.
 *
 * <p>{@code createdAt} stays an {@code Instant}: it is a real moment, and the month it falls into
 * is a Warsaw question the service answers explicitly. Bucketing it in SQL would answer it with
 * whatever zone the database session happens to hold.
 */
public record UserAccountRow(
    UUID id,
    Instant createdAt,
    boolean emailVerified,
    UserRole role,
    boolean athlete,
    @Nullable Instant trainingConsentAt,
    boolean newsletterSubscribed,
    boolean newsletterChoiceMade
) {}
