package pl.nextsteppro.climbing.api.admin.settlement;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.settlement.PayoutSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What one calendar entry costs each of its participants.
 *
 * <p><b>A separate response type, deliberately, rather than fields on {@code TimeSlotDto} or
 * {@code EventSummaryDto}.</b> Those shapes are served to anonymous visitors and cached under
 * {@code calendarMonth/Week/Day} whenever {@code userId == null}. An amount added to either would
 * compile, would look like a convenience, and would publish what a named person paid to everyone
 * who opens the calendar. Living in a type nothing else returns means no shared shape and no cache
 * has anything to leak — the same argument as {@code AdminNoteDto}, with a worse failure mode.
 *
 * @param targetDate the session's day — the slot's date, or the event's first day. Sent so the date
 *                   picker can prefill it when the admin ticks "settled": the money then lands in
 *                   the month the session happened, not the month somebody got round to clicking.
 */
record SettlementSectionDto(
    LocalDate targetDate,
    List<SettlementLineDto> lines,
    @Nullable SettlementCoverageDto coveredBy
) {}

/**
 * Who settles this session in bulk, when somebody does.
 *
 * <p>One shape for both kinds rather than two nullable pairs of fields: an institution paying a lump
 * for a month and a client whose subscription covers the session are the same phenomenon from two
 * sides, and the screen says the same sentence about either.
 *
 * @param kind {@code source} for an institution, {@code subscription} for a client's standing fee.
 */
record SettlementCoverageDto(String kind, UUID id, String name) {}

/**
 * One payer on one entry.
 *
 * @param participants how many people this booking covers. Shown next to the name because the
 *                     amount prices the whole row, not a head — "Piotr Nowak (2 osoby)" is
 *                     otherwise indistinguishable from a single seat at double the rate.
 * @param amount       what it costs. {@code null} when nothing has been priced yet — a different
 *                     state from {@code 0}, which means free of charge: only the second is a
 *                     decision, and only the second belongs in the totals.
 * @param paidAmount   what actually arrived against it. Cash rarely equals the charge, so these are
 *                     two figures and the screen shows both side by side.
 * @param balance      where this payer's whole account stands: positive means we are holding their
 *                     money. Carried on the line so the figure is in front of you at the moment you
 *                     type the next amount — which is the only moment it is any use.
 * @param orphaned     true when a settlement exists but the booking behind it does not any more.
 *                     The row stays visible rather than dropping out of the list: money that
 *                     changed hands does not stop having changed hands because somebody cancelled.
 * @param suggestedAmount what this person was last charged, offered as a prefill and never applied
 *                     on its own. This is what stands in for a default-rate column on the slot: the
 *                     real price follows the person, not the hour — and a money column on a cached,
 *                     publicly served shape is exactly what this feature is arranged to avoid.
 *                     Guests get none: a guest row is a one-off with no history to draw on.
 */
record SettlementLineDto(
    String payerType,
    UUID payerId,
    String name,
    int participants,
    boolean orphaned,
    @Nullable BigDecimal amount,
    BigDecimal paidAmount,
    BigDecimal balance,
    @Nullable LocalDate settledOn,
    @Nullable BigDecimal suggestedAmount
) {}

/**
 * Upsert payload.
 *
 * <p>{@code settledOn} null means "not settled yet" and is the whole status field — there is no
 * separate boolean, so there is no way to be marked paid without a date to count the money into.
 * Deliberately <b>not</b> bounded to the past: prepaying next month's course is ordinary, and the
 * client's own default is the session date, which for an upcoming session is in the future.
 */
record SaveSettlementRequest(
    @NotNull @DecimalMin("0") @DecimalMax("100000") BigDecimal amount,
    /**
     * What arrived against this row. Null is read as nothing yet — the same meaning a missing
     * {@code settledOn} carries, kept together so a row cannot claim a payment date with no money
     * behind it.
     */
    @Nullable @DecimalMin("0") @DecimalMax("100000") BigDecimal paidAmount,
    @Nullable LocalDate settledOn
) {}

/**
 * Settles everything one payer still owes, in one go and on one date.
 *
 * <p>⚠️ The date is NOT defaulted from the sessions here, unlike the per-participant field. One
 * transfer covered a month of them, so the day it arrived is the only date that is true of all of
 * them; taking each session's own day would scatter a single payment across the months it paid for.
 */
record SettleOutstandingRequest(
    @NotNull String payerType,
    @NotNull UUID payerId,
    @NotNull LocalDate settledOn,
    /** What actually changed hands. May be less than owed, or more — cash rarely has change. */
    @NotNull @DecimalMin("0") @DecimalMax("100000") BigDecimal received
) {}

