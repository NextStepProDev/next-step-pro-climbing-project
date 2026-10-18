package pl.nextsteppro.climbing.api.trainingcalendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.nextsteppro.climbing.domain.athletegoal.GoalHorizon;
import pl.nextsteppro.climbing.domain.athletegoal.GoalKind;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The coach side of the GDPR art. 9 consent gate.
 *
 * <p>⚠️ The flag used to be the coach's whole gate. Un-flagging clears consent and the privacy
 * policy says the calendar "stops being available" until it is given again — but re-flagging opened
 * the athlete's weight, RPE, feedback and conversation to the coach at once, before the athlete had
 * clicked anything. Owner's decision (2026-10-18): the plan stays workable, the health data does not.
 */
class CoachConsentGateIntegrationTest extends BaseIntegrationTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Autowired private TrainingCalendarService calendar;
    @Autowired private AdminTrainingCalendarService coachSide;
    @Autowired private AthleteWeightService weights;
    @Autowired private AthleteGoalService goals;
    @Autowired private PersonalTrainingRepository trainingRepository;
    @Autowired private TrainingCommentRepository commentRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private User athlete;
    private User coach;
    private UUID completedId;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM athlete_goals");
        jdbc.update("DELETE FROM athlete_weights");
        commentRepository.deleteAll();
        trainingRepository.deleteAll();
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        User a = new User("athlete@example.com", "Anna", "Wspinaczka", "+48123456789", "anna");
        a.setRole(UserRole.USER);
        a.setEmailVerified(true);
        a.setAthlete(true);
        a.grantTrainingConsent();
        athlete = userRepository.save(a);

        User c = new User("coach@example.com", "Trener", "Główny", "+48111111111", "coach");
        c.setRole(UserRole.ADMIN);
        c.setEmailVerified(true);
        coach = userRepository.save(c);

        // A history worth protecting, written while consent stood: a completed session with an RPE
        // and a note about the body, a weigh-in, a message, a weight goal.
        day = LocalDate.now(WARSAW).minusDays(2);
        completedId = calendar.createMy(athlete.getId(), new CreatePersonalTrainingRequest(
            day, LocalTime.of(18, 0), LocalTime.of(19, 0), "Siła", null)).id();
        calendar.complete(athlete.getId(), completedId, new CompleteTrainingRequest("kolano boli", 8));
        weights.recordMyWeight(athlete.getId(), new SaveWeightRequest(day, new BigDecimal("62.4")));
        calendar.addMyComment(athlete.getId(), completedId, "Źle spałam");
        goals.createGoal(athlete.getId(), new SaveGoalRequest(GoalKind.WEIGHT, GoalHorizon.SHORT,
            "Do zawodów", day.plusMonths(2), new BigDecimal("60.0")));
        goals.createGoal(athlete.getId(), new SaveGoalRequest(GoalKind.GENERAL, GoalHorizon.SHORT,
            "Poprowadzić 7a", day.plusMonths(2), null));

        // Un-flagged and flagged back: consent is gone, the data is not.
        athlete.setAthlete(false);
        athlete = userRepository.save(athlete);
        athlete.setAthlete(true);
        athlete = userRepository.save(athlete);
        assertFalse(athlete.hasTrainingConsent());
    }

    @Test
    void shouldShowThePlanButNotWhatTheAthleteSaidAboutTheirBody() {
        CalendarRangeDto range = coachSide.getRangeForAthlete(coach.getId(), athlete.getId(), day, day);

        assertTrue(range.healthDataHidden(), "The screen has to be able to say why");
        PersonalTrainingDto entry = range.trainings().getFirst();
        assertEquals("Siła", entry.title());
        assertNull(entry.rpe());
        assertNull(entry.feedback());
    }

    @Test
    void shouldRefuseTheWeightSeriesStatsAndConversationUntilConsent() {
        UUID athleteId = athlete.getId();
        UUID coachId = coach.getId();
        assertThrows(IllegalStateException.class, () -> weights.getSeriesForAthlete(athleteId, null));
        assertThrows(IllegalStateException.class, () -> coachSide.getStatsForAthlete(athleteId));
        assertThrows(IllegalStateException.class, () -> coachSide.getComments(coachId, completedId));
        assertThrows(IllegalStateException.class, () -> coachSide.addComment(coachId, completedId, "Jak kolano?"));
    }

    @Test
    void shouldHideWeightGoalsButKeepTheOthers() {
        GoalsDto visible = coachSide.getGoals(athlete.getId());

        assertEquals(1, visible.active().size());
        assertEquals("GENERAL", visible.active().getFirst().kind());
        assertThrows(IllegalStateException.class, () -> goals.createGoal(athlete.getId(),
            new SaveGoalRequest(GoalKind.WEIGHT, GoalHorizon.MEDIUM, "Niżej", day.plusMonths(4),
                new BigDecimal("59.0"))));
    }

    @Test
    void shouldStillLetTheCoachLayOutThePlan() {
        // The point of the split: a new athlete's first weeks can be prepared before they log in.
        assertDoesNotThrow(() -> coachSide.createForAthlete(coach.getId(), athlete.getId(),
            new CreatePersonalTrainingRequest(day.plusDays(5), LocalTime.of(18, 0), LocalTime.of(19, 0),
                "Technika", null)));
    }

    @Test
    void shouldOpenEverythingAgainOnceTheAthleteConsents() {
        calendar.grantConsent(athlete.getId());

        CalendarRangeDto range = coachSide.getRangeForAthlete(coach.getId(), athlete.getId(), day, day);
        assertFalse(range.healthDataHidden());
        assertEquals(8, range.trainings().getFirst().rpe());
        assertEquals(1, coachSide.getComments(coach.getId(), completedId).size());
        assertEquals(1, weights.getSeriesForAthlete(athlete.getId(), null).entries().size());
    }
}
