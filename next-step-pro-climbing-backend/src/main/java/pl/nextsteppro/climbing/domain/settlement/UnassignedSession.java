package pl.nextsteppro.climbing.domain.settlement;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A session that was worked and has <b>nobody to bill at all</b> — no participant, no amount, no
 * bulk payer.
 *
 * <p>The third kind of invisible work, and the one the "to be priced" queue cannot reach by
 * construction: that queue is driven by reservations and guests, so a session with zero people on
 * it produces no rows in any of its four reads. Nothing else notices it either — it is not revenue,
 * not a debt, and not in the hourly-rate denominator, so a forgotten assignment makes the rate read
 * <em>high</em> (one transfer over fewer sessions) with nothing on screen to say the denominator is
 * short.
 *
 * <p><b>Zero seats is the whole signal.</b> A past slot nobody could book, held anyway, is work; a
 * past slot with seats that nobody took is an unsold offer and is not this list's business. Without
 * that condition the queue would report every empty hour ever offered, which is noise rather than
 * a work queue — and a work queue nobody can face is the same as not having one.
 *
 * @param targetId  always a standalone slot today. An event needs a title and lives in its own
 *                  panel, and the owner records this work as a zero-seat slot.
 * @param title     may be null: an untitled zero-seat slot is exactly the case this list is for.
 */
public record UnassignedSession(UUID targetId, LocalDate targetDate, @Nullable String title) {}
