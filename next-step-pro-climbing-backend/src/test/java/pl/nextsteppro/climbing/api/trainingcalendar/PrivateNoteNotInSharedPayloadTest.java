package pl.nextsteppro.climbing.api.trainingcalendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTraining;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The coach's private note must never appear in the payload the ATHLETE receives.
 *
 * <p>{@code PrivateNoteIsolationTest} guards the same rule from the other side, by keeping the note
 * type unreachable outside its own two packages. This one is the direct evidence: it takes the real
 * shared record — one {@code CalendarRangeDto} serves both the coach and the athlete — serialises it
 * the way the browser would receive it, and looks for the text.
 *
 * <p>The assertion runs on the SERIALISED JSON rather than on the record's components on purpose,
 * the same way {@code AdminUserHistoryIntegrationTest} guards the user card: a field somebody adds
 * later reaches the browser whether or not they remembered this test existed.
 *
 * <p>Lives in this package because {@code CalendarRangeDto} is package-private.
 */
class PrivateNoteNotInSharedPayloadTest extends BaseIntegrationTest {

    private static final String SECRET = "Marek dalej boi sie wyklepania, nie mowic mu tego wprost";

    @Autowired private TrainingCalendarService calendarService;
    @Autowired private PersonalTrainingRepository trainingRepository;
    @Autowired private JdbcTemplate jdbc;

    private User athlete;
    private User coach;
    private PersonalTraining training;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM admin_private_notes");
        trainingRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        athlete = new User("athlete@example.com", "Anna", "Wspinaczka", "+48123456789", "anna");
        athlete.setRole(UserRole.USER);
        athlete.setEmailVerified(true);
        athlete.setAthlete(true);
        athlete.grantTrainingConsent();
        athlete = userRepository.saveAndFlush(athlete);

        coach = new User("coach@example.com", "Trener", "Glowny", "+48111111111", "coach");
        coach.setRole(UserRole.ADMIN);
        coach.setEmailVerified(true);
        coach = userRepository.saveAndFlush(coach);

        training = trainingRepository.saveAndFlush(new PersonalTraining(
            athlete, LocalDate.now(), LocalTime.of(18, 0), LocalTime.of(20, 0),
            "Wytrzymalosc", "Opis widoczny dla obu stron", true));

        // Written straight into the table rather than through AdminNoteService: this test is about
        // what leaves the calendar, and it must keep working even if the note's own API moves.
        jdbc.update("INSERT INTO admin_private_notes (author_id, training_id, body) VALUES (?, ?, ?)",
            coach.getId(), training.getId(), SECRET);

        // Without this, a fixture that silently failed to write would make every assertion below
        // pass for the wrong reason: "the note is not in the payload" is only evidence when there
        // is a note to leak.
        assertEquals(SECRET, jdbc.queryForObject(
            "SELECT body FROM admin_private_notes WHERE training_id = ?", String.class, training.getId()));
    }

    private String serialisedRange(boolean asCoach) throws Exception {
        CalendarRangeDto range = asCoach
            ? calendarService.getRangeForAthlete(coach.getId(), athlete.getId(),
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1))
            : calendarService.getMyRange(athlete.getId(),
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

        return new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(range);
    }

    @Test
    @DisplayName("shouldNotSerialiseThePrivateNoteIntoTheAthletesCalendar")
    void shouldNotSerialiseThePrivateNoteIntoTheAthletesCalendar() throws Exception {
        String json = serialisedRange(false);

        assertFalse(json.contains(SECRET),
            "The athlete's own calendar carried the coach's private note — it must never leave "
                + "the note's own admin endpoint");
        assertFalse(json.toLowerCase().contains("privatenote"),
            "A private-note field appeared on a record the athlete reads");

        // ...while the entry itself is still there, so a passing test means "no note", not "no data"
        assertTrue(json.contains("Wytrzymalosc"));
        assertTrue(json.contains("Opis widoczny dla obu stron"));
    }

    @Test
    @DisplayName("shouldNotSerialiseThePrivateNoteIntoTheCoachsCalendarEither")
    void shouldNotSerialiseThePrivateNoteIntoTheCoachsCalendarEither() throws Exception {
        // The coach may read this note — but through its own endpoint, not by having it folded
        // into a record the athlete also receives. One shape, one audience, no per-role variants.
        String json = serialisedRange(true);

        assertFalse(json.contains(SECRET));
        assertTrue(json.contains("Wytrzymalosc"));
    }
}
