package pl.nextsteppro.climbing.api.admin.settlement;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventRepository;
import pl.nextsteppro.climbing.domain.reservation.GuestReservation;
import pl.nextsteppro.climbing.domain.reservation.GuestReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationStatus;
import pl.nextsteppro.climbing.domain.settlement.PayerLastAmount;
import pl.nextsteppro.climbing.domain.settlement.Settlement;
import pl.nextsteppro.climbing.domain.settlement.SettlementRepository;
import pl.nextsteppro.climbing.domain.settlement.SettlementRow;
import pl.nextsteppro.climbing.domain.settlement.PayoutSource;
import pl.nextsteppro.climbing.domain.settlement.PayoutSourceRepository;
import pl.nextsteppro.climbing.domain.settlement.SessionPayoutRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Prices a calendar entry per participant and records who has paid.
 *
 * <p>Every operation is scoped to the pair (target, payer); no settlement is ever addressed by its
 * own id, so there is no branch in which the "does this row belong to this slot" comparison could
 * be forgotten. Same shape as {@code AdminNoteService}.
 *
 * <p><b>No cache and no activity log, both on purpose.</b> No cache because the admin must see his
 * own figure the moment he saves it, and because nothing here is added to the cached calendar DTOs.
 * No activity log because that log records actions that touch people — a price the client never
 * sees touches nobody — and a new {@code ActivityActionType} is a four-file change with a
 * white-screen failure mode.
 */
@Service
@Transactional
public class AdminSettlementService {

    private final SettlementRepository settlementRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final EventRepository eventRepository;
    private final ReservationRepository reservationRepository;
    private final GuestReservationRepository guestReservationRepository;
    private final UserRepository userRepository;
    private final SessionPayoutRepository sessionPayoutRepository;
    private final PayoutSourceRepository payoutSourceRepository;
    private final MessageService msg;

    public AdminSettlementService(SettlementRepository settlementRepository,
                                  TimeSlotRepository timeSlotRepository,
                                  EventRepository eventRepository,
                                  ReservationRepository reservationRepository,
                                  GuestReservationRepository guestReservationRepository,
                                  UserRepository userRepository,
                                  SessionPayoutRepository sessionPayoutRepository,
                                  PayoutSourceRepository payoutSourceRepository,
                                  MessageService msg) {
        this.settlementRepository = settlementRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.eventRepository = eventRepository;
        this.reservationRepository = reservationRepository;
        this.guestReservationRepository = guestReservationRepository;
        this.userRepository = userRepository;
        this.sessionPayoutRepository = sessionPayoutRepository;
        this.payoutSourceRepository = payoutSourceRepository;
        this.msg = msg;
    }

    /**
     * Everyone who can be charged for this entry, with what they have been charged so far.
     *
     * <p>The list is the union of three sets, not just the participants: confirmed bookings, guests,
     * and <em>existing settlements</em>. That third one is what keeps a payment visible after the
     * booking behind it is cancelled — dropping the row would make the money vanish from the screen
     * while it still counts in the monthly total, which is the worst of both readings.
     */
    @Transactional(readOnly = true)
    public SettlementSectionDto getSection(String targetSegment, UUID targetId) {
        SettlementTarget target = parseTarget(targetSegment);
        return switch (target) {
            case SLOT -> slotSection(requireSlot(targetId));
            case EVENT -> eventSection(requireEvent(targetId));
        };
    }

    public void save(String targetSegment, UUID targetId,
                     String payerSegment, UUID payerId,
                     SaveSettlementRequest request) {
        SettlementTarget target = parseTarget(targetSegment);
        SettlementPayer payer = parsePayer(payerSegment);
        requireTargetExists(target, targetId);

        BigDecimal amount = Settlement.normalizeAmount(
            request.amount(), msg.get("admin.settlement.amount.invalid"));
        LocalDate settledOn = request.settledOn();
        Instant now = Instant.now();

        // Single statement rather than read-then-save: a second tab or a double-click loses the race
        // on the partial unique index and surfaces as a 500. Overwriting is correct — the admin is
        // correcting his own figure.
        switch (payer) {
            case USER -> {
                requireChargeableUser(target, targetId, payerId);
                switch (target) {
                    case SLOT -> settlementRepository.upsertForSlotUser(targetId, payerId, amount, settledOn, now);
                    case EVENT -> settlementRepository.upsertForEventUser(targetId, payerId, amount, settledOn, now);
                }
            }
            case GUEST -> {
                requireGuestOfTarget(target, targetId, payerId);
                switch (target) {
                    case SLOT -> settlementRepository.upsertForSlotGuest(targetId, payerId, amount, settledOn, now);
                    case EVENT -> settlementRepository.upsertForEventGuest(targetId, payerId, amount, settledOn, now);
                }
            }
        }
    }

