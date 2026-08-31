package pl.nextsteppro.climbing.domain.settlement;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One session attributed to a payer, reduced to the two things the rate needs: whose it is and when
 * it happened. Bucketed into months in Java rather than grouped in SQL, so the query stays free of
 * date functions and the month boundary is decided in one place.
 */
public record SessionPayoutRow(UUID sourceId, LocalDate date) {}