/**
 * @param settled how many rows the money reached
 * @param balance where the account stands afterwards: positive means we are holding their money.
 */
record SettleOutstandingResultDto(int settled, BigDecimal balance) {}

/**
 * Everything the Settlements tab draws, from one read.
 *
 * <p>Assembled on the server even for figures the client could add up itself, for the reason spelled
 * out on {@code AdminUserStatsDtos}: the panel already holds rows, so the totals could be summed in
 * the browser — and then the funnel and the tiles have two denominators taken at two moments. A
 * difference of one looks like a bug long before anybody guesses it is a race.
 *
 * @param years available years, newest first — only those that actually hold data, so an empty
 *              January of a new year does not read as lost history.
 * @param year  the selected year, or {@code null} for "everything".
 */
record SettlementOverviewDto(
    List<Integer> years,
    @Nullable Integer year,
    UnpricedDto unpriced,
    OutstandingDto outstanding,
    RevenueDto revenue,
    List<PersonRevenueDto> people,
    PayoutsDto payouts
) {}

/**
 * Sessions that are over and were never priced at all.
 *
 * <p>The gap this closes is that such a session <b>cannot ask for itself</b>. An unpaid amount is at
 * least a row, and shows up as a debt; a session nobody priced is neither revenue nor debt, so it is
 * invisible on every screen and the only way to find it is to read the calendar — which is the
 * chore this tab exists to remove.
 *
 * <p>⚠️ Like outstanding debt, this ignores the year picker. Unlike it, it is bounded to a rolling
 * window: without one, the day the feature ships every session in the app's history reports as
 * unpriced, and a work queue that opens with several thousand rows is not a work queue. The window
 * is a fixed policy, not the selected range — the same rule as {@code LOWEST_WINDOW_DAYS} on the
 * weight tile — so the heading can name it and stay true.
 *
 * @param windowDays how far back the list looks, sent so the screen states the rule it applies
 *                   rather than leaving "why is my old session missing" to be guessed.
 */
record UnpricedDto(
    int count,
    int windowDays,
    List<UnpricedSessionDto> sessions
) {}

/**
 * One session with people still to price, grouped rather than listed per person: you collect money
 * from a person, but you <em>price</em> a session — and the modal it links to prices everyone on it
 * in one go.
 *
 * @param payerCount how many participants still have no amount. A bare row would not say whether
 *                   opening it is one field or ten.
 */
record UnpricedSessionDto(
    String targetType,
    UUID targetId,
    LocalDate date,
    @Nullable String title,
    int payerCount
) {}

/**
 * Money still owed.
 *
 * <p>⚠️ Whole history, <b>ignoring the selected year</b>. A debt from two years ago is still a debt,
 * and putting it behind a year picker is how it stops being collected. The tab says so above the
 * list, because a section that quietly disobeys the filter above it is otherwise indistinguishable
 * from a broken filter.
 *
 * @param oldest the earliest session with an unpaid amount, or {@code null} when nothing is owed.
 */
record OutstandingDto(
    BigDecimal total,
    int count,
    @Nullable LocalDate oldest,
    List<OutstandingItemDto> items
) {}

/**
 * One unpaid amount, addressed so the tab can settle it in place.
 *
 * @param targetType {@code slot}, {@code event}, or {@code month} for a standing coaching fee.
 *                   The first two are the same path segment the write endpoint takes, so the row
 *                   carries its own address rather than the client reconstructing one.
 * @param targetId   ⚠️ null for a monthly fee. It has no calendar entry behind it, so the client
 *                   must not offer a link into one — the null is that signal, not an omission.
 * @param date       the session's day. Outstanding debt is counted on this axis because an unpaid
 *                   row has no payment date to be counted on.
 */
record OutstandingItemDto(
    String targetType,
    @Nullable UUID targetId,
    LocalDate date,
    @Nullable String title,
    String payerType,
    UUID payerId,
    String name,
    BigDecimal amount
) {}

