package pl.nextsteppro.climbing.api.admin.settlement;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
 */
@Service
@Transactional
public class AdminSubscriptionService {

    static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final MessageService msg;

    public AdminSubscriptionService(SubscriptionRepository subscriptionRepository,
                                    UserRepository userRepository,
                                    MessageService msg) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDto> forUser(UUID userId) {
        return subscriptionRepository.findByUserId(userId).stream()
            .map(subscription -> new SubscriptionDto(subscription.getId(), subscription.getAmount(),
                subscription.getStartedOn(), subscription.getEndedOn(), subscription.isActive()))
            .toList();
    }

    /**
     * Starts a subscription and immediately bills every month it already covers.
     *
     * <p>Billing on creation rather than waiting for the nightly run: somebody adding a retainer
     * that started in March expects March to appear, not to turn up tomorrow.
     */
    public SubscriptionDto create(UUID userId, SaveSubscriptionRequest request) {
        if (subscriptionRepository.findActiveByUserId(userId).isPresent()) {
            throw new IllegalArgumentException(msg.get("admin.subscription.already.active"));
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("admin.subscription.user.not.found")));

        LocalDate start = Subscription.normalizeMonth(request.startedOn());
        LocalDate end = request.endedOn() == null ? null : Subscription.normalizeMonth(request.endedOn());
        if (end != null && end.isBefore(start)) {
            throw new IllegalArgumentException(msg.get("admin.subscription.end.before.start"));
        }
        Subscription saved = subscriptionRepository.save(
            new Subscription(user, amountOf(request.amount()), start, end));

        billUpTo(saved, currentMonth());
        return new SubscriptionDto(saved.getId(), saved.getAmount(), saved.getStartedOn(),
            saved.getEndedOn(), saved.isActive());
    }

    /** Forward-only: months already billed keep what they were billed at. */
    public void changeAmount(UUID subscriptionId, SaveSubscriptionRequest request) {
        requireSubscription(subscriptionId).changeAmount(amountOf(request.amount()));
    }

    /**
     * Ends a subscription, optionally on a date that has already passed.
     *
     * <p>⚠️ A backdated end drops the fees for months beginning after it — but only the UNPAID ones.
     * A paid fee stays: the money arrived, and rewriting that because a date was written down a week
     * late would be the application overruling the bank. The owner can still delete such a row by
     * hand if it really was a mistake.
     */
    public void end(UUID subscriptionId, EndSubscriptionRequest request) {
        Subscription subscription = requireSubscription(subscriptionId);
        LocalDate end = Subscription.normalizeMonth(request.endedOn());
        if (end.isBefore(subscription.getStartedOn())) {
            throw new IllegalArgumentException(msg.get("admin.subscription.end.before.start"));
        }
        subscription.endOn(end);
        subscriptionRepository.deleteUnpaidFeesAfter(subscription.getUserId(), end);
    }

    /** Reopening an ended one, for the case where it was closed by mistake. */
    public void reopen(UUID subscriptionId) {
        Subscription subscription = requireSubscription(subscriptionId);
        if (subscriptionRepository.findActiveByUserId(subscription.getUserId())
            .filter(other -> !other.getId().equals(subscriptionId)).isPresent()) {
            throw new IllegalArgumentException(msg.get("admin.subscription.already.active"));
        }
        subscription.endOn(null);
        billUpTo(subscription, currentMonth());
    }

    public void delete(UUID subscriptionId) {
        subscriptionRepository.deleteById(subscriptionId);
    }

    /**
     * The nightly run: every subscription, every month it covers up to this one.
     *
     * <p>⚠️ It catches up rather than billing only today's month. A box that fails to come up on the
     * first of the month would otherwise lose that month's fee silently and for ever — and the
     * unique index on (user, month) is what makes revisiting old months free.
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
        Set<LocalDate> billed = new HashSet<>(
            subscriptionRepository.findBilledMonths(subscription.getUserId()));
        Instant now = Instant.now();
        int created = 0;

        for (LocalDate month = subscription.getStartedOn();
             !month.isAfter(currentMonth);
             month = month.plusMonths(1)) {
            if (!subscription.covers(month) || billed.contains(month)) {
                continue;
            }
            subscriptionRepository.billMonth(subscription.getUserId(), month,
                subscription.getAmount(), now);
            billed.add(month);
            created++;
        }
        return created;
    }

    /** Warsaw's month, not the container's: the box runs in UTC and would flip a day early. */
    private LocalDate currentMonth() {
        return LocalDate.now(WARSAW).withDayOfMonth(1);
    }

    private BigDecimal amountOf(BigDecimal raw) {
        BigDecimal scaled = raw.setScale(2, java.math.RoundingMode.HALF_UP);
        if (scaled.compareTo(Subscription.MIN_AMOUNT) < 0
            || scaled.compareTo(Subscription.MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException(msg.get("admin.subscription.amount.invalid"));
        }
        return scaled;
    }

    private Subscription requireSubscription(UUID subscriptionId) {
        return subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("admin.subscription.not.found")));
    }

    @Nullable
    @Transactional(readOnly = true)
    public SubscriptionDto activeFor(UUID userId) {
        return subscriptionRepository.findActiveByUserId(userId)
            .map(s -> new SubscriptionDto(s.getId(), s.getAmount(), s.getStartedOn(), s.getEndedOn(), true))
            .orElse(null);
    }
}
