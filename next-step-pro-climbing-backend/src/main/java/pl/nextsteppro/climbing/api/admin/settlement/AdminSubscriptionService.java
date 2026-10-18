package pl.nextsteppro.climbing.api.admin.settlement;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.settlement.PaymentAllocator;
import pl.nextsteppro.climbing.domain.settlement.PaymentRepository;
import pl.nextsteppro.climbing.domain.settlement.PaymentRow;
import pl.nextsteppro.climbing.domain.settlement.SessionPayoutRepository;
import pl.nextsteppro.climbing.domain.settlement.SettlementRepository;
import pl.nextsteppro.climbing.domain.settlement.SettlementRow;
import pl.nextsteppro.climbing.domain.settlement.SubscriberSession;
import pl.nextsteppro.climbing.domain.settlement.Subscription;
import pl.nextsteppro.climbing.domain.settlement.SubscriptionRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Standing monthly coaching fees: the rule that produces them, and the run that bills them.
 *
 * <p>A subscription bills <b>up front</b> — the fee for a month appears on the first day of that
 * month, the same moment a slot in it becomes real. So the owner can see what is owed while the
 * month is still being worked, rather than learning it in arrears.
 *
 * <p>⚠️ <b>One person's subscriptions never overlap</b> — not only the running ones. Every write here
 * that moves a boundary (create, end, reopen) checks it, and everything else leans on it: a fee row
 * is addressed by (person, month), so a month covered twice has one row and two claims on its
 * price, and "the fees after this subscription's end" can only be read off the person's fees
 * because no other subscription of theirs reaches those months. Before this was enforced, ending an
 * old, closed retainer deleted every unpaid month of the one running now, and reopening one filled
 * the gap before a later one at the wrong price.
 */
@Service
@Transactional
public class AdminSubscriptionService {

    static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final SubscriptionRepository subscriptionRepository;
    private final SettlementRepository settlementRepository;
    private final PaymentRepository paymentRepository;
    private final SessionPayoutRepository sessionPayoutRepository;
    private final UserRepository userRepository;
    private final MessageService msg;

    public AdminSubscriptionService(SubscriptionRepository subscriptionRepository,
                                    SettlementRepository settlementRepository,
                                    PaymentRepository paymentRepository,
                                    SessionPayoutRepository sessionPayoutRepository,
                                    UserRepository userRepository,
                                    MessageService msg) {
        this.subscriptionRepository = subscriptionRepository;
        this.settlementRepository = settlementRepository;
        this.paymentRepository = paymentRepository;
        this.sessionPayoutRepository = sessionPayoutRepository;
        this.userRepository = userRepository;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDto> forUser(UUID userId) {
        List<LocalDate> skipped = subscriptionRepository.findSkippedMonths(userId);
        return subscriptionRepository.findByUserId(userId).stream()
            .map(subscription -> toDto(subscription, skipped))
            .toList();
    }

    /**
     * Starts a subscription and immediately bills every month it already covers.
     *
     * <p>Billing on creation rather than waiting for the nightly run: somebody adding a retainer
     * that started in March expects March to appear, not to turn up tomorrow.
     */
    public SubscriptionDto create(UUID userId, SaveSubscriptionRequest request) {
        if (request.endedOn() == null && subscriptionRepository.findActiveByUserId(userId).isPresent()) {
            throw new IllegalArgumentException(msg.get("admin.subscription.already.active"));
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("admin.subscription.user.not.found")));

        LocalDate start = Subscription.normalizeMonth(request.startedOn());
        LocalDate end = request.endedOn() == null ? null : Subscription.normalizeMonth(request.endedOn());
        if (end != null && end.isBefore(start)) {
            throw new IllegalArgumentException(msg.get("admin.subscription.end.before.start"));
        }
        // ⚠️ Creating a subscription bills every month it already covers, so the start date is the
        // one field here that can write dozens of rows. A mistyped year did: 2020 instead of 2026
        // produced 81 fees in one request, and a backdated end cannot take back the month it starts
        // in. The refusal is the cheap half of the fix — deleting a fee billed by mistake is the
        // other, and neither is enough alone.
        if (start.isBefore(currentMonth().minusMonths(Subscription.MAX_BACKDATE_MONTHS))) {
            throw new IllegalArgumentException(msg.get("admin.subscription.start.too.old"));
        }
        requireNoOverlap(userId, null, start, end);
        Subscription saved = subscriptionRepository.save(new Subscription(user, amountOf(request.amount()), start, end));

        billUpTo(saved, currentMonth());
        return toDto(saved, subscriptionRepository.findSkippedMonths(userId));
    }

