package pl.nextsteppro.climbing.api.admin.settlement;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.admin.GuestMoneyHandover;
import pl.nextsteppro.climbing.domain.settlement.PaymentRepository;
import pl.nextsteppro.climbing.domain.settlement.SettlementRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.time.Instant;
import java.util.UUID;

/**
 * The money half of turning a guest into a registered participant. Package-private: the only way in
 * is the {@link GuestMoneyHandover} interface, so no other package can name this class.
 *
 * <p>Nothing is recomputed. The charge keeps its target and amount, the payments keep their amounts
 * and {@code received_on}; only the payer column changes. From then on {@code PaymentAllocator}
 * treats those payments like any other of the account's — FIFO across all of its charges, which is
 * the same rule every other payment of that person already follows.
 */
@Service
@Transactional
class GuestMoneyHandoverService implements GuestMoneyHandover {

    private final SettlementRepository settlementRepository;
    private final PaymentRepository paymentRepository;
    private final MessageService msg;

    GuestMoneyHandoverService(SettlementRepository settlementRepository,
                              PaymentRepository paymentRepository,
                              MessageService msg) {
        this.settlementRepository = settlementRepository;
        this.paymentRepository = paymentRepository;
        this.msg = msg;
    }

    @Override
    public void requireHandoverPossible(UUID guestId, UUID userId) {
        if (settlementRepository.existsUserChargeOnGuestTarget(guestId, userId)) {
            throw new IllegalStateException(msg.get("admin.guest.assign.already.charged"));
        }
    }

    @Override
    public void handOver(UUID guestId, UUID userId) {
        // Asked again: the unique index would otherwise answer with a 500-looking constraint error.
        requireHandoverPossible(guestId, userId);
        settlementRepository.handOverGuestToUser(guestId, userId, Instant.now());
        paymentRepository.handOverGuestToUser(guestId, userId);
    }
}
