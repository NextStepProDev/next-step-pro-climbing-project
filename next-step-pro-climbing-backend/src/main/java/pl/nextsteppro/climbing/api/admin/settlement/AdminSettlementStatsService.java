package pl.nextsteppro.climbing.api.admin.settlement;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.settlement.Settlement;
import pl.nextsteppro.climbing.domain.settlement.SettlementRepository;
import pl.nextsteppro.climbing.domain.settlement.SettlementRow;
import pl.nextsteppro.climbing.domain.settlement.PayoutRepository;
import pl.nextsteppro.climbing.domain.settlement.PayoutRow;
import pl.nextsteppro.climbing.domain.settlement.SessionPayoutRepository;
import pl.nextsteppro.climbing.domain.settlement.SessionPayoutRow;
import pl.nextsteppro.climbing.domain.settlement.UnpricedPayer;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The Settlements tab: what is owed, what came in, and from whom.
 *
 * <p>Same shape as {@code AdminUserStatsService} and {@code TrainingStatsService}: a few projections,
 * then <b>one pass in Java</b>. A dozen {@code SUM(...) FILTER} clauses would be a dozen scans of the
 * same table, and the cost here is <b>four queries regardless of how many settlements exist</b> —
 * {@code AdminSettlementQueryCountTest} keeps that a constant rather than a function of the row count.
 *
 * <p><b>No cache, deliberately.</b> Ticking a payment has to change the figure at once; five minutes
 * of "it will show up shortly" is not an answer to somebody reconciling their own books. The client
 * still holds the global five-minute {@code staleTime}, so the panel invalidates
 * {@code ['admin','settlements']} on every write.
 *
 * <p><b>Two axes, and the screen names them</b> — the same discipline as the "active user" rule on
 * the user-base tab. Revenue is counted on {@code settledOn}, because that is when the money arrived.
 * Debt has no payment date, so it is counted on the session's own date. In practice the two agree,
 * because the client's default payment date is the session date; they part only when the admin
 * deliberately overrides it, and then neither figure is lying.
 */
@Service
@Transactional(readOnly = true)
public class AdminSettlementStatsService {

    static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    /** Twelve buckets, always — a chart that changes height with its data is hard to read across years. */
    static final int REVENUE_MONTHS = 12;

    /**
     * How far back the "to be priced" queue looks. A fixed policy, NOT the selected range, so the
     * heading can name it and stay true when the year picker moves — the same rule as
     * {@code LOWEST_WINDOW_DAYS} on the weight tile.
     *
     * <p>Bounded at all because without a window the first load after this ships reports every
     * session in the app's history as unpriced. Pricing is a weekly chore, so a quarter is a
     * generous backlog and anything older is archive, not work.
     */
    static final int UNPRICED_WINDOW_DAYS = 90;

    private final SettlementRepository settlementRepository;
    private final PayoutRepository payoutRepository;
    private final SessionPayoutRepository sessionPayoutRepository;
    private final AdminPayoutService payoutService;
    private final MessageService msg;

    public AdminSettlementStatsService(SettlementRepository settlementRepository,
                                       PayoutRepository payoutRepository,
                                       SessionPayoutRepository sessionPayoutRepository,
                                       AdminPayoutService payoutService,
                                       MessageService msg) {
        this.settlementRepository = settlementRepository;
        this.payoutRepository = payoutRepository;
        this.sessionPayoutRepository = sessionPayoutRepository;
        this.payoutService = payoutService;
        this.msg = msg;
    }

    /**
     * @param yearParam {@code null} or blank for the newest year that holds data, {@code "all"} for
     *                  everything, otherwise a four-digit year. Defaulting to the newest year with
     *                  data rather than the current one is the ascent log's precedent: an empty
     *                  January of a new year looks exactly like lost history.
     */
    public SettlementOverviewDto getOverview(@Nullable String yearParam) {
        return buildOverview(yearParam, LocalDate.now(WARSAW));
    }

