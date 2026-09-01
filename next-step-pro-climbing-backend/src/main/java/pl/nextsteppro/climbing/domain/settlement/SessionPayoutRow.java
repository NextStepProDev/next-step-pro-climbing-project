package pl.nextsteppro.climbing.domain.settlement;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * One session an institution settles, reduced to what the rate needs: whose it is, when it happened,
 * and how long it ran.
 *
 * <p>⚠️ The rate divides by HOURS, not by sessions, and that is not a detail. A school hour of 45
 * minutes and a block of an hour and a half are not the same unit, so "1400 zł ÷ 12 zajęć" averages
 * things that cannot be averaged and cannot be compared with an hourly price list. Hours can:
 * "78 zł/h" sits next to what you charge privately and answers the question the figure is for.
 *
 * <p>Bucketed into months in Java rather than grouped in SQL, so the query stays free of date
 * arithmetic and the month boundary is decided in one place.
 */
public record SessionPayoutRow(
    UUID sourceId,
    LocalDate date,
    @Nullable LocalDate endDate,
    @Nullable LocalTime startTime,
    @Nullable LocalTime endTime
) {

    /**
     * How long the session ran, or {@code null} when that is not knowable.
     *
     * <p>Two cases have no answer and must not be guessed at: an all-day entry has no times at all,
     * and a multi-day event's start and end belong to different days, so the difference between them
     * is not a duration. Both are reported as unknown rather than folded in at zero — a rate quietly
     * computed over a smaller denominator reads high and says nothing about why.
     */
    public @Nullable Integer minutes() {
        if (startTime == null || endTime == null) {
            return null;
        }
        if (endDate != null && !endDate.equals(date)) {
            return null;
        }
        return (int) Duration.between(startTime, endTime).toMinutes();
    }
}
