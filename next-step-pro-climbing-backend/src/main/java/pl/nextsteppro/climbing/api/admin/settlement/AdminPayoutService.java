package pl.nextsteppro.climbing.api.admin.settlement;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.settlement.Payout;
import pl.nextsteppro.climbing.domain.settlement.PayoutRepository;
import pl.nextsteppro.climbing.domain.settlement.PayoutSource;
import pl.nextsteppro.climbing.domain.settlement.PayoutSourceRepository;
import pl.nextsteppro.climbing.domain.settlement.SessionPayoutRepository;
import pl.nextsteppro.climbing.domain.settlement.SubscriptionRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Work paid for in bulk by somebody else: extra classes at a school, sessions run for a club.
 *
 * <p><b>A second money model, not a variant of the first.</b> A {@code Settlement} says what one
 * participant owes for one session, and the owner decides it at the time. Here the work happens
 * first, a third party calculates it, and one transfer covers a month — the amount does not exist
 * until it lands, and until then any figure written per head would be invented.
 *
 * <p>Three moving parts, because they answer three questions: who pays ({@code PayoutSource}), which
 * sessions were for them ({@code SessionPayout}), and what arrived ({@code Payout}). Only together
 * do they produce the number this is worth keeping for — what a place actually pays per session.
 */
@Service
@Transactional
// Package-private for the same reason as AdminSettlementStatsService: nothing outside this package
// uses it, so the compiler can enforce that rather than leaving it to a test to notice.
class AdminPayoutService {

    private final PayoutSourceRepository sourceRepository;
    private final PayoutRepository payoutRepository;
    private final SessionPayoutRepository sessionPayoutRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AdminSettlementService settlementService;
    private final MessageService msg;

    public AdminPayoutService(PayoutSourceRepository sourceRepository,
                              PayoutRepository payoutRepository,
                              SessionPayoutRepository sessionPayoutRepository,
                              SubscriptionRepository subscriptionRepository,
                              AdminSettlementService settlementService,
                              MessageService msg) {
        this.sourceRepository = sourceRepository;
        this.payoutRepository = payoutRepository;
        this.sessionPayoutRepository = sessionPayoutRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.settlementService = settlementService;
        this.msg = msg;
    }

    // ----------------------------------------------------------------- sources

    @Transactional(readOnly = true)
    public List<PayoutSourceDto> listSources() {
        return sourceRepository.findAllOrdered().stream()
            .map(source -> new PayoutSourceDto(source.getId(), source.getName(), source.isArchived()))
            .toList();
    }

    public PayoutSourceDto createSource(SavePayoutSourceRequest request) {
        String name = requireName(request.name());
        // Mirrors uq_payout_sources_active_name so the caller gets a sentence instead of a
        // constraint name. Archived namesakes are allowed through on purpose.
        if (sourceRepository.findActiveByName(name).isPresent()) {
            throw new IllegalArgumentException(msg.get("admin.payout.source.exists"));
        }
        PayoutSource saved = sourceRepository.save(new PayoutSource(name));
        return new PayoutSourceDto(saved.getId(), saved.getName(), saved.isArchived());
    }

    public void renameSource(UUID sourceId, SavePayoutSourceRequest request) {
        PayoutSource source = requireSource(sourceId);
        String name = requireName(request.name());
        Optional<PayoutSource> clash = sourceRepository.findActiveByName(name);
        if (clash.isPresent() && !clash.get().getId().equals(sourceId)) {
            throw new IllegalArgumentException(msg.get("admin.payout.source.exists"));
        }
        source.rename(name);
    }

    /**
     * Archived, never deleted: payouts and session assignments point here, and a collaboration that
     * ended does not un-earn what it paid. Restoring is the same switch the other way.
     */
    public void setSourceArchived(UUID sourceId, boolean archived) {
        PayoutSource source = requireSource(sourceId);
        if (archived) {
            source.archive();
        } else {
            if (sourceRepository.findActiveByName(source.getName())
                .filter(other -> !other.getId().equals(sourceId)).isPresent()) {
                throw new IllegalArgumentException(msg.get("admin.payout.source.exists"));
            }
            source.restore();
        }
    }

    // ------------------------------------------------------------- assignments