    /** Clock passed in so the month buckets and "newest year" are testable without waiting for one. */
    SettlementOverviewDto buildOverview(@Nullable String yearParam, LocalDate today) {
        List<Integer> years = availableYears();
        Integer year = resolveYear(yearParam, years, today);

        List<SettlementRow> rows = year == null
            ? settlementRepository.findAllRows()
            : settlementRepository.findRowsInRange(
                LocalDate.of(year - 1, 1, 1), LocalDate.of(year, 12, 31));
        List<SettlementRow> unsettled = settlementRepository.findUnsettledRows();

        LocalDate from = year == null ? LocalDate.MIN : LocalDate.of(year, 1, 1);
        LocalDate to = year == null ? LocalDate.MAX : LocalDate.of(year, 12, 31);
        List<YearMonth> buckets = monthBuckets(year, today);

        // Bulk transfers are revenue like any other and are counted on the day they arrived, so the
        // month total stays one number no matter which way the money came in.
        LocalDate windowFrom = buckets.getFirst().atDay(1);
        LocalDate windowTo = buckets.getLast().atEndOfMonth();
        // ⚠️ All-time when no year is chosen, matching findAllRows above. Reading transfers only
        // for the twelve charted months while settlements covered everything made the total short.
        List<PayoutRow> receivedPayouts = year == null
            ? payoutRepository.findAllRows()
            : payoutRepository.findByReceivedBetween(LocalDate.of(year - 1, 1, 1), to);

        return new SettlementOverviewDto(
            years,
            year,
            unpriced(today),
            outstanding(unsettled),
            revenue(rows, receivedPayouts, from, to, buckets, year),
            people(rows, from, to),
            payouts(receivedPayouts, windowFrom, windowTo));
    }

    /**
     * Every income line of a year, flattened — the file an accountant asks for in January.
     *
     * <p>Reuses the same two reads the tab does, so the export can never disagree with the screen it
     * was taken from. Sorted by the day the money is attributed to: the payment date where there is
     * one, the session's own date where there is not.
     */
    public List<SettlementExportRowDto> exportRows(@Nullable String yearParam, String clientKind,
                                                   String payoutKind) {
        LocalDate today = LocalDate.now(WARSAW);
        Integer year = resolveYear(yearParam, availableYears(), today);
        LocalDate from = year == null ? LocalDate.of(1970, 1, 1) : LocalDate.of(year, 1, 1);
        LocalDate to = year == null ? LocalDate.of(2999, 12, 31) : LocalDate.of(year, 12, 31);

        List<SettlementExportRowDto> lines = new ArrayList<>();
        for (SettlementRow row : year == null
                ? settlementRepository.findAllRows()
                : settlementRepository.findRowsInRange(from, to)) {
            lines.add(new SettlementExportRowDto(clientKind, row.targetDate(), row.targetTitle(),
                nameOf(row), row.amount(), row.paidAmount(), row.settledOn()));
        }
        for (PayoutRow payout : year == null
                ? payoutRepository.findAllRows()
                : payoutRepository.findByReceivedBetween(from, to)) {
            lines.add(new SettlementExportRowDto(payoutKind, payout.periodMonth(), null,
                payout.sourceName(), payout.amount(), payout.amount(), payout.receivedOn()));
        }

        lines.sort(Comparator.comparing(
            line -> line.settledOn() == null ? line.date() : line.settledOn()));
        return lines;
    }

    /**
     * What one client has paid and still owes, whole history.
     *
     * <p>Whole history, not the tab's selected year: this is somebody's card, and "what do I have
     * with this person" has no year in it.
     */
    public PayerSummaryDto payerSummary(UUID userId, int recentLimit) {
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        int count = 0;
        LocalDate lastPayment = null;
        List<PayerLineDto> lines = new ArrayList<>();

        for (SettlementRow row : settlementRepository.findRowsForUser(userId)) {
            if (row.settledOn() != null) {
                paid = paid.add(row.amount());
                count++;
                if (lastPayment == null || row.settledOn().isAfter(lastPayment)) {
                    lastPayment = row.settledOn();
                }
            } else {
                outstanding = outstanding.add(row.amount());
            }
            lines.add(new PayerLineDto(row.targetDate(), row.targetTitle(), row.amount(), row.settledOn()));
        }

        lines.sort(Comparator.comparing(PayerLineDto::date).reversed());
        return new PayerSummaryDto(scale(paid), scale(outstanding), count, lastPayment,
            lines.stream().limit(recentLimit).toList());
    }

    // ---------------------------------------------------------------- unpriced

