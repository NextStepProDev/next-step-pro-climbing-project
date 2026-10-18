package pl.nextsteppro.climbing.api.mysubscription;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What a client sees of their own subscription.
 *
 * <p>Own records rather than the admin's {@code SubscriptionDto}/{@code PayerLineDto}: a field added
 * to an admin screen must not reach the client by accident — the same rule the GDPR export follows.
 *
 * @param current the running subscription, or the most recent ended one; {@code null} when the
 *                person never had one (the card is then not shown at all)
 * @param months  monthly fees newest first: the last twelve months, plus any older one still owed
 * @param due     what is still owed across those fees, after payments are applied oldest debt
 *                first — the same allocation the coach's screens use, so the two cannot disagree
 */
record MySubscriptionDto(
    @Nullable CurrentSubscriptionDto current,
    List<MonthFeeDto> months,
    BigDecimal due
) {}

record CurrentSubscriptionDto(
    BigDecimal amount,
    LocalDate startedOn,
    @Nullable LocalDate endedOn,
    boolean active
) {}

/**
 * One month of the subscription.
 *
 * @param amount  {@code null} for a month deliberately not charged (the coach waived it)
 * @param skipped true for a waived month — shown, so it does not read as forgotten
 * @param paidOn  the day of the payment that completed it, when it is complete
 */
record MonthFeeDto(
    LocalDate month,
    @Nullable BigDecimal amount,
    BigDecimal covered,
    BigDecimal remaining,
    @Nullable LocalDate paidOn,
    boolean skipped
) {}