    /**
     * Marks a session as work for a bulk payer, or unmarks it when {@code sourceId} is null.
     *
     * <p>Reuses the settlement service's own target guard, so the two features agree on what an
     * addressable session is: an event is one engagement however many days it spans, and its per-day
     * slots are bookkeeping nobody can write to.
     */
    public void assignSource(String targetSegment, UUID targetId, AssignPayoutSourceRequest request) {
        SettlementTarget target = settlementService.requireAddressableTarget(targetSegment, targetId);
        UUID sourceId = request.sourceId();
        UUID userId = request.subscriberId();

        if (sourceId == null && userId == null) {
            switch (target) {
                case SLOT -> sessionPayoutRepository.clearSlot(targetId);
                case EVENT -> sessionPayoutRepository.clearEvent(targetId);
            }
            return;
        }
        if (sourceId != null && userId != null) {
            throw new IllegalArgumentException(msg.get("admin.payout.session.two.payers"));
        }
        // Refuses the CHANGE, never the state: a session already priced per participant keeps those
        // amounts, and marking it in bulk would hide them while they went on counting.
        if (settlementService.hasPricedParticipants(target, targetId)) {
            throw new IllegalArgumentException(msg.get("admin.payout.session.has.amounts"));
        }
        if (sourceId != null) {
            requireSource(sourceId);
        } else {
            requireSubscriberOfSession(target, targetId, userId);
        }
        switch (target) {
            case SLOT -> sessionPayoutRepository.assignSlot(targetId, sourceId, userId);
            case EVENT -> sessionPayoutRepository.assignEvent(targetId, sourceId, userId);
        }
    }

    /**
     * A session may only be marked as covered by somebody's retainer if that somebody is actually on
     * it and actually has a retainer for the month it falls in.
     *
     * <p>Both halves matter. Without the first, a session could be filed under a client who never
     * attended it. Without the second, the mark would take the session out of the pricing queue and
     * attribute it to a subscription that does not exist — the same invisible-work failure as an
     * amount hidden behind a bulk mark, only inverted: work that earns nothing and says so nowhere.
     *
     * <p>⚠️ And a third: the mark covers the SESSION, while a retainer covers one PERSON. On a
     * session with somebody else on it those are not the same claim, and the difference is a dead
     * end — marking it takes the whole session out of per-participant pricing, so the other
     * participant's cash has nowhere to go, while pricing them first blocks the mark. There is no
     * order that works, so it is refused here, at the click that causes it, instead of surfacing
     * later as "this session is settled in bulk" while somebody is trying to enter an amount.
     * A bulk payer is different and stays allowed on a group: a school really does pay for the room.
     */
    private void requireSubscriberOfSession(SettlementTarget target, UUID targetId, UUID userId) {
        if (!settlementService.isParticipant(target, targetId, userId)) {
            throw new IllegalArgumentException(msg.get("admin.payout.session.not.participant"));
        }
        if (settlementService.countPayers(target, targetId) > 1) {
            throw new IllegalArgumentException(msg.get("admin.payout.session.shared.with.others"));
        }
        LocalDate month = settlementService.sessionMonth(target, targetId);
        if (subscriptionRepository.findByUserId(userId).stream().noneMatch(s -> s.covers(month))) {
            throw new IllegalArgumentException(msg.get("admin.payout.session.no.subscription"));
        }
    }

    // ----------------------------------------------------------------- payouts

    public UUID createPayout(SavePayoutRequest request) {
        PayoutSource source = requireSource(request.sourceId());
        return payoutRepository.save(new Payout(source,
            Payout.normalizePeriod(request.periodMonth()),
            amountOf(request),
            request.receivedOn())).getId();
    }

    public void deletePayout(UUID payoutId) {
        payoutRepository.deleteById(payoutId);
    }

    // ------------------------------------------------------------------ shared

    private BigDecimal amountOf(SavePayoutRequest request) {
        return Payout.normalizeAmount(request.amount(), msg.get("admin.payout.amount.invalid"));
    }

    private String requireName(String raw) {
        String name = PayoutSource.sanitizeName(raw);
        if (name == null) {
            throw new IllegalArgumentException(msg.get("admin.payout.source.empty"));
        }
        return name;
    }

    private PayoutSource requireSource(UUID sourceId) {
        return sourceRepository.findById(sourceId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("admin.payout.source.not.found")));
    }

}