    /**
     * Sessions that are over and still have nobody priced on them.
     *
     * <p>Four reads because there are two kinds of session and two kinds of payer, then one pass in
     * Java to group them — counting distinct payers per session in SQL across two payer sources
     * would be a second query shape to keep in step with the first.
     *
     * <p>Oldest first, like the debts: the useful order for a backlog is the order it accumulated in.
     */
    private UnpricedDto unpriced(LocalDate today) {
        LocalDate from = today.minusDays(UNPRICED_WINDOW_DAYS);
        LocalTime now = LocalTime.MAX;

        Map<UUID, Session> sessions = new LinkedHashMap<>();
        collect(sessions, settlementRepository.findUnpricedSlotUsers(from, today, now), "slot");
        collect(sessions, settlementRepository.findUnpricedSlotGuests(from, today, now), "slot");
        collect(sessions, settlementRepository.findUnpricedEventUsers(from, today), "event");
        collect(sessions, settlementRepository.findUnpricedEventGuests(from, today), "event");

        List<UnpricedSessionDto> ordered = sessions.values().stream()
            .sorted(Comparator.comparing((Session session) -> session.date)
                .thenComparing(session -> session.title == null ? "" : session.title))
            .map(session -> new UnpricedSessionDto(session.targetType, session.targetId,
                session.date, session.title, session.payers.size()))
            .toList();

        return new UnpricedDto(ordered.size(), UNPRICED_WINDOW_DAYS, ordered);
    }

    private void collect(Map<UUID, Session> sessions, List<UnpricedPayer> rows, String targetType) {
        for (UnpricedPayer row : rows) {
            sessions.computeIfAbsent(row.targetId(),
                    id -> new Session(targetType, id, row.targetDate(), row.targetTitle()))
                // A Set, because the four reads are disjoint by construction but the cost of being
                // wrong about that is a payer counted twice, and nothing on screen would show it.
                .payers.add(row.payerId());
        }
    }

    private static final class Session {
        private final String targetType;
        private final UUID targetId;
        private final LocalDate date;
        private final @Nullable String title;
        private final Set<UUID> payers = new LinkedHashSet<>();

        private Session(String targetType, UUID targetId, LocalDate date, @Nullable String title) {
            this.targetType = targetType;
            this.targetId = targetId;
            this.date = date;
            this.title = title;
        }
    }

    // ------------------------------------------------------------- outstanding

    /**
     * ⚠️ Whole history on purpose — see {@link OutstandingDto}. Oldest first, because the useful
     * order for a list of debts is the order in which they have been owed the longest.
     */
    private OutstandingDto outstanding(List<SettlementRow> unsettled) {
        List<SettlementRow> sorted = unsettled.stream()
            .sorted(Comparator.comparing(SettlementRow::targetDate))
            .toList();

        BigDecimal total = BigDecimal.ZERO;
        List<OutstandingItemDto> items = new ArrayList<>(sorted.size());
        for (SettlementRow row : sorted) {
            total = total.add(row.remaining());
            items.add(new OutstandingItemDto(
                row.isMonthlyFee() ? "month" : row.eventId() != null ? "event" : "slot",
                row.isMonthlyFee() ? null : row.eventId() != null ? row.eventId() : row.slotId(),
                row.targetDate(),
                row.targetTitle(),
                row.isGuest() ? "guest" : "user",
                row.isGuest() ? row.guestId() : row.userId(),
                nameOf(row),
                row.remaining()));
        }
        return new OutstandingDto(
            scale(total), items.size(),
            sorted.isEmpty() ? null : sorted.getFirst().targetDate(),
            items);
    }

    // ----------------------------------------------------------------- revenue