    /**
     * Idempotent: removing a settlement that is not there is a success, not a 404.
     *
     * <p><b>No target gate here, unlike read and write</b> — the same reasoning as
     * {@code AdminNoteService.deleteNote}. Deleting a figure cannot leak anything, and the statement
     * is scoped to (target, payer) so it can only match a row about that entry. Gating it would mean
     * the opposite of safety: a settlement whose booking has since been cancelled would become
     * uneditable and undeletable at once, leaving a sum nobody can correct.
     */
    public void delete(String targetSegment, UUID targetId, String payerSegment, UUID payerId) {
        SettlementTarget target = parseTarget(targetSegment);
        SettlementPayer payer = parsePayer(payerSegment);
        switch (payer) {
            case USER -> {
                switch (target) {
                    case SLOT -> settlementRepository.deleteForSlotUser(targetId, payerId);
                    case EVENT -> settlementRepository.deleteForEventUser(targetId, payerId);
                }
            }
            case GUEST -> {
                switch (target) {
                    case SLOT -> settlementRepository.deleteForSlotGuest(targetId, payerId);
                    case EVENT -> settlementRepository.deleteForEventGuest(targetId, payerId);
                }
            }
        }
    }

    /**
     * Settles a whole month of one person's sessions on the day they actually paid.
     *
     * <p>Covers everything they owe, not just what one screen listed — which is the same set, since
     * the outstanding list deliberately spans the whole history. Deliberately NO participation
     * guard: it can only touch rows that already exist, and a person who has since cancelled still
     * owes for the sessions they attended.
     */
    public int settleOutstanding(SettleOutstandingRequest request) {
        SettlementPayer payer = parsePayer(request.payerType());
        Instant now = Instant.now();
        return switch (payer) {
            case USER -> settlementRepository.settleAllForUser(request.payerId(), request.settledOn(), now);
            case GUEST -> settlementRepository.settleAllForGuest(request.payerId(), request.settledOn(), now);
        };
    }

    // ---------------------------------------------------------------- sections

    private SettlementSectionDto slotSection(TimeSlot slot) {
        UUID slotId = slot.getId();
        List<Line> lines = new ArrayList<>();
        addUserLines(lines, reservationRepository.findConfirmedByTimeSlotIds(List.of(slotId)));
        addGuestLines(lines, guestReservationRepository.findByTimeSlotId(slotId));
        return assemble(SettlementTarget.SLOT, slotId, slot.getDate(), lines,
            settlementRepository.findRowsForSlot(slotId));
    }

    private SettlementSectionDto eventSection(Event event) {
        UUID eventId = event.getId();
        List<UUID> slotIds = timeSlotRepository.findByEventId(eventId).stream().map(TimeSlot::getId).toList();

        List<Line> lines = new ArrayList<>();
        if (!slotIds.isEmpty()) {
            // ⚠️ One line per PERSON, not per booking row. A multi-day event books one reservation
            // per day, so this list holds days × participants and has to collapse by user — the same
            // collapse the settlement's (event, user) key already performs in the database.
            addUserLines(lines, reservationRepository.findConfirmedByTimeSlotIds(slotIds));
        }
        List<GuestReservation> guests = new ArrayList<>(guestReservationRepository.findByEventId(eventId));
        if (!slotIds.isEmpty()) {
            guests.addAll(guestReservationRepository.findByTimeSlotIds(slotIds));
        }
        addGuestLines(lines, guests);

        return assemble(SettlementTarget.EVENT, eventId, event.getStartDate(), lines,
            settlementRepository.findRowsForEvent(eventId));
    }

