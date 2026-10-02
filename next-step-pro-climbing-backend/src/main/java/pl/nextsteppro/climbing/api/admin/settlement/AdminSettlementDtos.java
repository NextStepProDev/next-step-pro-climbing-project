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
 * @param targetDate the session's day — the slot's date, or the event's first day. Sent so the
 *                   payment's date picker can prefill it: the money then lands in the month the
 *                   session happened, not the month somebody got round to clicking.
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
 * <p>⚠️ Everything about money on this line except {@code amount} and {@code payments} is DERIVED
 * by {@code PaymentAllocator} from the person's whole ledger (V100). None of it is typed in, so
 * none of it can be "corrected" into disagreeing with the payments it came from.
 *
 * @param amount       what it costs. {@code null} when nothing has been priced yet — a different
 *                     state from {@code 0}, which means free of charge: only the second is a
 *                     decision, and only the second belongs in the totals.
 * @param covered      how much of {@code amount} the person's payments cover, oldest debt first.
 * @param remaining    {@code amount − covered}; zero means paid.
 * @param paidOn       the day of the payment that completed this charge, if it is complete.
 * @param accountDebt  what the person owes across ALL their charges.
 * @param accountCredit money they left with us that no charge has used yet. At most one of the two
 *                     is non-zero: money always pays the oldest debt first.
 * @param payments     payments typed in at this entry, plus payments typed elsewhere that cover this
 *                     charge — each with how it was split, so "she gave 200" stays readable as 200.
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
    BigDecimal covered,
    BigDecimal remaining,
    @Nullable LocalDate paidOn,
    BigDecimal accountDebt,
    BigDecimal accountCredit,
    List<LinePaymentDto> payments,
    @Nullable BigDecimal suggestedAmount
) {}

/**
 * One payment as the modal shows it: what was handed over, unchanged, and where it went.
 *
 * @param enteredHere true when it was typed in at this entry — false when it was typed elsewhere and
 *                    is listed only because part of it covers this charge.
 * @param unallocated the part no charge has used yet — the client's credit, from this payment.
 */
record LinePaymentDto(
    UUID id,
    BigDecimal amount,
    LocalDate receivedOn,
    boolean enteredHere,
    List<PaymentShareDto> shares,
    BigDecimal unallocated
) {}

/**
 * A slice of a payment applied to one charge.
 *
 * @param thisEntry  true when the charge is the one on screen
 * @param monthlyFee true for a standing coaching fee, which has no calendar entry and no title —
 *                   without the flag the screen would fall back to a session title it never had
 */
record PaymentShareDto(
    @Nullable String targetTitle,
    LocalDate targetDate,
    boolean thisEntry,
    boolean monthlyFee,
    BigDecimal amount
) {}

/**
 * Upsert payload — the charge alone. Money arrives through {@link AddPaymentRequest}, never here:
 * until V100 this carried {@code paidAmount}/{@code settledOn}, and one row answering both "what it
 * costs" and "what was handed over" is what lost the second.
 */
record SaveSettlementRequest(
    @NotNull @DecimalMin("0") @DecimalMax("100000") BigDecimal amount
) {}

/**
 * Money one person handed over.
 *
 * <p>The target is optional and is CONTEXT, not where the money goes: allocation is always oldest
 * debt first. It only lets the modal show the payment where it was typed in. Without it the
 * payment came from the "to collect" list, which is about the person, not a session.
 *
 * <p>⚠️ {@code receivedOn} is deliberately <b>not</b> bounded to the past: prepaying next month's
 * course is ordinary. The modal defaults it to the session's day, the outstanding list to today —
 * the reasons are on those screens.
 */
record AddPaymentRequest(
    @NotNull String payerType,
    @NotNull UUID payerId,
    @NotNull @DecimalMin("0.01") @DecimalMax("100000") BigDecimal amount,
    @NotNull LocalDate receivedOn,
    @Nullable String targetType,
    @Nullable UUID targetId
) {}