    /**
     * Forward-only: months already billed keep what they were billed at.
     *
     * <p>⚠️ Takes an amount and nothing else, deliberately. It used to accept the create request,
     * which carries {@code startedOn} — a field this operation ignores — so an admin correcting a
     * mistyped start date got 204 and no change. A request cannot carry what the handler discards.
     * The start date is not editable at all: the months it billed already exist, and moving it would
     * have to decide what happens to them.
     *
     * <p>Refused on an ended subscription: "from now on" has no now there, so it would answer 204 and
     * change nothing anybody can see — except the price of a month restored later.
     */
    public void changeAmount(UUID subscriptionId, ChangeSubscriptionAmountRequest request) {
        Subscription subscription = requireSubscription(subscriptionId);
        if (!subscription.isActive()) {
            throw new IllegalArgumentException(msg.get("admin.subscription.ended"));
        }
        subscription.changeAmount(amountOf(request.amount()));
    }

    /**
     * Sets (or moves) the last month of a subscription, optionally to one that has already passed.
     *
     * <p>⚠️ A backdated end drops the fees for the months it gives up — but only the UNPAID ones.
     * A paid fee stays: the money arrived, and rewriting that because a date was written down a week
     * late would be the application overruling the bank. The owner can still delete such a row by
     * hand if it really was a mistake.
     *
     * <p>"Unpaid" means the allocation covered NOTHING of it. A partly covered fee stays too — part
     * of the money arrived for it. (Deleting it would not lose the money, which lives in
     * {@code payments}, but it would turn a payment made for that month into loose credit.)
     *
     * <p>⚠️ "The months it gives up" is (new end, old end] — this subscription's own months, never
     * "every fee of this person after the new end". Overlap is refused, so nothing else reaches those
     * months, but the bound still has to be written down: the person-wide version deleted a later
     * subscription's fees. Moving the end LATER gains months, which owe their fee now.
     */
    public void end(UUID subscriptionId, EndSubscriptionRequest request) {
        Subscription subscription = requireSubscription(subscriptionId);
        LocalDate end = Subscription.normalizeMonth(request.endedOn());
        if (end.isBefore(subscription.getStartedOn())) {
            throw new IllegalArgumentException(msg.get("admin.subscription.end.before.start"));
        }
        UUID userId = subscription.getUserId();
        requireNoOverlap(userId, subscriptionId, subscription.getStartedOn(), end);
        LocalDate previousEnd = subscription.getEndedOn();
        subscription.endOn(end);

        List<SettlementRow> charges = settlementRepository.findRowsForUser(userId);
        PaymentAllocator.Allocation allocation = PaymentAllocator.allocate(
            charges.stream().map(SettlementRow::toCharge).toList(),
            paymentRepository.findRowsForUsers(List.of(userId)).stream().map(PaymentRow::toReceipt).toList());
        List<UUID> unpaidGivenUp = charges.stream()
            .filter(row -> row.periodMonth() != null
                && row.periodMonth().isAfter(end)
                && (previousEnd == null || !row.periodMonth().isAfter(previousEnd)))
            .filter(row -> allocation.chargeState(row.id()).covered().signum() == 0)
            .map(SettlementRow::id)
            .toList();
        if (!unpaidGivenUp.isEmpty()) {
            subscriptionRepository.deleteFees(unpaidGivenUp);
        }
        billUpTo(subscription, currentMonth());
        releaseUncoveredSessions(userId);
    }

    /**
     * Reopening an ended one, for the case where it was closed by mistake. Refused over a month a
     * later subscription covers: open-ended from its start, it would bill the gap and every month of
     * the later one at its own price.
     */
    public void reopen(UUID subscriptionId) {
        Subscription subscription = requireSubscription(subscriptionId);
        if (subscriptionRepository.findActiveByUserId(subscription.getUserId())
            .filter(other -> !other.getId().equals(subscriptionId)).isPresent()) {
            throw new IllegalArgumentException(msg.get("admin.subscription.already.active"));
        }
        requireNoOverlap(subscription.getUserId(), subscriptionId, subscription.getStartedOn(), null);
        subscription.endOn(null);
        billUpTo(subscription, currentMonth());
    }

    /**
     * Removes the rule. Fees it billed stay (somebody's debts or payments); sessions marked as
     * covered by it are released, because the mark would now name a retainer that does not exist.
     */
    public void delete(UUID subscriptionId) {
        subscriptionRepository.findById(subscriptionId).ifPresent(subscription -> {
            UUID userId = subscription.getUserId();
            subscriptionRepository.delete(subscription);
            subscriptionRepository.flush();
            releaseUncoveredSessions(userId);
        });
    }

    /**
     * Deletes one month's fee and — when a subscription covers that month — remembers that the
     * month is not charged, so the nightly run does not bring it back.
     *
     * <p>⚠️ The run fills every covered month without a row, so a bare delete was undone the very
     * next night, at the subscription's CURRENT price. A missing row cannot say "skipped on purpose",
     * hence the record (V103). A month NO subscription covers is a plain correction — a paid fee
     * left behind by a backdated end — and recording a skip there would silently swallow that month
     * in a subscription started later.
     *
     * <p>Idempotent, and with no guard beyond the pair (payer, month): the statement cannot match
     * anything else, and gating it would be the opposite of safety on a row whose whole problem is
     * that it should not exist.
     */
    public void skipMonth(UUID userId, LocalDate anyDay) {
        LocalDate month = Subscription.normalizeMonth(anyDay);
        if (subscriptionRepository.findByUserId(userId).stream().anyMatch(s -> s.covers(month))) {
            subscriptionRepository.skipMonth(userId, month);
        }
        settlementRepository.deleteMonthlyFee(userId, month);
    }

