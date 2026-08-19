package pl.nextsteppro.climbing.api.admin.userstats;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.UserBookingAggregate;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserAccountRow;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.domain.user.UserRole;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The admin's view of the user base as a whole: totals, growth, the funnel from account to
 * customer, and who actually shows up.
 *
 * <p><b>Counterpart to the user card, not a second copy of it.</b> {@code AdminUserHistoryService}
 * answers "who is this person"; this answers "what does the base look like". Nothing here is
 * per-user except the top-clients list, which exists to be clicked through to that card.
 *
 * <p><b>A fixed handful of queries, then one pass in Java.</b> Accounts and bookings each come back as one
 * projection, and every number is folded out of those two lists — the same shape as
 * {@code TrainingStatsService}, and for the same reason: a screen of a dozen counters asked as a
 * dozen aggregate queries is a dozen scans answering from a dozen moments, and the totals stop
 * adding up exactly when somebody registers mid-render.
 *
 * <p><b>No cache, deliberately.</b> Granting the athlete flag, deleting an account or cancelling a
 * booking has to move these numbers now; an admin who acts and sees the old figure has no way to
 * tell a stale cache from an action that did not take. The cost is three indexed reads over tables
 * this size.
 *
 * <p><b>Activity is measured in bookings because logins are not recorded.</b> There is no
 * last-login column, so the alternative to this definition is not a better cohort split — it is an
 * invented one. The window ships to the client so the screen can say which rule it applied.
 */
@Service
public class AdminUserStatsService {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    /** How far back a booking still counts as "active". Also shipped to the client as a label. */
    static final int ACTIVE_WINDOW_DAYS = 90;

    /** Bars on the growth chart, including the current (partial) month. */
    static final int REGISTRATION_MONTHS = 12;

    static final int TOP_CLIENTS = 10;

    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final PersonalTrainingRepository personalTrainingRepository;

    public AdminUserStatsService(UserRepository userRepository,
                                 ReservationRepository reservationRepository,
                                 PersonalTrainingRepository personalTrainingRepository) {
        this.userRepository = userRepository;
        this.reservationRepository = reservationRepository;
        this.personalTrainingRepository = personalTrainingRepository;
    }

    @Transactional(readOnly = true)
    public UserStatsDto buildStats() {
        return buildStats(LocalDateTime.now(WARSAW));
    }

    /**
     * Overload with "now" passed in, so tests can build a base around a fixed date instead of
     * around whatever day the CI runner happens to be on.
     */
    @Transactional(readOnly = true)
    public UserStatsDto buildStats(LocalDateTime nowWarsaw) {
        LocalDate today = nowWarsaw.toLocalDate();

        List<UserAccountRow> accounts = userRepository.findAccountRows();
        List<UserBookingAggregate> bookings =
            reservationRepository.aggregateConfirmedPerUser(today, nowWarsaw.toLocalTime());

        return new UserStatsDto(
            totals(accounts),
            registrations(accounts, YearMonth.from(today)),
            funnel(bookings),
            cohorts(accounts.size(), bookings, today),
            topClients(bookings, staffIds(accounts)),
            newsletter(accounts),
            athletes(accounts));
    }

    /**
     * Admin accounts, for the one place they are not customers.
     *
     * <p>They stay in the totals, the funnel and the cohorts — they are accounts in the base like
     * any other — but the coach booking onto their own sessions would otherwise top the ranking of
     * their own clients.
     */
    private Set<UUID> staffIds(List<UserAccountRow> accounts) {
        Set<UUID> ids = new HashSet<>();
        for (UserAccountRow row : accounts) {
            if (row.role() == UserRole.ADMIN) {
                ids.add(row.id());
            }
        }
        return ids;
    }

    private AccountTotalsDto totals(List<UserAccountRow> accounts) {
        long verified = 0;
        long athletes = 0;
        long newsletter = 0;
        long admins = 0;
        for (UserAccountRow row : accounts) {
            if (row.emailVerified()) verified++;
            if (row.athlete()) athletes++;
            if (row.newsletterSubscribed()) newsletter++;
            if (row.role() == UserRole.ADMIN) admins++;
        }
        return new AccountTotalsDto(accounts.size(), verified, athletes, newsletter, admins);
    }

