package pl.nextsteppro.climbing.api.admin.settlement;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.settlement.Amounts;
import pl.nextsteppro.climbing.domain.settlement.PaymentAllocator;
import pl.nextsteppro.climbing.domain.settlement.PaymentRepository;
import pl.nextsteppro.climbing.domain.settlement.PaymentRow;
import pl.nextsteppro.climbing.domain.settlement.Settlement;
import pl.nextsteppro.climbing.domain.settlement.SettlementRepository;
import pl.nextsteppro.climbing.domain.settlement.SettlementRow;
import pl.nextsteppro.climbing.domain.settlement.PayoutRepository;
import pl.nextsteppro.climbing.domain.settlement.PayoutRow;
import pl.nextsteppro.climbing.domain.settlement.SessionPayoutRepository;
import pl.nextsteppro.climbing.domain.settlement.SessionPayoutRow;
import pl.nextsteppro.climbing.api.admin.UnassignedSessionCounter;
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
import java.util.Objects;
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
 * the user-base tab. Revenue is counted on the payment's day, because that is when the money
 * arrived. Debt has no payment date, so it is counted on the session's own date. In practice the two
 * agree, because the modal's default payment date is the session date.
 *
 * <p>Since V100 both screens run the same {@code PaymentAllocator} over the same two reads (all
 * charges, all payments), so no figure here can disagree with another about one person.
 */
@Service
@Transactional(readOnly = true)
// Package-private on purpose: only this package's controller has any business reading what clients
// were charged, and Java is a stronger gate than a source-scanning test. A future service that tries
// to pull revenue into a shared DTO now fails to COMPILE rather than needing somebody to notice it
// in review. Spring proxies package-private classes fine — the proxy is generated in this package.
//
// The one thing that leaves this package is a bare count, through UnassignedSessionCounter: the
// interface lives beside AdminService, so the notifications DTO learns "N sessions have no payer"
// without the admin package naming a single money type — the isolation gate stays the rule.
class AdminSettlementStatsService implements UnassignedSessionCounter {

    static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    /** Twelve buckets, always — a chart that changes height with its data is hard to read across years. */
    static final int REVENUE_MONTHS = 12;

    /**
     * How far back the two work queues look — "to be priced" and "no payer at all". <b>One constant
     * for both on purpose:</b> they answer the same question about the same backlog ("recent enough
     * to still be worth chasing"), and two numbers here would be two headings that can disagree.
     *
     * <p>A fixed policy, NOT the selected range, so the
     * heading can name it and stay true when the year picker moves — the same rule as
     * {@code LOWEST_WINDOW_DAYS} on the weight tile.
     *
     * <p>Bounded at all because without a window the first load after this ships reports every
     * session in the app's history as unpriced. Pricing is a weekly chore, so a quarter is a
     * generous backlog and anything older is archive, not work.
     */
    static final int UNPRICED_WINDOW_DAYS = 90;

    /**
     * Widest calendar range the "no payer" markers will answer for — the same cap as the private
     * note markers and the training calendar, because all three are driven by what one screen can
     * show. Without it, a client asking for five years reads the whole table.
     */
    static final int MAX_MARKER_RANGE_DAYS = 62;

    private final SettlementRepository settlementRepository;
    private final PaymentRepository paymentRepository;
    private final PayoutRepository payoutRepository;
    private final SessionPayoutRepository sessionPayoutRepository;
    private final AdminPayoutService payoutService;
    private final MessageService msg;

