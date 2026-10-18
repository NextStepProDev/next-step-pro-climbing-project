/**
 * The client's own view of their coaching subscription.
 *
 * <p>⚠️ The one package outside {@code domain/settlement} and {@code api/admin/settlement} allowed to
 * read money ({@code SettlementIsolationTest}). It may, because everything here is scoped to the
 * caller's own id from the token and nothing is cached or shared: no anonymous shape, no calendar
 * payload. Anything added here must keep both properties.
 */
@NullMarked
package pl.nextsteppro.climbing.api.mysubscription;

import org.jspecify.annotations.NullMarked;