/**
 * Where the account stands after a payment was added or removed.
 *
 * @param debt   what the person still owes in total
 * @param credit what they have left with us that no charge has used. At most one is non-zero.
 */
record PaymentResultDto(BigDecimal debt, BigDecimal credit) {}

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
    UnassignedDto unassigned,
    UnpricedDto unpriced,
    OutstandingDto outstanding,
    CreditsDto credits,
    RevenueDto revenue,
    List<PersonRevenueDto> people,
    PayoutsDto payouts
) {}

/**
 * Sessions that were worked and have <b>nobody to bill at all</b>.
 *
 * <p>One step further out than {@link UnpricedDto}, and invisible to it by construction: that queue
 * is driven by reservations and guests, so a session with zero people on it produces no rows in any
 * of its reads. The cost of the gap is quiet and lands on the one figure this feature exists for —
 * an unassigned session is missing from the hourly rate's denominator, so one transfer spread over
 * ten sessions instead of twelve reads <em>high</em>, and nothing on the screen says why.
 *
 * <p>⚠️ Same two policies as the unpriced queue, and the screen states both: it ignores the year
 * picker, and it looks back over the same rolling window — one constant, not two that can drift.
 * Unlike that queue it also holds every <b>upcoming</b> session: this list is where the admin-nav
 * dot leads, and the dot counts sessions planned without a payer — the cheapest moment to fix one.
 *
 * @param windowDays how far back the list looks, sent so the screen can name the rule it applies.
 */
record UnassignedDto(
    int count,
    int windowDays,
    List<UnassignedSessionDto> sessions
) {}

/**
 * One session with no payer.
 *
 * <p>@param targetType always {@code "slot"} today, and sent rather than assumed: the row is drawn
 * by the same component as an unpriced one, which builds a calendar deep link from it. A literal
 * {@code slot=} baked into that link would be a silent wrong link the day events join this list.
 */
record UnassignedSessionDto(
    String targetType,
    UUID targetId,
    LocalDate date,
    @Nullable String title,
    // After today (Warsaw). A label only: the list counts both, because the admin-nav dot does
    boolean upcoming
) {}

/**
 * Where a closed session still has nobody to bill, as membership tests — the only question a
 * calendar cell ever asks. Same shape and same discipline as the private-note markers.
 *
 * @param slotDates the month cell knows its day but not which slots sit on it: the month payload
 *                  carries counts, not slot ids.
 */
