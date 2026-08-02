package pl.nextsteppro.climbing.api.trainingcalendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import pl.nextsteppro.climbing.domain.athletegoal.AthleteGoal;
import pl.nextsteppro.climbing.domain.athletegoal.AthleteGoalRepository;
import pl.nextsteppro.climbing.domain.athletegoal.GoalHorizon;
import pl.nextsteppro.climbing.domain.athletegoal.GoalKind;
import pl.nextsteppro.climbing.domain.athleteweight.AthleteWeight;
import pl.nextsteppro.climbing.domain.athleteweight.AthleteWeightRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Body-weight tracking over real PostgreSQL (Flyway V74/V75). The DDL is the point here:
 * the one-reading-per-day unique index, the kind-aware active-goal slot, and the CHECK
 * constraints that keep a WEIGHT goal from existing without its two weight columns —
 * none of which a mocked repository can prove.
 *
 * <p>Lives in this package (not integration/) because the DTO records are package-private.
 */
class AthleteWeightIntegrationTest extends BaseIntegrationTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Autowired private AthleteWeightService weightService;
    @Autowired private AdminTrainingCalendarService adminService;
    @Autowired private AthleteWeightRepository weightRepository;
    @Autowired private AthleteGoalRepository goalRepository;

    private User athlete;
    private User coach;

    @BeforeEach
    void setUp() {
        goalRepository.deleteAll();
        weightRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        athlete = new User("athlete@example.com", "Anna", "Wspinaczka", "+48123456789", "anna");
        athlete.setRole(UserRole.USER);
        athlete.setEmailVerified(true);
        athlete.setAthlete(true);
        // Athlete-side endpoints sit behind the GDPR art. 9 consent gate (V76)
        athlete.grantTrainingConsent();
        athlete = userRepository.save(athlete);

        coach = new User("coach@example.com", "Trener", "Główny", "+48111111111", "coach");
        coach.setRole(UserRole.ADMIN);
        coach.setEmailVerified(true);
        coach = userRepository.save(coach);
    }

    private static LocalDate today() {
        return LocalDate.now(WARSAW);
    }

    private AthleteWeightSeriesDto weighIn(LocalDate day, String kg) {
        return weightService.recordMyWeight(athlete.getId(), new SaveWeightRequest(day, new BigDecimal(kg)));
    }

    // ---------- DDL guarantees ----------

    @Test
    void shouldRejectASecondReadingOnTheSameDay() {
        // Given: a reading exists for today
        weightRepository.saveAndFlush(new AthleteWeight(athlete, today(), new BigDecimal("70.0")));

        // When / Then: the DB refuses a second row — the service upserts instead
        assertThrows(DataIntegrityViolationException.class, () ->
            weightRepository.saveAndFlush(new AthleteWeight(athlete, today(), new BigDecimal("71.0"))));
    }

    @Test
    void shouldAllowATrainingGoalAndAWeightGoalToShareAHorizon() {
        // Given / When: the active-goal index now includes the kind
        goalRepository.saveAndFlush(
            new AthleteGoal(athlete, GoalHorizon.SHORT, "Przejść 7a", today().plusMonths(2)));
        goalRepository.saveAndFlush(AthleteGoal.weightGoal(athlete, GoalHorizon.SHORT, "Zejść do 67",
            today().plusMonths(2), new BigDecimal("67.0"), new BigDecimal("70.0")));

        // Then
        assertEquals(2, goalRepository.findByAthleteIdAndAchievedAtIsNull(athlete.getId()).size());
    }

    @Test
    void shouldStillRejectTwoActiveWeightGoalsOnTheSameHorizon() {
        // Given
        goalRepository.saveAndFlush(AthleteGoal.weightGoal(athlete, GoalHorizon.SHORT, "Zejść do 67",
            today().plusMonths(2), new BigDecimal("67.0"), new BigDecimal("70.0")));

        // When / Then
        assertThrows(DataIntegrityViolationException.class, () ->
            goalRepository.saveAndFlush(AthleteGoal.weightGoal(athlete, GoalHorizon.SHORT, "Zejść do 66",
                today().plusMonths(3), new BigDecimal("66.0"), new BigDecimal("70.0"))));
    }

    // ---------- the flow the feature exists for ----------

    @Test
    void shouldCloseAWeightGoalOnlyOnceThreeReadingsConfirmTheTrend() {
        // Given: a loss goal from 70 kg down to 69 kg
        weighIn(today().minusDays(3), "70.0");
        AthleteGoalDto goal = adminService.createGoal(coach.getId(), athlete.getId(),
            new SaveGoalRequest(GoalKind.WEIGHT, GoalHorizon.SHORT, "Zejść do 69 kg",
                today().plusMonths(1), new BigDecimal("69.0")));
        assertEquals(0, new BigDecimal("70.00").compareTo(requireStart(goal)));

        // When: a second reading already sits below the target
        weighIn(today().minusDays(1), "68.0");

        // Then: two readings are not enough — a lucky morning must not hand out a trophy
        AthleteGoal stored = goalRepository.findById(goal.id()).orElseThrow();
        assertFalse(stored.isAchieved());
        assertFalse(weightService.getMySeries(athlete.getId(), null).trendConfirmed());

        // When: the third reading confirms the trend
        weighIn(today(), "68.0");

        // Then: the goal closes itself, flagged as machine-closed
        AthleteGoal closed = goalRepository.findById(goal.id()).orElseThrow();
        assertTrue(closed.isAchieved());
        assertTrue(closed.isAchievedAutomatically());
        assertTrue(weightService.getMySeries(athlete.getId(), null).trendConfirmed());
    }

    @Test
    void shouldLetTheCoachUndoAnAutomaticClosureButNotAManualOne() {
        // Given: a weight goal that closed itself
        weighIn(today().minusDays(2), "70.0");
        AthleteGoalDto weightGoal = adminService.createGoal(coach.getId(), athlete.getId(),
            new SaveGoalRequest(GoalKind.WEIGHT, GoalHorizon.SHORT, "Zejść do 69 kg",
                today().plusMonths(1), new BigDecimal("69.0")));
        weighIn(today().minusDays(1), "68.0");
        weighIn(today(), "68.0");
        assertTrue(goalRepository.findById(weightGoal.id()).orElseThrow().isAchieved());

        // When: the coach undoes it (the weigh-in was a typo)
        AthleteGoalDto reopened = adminService.reopenGoal(coach.getId(), weightGoal.id());

        // Then: active again, and the slot is free
        assertNull(reopened.achievedAt());
        assertFalse(reopened.achievedAutomatically());

        // Given: a training goal the coach closed by hand
        AthleteGoalDto general = adminService.createGoal(coach.getId(), athlete.getId(),
            new SaveGoalRequest(null, GoalHorizon.LONG, "7c przed 30-tką", today().plusYears(1), null));
        adminService.achieveGoal(coach.getId(), general.id(), new AchieveGoalRequest(null));

        // When / Then: a human decision stays final
        assertThrows(IllegalStateException.class, () -> adminService.reopenGoal(coach.getId(), general.id()));
    }

    @Test
    void shouldTreatASecondWeighInOnTheSameDayAsACorrection() {
        // Given / When
        weighIn(today(), "70.0");
        AthleteWeightSeriesDto series = weighIn(today(), "71.5");

        // Then: one point on the chart, carrying the corrected value
        assertEquals(1, series.entries().size());
        assertEquals(0, new BigDecimal("71.50").compareTo(requireLatest(series)));
    }

    @Test
    void shouldNotReopenAnAchievedGoalWhenAReadingIsDeleted() {
        // Given: a goal closed by three readings
        weighIn(today().minusDays(2), "70.0");
        AthleteGoalDto goal = adminService.createGoal(coach.getId(), athlete.getId(),
            new SaveGoalRequest(GoalKind.WEIGHT, GoalHorizon.SHORT, "Zejść do 69 kg",
                today().plusMonths(1), new BigDecimal("69.0")));
        weighIn(today().minusDays(1), "68.0");
        weighIn(today(), "68.0");

        // When: the athlete removes one of the readings
        weightService.deleteMyWeight(athlete.getId(), today());

        // Then: the trophy stays — removing data never takes an achievement away
        assertTrue(goalRepository.findById(goal.id()).orElseThrow().isAchieved());
    }

    @Test
    void shouldRefuseAWeightGoalBeforeTheAthleteEverWeighedIn() {
        // Given: no readings at all
        UUID athleteId = athlete.getId();
        SaveGoalRequest request = new SaveGoalRequest(GoalKind.WEIGHT, GoalHorizon.SHORT, "Zejść do 67 kg",
            today().plusMonths(1), new BigDecimal("67.0"));

        // When / Then
        assertThrows(IllegalStateException.class, () -> adminService.createGoal(coach.getId(), athleteId, request));
    }

    private static BigDecimal requireStart(AthleteGoalDto dto) {
        return assertDoesNotThrow(() -> java.util.Objects.requireNonNull(dto.startWeightKg()));
    }

    private static BigDecimal requireLatest(AthleteWeightSeriesDto dto) {
        return assertDoesNotThrow(() -> java.util.Objects.requireNonNull(dto.latestWeightKg()));
    }
}