    /** Collapses reservations by user, keeping the largest headcount that person booked. */
    private void addUserLines(List<Line> lines, List<Reservation> reservations) {
        Map<UUID, Line> byUser = new LinkedHashMap<>();
        for (Reservation reservation : reservations) {
            UUID userId = reservation.getUser().getId();
            String name = displayName(reservation.getUser().getFirstName(), reservation.getUser().getLastName());
            Line existing = byUser.get(userId);
            int participants = existing == null
                ? reservation.getParticipants()
                : Math.max(existing.participants, reservation.getParticipants());
            byUser.put(userId, new Line(SettlementPayer.USER, userId, name, participants));
        }
        List<Line> sorted = new ArrayList<>(byUser.values());
        sorted.sort(Comparator.comparing(Line::name, String.CASE_INSENSITIVE_ORDER));
        lines.addAll(sorted);
    }

    private void addGuestLines(List<Line> lines, List<GuestReservation> guests) {
        List<Line> sorted = guests.stream()
            .map(guest -> new Line(SettlementPayer.GUEST, guest.getId(), guest.getNote(), guest.getParticipants()))
            .sorted(Comparator.comparing(Line::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
        lines.addAll(sorted);
    }

    /**
     * Folds saved amounts onto the participant list, then appends the settlements that no longer
     * match anybody bookable — those are the cancelled bookings that already paid.
     */
    private SettlementSectionDto assemble(SettlementTarget target, UUID targetId, LocalDate targetDate,
                                          List<Line> participants, List<SettlementRow> saved) {
        Map<UUID, SettlementRow> byPayer = new LinkedHashMap<>();
        for (SettlementRow row : saved) {
            UUID payerId = row.isGuest() ? row.guestId() : row.userId();
            if (payerId != null) {
                byPayer.put(payerId, row);
            }
        }
        Map<UUID, BigDecimal> suggestions = lastAmountsFor(participants, byPayer.keySet());

        List<SettlementLineDto> result = new ArrayList<>();
        for (Line line : participants) {
            SettlementRow row = byPayer.remove(line.payerId());
            result.add(new SettlementLineDto(
                segment(line.payer()), line.payerId(), line.name(), line.participants(), false,
                row == null ? null : row.amount(),
                row == null ? null : row.settledOn(),
                // Only offered where there is nothing yet — a prefill next to a figure the admin
                // already wrote reads as a second, competing amount.
                row == null ? suggestions.get(line.payerId()) : null));
        }
        for (SettlementRow orphan : byPayer.values()) {
            boolean guest = orphan.isGuest();
            UUID payerId = guest ? orphan.guestId() : orphan.userId();
            String note = orphan.guestNote();
            String name = guest
                ? (note == null ? "" : note)
                : displayName(orphan.firstName(), orphan.lastName());
            result.add(new SettlementLineDto(
                segment(guest ? SettlementPayer.GUEST : SettlementPayer.USER),
                Objects.requireNonNull(payerId), name, 1, true,
                orphan.amount(), orphan.settledOn(), null));
        }
        // A session settled in bulk has nobody to charge per head, so the section switches mode
        // rather than offering fields that would invent an amount.
        Optional<UUID> sourceId = switch (target) {
            case SLOT -> sessionPayoutRepository.findSourceIdForSlot(targetId);
            case EVENT -> sessionPayoutRepository.findSourceIdForEvent(targetId);
        };
        PayoutSource source = sourceId.flatMap(payoutSourceRepository::findById).orElse(null);

        return new SettlementSectionDto(targetDate, result,
            source == null ? null : source.getId(),
            source == null ? null : source.getName());
    }

    /**
     * Last charged amount per registered participant. Guests are one-offs with no history.
     *
     * <p>Only asked for people who still need it. This is the one query in the section that scans
     * the settlement history rather than one session's worth of rows, and it runs every time an
     * admin opens a slot — so a fully priced session, which is most of them once the week is done,
     * must not pay for a suggestion nothing will show.
     */
    private Map<UUID, BigDecimal> lastAmountsFor(List<Line> participants, Set<UUID> alreadyPriced) {
        List<UUID> userIds = participants.stream()
            .filter(line -> line.payer() == SettlementPayer.USER)
            .map(Line::payerId)
            .filter(userId -> !alreadyPriced.contains(userId))
            .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BigDecimal> byUser = new LinkedHashMap<>();
        for (PayerLastAmount last : settlementRepository.findLastAmountsForUsers(userIds)) {
            byUser.putIfAbsent(last.userId(), last.amount());
        }
        return byUser;
    }

    private static String displayName(@Nullable String firstName, @Nullable String lastName) {
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }

    /** The lower-case path segment the client uses to address this payer back. */
    private static String segment(SettlementPayer payer) {
        return payer.name().toLowerCase(Locale.ROOT);
    }

    private record Line(SettlementPayer payer, UUID payerId, String name, int participants) {}

    // ------------------------------------------------------------------ guards

    private SettlementTarget parseTarget(String segment) {
        return SettlementTarget.tryFrom(segment)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("admin.settlement.target.unknown")));
    }

