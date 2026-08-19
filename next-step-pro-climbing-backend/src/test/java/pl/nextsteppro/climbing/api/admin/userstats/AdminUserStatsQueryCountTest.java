package pl.nextsteppro.climbing.api.admin.userstats;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The statistics screen reads the WHOLE base — every account and every confirmed booking — so its
 * cost has to be flat in the size of that base, not linear in it.
 *
 * <p>This is the gate the feature would otherwise lack. Both aggregates are grouped in SQL today,
 * but the shape that replaces them under maintenance is always the same: a loop calling one of the
 * per-user counters that already exist next door ({@code countConfirmedByUserId},
 * {@code countCompletedTrainings}). That reads perfectly well and turns one query into one per
 * account, which nothing else in the build would notice.
 *
 * <p>The budget is a <b>constant</b>, deliberately, not a function of {@code ACCOUNTS}: a ceiling
 * that grows with the fixture is a ceiling a per-account cost fits under.
 */
class AdminUserStatsQueryCountTest extends BaseIntegrationTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final int ACCOUNTS = 30;

    /**
     * Accounts, bookings, athletes-with-a-plan and the ≤10 names behind the ranking — one query
     * each, plus slack for Spring Data's own round trips.
     */
    private static final int MAX_QUERIES = 6;

    @Autowired private AdminUserStatsService service;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private EntityManager entityManager;

    private LocalDateTime now;

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        now = LocalDateTime.now(WARSAW);
        for (int i = 1; i <= ACCOUNTS; i++) {
            User user = new User("stats" + i + "@example.com", "Jan", "Kowalski", "+48123456789", "janek" + i);
            user.setEmailVerified(true);
            user = userRepository.save(user);

            // Enough people in the ranking that a per-name lookup would show as a slope, and a
            // spread of dates so the cohorts and the growth chart both have something to fold.
            LocalDate date = now.toLocalDate().minusDays(i);
            TimeSlot slot = timeSlotRepository.save(
                new TimeSlot(date, LocalTime.of(18, 0), LocalTime.of(20, 0), 10));
            reservationRepository.save(new Reservation(user, slot));
        }
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("shouldNotScaleQueryCountWithTheSizeOfTheUserBase")
    void shouldNotScaleQueryCountWithTheSizeOfTheUserBase() {
        Statistics stats = statistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        UserStatsDto result = service.buildStats(now);

        long queries = stats.getPrepareStatementCount();
        assertTrue(queries > 0, "Hibernate statistics collected nothing — the measurement is broken, not the code");
        assertEquals(ACCOUNTS, result.totals().accounts(),
            "fixture did not land as expected: " + result.totals().accounts() + " accounts");
        assertEquals(AdminUserStatsService.TOP_CLIENTS, result.topClients().size(),
            "the ranking has to be full, or a per-name lookup would have nothing to scale with");

        assertTrue(queries <= MAX_QUERIES,
            "buildStats ran " + queries + " queries for " + ACCOUNTS + " accounts (budget " + MAX_QUERIES
                + "). Something asks the database once per account — the aggregates belong in the "
                + "grouped queries, not in a loop over the rows.");
    }
}