    public AdminSettlementStatsService(SettlementRepository settlementRepository,
                                       PaymentRepository paymentRepository,
                                       PayoutRepository payoutRepository,
                                       SessionPayoutRepository sessionPayoutRepository,
                                       AdminPayoutService payoutService,
                                       MessageService msg) {
        this.settlementRepository = settlementRepository;
        this.paymentRepository = paymentRepository;
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

        // ⚠️ The whole ledger, whatever the year. Which payment covers which charge depends on each
        // person's complete history (oldest debt first), so a year's slice cannot be allocated on its
        // own — and one read of each side replaced the three slices this used to take.
        Ledger ledger = readLedger();

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
            unassigned(today),
            unpriced(today),
            outstanding(ledger),
            credits(ledger),
            revenue(ledger, receivedPayouts, from, to, buckets, year),
            people(ledger, from, to),
            payouts(receivedPayouts, windowFrom, windowTo));
    }

    /** Both sides of the clients' ledger and the allocation between them — two reads, any size. */
    private Ledger readLedger() {
        List<SettlementRow> charges = settlementRepository.findAllRows();
        List<PaymentRow> payments = paymentRepository.findAllRows();
        Map<UUID, SettlementRow> chargeById = new LinkedHashMap<>();
        charges.forEach(row -> chargeById.put(row.id(), row));
        return new Ledger(charges, chargeById, payments, PaymentAllocator.byPayer(charges, payments));
    }

    private record Ledger(List<SettlementRow> charges,
                          Map<UUID, SettlementRow> chargeById,
                          List<PaymentRow> payments,
                          Map<String, PaymentAllocator.Allocation> allocations) {

        PaymentAllocator.Allocation of(String payerKey) {
            return allocations.getOrDefault(payerKey, PaymentAllocator.EMPTY);
        }

        PaymentAllocator.ChargeState stateOf(SettlementRow row) {
            return of(row.payerKey()).chargeState(row.id());
        }
    }

    /**
     * Every income line of a year, flattened — the file an accountant asks for in January.
     *
     * <p>Two sheets: what was owed (with how much of it is covered) and what was handed over. Built
     * from the same reads and the same allocation the tab uses, so the file can never disagree with
     * the screen it was taken from.
     */
    public SettlementExportDto exportRows(@Nullable String yearParam, String clientKindParam,
                                          String payoutKindParam) {
        // These two are display labels the caller translates and hands us, so they are the only
        // free text on this endpoint — and each is copied onto EVERY row of the export. Capping the
        // length here rather than with @Size on the parameter is deliberate: nothing in this
        // codebase puts @Validated on a controller, so the annotation would be inert, and switching
        // it on would turn an over-long label into an unhandled ConstraintViolationException — a
        // 500 and an ERROR in the log for a request the server understood perfectly well.
        String clientKind = capLabel(clientKindParam);
        String payoutKind = capLabel(payoutKindParam);
        LocalDate today = LocalDate.now(WARSAW);
        Integer year = resolveYear(yearParam, availableYears(), today);
        LocalDate from = year == null ? LocalDate.of(1970, 1, 1) : LocalDate.of(year, 1, 1);
        LocalDate to = year == null ? LocalDate.of(2999, 12, 31) : LocalDate.of(year, 12, 31);

        Ledger ledger = readLedger();
        List<SettlementExportRowDto> lines = new ArrayList<>();
        for (SettlementRow row : ledger.charges()) {
            if (row.targetDate().isBefore(from) || row.targetDate().isAfter(to)) {
                continue;
            }
            PaymentAllocator.ChargeState state = ledger.stateOf(row);
            lines.add(new SettlementExportRowDto(clientKind, row.targetDate(), row.targetTitle(),
                nameOf(row), row.amount(), state.covered(), state.paidOn()));
        }
        for (PayoutRow payout : year == null
                ? payoutRepository.findAllRows()
                : payoutRepository.findByReceivedBetween(from, to)) {
            lines.add(new SettlementExportRowDto(payoutKind, payout.periodMonth(), null,
                payout.sourceName(), payout.amount(), payout.amount(), payout.receivedOn()));
        }
        lines.sort(Comparator.comparing(SettlementExportRowDto::date));

        List<PaymentExportRowDto> payments = ledger.payments().stream()
            .filter(row -> !row.receivedOn().isBefore(from) && !row.receivedOn().isAfter(to))
            .sorted(Comparator.comparing(PaymentRow::receivedOn).thenComparing(PaymentRow::createdAt))
            .map(row -> new PaymentExportRowDto(row.receivedOn(), row.payerName(), row.amount(),
                row.enteredTitle()))
            .toList();
        return new SettlementExportDto(lines, payments);
    }

    /**
     * What one client has paid and still owes, whole history.
     *
     * <p>Whole history, not the tab's selected year: this is somebody's card, and "what do I have
     * with this person" has no year in it.
     */
    public PayerSummaryDto payerSummary(UUID userId, int recentLimit) {
        List<SettlementRow> charges = settlementRepository.findRowsForUser(userId);
        List<PaymentRow> payments = paymentRepository.findRowsForUsers(List.of(userId));
        // ⚠️ The same allocation the tab runs, so the card and the tab cannot disagree about one
        // person — the failure this block used to have, when each screen did its own arithmetic.
        PaymentAllocator.Allocation allocation = PaymentAllocator.allocate(
            charges.stream().map(SettlementRow::toCharge).toList(),
            payments.stream().map(PaymentRow::toReceipt).toList());

        BigDecimal paid = BigDecimal.ZERO;
        LocalDate lastPayment = null;
        for (PaymentRow payment : payments) {
            paid = paid.add(payment.amount());
            if (lastPayment == null || payment.receivedOn().isAfter(lastPayment)) {
                lastPayment = payment.receivedOn();
            }
        }

        List<PayerLineDto> lines = new ArrayList<>();
        for (SettlementRow row : charges) {
            PaymentAllocator.ChargeState state = allocation.chargeState(row.id());
            lines.add(new PayerLineDto(row.targetDate(), row.targetTitle(), row.isMonthlyFee(),
                row.amount(), state.covered(), state.paidOn()));
        }
        lines.sort(Comparator.comparing(PayerLineDto::date).reversed());
        return new PayerSummaryDto(scale(paid), allocation.debt(), allocation.credit(), payments.size(),
            lastPayment, lines.stream().limit(recentLimit).toList());
    }

    // -------------------------------------------------------------- unassigned

    /**
     * Sessions that were worked and have nobody to bill — see {@code UnassignedSession} for why the
     * unpriced queue cannot reach them and what makes this list quiet enough to be a work queue.
     *
     * <p>One read, already ordered and already free of everything the unpriced queue reports, so
     * there is nothing to group or de-duplicate here: the two lists are disjoint by their WHERE
     * clauses rather than by a filter somebody has to remember.
     */
    private UnassignedDto unassigned(LocalDate today) {
        List<UnassignedSessionDto> sessions = settlementRepository
            .findUnassignedSlotsFrom(unassignedWindowStart(today))
            .stream()
            // Day resolution, like the window: today's session sits with the past ones, as it did
            // when this list was past-only. "Upcoming" only labels the row; it changes no count.
            .map(row -> new UnassignedSessionDto("slot", row.targetId(), row.targetDate(), row.title(),
                row.targetDate().isAfter(today)))
            .toList();
        return new UnassignedDto(sessions.size(), UNPRICED_WINDOW_DAYS, sessions);
    }

    /**
     * The admin-nav dot: the size of the list above, counted rather than loaded.
     *
     * <p>⚠️ Same lower bound through one helper, so the dot and the list it opens cannot disagree.
     */
    @Override
    public int countUnassigned(LocalDate today) {
        return Math.toIntExact(settlementRepository.countUnassignedSlotsFrom(unassignedWindowStart(today)));
    }

    private static LocalDate unassignedWindowStart(LocalDate today) {
        return today.minusDays(UNPRICED_WINDOW_DAYS);
    }

    /**
     * Where the calendar should warn that a closed session has nobody to bill.
     *
     * <p>Bounded like every other range read in this app, and for the same reason: a client asking
     * for five years would read the whole table. The cap matches the note markers', because both
     * are driven by the same visible calendar range.
     */
    public UnassignedMarkersDto getUnassignedMarkers(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_MARKER_RANGE_DAYS) {
            throw new IllegalArgumentException(msg.get("admin.settlement.range.invalid"));
        }
        return new UnassignedMarkersDto(
            settlementRepository.findUnassignedSlotIdsBetween(from, to),
            settlementRepository.findUnassignedSlotDatesBetween(from, to));
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
    private OutstandingDto outstanding(Ledger ledger) {
        BigDecimal total = BigDecimal.ZERO;
        List<OutstandingItemDto> items = new ArrayList<>();
        LocalDate oldest = null;
        List<SettlementRow> sorted = ledger.charges().stream()
            .sorted(Comparator.comparing(SettlementRow::targetDate))
            .toList();
        for (SettlementRow row : sorted) {
            BigDecimal remaining = ledger.stateOf(row).remaining();
            if (remaining.signum() == 0) {
                continue;
            }
            if (oldest == null) {
                oldest = row.targetDate();
            }
            total = total.add(remaining);
            items.add(new OutstandingItemDto(
                row.isMonthlyFee() ? "month" : row.eventId() != null ? "event" : "slot",
                row.isMonthlyFee() ? null : row.eventId() != null ? row.eventId() : row.slotId(),
                row.targetDate(),
                row.targetTitle(),
                row.isGuest() ? "guest" : "user",
                row.isGuest() ? row.guestId() : row.userId(),
                nameOf(row),
                remaining));
        }
        return new OutstandingDto(scale(total), items.size(), oldest, items);
    }

    // ---------------------------------------------------------------- credits

    /**
     * People holding money of ours that no charge has used yet — see {@link CreditsDto}.
     *
     * <p>One item per payment that still has an unused part, named by when it arrived and where it
     * was typed in: that is what the owner remembers ("the 200 from the 2nd"), and the payment is
     * where a mistake is corrected. Nobody here owes anything — allocation pays the oldest debt
     * first, so credit only exists once everything is covered.
     *
     * <p>Biggest first: this list is a memo to consult when pricing the next session, and the
     * hundred and fifty somebody left behind is the one worth remembering.
     */
    private CreditsDto credits(Ledger ledger) {
        Map<String, BigDecimal> byPayer = new LinkedHashMap<>();
        List<PaymentRow> kept = new ArrayList<>();
        for (PaymentRow payment : ledger.payments()) {
            BigDecimal unused = ledger.of(payment.payerKey()).unallocatedOf(payment.id());
            if (unused.signum() > 0) {
                kept.add(payment);
                byPayer.merge(payment.payerKey(), unused, BigDecimal::add);
            }
        }
        BigDecimal total = byPayer.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CreditItemDto> items = kept.stream()
            .sorted(Comparator
                .comparing((PaymentRow row) -> byPayer.get(row.payerKey()), Comparator.reverseOrder())
                .thenComparing(PaymentRow::payerKey)
                .thenComparing(PaymentRow::receivedOn))
            .map(row -> new CreditItemDto(
                row.enteredEventId() != null ? "event" : row.enteredSlotId() != null ? "slot" : null,
                row.enteredEventId() != null ? row.enteredEventId() : row.enteredSlotId(),
                row.enteredDate(),
                row.receivedOn(),
                row.enteredTitle(),
                row.isGuest() ? "guest" : "user",
                Objects.requireNonNull(row.isGuest() ? row.guestId() : row.userId()),
                row.payerName(),
                ledger.of(row.payerKey()).unallocatedOf(row.id())))
            .toList();

        return new CreditsDto(scale(total), byPayer.size(), items);
    }

    // ----------------------------------------------------------------- revenue

    private RevenueDto revenue(Ledger ledger, List<PayoutRow> receivedPayouts,
                               LocalDate from, LocalDate to, List<YearMonth> buckets,
                               @Nullable Integer year) {
        Map<YearMonth, BigDecimal> byMonth = new LinkedHashMap<>();
        for (YearMonth bucket : buckets) {
            byMonth.put(bucket, BigDecimal.ZERO);
        }

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal fromSlots = BigDecimal.ZERO;
        BigDecimal fromEvents = BigDecimal.ZERO;
        BigDecimal fromSubscriptions = BigDecimal.ZERO;
        BigDecimal fromPayouts = BigDecimal.ZERO;
        BigDecimal fromCredit = BigDecimal.ZERO;
        for (PaymentRow payment : ledger.payments()) {
            LocalDate paidOn = payment.receivedOn();
            if (paidOn.isBefore(from) || paidOn.isAfter(to)) {
                continue;
            }
            // ⚠️ What arrived, not what was charged — cash basis, on the day it arrived.
            total = total.add(payment.amount());
            // The split follows where the money WENT. ⚠️ Three targets, three buckets — a monthly
            // fee has neither a slot nor an event, so an "else" here quietly filed retainers under
            // session income — plus a fourth for the part nothing has used yet.
            PaymentAllocator.Allocation allocation = ledger.of(payment.payerKey());
            for (PaymentAllocator.Share share : allocation.sharesOf(payment.id())) {
                SettlementRow charge = Objects.requireNonNull(ledger.chargeById().get(share.chargeId()));
                if (charge.isMonthlyFee()) {
                    fromSubscriptions = fromSubscriptions.add(share.amount());
                } else if (charge.eventId() != null) {
                    fromEvents = fromEvents.add(share.amount());
                } else {
                    fromSlots = fromSlots.add(share.amount());
                }
            }
            fromCredit = fromCredit.add(allocation.unallocatedOf(payment.id()));
            // A payment can fall inside the selected year and outside the twelve drawn buckets only
            // in the "everything" view, where the chart is a rolling window rather than a year.
            byMonth.computeIfPresent(YearMonth.from(paidOn), (month, sum) -> sum.add(payment.amount()));
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
            previousTotal = fill(lastYear, ledger.payments(), receivedPayouts,
                LocalDate.of(year - 1, 1, 1), LocalDate.of(year - 1, 12, 31));
            previousMonths = lastYear.entrySet().stream()
                .map(entry -> new MonthlyRevenueDto(entry.getKey().atDay(1), scale(entry.getValue())))
                .toList();
        }

        return new RevenueDto(scale(total), monthlyAverage(byMonth), months,
            scale(fromSlots), scale(fromEvents), scale(fromSubscriptions), scale(fromPayouts),
            scale(fromCredit), previousMonths, scale(previousTotal));
    }

    /** Buckets both money sources into a prepared month map and returns what landed in the range. */
    private BigDecimal fill(Map<YearMonth, BigDecimal> byMonth, List<PaymentRow> payments,
                            List<PayoutRow> payouts, LocalDate from, LocalDate to) {
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentRow payment : payments) {
            LocalDate paidOn = payment.receivedOn();
            if (paidOn.isBefore(from) || paidOn.isAfter(to)) continue;
            total = total.add(payment.amount());
            byMonth.computeIfPresent(YearMonth.from(paidOn), (month, sum) -> sum.add(payment.amount()));
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
                .addSession(session);
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
                scale(period.amount), period.rate(), period.transfers, period.heldSessions()))
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
        private final List<SessionPayoutRow> rows = new ArrayList<>();

        private Period(UUID sourceId, String sourceName, LocalDate month) {
            this.sourceId = sourceId;
            this.sourceName = sourceName;
            this.month = month;
        }

        private void add(PayoutRow payout) {
            amount = amount.add(payout.amount());
            transfers.add(new PayoutEntryDto(payout.id(), payout.amount(), payout.receivedOn()));
        }

        private void addSession(SessionPayoutRow row) {
            rows.add(row);
            sessions++;
            Integer sessionMinutes = row.minutes();
            if (sessionMinutes == null) {
                sessionsWithoutHours++;
            } else {
                minutes += sessionMinutes;
            }
        }

        /**
         * The sessions behind the count and the hours, oldest first.
         *
         * <p>Built here rather than queried again: the read this comes from is the same one the
         * totals are made of, so the list and the figures above it cannot disagree — which is the
         * entire point of being able to open the row.
         */
        private List<PayoutSessionDto> heldSessions() {
            return rows.stream()
                .sorted(Comparator.comparing(SessionPayoutRow::date)
                    .thenComparing(row -> row.startTime() == null ? LocalTime.MIN : row.startTime()))
                .map(row -> new PayoutSessionDto(row.targetType(), row.targetId(), row.date(),
                    row.title(), row.minutes()))
                .toList();
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
            return hourlyRate(amount, minutes);
        }
    }

    // ----------------------------------------------------- one payer's history

    /**
     * Everything one institution ever held and paid — see {@link PayoutSourceHistoryDto}.
     *
     * <p>Two reads plus the payer's own row, then one pass in Java, the same shape as the tab. The
     * month rows are built by the very same accumulator the tab uses, so a figure cannot mean one
     * thing on the list and another on the screen you reach by clicking it.
     */
    public PayoutSourceHistoryDto sourceHistory(UUID sourceId) {
        PayoutSourceDto source = payoutService.requireSourceDto(sourceId);
        List<SessionPayoutRow> sessions = sessionPayoutRepository.findSessionsForSource(sourceId);
        List<PayoutRow> payouts = payoutRepository.findBySourceId(sourceId);

        Map<String, Period> byKey = new LinkedHashMap<>();
        Map<UUID, String> names = Map.of(source.id(), source.name());
        for (PayoutRow payout : payouts) {
            periodOf(byKey, names, sourceId, payout.periodMonth()).add(payout);
        }
        for (SessionPayoutRow session : sessions) {
            periodOf(byKey, names, sourceId, session.date().withDayOfMonth(1)).addSession(session);
        }

        List<Period> periods = byKey.values().stream()
            .sorted(Comparator.comparing((Period period) -> period.month).reversed())
            .toList();

        BigDecimal totalAmount = BigDecimal.ZERO;
        int totalSessions = 0;
        int totalMinutes = 0;
        int withoutHours = 0;
        for (Period period : periods) {
            totalAmount = totalAmount.add(period.amount);
            totalSessions += period.sessions;
            totalMinutes += period.minutes;
            withoutHours += period.sessionsWithoutHours;
        }

        return new PayoutSourceHistoryDto(
            source.id(), source.name(), source.archived(),
            periods.isEmpty() ? null : periods.getLast().month,
            periods.isEmpty() ? null : periods.getFirst().month,
            span(periods),
            totalSessions, totalMinutes, withoutHours,
            scale(totalAmount),
            hourlyRate(totalAmount, totalMinutes),
            chart(periods),
            years(periods),
            periods.stream()
                .map(period -> new PayoutPeriodDto(period.sourceId, period.sourceName, period.month,
                    period.sessions, period.minutes, period.sessionsWithoutHours,
                    scale(period.amount), period.rate(), period.transfers, period.heldSessions()))
                .toList());
    }

    /** Months from the first activity to the last, inclusive — the span, not a count of busy ones. */
    private int span(List<Period> periods) {
        if (periods.isEmpty()) {
            return 0;
        }
        return (int) ChronoUnit.MONTHS.between(
            YearMonth.from(periods.getLast().month), YearMonth.from(periods.getFirst().month)) + 1;
    }

    /**
     * A bar per month of the collaboration, oldest first, <b>gaps included</b>: a month with nothing
     * in it is a fact about the collaboration, and closing the gaps would draw a busier partner than
     * the one in the data.
     */
    private List<MonthlyRevenueDto> chart(List<Period> periods) {
        if (periods.isEmpty()) {
            return List.of();
        }
        Map<YearMonth, BigDecimal> byMonth = new LinkedHashMap<>();
        for (YearMonth month = YearMonth.from(periods.getLast().month);
             !month.isAfter(YearMonth.from(periods.getFirst().month));
             month = month.plusMonths(1)) {
            byMonth.put(month, BigDecimal.ZERO);
        }
        for (Period period : periods) {
            byMonth.computeIfPresent(YearMonth.from(period.month), (month, sum) -> sum.add(period.amount));
        }
        return byMonth.entrySet().stream()
            .map(entry -> new MonthlyRevenueDto(entry.getKey().atDay(1), scale(entry.getValue())))
            .toList();
    }

    /** Newest year first, like every other list on this tab. */
    private List<PayoutYearDto> years(List<Period> periods) {
        Map<Integer, Period> byYear = new LinkedHashMap<>();
        for (Period period : periods) {
            Period year = byYear.computeIfAbsent(period.month.getYear(),
                ignored -> new Period(period.sourceId, period.sourceName, period.month));
            year.amount = year.amount.add(period.amount);
            year.sessions += period.sessions;
            year.minutes += period.minutes;
            year.sessionsWithoutHours += period.sessionsWithoutHours;
        }
        return byYear.entrySet().stream()
            .sorted(Map.Entry.<Integer, Period>comparingByKey().reversed())
            .map(entry -> new PayoutYearDto(entry.getKey(), entry.getValue().sessions,
                entry.getValue().minutes, entry.getValue().sessionsWithoutHours,
                scale(entry.getValue().amount),
                hourlyRate(entry.getValue().amount, entry.getValue().minutes)))
            .toList();
    }

    /**
     * What an amount works out to per hour, or {@code null} when either half is missing.
     *
     * <p>The one arithmetic behind every rate on this feature, written once: hours are the unit
     * (a 45-minute school hour and a ninety-minute block are not interchangeable), and a missing
     * half is a gap rather than a zero.
     */
    private static @Nullable BigDecimal hourlyRate(BigDecimal amount, int minutes) {
        if (minutes == 0 || amount.signum() == 0) {
            return null;
        }
        return amount.multiply(BigDecimal.valueOf(60))
            .divide(BigDecimal.valueOf(minutes), Settlement.AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ people

    /**
     * One row per payer. Guests are kept apart by their own row id rather than merged by written
     * name: two guests called "Ekipa z Krakowa" on different trips are two payers, and merging them
     * would invent a returning client out of a coincidence.
     */
    private List<PersonRevenueDto> people(Ledger ledger, LocalDate from, LocalDate to) {
        Map<String, Accumulator> byPayer = new LinkedHashMap<>();
        // ⚠️ An accumulator is created only by something that actually contributes in range — the
        // whole ledger is read regardless of the year, so creating one per payer up front would
        // fill the ranking with every client ever, at 0 paid and 0 owed.
        for (PaymentRow payment : ledger.payments()) {
            LocalDate paidOn = payment.receivedOn();
            if (paidOn.isBefore(from) || paidOn.isAfter(to)) {
                continue;
            }
            Accumulator acc = byPayer.computeIfAbsent(payment.payerKey(),
                key -> new Accumulator(payment.isGuest() ? "guest" : "user", payment.userId(),
                    payment.payerName()));
            acc.paid = acc.paid.add(payment.amount());
            acc.count++;
            if (acc.lastPayment == null || paidOn.isAfter(acc.lastPayment)) {
                acc.lastPayment = paidOn;
            }
        }
        for (SettlementRow row : ledger.charges()) {
            if (row.targetDate().isBefore(from) || row.targetDate().isAfter(to)) {
                continue;
            }
            BigDecimal remaining = ledger.stateOf(row).remaining();
            if (remaining.signum() == 0) {
                continue;
            }
            Accumulator acc = byPayer.computeIfAbsent(row.payerKey(),
                key -> new Accumulator(row.isGuest() ? "guest" : "user", row.userId(), nameOf(row)));
            acc.outstanding = acc.outstanding.add(remaining);
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
        for (LocalDate date : paymentRepository.findDistinctReceivedDates()) {
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
        return Amounts.scale(value);
    }

    /**
     * Longest a caller-supplied export label may be. Comfortably above the real ones ("Klient",
     * "Wypłata zbiorcza") in any of the three languages, and low enough that the label cannot
     * dominate a response it is repeated on once per row.
     */
    private static final int MAX_LABEL_LENGTH = 100;

    /**
     * Truncates rather than rejects, because the label is decoration: a caller that sends something
     * absurd should still get its numbers back. Silently dropping it instead would produce an export
     * whose kind column is blank, which reads as missing data rather than as a rejected label.
     */
    private static String capLabel(String label) {
        String trimmed = label.strip();
        return trimmed.length() <= MAX_LABEL_LENGTH ? trimmed : trimmed.substring(0, MAX_LABEL_LENGTH);
    }
}
