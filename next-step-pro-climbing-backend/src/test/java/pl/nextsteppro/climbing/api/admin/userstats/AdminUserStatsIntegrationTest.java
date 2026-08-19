package pl.nextsteppro.climbing.api.admin.userstats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTraining;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Admin user statistics over real PostgreSQL.
 *
 * <p>Lives in this package because the DTO records are package-private.
 *
 * <p>Most of what is asserted here is a decision rather than plumbing, and every one of them is
 * easy to "tidy" into something wrong: that a cancelled booking does not make somebody a customer,
 * that a booking in the future counts as active but not as attended, that "never asked about the
 * newsletter" is not the same as "said no", and that the growth chart ships empty months rather
 * than leaving the client to invent them. The grouped query is exercised as a side effect — JPQL
 * constructor projections with {@code SUM(CASE …)} fail at runtime, not at compile time.
 */
class AdminUserStatsIntegrationTest extends BaseIntegrationTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Autowired private AdminUserStatsService service;
    @Autowired private PersonalTrainingRepository personalTrainingRepository;

    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        personalTrainingRepository.deleteAll();
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();
        // Fixtures are anchored to the Warsaw clock, never the JVM default: the runner is UTC and
        // the attended/upcoming split asks Warsaw.
        now = LocalDateTime.now(WARSAW);
    }

    private User account(String email) {
        User user = new User(email, "Jan", "Kowalski", "+48123456789", "janek");
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    /** A slot far enough from "now" that the boundary cannot be crossed while the test runs. */
    private TimeSlot slotOn(LocalDate date) {
        return timeSlotRepository.save(new TimeSlot(date, LocalTime.of(10, 0), LocalTime.of(12, 0), 10));
    }

    private Reservation booking(User user, TimeSlot slot) {
        return reservationRepository.save(new Reservation(user, slot));
    }

    @Test
    @DisplayName("shouldNotCountACancelledBookingAsBecomingACustomer")
    void shouldNotCountACancelledBookingAsBecomingACustomer() {
        User quitter = account("cancelled@example.com");
        Reservation reservation = booking(quitter, slotOn(now.toLocalDate().minusDays(7)));
        reservation.cancel();
        reservationRepository.save(reservation);

        UserStatsDto stats = service.buildStats(now);

        assertEquals(0, stats.funnel().booked(), "A cancelled booking is exactly the case where the account did not turn into a customer");
        assertEquals(1, stats.cohorts().never());
        assertEquals(0, stats.cohorts().active());
        assertTrue(stats.topClients().isEmpty());
    }

    @Test
    @DisplayName("shouldCountAFutureBookingAsActiveButNotAsAttended")
    void shouldCountAFutureBookingAsActiveButNotAsAttended() {
        User booked = account("future@example.com");
        booking(booked, slotOn(now.toLocalDate().plusDays(14)));

        UserStatsDto stats = service.buildStats(now);

        assertEquals(1, stats.funnel().booked());
        assertEquals(1, stats.cohorts().active(), "A booking in the future is the strongest sign of activity there is");
        assertEquals(0, stats.cohorts().dormant());
        assertEquals(0, stats.cohorts().never());
        assertTrue(stats.topClients().isEmpty(), "Top clients rank attendance — an upcoming booking must not buy a place");
    }

    @Test
    @DisplayName("shouldSplitBookersIntoActiveAndDormantByTheWindow")
    void shouldSplitBookersIntoActiveAndDormantByTheWindow() {
        User recent = account("recent@example.com");
        booking(recent, slotOn(now.toLocalDate().minusDays(10)));

        User longGone = account("gone@example.com");
        booking(longGone, slotOn(now.toLocalDate().minusDays(AdminUserStatsService.ACTIVE_WINDOW_DAYS + 5)));

        account("neverbooked@example.com");

        UserStatsDto stats = service.buildStats(now);

        assertEquals(1, stats.cohorts().active());
        assertEquals(1, stats.cohorts().dormant());
        assertEquals(1, stats.cohorts().never());
        assertEquals(stats.totals().accounts(),
            stats.cohorts().active() + stats.cohorts().dormant() + stats.cohorts().never(),
            "The three cohorts partition the base — a gap between them is a person nobody can see");
    }

    @Test
    @DisplayName("shouldRankTopClientsByAttendanceAndCountReturningCustomers")
    void shouldRankTopClientsByAttendanceAndCountReturningCustomers() {
        User regular = account("regular@example.com");
        booking(regular, slotOn(now.toLocalDate().minusDays(20)));
        booking(regular, slotOn(now.toLocalDate().minusDays(10)));
        booking(regular, slotOn(now.toLocalDate().minusDays(5)));

        User once = account("once@example.com");
        booking(once, slotOn(now.toLocalDate().minusDays(3)));

        UserStatsDto stats = service.buildStats(now);

        assertEquals(2, stats.funnel().booked());
        assertEquals(1, stats.funnel().returning(), "Returning means more than one confirmed booking, ever");

        List<TopClientDto> top = stats.topClients();
        assertEquals(2, top.size());
        assertEquals(regular.getId(), top.get(0).userId());
        assertEquals(3, top.get(0).attended());
        assertEquals(1, top.get(1).attended());
    }

    @Test
    @DisplayName("shouldTellDecliningTheNewsletterApartFromNeverBeingAsked")
    void shouldTellDecliningTheNewsletterApartFromNeverBeingAsked() {
        User subscriber = account("yes@example.com");
        subscriber.setNewsletterSubscribed(true);
        subscriber.setNewsletterChoiceMade(true);
        userRepository.save(subscriber);

        User decliner = account("no@example.com");
        decliner.setNewsletterChoiceMade(true);
        userRepository.save(decliner);

        account("neverasked@example.com");

        UserStatsDto stats = service.buildStats(now);

        assertEquals(1, stats.newsletter().subscribed());
        assertEquals(1, stats.newsletter().unsubscribed());
        assertEquals(1, stats.newsletter().undecided(), "Only the never-asked group is worth doing anything about");
        assertEquals(stats.totals().accounts(),
            stats.newsletter().subscribed() + stats.newsletter().unsubscribed() + stats.newsletter().undecided());
    }

    @Test
    @DisplayName("shouldCountAthletesByFlagAndByConsentSeparately")
    void shouldCountAthletesByFlagAndByConsentSeparately() {
        User consented = account("athlete@example.com");
        consented.setAthlete(true);
        consented.grantTrainingConsent();
        userRepository.save(consented);

        User flaggedOnly = account("newathlete@example.com");
        flaggedOnly.setAthlete(true);
        userRepository.save(flaggedOnly);

        User admin = account("admin@example.com");
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);

        UserStatsDto stats = service.buildStats(now);

        assertEquals(2, stats.athletes().flagged());
        assertEquals(1, stats.athletes().consented(), "The gap is the people who were given access and never opened it");
        assertEquals(2, stats.totals().athletes());
        assertEquals(1, stats.totals().admins());
    }

    /**
     * Revoking the flag leaves the plan rows behind on purpose, so a count over the whole table
     * counts calendars the coach can no longer open — and can exceed the flagged total, which the
     * card renders as a share of it ("150%").
     */
    @Test
    @DisplayName("shouldStopCountingAPlanOnceTheAthleteFlagIsRevoked")
    void shouldStopCountingAPlanOnceTheAthleteFlagIsRevoked() {
        User athlete = account("former@example.com");
        athlete.setAthlete(true);
        athlete.grantTrainingConsent();
        athlete = userRepository.save(athlete);
        personalTrainingRepository.save(new PersonalTraining(
            athlete, now.toLocalDate(), LocalTime.of(17, 0), LocalTime.of(19, 0),
            "Siła", null, true));

        assertEquals(1, service.buildStats(now).athletes().withPlan());

        athlete.setAthlete(false);
        userRepository.save(athlete);

        AthleteBreakdownDto athletes = service.buildStats(now).athletes();
        assertEquals(0, athletes.flagged());
        assertEquals(0, athletes.withPlan(), "The plan survives the revocation; the coach's access to it does not");
        assertTrue(athletes.withPlan() <= athletes.flagged(),
            "withPlan is rendered as a share of flagged, so it can never be the larger of the two");
    }

    /**
     * Both halves in one test on purpose: staff belong in the base but not in a ranking of that
     * base's customers, and "tidying" the filter to cover everything is exactly how the second
     * half breaks without anybody noticing.
     */
    @Test
    @DisplayName("shouldKeepStaffOutOfTheClientRankingButNotOutOfTheBase")
    void shouldKeepStaffOutOfTheClientRankingButNotOutOfTheBase() {
        User coach = account("coach@example.com");
        coach.setRole(UserRole.ADMIN);
        coach = userRepository.save(coach);
        booking(coach, slotOn(now.toLocalDate().minusDays(9)));
        booking(coach, slotOn(now.toLocalDate().minusDays(4)));

        User client = account("client@example.com");
        booking(client, slotOn(now.toLocalDate().minusDays(2)));

        UserStatsDto stats = service.buildStats(now);

        assertEquals(1, stats.topClients().size(), "The coach's own attendance is not a client ranking");
        assertEquals(client.getId(), stats.topClients().get(0).userId());

        assertEquals(2, stats.funnel().booked(), "Staff are still accounts in the base");
        assertEquals(2, stats.cohorts().active());
        assertEquals(0, stats.cohorts().never());
    }

    @Test
    @DisplayName("shouldShipEveryMonthOfTheGrowthChartIncludingEmptyOnes")
    void shouldShipEveryMonthOfTheGrowthChartIncludingEmptyOnes() {
        account("fresh@example.com");

        UserStatsDto stats = service.buildStats(now);

        List<MonthlyRegistrationsDto> bars = stats.registrations();
        assertEquals(AdminUserStatsService.REGISTRATION_MONTHS, bars.size());

        MonthlyRegistrationsDto last = bars.get(bars.size() - 1);
        assertEquals(YearMonth.from(now).atDay(1), last.month(), "The chart ends on the current month, not on the last month with data");
        assertEquals(1, last.total());
        assertEquals(1, last.verified());

        assertEquals(1, bars.get(0).month().getDayOfMonth(), "Bars are labelled by the first of the month so the client parses one date shape");
        // Everything but the current month is empty here, and must still be present: a month the
        // client has to fill in is a month the client can fill in wrong.
        assertEquals(0, bars.subList(0, bars.size() - 1).stream().mapToLong(MonthlyRegistrationsDto::total).sum());
    }
}
