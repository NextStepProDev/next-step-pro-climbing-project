package pl.nextsteppro.climbing.api.admin.settlement;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.settlement.Payout;
import pl.nextsteppro.climbing.domain.settlement.PayoutRepository;
import pl.nextsteppro.climbing.domain.settlement.PayoutSource;
import pl.nextsteppro.climbing.domain.settlement.PayoutSourceRepository;
import pl.nextsteppro.climbing.domain.settlement.SessionPayoutRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.math.BigDecimal;
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
public class AdminPayoutService {

    private final PayoutSourceRepository sourceRepository;
    private final PayoutRepository payoutRepository;
    private final SessionPayoutRepository sessionPayoutRepository;
    private final AdminSettlementService settlementService;
    private final MessageService msg;

    public AdminPayoutService(PayoutSourceRepository sourceRepository,
                              PayoutRepository payoutRepository,
                              SessionPayoutRepository sessionPayoutRepository,
                              AdminSettlementService settlementService,
                              MessageService msg) {
        this.sourceRepository = sourceRepository;
        this.payoutRepository = payoutRepository;
        this.sessionPayoutRepository = sessionPayoutRepository;
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

        if (sourceId == null) {
            switch (target) {
                case SLOT -> sessionPayoutRepository.clearSlot(targetId);
                case EVENT -> sessionPayoutRepository.clearEvent(targetId);
            }
            return;
        }
        // Refuses the CHANGE, never the state: a session already priced per participant keeps those
        // amounts, and marking it in bulk would hide them while they went on counting.
        if (settlementService.hasPricedParticipants(target, targetId)) {
            throw new IllegalArgumentException(msg.get("admin.payout.session.has.amounts"));
        }
        requireSource(sourceId);
        switch (target) {
            case SLOT -> sessionPayoutRepository.assignSlot(targetId, sourceId);
            case EVENT -> sessionPayoutRepository.assignEvent(targetId, sourceId);
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