    /** Takes a skip back and bills the month straight away, rather than tomorrow night. */
    public void restoreMonth(UUID userId, LocalDate anyDay) {
        LocalDate month = Subscription.normalizeMonth(anyDay);
        subscriptionRepository.unskipMonth(userId, month);
        subscriptionRepository.findByUserId(userId).stream()
            .filter(s -> s.covers(month))
            .findFirst()
            .ifPresent(s -> billUpTo(s, currentMonth()));
    }

    /**
     * The nightly run: every subscription, every month it covers up to this one.
     *
     * <p>⚠️ It catches up rather than billing only today's month. A box that fails to come up on the
     * first of the month would otherwise lose that month's fee silently and for ever — and the
     * unique index on (user, month) is what makes revisiting old months free. Months skipped on
     * purpose are the one thing it steps over.
     *
     * @return how many fees it created, for the log
     */
    public int billDueMonths() {
        LocalDate month = currentMonth();
        int created = 0;
        for (Subscription subscription : subscriptionRepository.findAllWithUser()) {
            created += billUpTo(subscription, month);
        }
        return created;
    }

    private int billUpTo(Subscription subscription, LocalDate currentMonth) {
        Set<LocalDate> billed = new HashSet<>(subscriptionRepository.findBilledMonths(subscription.getUserId()));
        billed.addAll(subscriptionRepository.findSkippedMonths(subscription.getUserId()));
        Instant now = Instant.now();
        int created = 0;

        for (LocalDate month = subscription.getStartedOn();
             !month.isAfter(currentMonth);
             month = month.plusMonths(1)) {
            if (!subscription.covers(month) || billed.contains(month)) {
                continue;
            }
            subscriptionRepository.billMonth(subscription.getUserId(), month, subscription.getAmount(), now);
            billed.add(month);
            created++;
        }
        return created;
    }

    /** Refuses any other subscription of this person reaching a month of [start, end]. */
    private void requireNoOverlap(UUID userId, @Nullable UUID self, LocalDate start, @Nullable LocalDate end) {
        for (Subscription other : subscriptionRepository.findByUserId(userId)) {
            if (other.getId().equals(self)) {
                continue;
            }
            boolean startsBeforeOurEnd = end == null || !other.getStartedOn().isAfter(end);
            boolean endsAfterOurStart = other.getEndedOn() == null || !other.getEndedOn().isBefore(start);
            if (startsBeforeOurEnd && endsAfterOurStart) {
                LocalDate otherEnd = other.getEndedOn();
                throw new IllegalArgumentException(otherEnd == null
                    ? msg.get("admin.subscription.overlaps.running", monthLabel(other.getStartedOn()))
                    : msg.get("admin.subscription.overlaps.ended", monthLabel(other.getStartedOn()), monthLabel(otherEnd)));
            }
        }
    }

    /**
     * Drops the "covered by subscription" mark from every session of this person in a month none of
     * their subscriptions reaches any more. The session goes back to "to be priced", where it can be
     * seen — the mark otherwise claimed a retainer that no longer paid for it, and the session
     * earned nothing while saying so nowhere.
     */
    private void releaseUncoveredSessions(UUID userId) {
        List<Subscription> remaining = subscriptionRepository.findByUserId(userId);
        List<UUID> uncovered = sessionPayoutRepository.findSubscriberSessions(userId).stream()
            .filter(session -> remaining.stream().noneMatch(s -> s.covers(session.month())))
            .map(SubscriberSession::payoutId)
            .toList();
        if (!uncovered.isEmpty()) {
            sessionPayoutRepository.deleteByIdIn(uncovered);
        }
    }

    private SubscriptionDto toDto(Subscription subscription, List<LocalDate> skipped) {
        return new SubscriptionDto(subscription.getId(), subscription.getAmount(),
            subscription.getStartedOn(), subscription.getEndedOn(), subscription.isActive(),
            skipped.stream().filter(subscription::covers).toList());
    }

    /** "03.2026" — how the card names a month, in every language. */
    private static String monthLabel(LocalDate month) {
        return String.format("%02d.%d", month.getMonthValue(), month.getYear());
    }

    /** Warsaw's month, not the container's: the box runs in UTC and would flip a day early. */
    private LocalDate currentMonth() {
        return LocalDate.now(WARSAW).withDayOfMonth(1);
    }

    private BigDecimal amountOf(BigDecimal raw) {
        return Subscription.normalizeAmount(raw, msg.get("admin.subscription.amount.invalid"));
    }

    private Subscription requireSubscription(UUID subscriptionId) {
        return subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("admin.subscription.not.found")));
    }
}