/**
 * Money that arrived, counted on {@code settledOn} — the axis the tab labels.
 *
 * @param months         twelve buckets: the calendar months of the selected year, or the last twelve
 *                       ending this month when no year is selected. Always twelve, so the chart does
 *                       not change height with the data underneath it.
 * @param monthlyAverage the total spread over the months actually spanned by the data — first month
 *                       with money to last, inclusive — not over twelve. A year that started trading
 *                       in September otherwise reads as a third of what it earned. {@code null} when
 *                       nothing has been paid, so the tile disappears rather than showing a zero.
 * @param fromSlots      revenue from one-to-one slots, {@code fromEvents} from courses, workshops and
 *                       trips, {@code fromSubscriptions} from standing monthly coaching fees, and
 *                       {@code fromPayouts} from work somebody else settles in bulk. Four genuinely
 *                       different ways of earning, not four labels on one — you set the price of the
 *                       first three and not of the last.
 *                       <p>⚠️ They must add up to {@code total}, because the client draws them as
 *                       one bar against it: a source missing from the split is an unexplained gap,
 *                       and one counted twice is a bar wider than its own track. A retainer in
 *                       particular is NOT slot income — the sessions it covers are deliberately left
 *                       unpriced, so filing it under slots claims session earnings for a client
 *                       whose sessions all earned nothing.
 * @param previousMonths the SAME twelve months a year earlier, or empty in the "everything" view,
 *                       which has no previous to compare against.
 *                       <p>⚠️ This is the only honest comparison this business has. Climbing is
 *                       seasonal, so month against previous month says a quiet October is a bad
 *                       month when it is simply October; only October against last October answers
 *                       whether the year is going up. A month-over-month arrow would be a confident
 *                       wrong reading, which is worse than none.
 * @param previousTotal  what those twelve months earned, so the headline can be compared without
 *                       the client re-summing a list and disagreeing by a rounding step.
 */
record RevenueDto(
    BigDecimal total,
    @Nullable BigDecimal monthlyAverage,
    List<MonthlyRevenueDto> months,
    BigDecimal fromSlots,
    BigDecimal fromEvents,
    BigDecimal fromSubscriptions,
    BigDecimal fromPayouts,
    List<MonthlyRevenueDto> previousMonths,
    BigDecimal previousTotal
) {}

/**
 * One bucket of the revenue chart.
 *
 * @param month the first day of the month, matching {@code MonthlyRegistrationsDto} — a date rather
 *              than a {@code yyyy-MM} string so the client parses it with the same
 *              {@code parseCalendarDate} as every other calendar label and formats the month name
 *              in its own language.
 */
record MonthlyRevenueDto(LocalDate month, BigDecimal amount) {}

/**
 * One payer's year.
 *
 * @param userId {@code null} for a guest — no account, so no user card to link to. That is the whole
 *               reason the type is nullable, and the client uses it as exactly that signal.
 * @param paid   settled within the selected range; {@code outstanding} is unpaid work whose session
 *               falls in it. Two axes, as everywhere else here — and the two never double-count,
 *               because a row is either settled or it is not.
 */
record PersonRevenueDto(
    String payerType,
    @Nullable UUID userId,
    String name,
    int settlementCount,
    BigDecimal paid,
    BigDecimal outstanding,
    @Nullable LocalDate lastPayment
) {}

/**
 * A payer who settles in bulk — a school, a club. Archived ones still come back in the list, because
 * the tab has to be able to name the source of money earned last year.
 */
record PayoutSourceDto(UUID id, String name, boolean archived) {}

record SavePayoutSourceRequest(
    @NotBlank @Size(max = PayoutSource.MAX_NAME_LENGTH) String name
) {}

/**
 * Attaches a session to a bulk payer, or detaches it when {@code sourceId} is null.
 *
 * <p>One endpoint with a nullable body rather than a PUT and a DELETE, and deliberately NOT
 * {@code /{targetType}/{targetId}/source/{sourceId}}: that shape has the same four segments as the
 * per-payer write, so it would resolve only by Spring preferring a literal segment over a variable.
 * It would work, and it would read as a bug to whoever met the two routes next — the same reason
 * {@code /admin/user-stats} does not live at {@code /admin/users/stats}.
 */
record AssignPayoutSourceRequest(
    @Nullable UUID sourceId,
    /** A client whose standing subscription covers the session. Exclusive with {@code sourceId}. */
    @Nullable UUID subscriberId
) {}

/**
 * One transfer. {@code periodMonth} is any day of the month the work was done in — the server snaps
 * it to the first — while {@code receivedOn} is when the money landed. Revenue counts on the second,
 * the derived rate on the first.
 */
record SavePayoutRequest(
    @NotNull UUID sourceId,
    @NotNull LocalDate periodMonth,
    @NotNull @DecimalMin("0") @DecimalMax("1000000") BigDecimal amount,
    @NotNull LocalDate receivedOn
) {}

/**
 * The bulk-payment half of the tab.
 *
 * @param total money that ARRIVED inside the selected range, on the same axis as settled amounts, so
 *              the two halves of revenue add up to one monthly figure.
 */
record PayoutsDto(
    List<PayoutSourceDto> sources,
    BigDecimal total,
    List<PayoutPeriodDto> periods
) {}