record UnassignedMarkersDto(
    List<UUID> slotIds,
    List<LocalDate> slotDates
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
 * <p>Already net of anything the person paid: payments cover the oldest debt first (V100), so a
 * debtor cannot also be holding credit, and there is no "before overpayments" figure to show.
 *
 * @param oldest  the earliest session with an unpaid amount, or {@code null} when nothing is owed.
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
 * Money we are holding that no charge has used yet — the other half of {@link OutstandingDto}.
 *
 * <p>Since V100 a debtor cannot appear here: money always covers the oldest debt first, so credit
 * exists only once everything is paid. The two lists are disjoint by construction, not by a filter.
 *
 * <p>Whole history, ignoring the year picker, for the reason {@link OutstandingDto} gives.
 *
 * @param payers how many people the items group into. Sent rather than derived so the heading and
 *               the rows cannot disagree, and so the client can hide the card without grouping first.
 */
record CreditsDto(
    BigDecimal total,
    int payers,
    List<CreditItemDto> items
) {}

/**
 * The unused part of one payment: how much, when it arrived, and where it was typed in.
 *
 * @param targetType {@code slot} / {@code event} where the payment was typed in, or {@code null}
 *                   when it was taken from the outstanding list — then there is nothing to link.
 * @param targetDate that session's day, for the calendar link — not the same as {@code date}
 * @param date       the day the money arrived
 * @param title      the session it was typed in at, when there is one
 * @param amount     the part of this payment no charge has used, always positive
 */
record CreditItemDto(
    @Nullable String targetType,
    @Nullable UUID targetId,
    @Nullable LocalDate targetDate,
    LocalDate date,
    @Nullable String title,
    String payerType,
    UUID payerId,
    String name,
    BigDecimal amount
) {}

/**
 * Money that arrived, counted on the payment's day — the axis the tab labels.
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
 * @param fromCredit     the part of payments in range that no charge has used yet — money in hand
 *                       ahead of the work (a prepayment, change left from a big note). It is revenue
 *                       by date like any other, but it has no source to file it under until a charge
 *                       uses it, and the five buckets must still add up to {@code total}.
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
    BigDecimal fromCredit,
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
 * @param paid         payments received within the selected range; {@code outstanding} is the
 *                     uncovered part of charges whose session falls in it. Two axes, as everywhere
 *                     else here.
 * @param paymentCount how many payments those were
 */
record PersonRevenueDto(
    String payerType,
    @Nullable UUID userId,
    String name,
    int paymentCount,
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
    List<PayoutEntryDto> transfers,
    List<PayoutSessionDto> heldSessions
) {}

/** One arrival, addressable so it can be deleted. */
record PayoutEntryDto(UUID id, BigDecimal amount, LocalDate receivedOn) {}

/**
 * One institution's whole history: what they had, what they paid, and how that moved.
 *
 * <p><b>All-time, and deliberately deaf to the tab's year picker</b> — "what do I have with this
 * place" has no year in it, the same reasoning as the money block on a client's card. The years are
 * broken out below anyway, so nothing is lost by not filtering.
 *
 * <p>⚠️ <b>The chart is bucketed by the month the work was FOR, not by the day the money landed</b>,
 * unlike revenue everywhere else in this feature. It sits directly above rows that are period
 * months, and two axes stacked on one screen would disagree with each other in front of the reader
 * — which is worse than either axis being the wrong choice.
 *
 * @param months        how long the collaboration spans, first activity to last, inclusive — the
 *                      denominator behind "14 months" rather than a count of months that had work.
 * @param periods       the same month rows the tab draws, so the screen reuses one renderer and one
 *                      set of rules — expandable into their sessions and transfers, and their
 *                      transfers stay deletable here.
 */
record PayoutSourceHistoryDto(
    UUID id,
    String name,
    boolean archived,
    @Nullable LocalDate firstActivity,
    @Nullable LocalDate lastActivity,
    int months,
    int totalSessions,
    int totalMinutes,
    int sessionsWithoutHours,
    BigDecimal totalAmount,
    @Nullable BigDecimal averageRatePerHour,
    List<MonthlyRevenueDto> chart,
    List<PayoutYearDto> years,
    List<PayoutPeriodDto> periods
) {}

/**
 * One year of one institution.
 *
 * @param ratePerHour null when the year is missing either half — hours with no transfer yet, or a
 *                    transfer against work whose length is unknown. A zero would be a claim.
 */
record PayoutYearDto(
    int year,
    int sessions,
    int minutes,
    int sessionsWithoutHours,
    BigDecimal amount,
    @Nullable BigDecimal ratePerHour
) {}

/**
 * One session counted in a month of bulk work.
 *
 * <p>The row above is an aggregate of two things — a count of sessions and a sum of hours — and
 * both feed the rate, which is the figure the whole feature exists for. Without a way down to the
 * sessions themselves, a wrong rate is a dead end: you can see that "12 sessions, 14 h" is off and
 * have nowhere to go to find out which of the twelve is wrong.
 *
 * @param minutes {@code null} when the length is not knowable (an all-day entry, or a multi-day
 *                event whose start and end are on different days) — the same sessions the row
 *                counts apart as "+N without hours", named here so the gap has faces.
 */
record PayoutSessionDto(
    String targetType,
    UUID targetId,
    LocalDate date,
    @Nullable String title,
    @Nullable Integer minutes
) {}

/**
 * One line of income for the year, flattened for an accountant.
 *
 * <p>Its own endpoint rather than a field on the overview: these are line items, and the tab needs
 * aggregates. Loading every row of a year into a response that renders four cards would make the
 * common read pay for the rare one.
 *
 * <p>Unpaid lines are included with an empty {@code paidOn} on purpose. What was received is the
 * question most of the time, but "what is still owed for this year" is the other half of the same
 * conversation, and dropping those rows would make the file impossible to reconcile against the
 * screen it came from.
 *
 * @param kind    which money model the line came from, because they are chased differently: a
 *                client's own fee versus a transfer from a school.
 * @param payer   the person or the institution. Guests appear under whatever name was written down
 *                — it is the only one there is.
 * @param amount  what it cost
 * @param covered how much of it the person's payments cover (derived, oldest debt first)
 * @param paidOn  the day the payment that completed it arrived; empty while something is owed
 */
record SettlementExportRowDto(
    String kind,
    LocalDate date,
    @Nullable String title,
    String payer,
    BigDecimal amount,
    BigDecimal covered,
    @Nullable LocalDate paidOn
) {}

/**
 * One payment, exactly as it was handed over — the cash sheet of the export. The charges sheet says
 * what was owed; this one says what arrived and when, which is what an accountant reconciles.
 *
 * @param enteredAt the session it was typed in at, if any
 */
record PaymentExportRowDto(
    LocalDate receivedOn,
    String payer,
    BigDecimal amount,
    @Nullable String enteredAt
) {}

/** The export: what was owed (and transfers from schools), and what clients handed over. */
record SettlementExportDto(
    List<SettlementExportRowDto> lines,
    List<PaymentExportRowDto> payments
) {}

/**
 * One client's money, for their card in the Users panel.
 *
 * <p>Served from here rather than folded into {@code UserDetailDto} for a reason the isolation gate
 * enforces: {@code api/admin/userhistory} may not reach the settlement types at all, so the card's
 * money block is a second request from the browser rather than a second import in Java. That keeps
 * one rule — money lives in one package — instead of an exception to it.
 *
 * @param recent  the last few lines, newest first. Enough to answer "what is this made of" without
 *                turning the card into a second Settlements tab.
 * @param paid        everything they have ever handed over
 * @param outstanding what their payments do not cover yet
 * @param credit      what they left with us that no charge has used yet. At most one of
 *                    {@code outstanding} / {@code credit} is non-zero — money covers the oldest debt
 *                    first, by the same allocation the Settlements tab uses, so the two screens
 *                    cannot disagree about one person.
 */
record PayerSummaryDto(
    BigDecimal paid,
    BigDecimal outstanding,
    BigDecimal credit,
    int paymentCount,
    @Nullable LocalDate lastPayment,
    List<PayerLineDto> recent
) {}

/**
 * @param amount  what this session cost
 * @param covered how much of it is paid. Carried beside {@code amount} because a line showing only
 *                the charge next to a date reads as paid in full — which a part payment is not.
 * @param paidOn  the day the payment that completed it arrived; {@code null} while short
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
    BigDecimal covered,
    @Nullable LocalDate paidOn
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

/**
 * A raise, and nothing else.
 *
 * <p>⚠️ Separate from {@link SaveSubscriptionRequest} on purpose. Reusing that one meant this
 * endpoint required a {@code startedOn} it then ignored, so correcting a mistyped start date
 * answered 204 and changed nothing.
 */
record ChangeSubscriptionAmountRequest(
    @NotNull @DecimalMin("0") @DecimalMax("100000") BigDecimal amount
) {}

/** Any day of the month; the server snaps it, because a subscription ends in a month, not on a day. */
record EndSubscriptionRequest(@NotNull LocalDate endedOn) {}