    private RevenueDto revenue(List<SettlementRow> rows, List<PayoutRow> receivedPayouts,
                               LocalDate from, LocalDate to, List<YearMonth> buckets,
                               @Nullable Integer year) {
        Map<YearMonth, BigDecimal> byMonth = new LinkedHashMap<>();
        for (YearMonth bucket : buckets) {
            byMonth.put(bucket, BigDecimal.ZERO);
        }

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal fromSlots = BigDecimal.ZERO;
        BigDecimal fromEvents = BigDecimal.ZERO;
        BigDecimal fromPayouts = BigDecimal.ZERO;
        for (SettlementRow row : rows) {
            LocalDate paidOn = row.settledOn();
            if (paidOn == null || paidOn.isBefore(from) || paidOn.isAfter(to)) {
                continue;
            }
            // ⚠️ What arrived, not what was charged. A row paid 100 of 150 is 100 of revenue, and
            // one paid 200 of 150 is 200 — the overpayment is money in hand like any other.
            total = total.add(row.paidAmount());
            if (row.eventId() != null) {
                fromEvents = fromEvents.add(row.paidAmount());
            } else {
                fromSlots = fromSlots.add(row.paidAmount());
            }
            // A payment can fall inside the selected year and outside the twelve drawn buckets only
            // in the "everything" view, where the chart is a rolling window rather than a year.
            byMonth.computeIfPresent(YearMonth.from(paidOn), (month, sum) -> sum.add(row.paidAmount()));
        }

        for (PayoutRow payout : receivedPayouts) {
            LocalDate paidOn = payout.receivedOn();
            if (paidOn.isBefore(from) || paidOn.isAfter(to)) {
                continue;
            }
            total = total.add(payout.amount());
            fromPayouts = fromPayouts.add(payout.amount());
            byMonth.computeIfPresent(YearMonth.from(paidOn), (month, sum) -> sum.add(payout.amount()));
        }

        List<MonthlyRevenueDto> months = byMonth.entrySet().stream()
            .map(entry -> new MonthlyRevenueDto(entry.getKey().atDay(1), scale(entry.getValue())))
            .toList();

        // The same twelve months a year earlier. Only for a chosen year: "everything" has no
        // previous, and inventing one by shifting a rolling window would compare two arbitrary spans.
        List<MonthlyRevenueDto> previousMonths = List.of();
        BigDecimal previousTotal = BigDecimal.ZERO;
        if (year != null) {
            Map<YearMonth, BigDecimal> lastYear = new LinkedHashMap<>();
            for (YearMonth bucket : buckets) {
                lastYear.put(bucket.minusYears(1), BigDecimal.ZERO);
            }
            previousTotal = fill(lastYear, rows, receivedPayouts,
                LocalDate.of(year - 1, 1, 1), LocalDate.of(year - 1, 12, 31));
            previousMonths = lastYear.entrySet().stream()
                .map(entry -> new MonthlyRevenueDto(entry.getKey().atDay(1), scale(entry.getValue())))
                .toList();
        }

        return new RevenueDto(scale(total), monthlyAverage(byMonth), months,
            scale(fromSlots), scale(fromEvents), scale(fromPayouts),
            previousMonths, scale(previousTotal));
    }

    /** Buckets both money sources into a prepared month map and returns what landed in the range. */
    private BigDecimal fill(Map<YearMonth, BigDecimal> byMonth, List<SettlementRow> rows,
                            List<PayoutRow> payouts, LocalDate from, LocalDate to) {
        BigDecimal total = BigDecimal.ZERO;
        for (SettlementRow row : rows) {
            LocalDate paidOn = row.settledOn();
            if (paidOn == null || paidOn.isBefore(from) || paidOn.isAfter(to)) continue;
            total = total.add(row.paidAmount());
            byMonth.computeIfPresent(YearMonth.from(paidOn), (month, sum) -> sum.add(row.paidAmount()));
        }
        for (PayoutRow payout : payouts) {
            LocalDate paidOn = payout.receivedOn();
            if (paidOn.isBefore(from) || paidOn.isAfter(to)) continue;
            total = total.add(payout.amount());
            byMonth.computeIfPresent(YearMonth.from(paidOn), (month, sum) -> sum.add(payout.amount()));
        }
        return total;
    }