/**
 * What one month of work for one payer held, and what it earned.
 *
 * <p>⚠️ Rows come from the union of both sides, not from the payouts alone. A month with sessions and
 * no transfer yet is the single most useful row on this table — it is the invoice nobody has paid —
 * and listing only what arrived would hide exactly that.
 *
 * @param ratePerHour    the point of the whole feature: what the place actually pays per HOUR.
 *                       ⚠️ Per hour and not per session, because a 45-minute school hour and a
 *                       ninety-minute block are not the same unit — averaging them produces a figure
 *                       that cannot be compared with anything, least of all your own hourly price.
 *                       {@code null} when either half is missing: a rate needs a numerator and a
 *                       denominator, and a zero would be a claim rather than a gap.
 * @param minutes        total measured time, so the screen can show the denominator it divided by.
 * @param sessionsWithoutHours how many covered entries had no knowable duration — an all-day entry,
 *                       or a multi-day event whose start and end are on different days. Shown rather
 *                       than folded in at zero: a rate quietly computed over a smaller denominator
 *                       reads high and says nothing about why.
 * @param transfers      the individual arrivals this row adds up. Carried so a mistyped figure can
 *                       be removed: without them the feature is write-only, and a 14000 entered for
 *                       1400 would be permanent.
 */
record PayoutPeriodDto(
    UUID sourceId,
    String sourceName,
    LocalDate month,
    int sessions,
    int minutes,
    int sessionsWithoutHours,
    BigDecimal amount,
    @Nullable BigDecimal ratePerHour,
    List<PayoutEntryDto> transfers
) {}

/** One arrival, addressable so it can be deleted. */
record PayoutEntryDto(UUID id, BigDecimal amount, LocalDate receivedOn) {}

/**
 * One line of income for the year, flattened for an accountant.
 *
 * <p>Its own endpoint rather than a field on the overview: these are line items, and the tab needs
 * aggregates. Loading every row of a year into a response that renders four cards would make the
 * common read pay for the rare one.
 *
 * <p>Unpaid lines are included with an empty {@code settledOn} on purpose. What was received is the
 * question most of the time, but "what is still owed for this year" is the other half of the same
 * conversation, and dropping those rows would make the file impossible to reconcile against the
 * screen it came from.
 *
 * @param kind  which money model the line came from, because they are chased differently: a client's
 *              own fee versus a transfer from a school.
 * @param payer the person or the institution. Guests appear under whatever name was written down —
 *              it is the only one there is.
 */
record SettlementExportRowDto(
    String kind,
    LocalDate date,
    @Nullable String title,
    String payer,
    /** What it cost. */
    BigDecimal amount,
    /** What actually arrived. Less than the charge leaves a remainder; more is an overpayment. */
    BigDecimal paid,
    @Nullable LocalDate settledOn
) {}

/**
 * One client's money, for their card in the Users panel.
 *
 * <p>Served from here rather than folded into {@code UserDetailDto} for a reason the isolation gate
 * enforces: {@code api/admin/userhistory} may not reach the settlement types at all, so the card's
 * money block is a second request from the browser rather than a second import in Java. That keeps
 * one rule — money lives in one package — instead of an exception to it.
 *
 * @param recent the last few lines, newest first. Enough to answer "what is this made of" without
 *               turning the card into a second Settlements tab.
 */
record PayerSummaryDto(
    BigDecimal paid,
    BigDecimal outstanding,
    int settlementCount,
    @Nullable LocalDate lastPayment,
    List<PayerLineDto> recent
) {}

/**
 * @param amount     what this session cost
 * @param paidAmount what actually arrived against it. Carried beside {@code amount} rather than
 *                   folded into it, because a line showing only the charge next to a payment date
 *                   reads as paid in full — which a part payment is not.
 */
record PayerLineDto(
    LocalDate date,
    @Nullable String title,
    /**
     * ⚠️ A standing monthly fee has no calendar entry, so the client must not fall back to its
     * "untitled session" label for it — that label is "Trening 1:1", which would put a training
     * that never happened on the card, three times a quarter.
     */
    boolean monthlyFee,
    BigDecimal amount,
    BigDecimal paidAmount,
    @Nullable LocalDate settledOn
) {}

/**
 * A standing monthly coaching fee.
 *
 * @param endedOn {@code null} while it runs. May be a past month: a collaboration ends in a
 *                conversation and gets written down a week later.
 */
record SubscriptionDto(
    UUID id,
    BigDecimal amount,
    LocalDate startedOn,
    @Nullable LocalDate endedOn,
    boolean active
) {}

record SaveSubscriptionRequest(
    @NotNull @DecimalMin("0") @DecimalMax("100000") BigDecimal amount,
    @NotNull LocalDate startedOn,
    @Nullable LocalDate endedOn
) {}

/** Any day of the month; the server snaps it, because a subscription ends in a month, not on a day. */
record EndSubscriptionRequest(@NotNull LocalDate endedOn) {}
