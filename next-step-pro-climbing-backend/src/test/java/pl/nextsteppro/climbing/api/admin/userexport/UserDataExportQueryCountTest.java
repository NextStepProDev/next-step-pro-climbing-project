package pl.nextsteppro.climbing.api.admin.userexport;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.nextsteppro.climbing.domain.activitylog.ActivityLogRepository;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTraining;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingComment;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The export reads a whole account, so its cost must be flat in the number of rows. Same method as
 * the user card's test: count prepared statements, with a budget that is a constant rather than a
 * function of {@code ROWS} — {@code default_batch_fetch_size} turns an N+1 into a gentle slope that
 * only a fixed ceiling exposes.
 */
class UserDataExportQueryCountTest extends BaseIntegrationTest {

    private static final int ROWS = 48;
    /** Measured at 18: one read per section, the user, the RPE and attachment/file batches, the admin
     * and the log insert. One query of slack and no more — dropping a single JOIN FETCH costs only
     * two batched round trips at this size, and a looser ceiling would swallow them. */
    private static final int MAX_QUERIES = 19;

    @Autowired private UserDataExportService service;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private EntityManager entityManager;
    @Autowired private PersonalTrainingRepository trainingRepository;
    @Autowired private TrainingCommentRepository commentRepository;
    @Autowired private ActivityLogRepository activityLogRepository;

    private User user;
    private User admin;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
        trainingRepository.deleteAll();
        activityLogRepository.deleteAll();
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        eventRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        user = new User("licznik@example.com", "Jan", "Kowalski", "+48123456789", "janek");
        user.setRole(UserRole.USER);
        user.setEmailVerified(true);
        user = userRepository.save(user);
        admin = new User("admin-licznik@example.com", "Ad", "Min", "+48123456780", "admin");
        admin.setRole(UserRole.ADMIN);
        admin.setEmailVerified(true);
        admin = userRepository.save(admin);

        LocalDate base = LocalDate.of(2026, 1, 1);
        for (int i = 1; i <= ROWS; i++) {
            LocalDate date = base.plusDays(i);
            // Half the slots hang off an event, so the title has to reach for event.getTitle().
            Event event = i % 2 == 0
                ? eventRepository.save(new Event("Kurs " + i, EventType.COURSE, date, date, 10))
                : null;
            TimeSlot slot = timeSlotRepository.save(event != null
                ? new TimeSlot(event, date, LocalTime.of(18, 0), LocalTime.of(20, 0), 10)
                : new TimeSlot(date, LocalTime.of(18, 0), LocalTime.of(20, 0), 10));
            reservationRepository.save(new Reservation(user, slot));

            PersonalTraining training = trainingRepository.save(
                new PersonalTraining(user, date, null, null, "Trening " + i, null, true));
            commentRepository.save(TrainingComment.onTraining(training, admin, true, "Wiadomość " + i));
        }
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("shouldNotScaleQueryCountWithTheSizeOfTheAccount")
    void shouldNotScaleQueryCountWithTheSizeOfTheAccount() {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        UserDataExportDto export = service.export(user.getId(), admin.getId()).orElseThrow();

        long queries = stats.getPrepareStatementCount();
        System.out.println(">>> export queries=" + queries);
        assertTrue(queries > 0, "Hibernate statistics collected nothing — the measurement is broken, not the code");
        assertEquals(ROWS, export.reservations().size());
        assertEquals(ROWS, export.trainingPlan().size());
        assertEquals(ROWS, export.messages().size());
        assertTrue(queries <= MAX_QUERIES,
            "export ran " + queries + " queries for " + ROWS + " rows per section (budget " + MAX_QUERIES
                + "). Something dereferences a LAZY association per row.");
    }
}