    /**
     * Spread over the months the data actually spans, not over twelve.
     *
     * <p>Dividing by twelve makes a year that started trading in September read as a third of what it
     * earned — the same kind of untrue denominator the task block avoids by printing "3 / 12" instead
     * of "3". Null when nothing was paid, so the tile disappears rather than claiming a zero average.
     *
     * <p>⚠️ Averaged over the months the CHART shows, not over the selected total. In the
     * "everything" view those differ: the chart is a rolling twelve months while the total is
     * all-time, so dividing one by the other would print an average of a period nobody is looking at.
     */
    private @Nullable BigDecimal monthlyAverage(Map<YearMonth, BigDecimal> byMonth) {
        List<YearMonth> withMoney = byMonth.entrySet().stream()
            .filter(entry -> entry.getValue().signum() > 0)
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
        if (withMoney.isEmpty()) {
            return null;
        }
        BigDecimal charted = byMonth.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        long spanned = ChronoUnit.MONTHS.between(withMoney.getFirst(), withMoney.getLast()) + 1;
        return charted.divide(BigDecimal.valueOf(spanned), Settlement.AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /** The twelve months of the chosen year, or the twelve ending this month when none is chosen. */
    private List<YearMonth> monthBuckets(@Nullable Integer year, LocalDate today) {
        List<YearMonth> buckets = new ArrayList<>(REVENUE_MONTHS);
        if (year != null) {
            for (int month = 1; month <= REVENUE_MONTHS; month++) {
                buckets.add(YearMonth.of(year, month));
            }
            return buckets;
        }
        YearMonth end = YearMonth.from(today);
        for (int back = REVENUE_MONTHS - 1; back >= 0; back--) {
            buckets.add(end.minusMonths(back));
        }
        return buckets;
    }

    // ----------------------------------------------------------------- payouts

    /**
     * What each month of bulk work held and what it earned.
     *
     * <p>⚠️ Rows are the UNION of both sides, not the transfers alone. A month with sessions and no
     * transfer yet is the most useful row on this table — it is the invoice nobody has paid — and
     * listing only what arrived would hide precisely that. The mirror case is a transfer against no
     * marked sessions, which says the calendar was not filled in.
     *
     * <p>The rate is the point of the whole feature: one transfer divided by the sessions it covered
     * is what the place actually pays. Null whenever either half is missing, because a rate needs
     * both and a zero would be a claim rather than a gap.
     */
    private PayoutsDto payouts(List<PayoutRow> receivedPayouts, LocalDate windowFrom, LocalDate windowTo) {
        // Once, up front. Resolving a name per session row would be a query inside a loop — the
        // exact shape the query-count gates on this tab exist to keep out.
        List<PayoutSourceDto> sources = payoutService.listSources();
        Map<UUID, String> names = new LinkedHashMap<>();
        for (PayoutSourceDto source : sources) {
            names.put(source.id(), source.name());
        }

        Map<String, Period> byKey = new LinkedHashMap<>();
        for (PayoutRow payout : payoutRepository.findByPeriodBetween(windowFrom, windowTo)) {
            periodOf(byKey, names, payout.sourceId(), payout.periodMonth()).add(payout);
        }
        for (SessionPayoutRow session : sessionPayoutRepository.findSessionsBetween(windowFrom, windowTo)) {
            periodOf(byKey, names, session.sourceId(), session.date().withDayOfMonth(1))
                .addSession(session.minutes());
        }

        BigDecimal total = BigDecimal.ZERO;
        for (PayoutRow payout : receivedPayouts) {
            total = total.add(payout.amount());
        }

        List<PayoutPeriodDto> periods = byKey.values().stream()
            .sorted(Comparator.comparing((Period period) -> period.month).reversed()
                .thenComparing(period -> period.sourceName, String.CASE_INSENSITIVE_ORDER))
            .map(period -> new PayoutPeriodDto(period.sourceId, period.sourceName, period.month,
                period.sessions, period.minutes, period.sessionsWithoutHours,
                scale(period.amount), period.rate(), period.transfers))
            .toList();

        return new PayoutsDto(sources, scale(total), periods);
    }

    private static Period periodOf(Map<String, Period> byKey, Map<UUID, String> names,
                                   UUID sourceId, LocalDate month) {
        return byKey.computeIfAbsent(sourceId + "@" + month,
            ignored -> new Period(sourceId, names.getOrDefault(sourceId, ""), month));
    }

    private static final class Period {
        private final UUID sourceId;
        private final String sourceName;
        private final LocalDate month;
        private BigDecimal amount = BigDecimal.ZERO;
        private int sessions;
        private int minutes;
        private int sessionsWithoutHours;
        private final List<PayoutEntryDto> transfers = new ArrayList<>();

        private Period(UUID sourceId, String sourceName, LocalDate month) {
            this.sourceId = sourceId;
            this.sourceName = sourceName;
            this.month = month;
        }

        private void add(PayoutRow payout) {
            amount = amount.add(payout.amount());
            transfers.add(new PayoutEntryDto(payout.id(), payout.amount(), payout.receivedOn()));
        }

        private void addSession(@Nullable Integer sessionMinutes) {
            sessions++;
            if (sessionMinutes == null) {
                sessionsWithoutHours++;
            } else {
                minutes += sessionMinutes;
            }
        }

        /**
         * What the place actually paid per HOUR.
         *
         * <p>⚠️ Per hour and not per session. A 45-minute school hour and a ninety-minute block are
         * not the same unit, so dividing by a count averages things that cannot be averaged and
         * yields a number comparable with nothing — least of all an hourly price list.
         *
         * <p>Null when either half is missing, and null when nothing had a knowable duration: a rate
         * needs a numerator and a denominator, and a zero would be a claim rather than a gap.
         */
        private @Nullable BigDecimal rate() {
            if (minutes == 0 || amount.signum() == 0) {
                return null;
            }
            return amount.multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(minutes), Settlement.AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
    }

    // ------------------------------------------------------------------ people

    /**
     * One row per payer. Guests are kept apart by their own row id rather than merged by written
     * name: two guests called "Ekipa z Krakowa" on different trips are two payers, and merging them
     * would invent a returning client out of a coincidence.
     */
    private List<PersonRevenueDto> people(List<SettlementRow> rows, LocalDate from, LocalDate to) {
        Map<String, Accumulator> byPayer = new LinkedHashMap<>();
        for (SettlementRow row : rows) {
            LocalDate paidOn = row.settledOn();
            boolean paidInRange = paidOn != null && !paidOn.isBefore(from) && !paidOn.isAfter(to);
            // A row paid in part is BOTH: money that arrived, and a remainder still owed. It is
            // counted on the payment axis above and, when still short, on the session axis here.
            boolean owedInRange = !row.isFullyPaid()
                && !row.targetDate().isBefore(from) && !row.targetDate().isAfter(to);
            // ⚠️ The accumulator is created only by a row that actually contributes. Creating it
            // first and then testing the range put payers into the ranking with 0 paid and 0 owed:
            // rows reach here matched on EITHER axis, so a session held this year but settled next
            // one already produced a phantom, and widening the read to two years for the
            // year-over-year comparison would have filled the table with last year's clients.
            if (!paidInRange && !owedInRange) {
                continue;
            }
            Accumulator acc = byPayer.computeIfAbsent(row.payerKey(),
                key -> new Accumulator(row.isGuest() ? "guest" : "user", row.userId(), nameOf(row)));
            if (paidInRange) {
                acc.paid = acc.paid.add(row.paidAmount());
                acc.count++;
                if (acc.lastPayment == null || paidOn.isAfter(acc.lastPayment)) {
                    acc.lastPayment = paidOn;
                }
            }
            if (owedInRange) {
                acc.outstanding = acc.outstanding.add(row.remaining());
            }
        }

        return byPayer.values().stream()
            // Biggest payer first: the ranking answers "who is this business built on", and a debt
            // column beside it answers the other question without needing its own sort.
            .sorted(Comparator.comparing((Accumulator acc) -> acc.paid).reversed()
                .thenComparing(acc -> acc.name, String.CASE_INSENSITIVE_ORDER))
            .map(acc -> new PersonRevenueDto(acc.payerType, acc.userId, acc.name, acc.count,
                scale(acc.paid), scale(acc.outstanding), acc.lastPayment))
            .toList();
    }

    private static final class Accumulator {
        private final String payerType;
        private final @Nullable UUID userId;
        private final String name;
        private BigDecimal paid = BigDecimal.ZERO;
        private BigDecimal outstanding = BigDecimal.ZERO;
        private int count;
        private @Nullable LocalDate lastPayment;

        private Accumulator(String payerType, @Nullable UUID userId, String name) {
            this.payerType = payerType;
            this.userId = userId;
            this.name = name;
        }
    }

    // ------------------------------------------------------------------- years

    private List<Integer> availableYears() {
        TreeSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        for (LocalDate date : settlementRepository.findDistinctTargetDates()) {
            years.add(date.getYear());
        }
        for (LocalDate date : settlementRepository.findDistinctSettledDates()) {
            years.add(date.getYear());
        }
        return List.copyOf(years);
    }

    private @Nullable Integer resolveYear(@Nullable String yearParam, List<Integer> years, LocalDate today) {
        if (yearParam == null || yearParam.isBlank()) {
            // Newest year holding data, not the current one — an empty January is not lost history.
            return years.isEmpty() ? today.getYear() : years.getFirst();
        }
        if ("all".equalsIgnoreCase(yearParam)) {
            return null;
        }
        try {
            int year = Integer.parseInt(yearParam.trim());
            if (year < 2000 || year > 2999) {
                throw new NumberFormatException(yearParam);
            }
            return year;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(msg.get("admin.settlement.year.invalid"));
        }
    }

    // ------------------------------------------------------------------ shared

    private static String nameOf(SettlementRow row) {
        if (row.isGuest()) {
            String note = row.guestNote();
            return note == null ? "" : note;
        }
        String first = row.firstName() == null ? "" : row.firstName();
        String last = row.lastName() == null ? "" : row.lastName();
        return (first + " " + last).trim();
    }

    /** Every figure leaves at the column's scale, so the client never has to round money itself. */
    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(Settlement.AMOUNT_SCALE, RoundingMode.HALF_UP);
    }
}