    private SettlementPayer parsePayer(String segment) {
        return SettlementPayer.tryFrom(segment)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("admin.settlement.payer.unknown")));
    }

    /**
     * Parses the path segment and checks the session can actually be addressed, returning the target
     * so callers do not re-derive it. Shared with {@code AdminPayoutService} so both halves of the
     * money model agree on what an addressable session is — an event is one engagement however many
     * days it spans, and its per-day slots are bookkeeping nobody writes to.
     */
    SettlementTarget requireAddressableTarget(String targetSegment, UUID targetId) {
        SettlementTarget target = parseTarget(targetSegment);
        requireTargetExists(target, targetId);
        return target;
    }

    private void requireTargetExists(SettlementTarget target, UUID targetId) {
        switch (target) {
            case SLOT -> requireSlot(targetId);
            case EVENT -> requireEvent(targetId);
        }
    }

    /**
     * ⚠️ An event is priced ONCE however many days it spans, so a slot that belongs to one is
     * refused. Its per-day slots are bookkeeping the first booking creates; the admin never sees
     * them, so an amount written there would be a second, invisible price for the same event — and
     * the event's own settlement would silently disagree with it.
     */
    private TimeSlot requireSlot(UUID slotId) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("admin.settlement.target.not.found")));
        if (slot.belongsToEvent()) {
            throw new IllegalArgumentException(msg.get("admin.settlement.slot.belongs.to.event"));
        }
        return slot;
    }

    private Event requireEvent(UUID eventId) {
        return eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("admin.settlement.target.not.found")));
    }

    /**
     * ⚠️ Rejects the CHANGE, never the STATE — the invariant that runs through this whole domain.
     * A brand-new amount must name somebody actually booked on the entry, which catches the wrong
     * id before it becomes a phantom client in the revenue ranking. But an amount that <em>already
     * exists</em> stays editable even after that person cancels, because otherwise the money they
     * paid would be frozen in a row nobody can correct or remove.
     */
    private void requireChargeableUser(SettlementTarget target, UUID targetId, UUID userId) {
        boolean alreadySettled = switch (target) {
            case SLOT -> settlementRepository.existsForSlotUser(targetId, userId);
            case EVENT -> settlementRepository.existsForEventUser(targetId, userId);
        };
        if (alreadySettled) {
            return;
        }
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException(msg.get("admin.settlement.payer.not.found"));
        }
        boolean participates = switch (target) {
            case SLOT -> reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(
                userId, targetId, ReservationStatus.CONFIRMED);
            case EVENT -> !reservationRepository.findUserParticipantsForEvent(userId, targetId).isEmpty();
        };
        if (!participates) {
            throw new IllegalArgumentException(msg.get("admin.settlement.payer.not.participant"));
        }
    }

    /**
     * A guest row cannot be cancelled — it is deleted — so unlike a user this is checked on every
     * write. Both attachments count: the booking path hangs guests on the event, an admin adding one
     * from a day view hangs them on that day's slot.
     */
    private void requireGuestOfTarget(SettlementTarget target, UUID targetId, UUID guestId) {
        GuestReservation guest = guestReservationRepository.findById(guestId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("admin.settlement.payer.not.found")));
        boolean belongs = switch (target) {
            case SLOT -> guest.getTimeSlot() != null && targetId.equals(guest.getTimeSlot().getId());
            case EVENT -> (guest.getEvent() != null && targetId.equals(guest.getEvent().getId()))
                || (guest.getTimeSlot() != null && guest.getTimeSlot().getEvent() != null
                    && targetId.equals(guest.getTimeSlot().getEvent().getId()));
        };
        if (!belongs) {
            throw new IllegalArgumentException(msg.get("admin.settlement.payer.not.participant"));
        }
    }
}
