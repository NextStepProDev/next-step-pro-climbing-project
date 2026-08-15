package pl.nextsteppro.climbing.api.ascent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.nextsteppro.climbing.domain.climbingascent.AscentDiscipline;
import pl.nextsteppro.climbing.domain.climbingascent.AscentStyle;
import pl.nextsteppro.climbing.domain.climbingascent.AscentTerrain;
import pl.nextsteppro.climbing.domain.climbingascent.ClimbingAscentRepository;
import pl.nextsteppro.climbing.domain.climbingascent.ClimbingGrade;
import org.springframework.cache.CacheManager;
import pl.nextsteppro.climbing.api.user.UserService;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The climbing logbook over real PostgreSQL (Flyway V82). The DDL is the point: the CHECKs that
 * keep a boulder off the route scale, keep pinkpoint out of bouldering and keep "onsight in four
 * goes" from existing are invisible to a mocked repository — and so is the deliberate ABSENCE of
 * a unique constraint, which is what lets the same route be logged twice in one day.
 *
 * <p>Lives in this package (not integration/) because the DTO records are package-private.
 */
class AscentIntegrationTest extends BaseIntegrationTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Autowired private AscentService ascentService;
    @Autowired private PublicAscentService publicAscentService;
    @Autowired private AscentStatsService ascentStatsService;
    @Autowired private UserService userService;
    @Autowired private CacheManager cacheManager;
    @Autowired private ClimbingAscentRepository ascentRepository;
    @Autowired private JdbcTemplate jdbc;

    private User athlete;

    @BeforeEach
    void setUp() {
        // The feed is cached and the context is shared across tests; deleting rows straight
        // through the repository does not go past the @CacheEvict that production writes do
        Objects.requireNonNull(cacheManager.getCache(PublicAscentService.CACHE)).clear();
        ascentRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        athlete = new User("climber@example.com", "Anna", "Wspinaczka", "+48123456789", "anna");
        athlete.setRole(UserRole.USER);
        athlete.setEmailVerified(true);
        athlete.setAthlete(true);
        // Athlete-side endpoints sit behind the GDPR art. 9 consent gate (V76)
        athlete.grantTrainingConsent();
        athlete = userRepository.save(athlete);
    }

    private static LocalDate today() {
        return LocalDate.now(WARSAW);
    }

    private SaveAscentRequest sportRequest(String routeName, ClimbingGrade grade, AscentStyle style) {
        return new SaveAscentRequest(null, today(), AscentDiscipline.SPORT, grade, style,
            "Jura Północna", "Dolina Bolechowicka", routeName, null, null, null,
            null, null, null, null, null, null, null, null);
    }

    /** Bypasses the entity's own guards to prove the constraint, not the setter, is doing the work. */
    private void rawInsert(String discipline, String grade, String style, Integer attempts) {
        jdbc.update("""
            INSERT INTO climbing_ascents
              (athlete_id, climbed_on, discipline, grade, style, area, crag, route_name,
               area_key, crag_key, attempts)
            VALUES (?, ?, ?, ?, ?, 'Jura', 'Skala', 'Droga', 'jura', 'skala', ?)
            """, athlete.getId(), today(), discipline, grade, style, attempts);
    }

    // ---------- DDL guarantees ----------

    @Test
    @DisplayName("a boulder cannot carry a route grade — chk_climbing_ascents_grade_scale")
    void shouldRejectABoulderGradedOnTheRouteScale() {
        assertThrows(DataIntegrityViolationException.class,
            () -> rawInsert("BOULDER", "FR_7A", "RP", null));
    }

    @Test
    void shouldRejectARouteGradedOnTheBoulderScale() {
        assertThrows(DataIntegrityViolationException.class,
            () -> rawInsert("SPORT", "FB_7A", "RP", null));
    }

    @Test
    @DisplayName("nothing to hang a rope on in bouldering — chk_climbing_ascents_boulder_style")
    void shouldRejectTopropeOnABoulder() {
        assertThrows(DataIntegrityViolationException.class,
            () -> rawInsert("BOULDER", "FB_7A", "TR", null));
    }

    @Test
    @DisplayName("pinkpoint no longer exists — chk_climbing_ascents_style")
    void shouldRejectPinkpointAnywhere() {
        assertThrows(DataIntegrityViolationException.class,
            () -> rawInsert("SPORT", "FR_7A", "PP", null));
    }

    @Test
    @DisplayName("no rope to hang down an alpine route — chk_climbing_ascents_mountain_style")
    void shouldRejectTopropeInTheMountains() {
        SaveAscentRequest topropeInTheAlps = new SaveAscentRequest(AscentTerrain.MOUNTAIN, today(),
            null, ClimbingGrade.FR_5A, AscentStyle.TR, "Tatry", "Mnich", "Droga", null, null, null,
            false, null, null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class,
            () -> ascentService.createMyAscent(athlete.getId(), topropeInTheAlps));
    }

    @Test
    @DisplayName("A0 is a mountain style — on a crag the same day is a redpoint attempt")
    void shouldAcceptAidInTheMountainsOnly() {
        AscentDto mountain = ascentService.createMyAscent(athlete.getId(),
            new SaveAscentRequest(AscentTerrain.MOUNTAIN, today(), null, ClimbingGrade.FR_5A,
                AscentStyle.A0, "Dolina Rybiego Potoku", "Mnich", "Klasyczna", null, null, null,
                false, null, null, null, null, null, null, null));
        assertEquals("A0", mountain.style());

        assertThrows(IllegalArgumentException.class, () -> ascentService.createMyAscent(
            athlete.getId(), sportRequest("Skalna", ClimbingGrade.FR_6A, AscentStyle.A0)));
    }

    @Test
    void shouldAcceptFreeSoloOnBothTerrains() {
        AscentDto rock = ascentService.createMyAscent(athlete.getId(),
            sportRequest("Na Żywca", ClimbingGrade.FR_6A, AscentStyle.FREE_SOLO));
        AscentDto mountain = ascentService.createMyAscent(athlete.getId(),
            new SaveAscentRequest(AscentTerrain.MOUNTAIN, today(), null, ClimbingGrade.FR_5A,
                AscentStyle.FREE_SOLO, "Tatry", "Mnich", "Grań", null, null, null,
                false, null, null, null, null, null, null, null));

        assertEquals("FREE_SOLO", rock.style());
        assertEquals("FREE_SOLO", mountain.style());
    }

    @Test
    @DisplayName("'onsight in four goes' contradicts itself — chk_climbing_ascents_first_try")
    void shouldRejectAnOnsightWithMoreThanOneAttempt() {
        assertThrows(DataIntegrityViolationException.class,
            () -> rawInsert("SPORT", "FR_7A", "OS", 4));
    }

    @Test
    void shouldRejectZeroAttempts() {
        assertThrows(DataIntegrityViolationException.class,
            () -> rawInsert("SPORT", "FR_7A", "RP", 0));
    }

    @Test
    @DisplayName("the same route twice in one day is legal — warm-up on toprope, then the redpoint")
    void shouldAllowTheSameRouteTwiceOnOneDay() {
        // Given: a toprope lap
        ascentService.createMyAscent(athlete.getId(),
            sportRequest("Pajęczyna", ClimbingGrade.FR_6C, AscentStyle.TR));

        // When: the same route goes down clean later that day
        ascentService.createMyAscent(athlete.getId(),
            sportRequest("Pajęczyna", ClimbingGrade.FR_6C, AscentStyle.RP));

        // Then: both are kept — there is no unique constraint, on purpose
        assertEquals(2, ascentRepository.countByAthleteIdAndTerrain(athlete.getId(), AscentTerrain.ROCK));
    }

    // ---------- the gate ----------

    /**
     * The logbook holds no health data, so it is behind neither the athlete flag nor the GDPR
     * art. 9 consent that gates the training calendar. Anyone with an account keeps their own.
     */
    @Test
    @DisplayName("a plain user — no athlete flag, no consent — can keep a logbook")
    void shouldLetAnyLoggedInUserKeepALogbook() {
        User plain = new User("plain@example.com", "Zwykły", "Użytkownik", "+48333333333", "pass");
        plain.setEmailVerified(true);
        User saved = userRepository.save(plain);

        AscentDto logged = ascentService.createMyAscent(saved.getId(),
            sportRequest("Pierwsza Droga", ClimbingGrade.FR_6A, AscentStyle.RP));

        assertEquals("Pierwsza Droga", logged.routeName());
        assertEquals(1, ascentService.getMyLog(saved.getId(), AscentTerrain.ROCK, null).entries().size());
    }

    @Test
    void shouldNotRequireTrainingConsentToRead() {
        User withoutConsent = new User("noconsent@example.com", "Bez", "Zgody", "+48222222222", "pass");
        withoutConsent.setEmailVerified(true);
        withoutConsent.setAthlete(true);
        User saved = userRepository.save(withoutConsent);

        assertTrue(ascentService.getMyLog(saved.getId(), AscentTerrain.ROCK, null).entries().isEmpty());
    }

    /**
     * The other half of opening it up: a logbook is only visible to the coach when its owner is
     * a designated athlete, so signing up for an account is not signing up for supervision.
     */
    @Test
    @DisplayName("the coach cannot read the logbook of somebody who is not their athlete")
    void shouldKeepAPlainUsersLogbookPrivateFromTheCoach() {
        User plain = new User("private@example.com", "Prywatny", "Wspinacz", "+48666666666", "pass");
        plain.setEmailVerified(true);
        User saved = userRepository.save(plain);
        ascentService.createMyAscent(saved.getId(),
            sportRequest("Nie Twoja Sprawa", ClimbingGrade.FR_7A, AscentStyle.RP));

        assertThrows(IllegalArgumentException.class,
            () -> ascentService.getLogForAthlete(saved.getId(), AscentTerrain.ROCK, null));
    }

    @Test
    @DisplayName("the coach reads the logbook through the by-athlete path")
    void shouldLetTheCoachReadTheLogbook() {
        ascentService.createMyAscent(athlete.getId(),
            sportRequest("Wielkie Ciśnienie", ClimbingGrade.FR_7A, AscentStyle.RP));

        AscentLogDto log = ascentService.getLogForAthlete(athlete.getId(), AscentTerrain.ROCK, null);

        assertEquals(1, log.entries().size());
        assertEquals("Wielkie Ciśnienie", log.entries().getFirst().routeName());
    }

    @Test
    void shouldRefuseTheCoachPathForSomebodyWhoIsNotAnAthlete() {
        User plain = new User("plain2@example.com", "Zwykły", "Użytkownik", "+48444444444", "pass");
        plain.setEmailVerified(true);
        User saved = userRepository.save(plain);

        assertThrows(IllegalArgumentException.class,
            () -> ascentService.getLogForAthlete(saved.getId(), AscentTerrain.ROCK, null));
    }

    @Test
    void shouldRefuseToTouchSomebodyElsesAscent() {
        AscentDto mine = ascentService.createMyAscent(athlete.getId(),
            sportRequest("Mój Projekt", ClimbingGrade.FR_7A, AscentStyle.RP));

        User other = new User("other@example.com", "Ktoś", "Inny", "+48555555555", "pass");
        other.setEmailVerified(true);
        other.setAthlete(true);
        other.grantTrainingConsent();
        User saved = userRepository.save(other);

        assertThrows(IllegalArgumentException.class,
            () -> ascentService.deleteMyAscent(saved.getId(), mine.id()));
    }

    // ---------- the flow the feature exists for ----------

    @Test
    void shouldNormalizeAttemptsToOneForAnOnsight() {
        AscentDto saved = ascentService.createMyAscent(athlete.getId(),
            new SaveAscentRequest(null, today(), AscentDiscipline.SPORT, ClimbingGrade.FR_6C, AscentStyle.OS,
                "Jura", "Rzędkowice", "Filar", 5, 4, "szło samo",
                null, null, null, null, null, null, null, null));

        assertEquals(1, saved.attempts());
    }

    @Test
    void shouldRejectAGradeFromTheWrongScaleWithAReadableError() {
        SaveAscentRequest mismatched = new SaveAscentRequest(null, today(), AscentDiscipline.BOULDER,
            ClimbingGrade.FR_7A, AscentStyle.RP, "Jura", "Kołoczek", "Blok", null, null, null,
            null, null, null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class,
            () -> ascentService.createMyAscent(athlete.getId(), mismatched));
    }

    @Test
    void shouldRejectAnAscentDatedInTheFuture() {
        SaveAscentRequest tomorrow = new SaveAscentRequest(null, today().plusDays(1), AscentDiscipline.SPORT,
            ClimbingGrade.FR_6A, AscentStyle.RP, "Jura", "Podlesice", "Coś", null, null, null,
            null, null, null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class,
            () -> ascentService.createMyAscent(athlete.getId(), tomorrow));
    }

    @Test
    @DisplayName("changing the discipline moves the grade with it")
    void shouldUpdateDisciplineAndGradeTogether() {
        AscentDto saved = ascentService.createMyAscent(athlete.getId(),
            sportRequest("Przeprowadzka", ClimbingGrade.FR_6C, AscentStyle.RP));

        AscentDto updated = ascentService.updateMyAscent(athlete.getId(), saved.id(),
            new SaveAscentRequest(null, today(), AscentDiscipline.BOULDER, ClimbingGrade.FB_6C,
                AscentStyle.RP, "Jura", "Kołoczek", "Przeprowadzka", 3, null, null,
                null, null, null, null, null, null, null, null));

        assertEquals("BOULDER", updated.discipline());
        assertEquals("FONT_BOULDER", updated.gradeScale());
        assertEquals("6C", updated.gradeLabel());
    }

    // ---------- the year filter ----------

    @Test
    @DisplayName("no year parameter selects the newest year with data, not the current one")
    void shouldDefaultToTheNewestYearThatHasData() {
        logOn(today().minusYears(2), "Stara Droga");
        logOn(today().minusYears(1), "Nowsza Droga");

        AscentLogDto log = ascentService.getMyLog(athlete.getId(), AscentTerrain.ROCK, null);

        assertEquals(today().minusYears(1).getYear(), log.selectedYear());
        assertEquals(1, log.entries().size());
        assertEquals("Nowsza Droga", log.entries().getFirst().routeName());
        assertEquals(2, log.totalCount(), "totalCount spans every year");
    }

    @Test
    void shouldReturnEveryYearWhenAskedForAll() {
        logOn(today().minusYears(2), "Stara Droga");
        logOn(today().minusYears(1), "Nowsza Droga");

        AscentLogDto log = ascentService.getMyLog(athlete.getId(), AscentTerrain.ROCK, "all");

        assertNull(log.selectedYear());
        assertEquals(2, log.entries().size());
        assertEquals(2, log.availableYears().size());
    }

    @Test
    void shouldRejectAnUnparseableYear() {
        assertThrows(IllegalArgumentException.class,
            () -> ascentService.getMyLog(athlete.getId(), AscentTerrain.ROCK, "abc"));
    }

    @Test
    void shouldReturnAnEmptyLogbookWithoutAYearRatherThanFailing() {
        AscentLogDto log = ascentService.getMyLog(athlete.getId(), AscentTerrain.ROCK, null);

        assertNull(log.selectedYear());
        assertTrue(log.entries().isEmpty());
        assertTrue(log.availableYears().isEmpty());
        assertEquals(0, log.totalCount());
    }

    // ---------- mountains ----------

    private SaveAscentRequest mountainRequest(String routeName, ClimbingGrade grade, boolean winter) {
        return new SaveAscentRequest(AscentTerrain.MOUNTAIN, today(), null, grade, AscentStyle.OS,
            "Tatry Wysokie", "Mnich", routeName, null, null, null,
            winter, "V", 250, 8, 330, ClimbingGrade.FR_5A, 4, "Paula Skrzypczak");
    }

    @Test
    @DisplayName("a mountain ascent keeps the unified French grade and the guidebook's own")
    void shouldLogAMountainAscent() {
        AscentDto saved = ascentService.createMyAscent(athlete.getId(),
            mountainRequest("Filar Kurtyki", ClimbingGrade.FR_6C_PLUS, false));

        assertEquals("MOUNTAIN", saved.terrain());
        assertNull(saved.discipline());
        assertEquals("6c+", saved.gradeLabel());
        assertEquals("V", saved.originalGrade());
        assertEquals(250, saved.lengthMeters());
        assertEquals(8, saved.pitches());
        assertEquals(330, saved.durationMinutes());
        assertEquals("5a", saved.ledGradeLabel());
        assertEquals(4, saved.ledPitches());
        assertEquals("Paula Skrzypczak", saved.partners());
        assertEquals(Boolean.FALSE, saved.winter());
    }

    @Test
    @DisplayName("the two logbooks do not see each other")
    void shouldKeepTerrainsApart() {
        ascentService.createMyAscent(athlete.getId(),
            sportRequest("Skalna", ClimbingGrade.FR_6A, AscentStyle.RP));
        ascentService.createMyAscent(athlete.getId(),
            mountainRequest("Górska", ClimbingGrade.FR_5A, true));

        AscentLogDto rock = ascentService.getMyLog(athlete.getId(), AscentTerrain.ROCK, null);
        AscentLogDto mountain = ascentService.getMyLog(athlete.getId(), AscentTerrain.MOUNTAIN, null);

        assertEquals(1, rock.entries().size());
        assertEquals("Skalna", rock.entries().getFirst().routeName());
        assertEquals(1, mountain.entries().size());
        assertEquals("Górska", mountain.entries().getFirst().routeName());
    }

    @Test
    void shouldRefuseMountainFieldsOnARockEntry() {
        SaveAscentRequest mixed = new SaveAscentRequest(AscentTerrain.ROCK, today(),
            AscentDiscipline.SPORT, ClimbingGrade.FR_6A, AscentStyle.RP,
            "Jura", "Kołoczek", "Droga", null, null, null,
            null, null, 250, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class,
            () -> ascentService.createMyAscent(athlete.getId(), mixed));
    }

    @Test
    void shouldRequireTheSeasonOnAMountainEntry() {
        SaveAscentRequest noSeason = new SaveAscentRequest(AscentTerrain.MOUNTAIN, today(), null,
            ClimbingGrade.FR_5A, AscentStyle.OS, "Tatry", "Mnich", "Droga", null, null, null,
            null, null, null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class,
            () -> ascentService.createMyAscent(athlete.getId(), noSeason));
    }

    @Test
    @DisplayName("led pitches without a pitch count answers \"4 out of what?\"")
    void shouldRefuseLedPitchesWithoutTotalPitches() {
        SaveAscentRequest dangling = new SaveAscentRequest(AscentTerrain.MOUNTAIN, today(), null,
            ClimbingGrade.FR_5A, AscentStyle.OS, "Tatry", "Mnich", "Droga", null, null, null,
            false, null, null, null, null, null, 4, null);

        assertThrows(IllegalArgumentException.class,
            () -> ascentService.createMyAscent(athlete.getId(), dangling));
    }

    @Test
    @DisplayName("the terrain is fixed at creation, like a training's kind")
    void shouldRefuseChangingTheTerrain() {
        AscentDto rock = ascentService.createMyAscent(athlete.getId(),
            sportRequest("Skalna", ClimbingGrade.FR_6A, AscentStyle.RP));

        assertThrows(IllegalArgumentException.class,
            () -> ascentService.updateMyAscent(athlete.getId(), rock.id(),
                mountainRequest("Skalna", ClimbingGrade.FR_6A, false)));
    }

    /**
     * The terrain field is optional on the way in (older clients, and the rock form omits it), so
     * an update that leaves it out must still be judged as what the row IS. It used to be judged
     * as ROCK — the fallback — and a mountain entry came back with "choose a discipline", an error
     * about a field its terrain does not have.
     */
    @Test
    @DisplayName("an update without a terrain is validated against the stored one, not the fallback")
    void shouldValidateAnUpdateAgainstTheStoredTerrain() {
        AscentDto mountain = ascentService.createMyAscent(athlete.getId(),
            mountainRequest("Filar", ClimbingGrade.FR_6A, false));

        SaveAscentRequest withoutTerrain = new SaveAscentRequest(null, today(), null,
            ClimbingGrade.FR_6B, AscentStyle.OS, "Tatry Wysokie", "Mnich", "Filar poprawiony",
            null, null, null, false, "V", 250, 8, 330, ClimbingGrade.FR_5A, 4, "Paula Skrzypczak");

        AscentDto updated = ascentService.updateMyAscent(athlete.getId(), mountain.id(), withoutTerrain);

        assertEquals("MOUNTAIN", updated.terrain());
        assertEquals("6b", updated.gradeLabel());
        assertEquals("Filar poprawiony", updated.routeName());
    }

    @Test
    void shouldBuildMountainStatisticsInsteadOfDisciplineBlocks() {
        ascentService.createMyAscent(athlete.getId(),
            mountainRequest("Letnia", ClimbingGrade.FR_6A, false));
        ascentService.createMyAscent(athlete.getId(),
            mountainRequest("Zimowa", ClimbingGrade.FR_5A, true));

        AscentStatsDto stats = ascentStatsService.getMyStats(athlete.getId(), AscentTerrain.MOUNTAIN, "all");

        assertTrue(stats.disciplines().isEmpty(), "mountains have no discipline blocks");
        assertNotNull(stats.mountain());
        assertEquals(1, stats.mountain().summerCount());
        assertEquals(1, stats.mountain().winterCount());
        assertEquals(500, stats.mountain().totalMeters());
        assertEquals(2, stats.mountain().entriesWithLength());
        assertEquals(660, stats.mountain().totalMinutes());
        assertEquals(8, stats.mountain().ledPitchesTotal());
        assertEquals("5a", stats.mountain().hardestLed().gradeLabel());
        assertEquals(1, stats.mountain().leadPyramid().size());
        // The routes themselves: mountains get no discipline block, so without this pyramid
        // there would be nowhere to see what level is being climbed
        assertEquals(2, stats.mountain().pyramid().size());
        assertEquals("6a", stats.mountain().hardestByStyle().get("OS").gradeLabel());
    }

    // ---------- the public feed ----------

    /**
     * The rule the brief is explicit about: "10 newest", not "10 most recently typed in".
     * Somebody backfilling a 2019 season must not sweep this week's sends off the list.
     */
    @Test
    @DisplayName("the feed is ordered by the date climbed, not by when the entry was added")
    void shouldOrderThePublicFeedByTheDateClimbed() {
        logOn(today().minusDays(1), "Wczorajsza");
        // Added last, but climbed years ago — it must NOT lead the list
        logOn(today().minusYears(3), "Zaległość z 2023");

        List<PublicAscentDto> recent = publicAscentService.getRecent();

        assertEquals("Wczorajsza", recent.getFirst().routeName());
        assertEquals(2, recent.size());
    }

    @Test
    void shouldCapTheFeedAtTenEntries() {
        for (int i = 0; i < 13; i++) {
            logOn(today().minusDays(i), "Droga " + i);
        }

        assertEquals(10, publicAscentService.getRecent().size());
    }

    @Test
    @DisplayName("switching visibility off removes every entry of that climber at once")
    void shouldHonourTheOptOut() {
        logOn(today(), "Widoczna");
        assertEquals(1, publicAscentService.getRecent().size());

        // Through the service, not the repository: that is the path the settings screen takes,
        // and it is what drops the cached list — "you disappear in up to five minutes" is not
        // an answer to somebody asking to be removed
        userService.updateAscentsVisibility(athlete.getId(), false);

        assertTrue(publicAscentService.getRecent().isEmpty());
    }

    /**
     * Erasing the account takes the ascents with it (ON DELETE CASCADE) — but the feed is cached,
     * so without an eviction on that path the name of somebody who just deleted their account
     * keeps being served for the rest of the TTL. Exactly what the opt-out switch refuses to do.
     */
    @Test
    @DisplayName("deleting the account takes the climber off the public list at once")
    void shouldDropDeletedAccountsFromTheFeedImmediately() {
        User leaving = new User("odchodzi@example.com", "Jan", "Odchodzący", "+48111222333", "jan");
        leaving.setEmailVerified(true);
        leaving = userRepository.saveAndFlush(leaving);

        ascentService.createMyAscent(leaving.getId(),
            sportRequest("Pożegnalna", ClimbingGrade.FR_6A, AscentStyle.RP));
        // Populates the cache, which is the thing that has to be dropped
        assertEquals(1, publicAscentService.getRecent().size());

        // No password on this fixture (OAuth-shaped account), so none is required to erase it
        userService.deleteAccount(leaving.getId(), null);

        // Asserted on the cache rather than by reading the feed again: this class runs inside one
        // transaction, so a re-read would flush a persistence context still holding the ascent of
        // the user just removed — an artefact of the test setup, not of production, where the two
        // calls are separate requests. What the deletion path owes the feed is exactly this: the
        // stale list must be gone, so the next reader rebuilds it.
        assertNull(Objects.requireNonNull(cacheManager.getCache(PublicAscentService.CACHE))
            .get(PublicAscentService.CACHE_KEY.replace("'", "")));
    }

    @Test
    void shouldShowTheClimbersNameAndNothingPrivate() {
        ascentService.createMyAscent(athlete.getId(),
            new SaveAscentRequest(null, today(), AscentDiscipline.SPORT, ClimbingGrade.FR_7A, AscentStyle.RP,
                "Jura", "Kołoczek", "Wielkie Ciśnienie", 7, 5, "sekret: bałem się",
                null, null, null, null, null, null, null, null));

        PublicAscentDto entry = publicAscentService.getRecent().getFirst();

        assertEquals("Anna Wspinaczka", entry.climberName());
        assertEquals("7a", entry.gradeLabel());
        assertEquals("Wielkie Ciśnienie", entry.routeName());
        // The DTO has no field for any of it — comment, attempts and rating stay in the logbook
        assertFalse(entry.toString().contains("sekret"));
    }

    // ---------- autocomplete ----------

    @Test
    @DisplayName("suggestions collapse spellings and span every year")
    void shouldSuggestPlacesFromTheWholeHistory() {
        logOn(today().minusYears(3), "Dawna", "Jura Północna", "Kołoczek");
        logOn(today(), "Dzisiejsza", "jura  polnocna", "Koloczek");

        // Looking at this year only — the suggestion from three years ago still has to be there
        AscentLogDto log = ascentService.getMyLog(athlete.getId(), AscentTerrain.ROCK, String.valueOf(today().getYear()));

        assertEquals(1, log.places().size(), "one area, not two spellings");
        assertEquals(1, log.places().getFirst().crags().size());
        assertEquals("jura  polnocna", log.places().getFirst().area(),
            "the most recent spelling is the one shown");
    }

    @Test
    void shouldServeTheGradeCatalogueToAnybodyLoggedIn() {
        AscentOptionsDto options = ascentService.getOptions();

        assertEquals(AscentDiscipline.values().length, options.disciplines().size());
        assertTrue(options.gradesByScale().get("FRENCH_ROUTE").size() > 20);
        assertTrue(options.gradesByScale().get("FONT_BOULDER").size() > 20);
    }

    private void logOn(LocalDate day, String routeName) {
        logOn(day, routeName, "Jura Północna", "Dolina Bolechowicka");
    }

    private void logOn(LocalDate day, String routeName, String area, String crag) {
        ascentService.createMyAscent(athlete.getId(),
            new SaveAscentRequest(null, day, AscentDiscipline.SPORT, ClimbingGrade.FR_6C, AscentStyle.RP,
                area, crag, routeName, null, null, null,
                null, null, null, null, null, null, null, null));
    }
}