    /**
     * The last {@link #REGISTRATION_MONTHS} months, oldest first, with empty months present as
     * zeroes.
     *
     * <p>The month an account belongs to is a Warsaw question asked of an {@code Instant}: a
     * registration just before midnight PL is the previous day in UTC, and on the first of the
     * month that is a different bar.
     */
    private List<MonthlyRegistrationsDto> registrations(List<UserAccountRow> accounts, YearMonth current) {
        YearMonth firstBar = current.minusMonths(REGISTRATION_MONTHS - 1L);

        Map<YearMonth, long[]> buckets = new HashMap<>();
        for (UserAccountRow row : accounts) {
            YearMonth month = YearMonth.from(row.createdAt().atZone(WARSAW));
            if (month.isBefore(firstBar) || month.isAfter(current)) {
                continue;
            }
            long[] bucket = buckets.computeIfAbsent(month, m -> new long[2]);
            bucket[0]++;
            if (row.emailVerified()) bucket[1]++;
        }

        List<MonthlyRegistrationsDto> bars = new ArrayList<>(REGISTRATION_MONTHS);
        for (int i = 0; i < REGISTRATION_MONTHS; i++) {
            YearMonth month = firstBar.plusMonths(i);
            long[] bucket = buckets.getOrDefault(month, new long[2]);
            bars.add(new MonthlyRegistrationsDto(month.atDay(1), bucket[0], bucket[1]));
        }
        return bars;
    }

    private FunnelDto funnel(List<UserBookingAggregate> bookings) {
        long returning = bookings.stream().filter(b -> b.confirmed() >= 2).count();
        return new FunnelDto(bookings.size(), returning);
    }

    private CohortsDto cohorts(int accounts, List<UserBookingAggregate> bookings, LocalDate today) {
        LocalDate cutoff = today.minusDays(ACTIVE_WINDOW_DAYS);
        long active = bookings.stream().filter(b -> !b.lastDate().isBefore(cutoff)).count();
        long dormant = bookings.size() - active;
        // Everyone without a row booked nothing — the query only returns people who did.
        //
        // Clamped because the two reads are separate statements under READ COMMITTED, so they see
        // two snapshots: an account deleted between them is counted here and gone from the
        // bookings, and the reverse ordering would put the screen's most prominent cohort below
        // zero. Cheaper than raising the isolation level of the whole request for one subtraction.
        long never = Math.max(0, accounts - bookings.size());
        return new CohortsDto(active, dormant, never, ACTIVE_WINDOW_DAYS);
    }

    /**
     * Ranked by attendance, ties broken by id so two people on the same count do not swap places
     * between reloads for no reason.
     */
    private List<TopClientDto> topClients(List<UserBookingAggregate> bookings, Set<UUID> staffIds) {
        List<UserBookingAggregate> ranked = bookings.stream()
            .filter(b -> b.attended() > 0)
            // Staff drop out BEFORE the cut, not after: filtering the finished top ten would leave
            // a hole where the coach was instead of letting the eleventh customer move up.
            .filter(b -> !staffIds.contains(b.userId()))
            .sorted(Comparator.comparingLong(UserBookingAggregate::attended).reversed()
                .thenComparing(UserBookingAggregate::userId))
            .limit(TOP_CLIENTS)
            .toList();

        if (ranked.isEmpty()) {
            return List.of();
        }

        Map<UUID, User> byId = new HashMap<>();
        for (User user : userRepository.findAllById(ranked.stream().map(UserBookingAggregate::userId).toList())) {
            byId.put(user.getId(), user);
        }

        List<TopClientDto> top = new ArrayList<>(ranked.size());
        for (UserBookingAggregate row : ranked) {
            User user = byId.get(row.userId());
            // A booking whose owner is gone cannot happen (reservations cascade with the account),
            // but reading the name off a missing row would white-screen the panel if it ever did.
            if (user != null) {
                top.add(new TopClientDto(user.getId(), user.getFirstName(), user.getLastName(), row.attended()));
            }
        }
        return top;
    }

    private NewsletterBreakdownDto newsletter(List<UserAccountRow> accounts) {
        long subscribed = 0;
        long undecided = 0;
        for (UserAccountRow row : accounts) {
            if (row.newsletterSubscribed()) {
                subscribed++;
            } else if (!row.newsletterChoiceMade()) {
                undecided++;
            }
        }
        return new NewsletterBreakdownDto(subscribed, accounts.size() - subscribed - undecided, undecided);
    }

    private AthleteBreakdownDto athletes(List<UserAccountRow> accounts) {
        long flagged = 0;
        long consented = 0;
        for (UserAccountRow row : accounts) {
            if (!row.athlete()) {
                continue;
            }
            flagged++;
            // Dropping the flag clears the consent (User.setAthlete), so this pair can never
            // count somebody whose access was revoked.
            if (row.trainingConsentAt() != null) consented++;
        }
        return new AthleteBreakdownDto(flagged, consented, personalTrainingRepository.countAthletesWithPlan());
    }
}
