package pl.nextsteppro.climbing.api.mysubscription;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.settlement.PaymentAllocator;
import pl.nextsteppro.climbing.domain.settlement.PaymentRepository;
import pl.nextsteppro.climbing.domain.settlement.PaymentRow;
import pl.nextsteppro.climbing.domain.settlement.SettlementRepository;
import pl.nextsteppro.climbing.domain.settlement.SettlementRow;
import pl.nextsteppro.climbing.domain.settlement.Subscription;
import pl.nextsteppro.climbing.domain.settlement.SubscriptionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The client's own subscription: the rule, the months it billed, and what is still owed.
 *
 * <p>⚠️ "Paid" comes from the same {@link PaymentAllocator} run over the person's WHOLE ledger
 * that the coach's card and the Settlements tab use — sessions included, oldest debt first. A fee
 * computed here by its own arithmetic would be the third copy of a rule that has already disagreed
 * with itself once (V100), and this time the disagreement would be shown to the client.
 */
@Service
@Transactional(readOnly = true)
class MySubscriptionService {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    /** How far back the card lists paid months; anything still owed is listed however old. */
    static final int HISTORY_MONTHS = 12;

    private final SubscriptionRepository subscriptionRepository;
    private final SettlementRepository settlementRepository;
    private final PaymentRepository paymentRepository;

    MySubscriptionService(SubscriptionRepository subscriptionRepository,
                          SettlementRepository settlementRepository,
                          PaymentRepository paymentRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.settlementRepository = settlementRepository;
        this.paymentRepository = paymentRepository;
    }

    MySubscriptionDto forUser(UUID userId) {
        return build(userId, LocalDate.now(WARSAW).withDayOfMonth(1));
    }

    MySubscriptionDto build(UUID userId, LocalDate currentMonth) {
        List<Subscription> subscriptions = subscriptionRepository.findByUserId(userId);
        CurrentSubscriptionDto current = subscriptions.stream()
            .max(Comparator.comparing(Subscription::getStartedOn))
            .map(s -> new CurrentSubscriptionDto(s.getAmount(), s.getStartedOn(), s.getEndedOn(), s.isActive()))
            .orElse(null);

        List<SettlementRow> charges = settlementRepository.findRowsForUser(userId);
        PaymentAllocator.Allocation allocation = PaymentAllocator.allocate(
            charges.stream().map(SettlementRow::toCharge).toList(),
            paymentRepository.findRowsForUsers(List.of(userId)).stream().map(PaymentRow::toReceipt).toList());

        LocalDate windowStart = currentMonth.minusMonths(HISTORY_MONTHS - 1);
        List<MonthFeeDto> months = new ArrayList<>();
        BigDecimal due = BigDecimal.ZERO;
        for (SettlementRow row : charges) {
            LocalDate month = row.periodMonth();
            if (month == null) {
                continue;
            }
            PaymentAllocator.ChargeState state = allocation.chargeState(row.id());
            due = due.add(state.remaining());
            if (month.isBefore(windowStart) && state.remaining().signum() == 0) {
                continue;
            }
            months.add(new MonthFeeDto(month, row.amount(), state.covered(), state.remaining(),
                state.paidOn(), false));
        }
        // A waived month is shown as waived: missing from the list, it reads as one the coach forgot.
        for (LocalDate skipped : subscriptionRepository.findSkippedMonths(userId)) {
            if (!skipped.isBefore(windowStart) && !skipped.isAfter(currentMonth)
                && subscriptions.stream().anyMatch(s -> s.covers(skipped))) {
                months.add(new MonthFeeDto(skipped, null, BigDecimal.ZERO, BigDecimal.ZERO, null, true));
            }
        }
        months.sort(Comparator.comparing(MonthFeeDto::month).reversed());
        return new MySubscriptionDto(current, months, due);
    }
}
