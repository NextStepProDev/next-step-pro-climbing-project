package pl.nextsteppro.climbing.api.trainingcalendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingStatsRow;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationStatsRow;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TrainingStatsService.
 * Verifies: streak semantics (grace week, gaps, year boundary), month trend and average
 * windows, derived missed/attendance, both-sources totals and heatmap, type buckets,
 * RPE windows, location ranking, empty state, athlete guard.
 */
@ExtendWith(MockitoExtension.class)
class TrainingStatsServiceTest {

    // Fixed clock: Wednesday 2026-07-15, 12:00 Warsaw time (this ISO week starts Mon 2026-07-13)
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 15, 12, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final Instant DONE = Instant.parse("2026-07-01T10:00:00Z");

    @Mock private PersonalTrainingRepository trainingRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private TrainingCalendarService calendarService;

    private TrainingStatsService service;

    private UUID athleteId;

    @BeforeEach
    void setUp() {
        service = new TrainingStatsService(trainingRepository, reservationRepository, calendarService);
        athleteId = UUID.randomUUID();
    }

    // ========== streaks ==========

    @Test
    void shouldCountCurrentWeekInStreakWhenItHasActivity() {
        // Given: activities in this week and the two directly preceding weeks
        givenTrainings(completed(d(2026, 7, 14)), completed(d(2026, 7, 6)), completed(d(2026, 6, 29)));
        givenReservations();

        // When
        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        // Then
        assertEquals(3, stats.currentStreakWeeks());
        assertEquals(3, stats.bestStreakWeeks());
    }

    @Test
    void shouldKeepStreakWhenCurrentWeekEmptyButPreviousWeekActive() {
        // Given: nothing this week yet, but the two previous weeks are active (grace period)
        givenTrainings(completed(d(2026, 7, 6)), completed(d(2026, 6, 30)));
        givenReservations();

        // When
        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        // Then
        assertEquals(2, stats.currentStreakWeeks());
    }

    @Test
    void shouldReturnZeroStreakWhenCurrentAndPreviousWeeksEmpty() {
        // Given: last activity two weeks back — streak already broken
        givenTrainings(completed(d(2026, 6, 24)));
        givenReservations();

        // When
        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        // Then
        assertEquals(0, stats.currentStreakWeeks());
        assertEquals(1, stats.bestStreakWeeks());
    }

    @Test
    void shouldComputeBestStreakAcrossGaps() {
        // Given: a 3-week run in March, a lone week in May, one activity this week
        givenTrainings(
            completed(d(2026, 3, 3)), completed(d(2026, 3, 11)), completed(d(2026, 3, 18)),
            completed(d(2026, 5, 6)),
            completed(d(2026, 7, 14)));
        givenReservations();

        // When
        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        // Then
        assertEquals(1, stats.currentStreakWeeks());
        assertEquals(3, stats.bestStreakWeeks());
    }

    @Test
    void shouldTreatYearBoundaryWeeksAsConsecutive() {
        // Given: week of Mon 2025-12-29 and week of Mon 2026-01-05
        givenTrainings(completed(d(2025, 12, 30)), completed(d(2026, 1, 7)));
        givenReservations();

        // When
        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        // Then
        assertEquals(2, stats.bestStreakWeeks());
        assertEquals(0, stats.currentStreakWeeks());
    }

    // ========== months ==========

    @Test
    void shouldComputeMonthTrendAcrossMonthBoundary() {
        // Given: 2 activities in July (current), 3 in June
        givenTrainings(completed(d(2026, 7, 2)), completed(d(2026, 7, 10)));
        givenReservations(reservation(d(2026, 6, 5)), reservation(d(2026, 6, 10)), reservation(d(2026, 6, 20)));

        // When
        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        // Then
        assertEquals(2, stats.thisMonthCount());
        assertEquals(3, stats.prevMonthCount());
    }

    @Test
    void shouldAverageOverLastSixFullMonths() {
        // Given: first activity Oct 2025 (before the window), 12 activities Jan-Jun 2026,
        // plus July ones that must NOT count (current partial month)
        givenTrainings(completed(d(2025, 10, 1)), completed(d(2026, 7, 2)));
        givenReservations(
            reservation(d(2026, 1, 10)), reservation(d(2026, 1, 20)),
            reservation(d(2026, 2, 10)), reservation(d(2026, 2, 20)),
            reservation(d(2026, 3, 10)), reservation(d(2026, 3, 20)),
            reservation(d(2026, 4, 10)), reservation(d(2026, 4, 20)),
            reservation(d(2026, 5, 10)), reservation(d(2026, 5, 20)),
            reservation(d(2026, 6, 10)), reservation(d(2026, 6, 20)));

        // When
        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        // Then: 12 activities over 6 full months
        assertEquals(2.0, stats.avgPerMonth());
    }

    @Test
    void shouldShortenAvgWindowToFirstActivityMonth() {
        // Given: first activity in May 2026 -> window = May + June (2 full months), 5 activities there
        givenTrainings(
            completed(d(2026, 5, 3)), completed(d(2026, 5, 10)), completed(d(2026, 5, 20)),
            completed(d(2026, 6, 5)), completed(d(2026, 6, 15)),
            completed(d(2026, 7, 2)));
        givenReservations();

        // When
        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        // Then
        assertEquals(2.5, stats.avgPerMonth());
    }

    @Test
    void shouldReturnNullAvgWhenFirstActivityInCurrentMonth() {
        // Given: history starts this month — no full month elapsed yet
        givenTrainings(completed(d(2026, 7, 5)));
        givenReservations();

        // When
        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        // Then
        assertNull(stats.avgPerMonth());
    }

    // ========== attendance (personal trainings only) ==========

    @Test
    void shouldDeriveMissedWhenNotCompletedAndEndInPast() {
        // Given: 2 completed + 1 planned whose end already passed (= missed)
        givenTrainings(completed(d(2026, 7, 10)), completed(d(2026, 7, 12)), planned(d(2026, 7, 14)));
        givenReservations();

        // When
        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        // Then
        assertEquals(67, stats.attendanceRatePercent());
    }

    @Test
    void shouldExcludePlannedFutureFromAttendance() {
        // Given: 1 completed + 1 planned in the future (not missed yet)
        givenTrainings(completed(d(2026, 7, 10)), planned(d(2026, 7, 20)));
        givenReservations();

        // When
        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        // Then
        assertEquals(100, stats.attendanceRatePercent());
    }

    @Test
    void shouldExcludeMissedTrainingsOlderThanWindowFromAttendance() {
        // Given: 1 recent completed (in window) + 1 very old missed (2026-03-01, outside the 90-day
        // window that ends at NOW=2026-07-15). The old miss must not drag attendance down forever.
        givenTrainings(completed(d(2026, 7, 10)), planned(d(2026, 3, 1)));
        givenReservations();

        // When
        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        // Then: only the in-window completed one counts → 100%, not 50%
        assertEquals(100, stats.attendanceRatePercent());
    }

    @Test
    void shouldCountUntimedTrainingsInAttendanceViaEndOfDay() {
        // Given: untimed (null endTime) rows — missed derivation must use end-of-day, not NPE.
        // 1 completed (past) + 1 missed (past, uncompleted) + 1 today untimed still planned (not missed yet)
        givenTrainings(untimedCompleted(d(2026, 7, 10)), untimedPlanned(d(2026, 7, 12)), untimedPlanned(d(2026, 7, 15)));
        givenReservations();

        // When
        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        // Then: today's untimed is excluded (end-of-day > NOW) → 1/2 = 50%
        assertEquals(50, stats.attendanceRatePercent());
    }

    @Test
    void shouldReturnNullAttendanceWhenNoPersonalTrainingEnded() {
        // Given: only future plans; attended reservations must not fill in for attendance
        givenTrainings(planned(d(2026, 7, 20)));
        givenReservations(reservation(d(2026, 7, 1)));

        // When
        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        // Then
        assertNull(stats.attendanceRatePercent());
    }

    // ========== sources, totals, heatmap ==========

    @Test
    void shouldCombineBothSourcesInTotalsAndHeatmap() {
        // Given: a completed training and a reservation on the same day + an earlier reservation
        givenTrainings(completed(d(2026, 7, 10)));
        givenReservations(reservation(d(2026, 7, 10)), reservation(d(2026, 7, 1)));

        // When
        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        // Then
        assertEquals(3, stats.totalCount());
        assertEquals(d(2026, 7, 1), stats.firstActivityDate());
        assertEquals(2, stats.heatmap().get(d(2026, 7, 10)));
        assertEquals(1, stats.heatmap().get(d(2026, 7, 1)));
    }

    @Test
    void shouldExcludeUncompletedTrainingsFromTotals() {
        // Given: one completed, one past-but-unchecked (missed) — only the completed one counts
        givenTrainings(completed(d(2026, 7, 10)), planned(d(2026, 7, 8)));
        givenReservations();

        // When
        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        // Then
        assertEquals(1, stats.totalCount());
        assertEquals(1, stats.byType().personal());
        assertFalse(stats.heatmap().containsKey(d(2026, 7, 8)));
    }

    @Test
    void shouldExcludeDaysOlderThanYearFromHeatmap() {
        // Given: one activity just inside the 365-day window, one just outside
        givenTrainings(completed(TODAY.minusDays(364)), completed(TODAY.minusDays(365)));
        givenReservations();

        // When
        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        // Then: totals keep both, heatmap only the one in range
        assertEquals(2, stats.totalCount());
        assertTrue(stats.heatmap().containsKey(TODAY.minusDays(364)));
        assertFalse(stats.heatmap().containsKey(TODAY.minusDays(365)));
    }

    // ========== type buckets ==========

    @Test
    void shouldBucketReservationWithoutEventAsIndividualSlot() {
        // Given: one reservation per bucket
        givenTrainings(completed(d(2026, 7, 10)));
        givenReservations(
            reservation(d(2026, 7, 1)),
            reservation(d(2026, 7, 2), EventType.COURSE, null),
            reservation(d(2026, 7, 3), EventType.TRAINING, null),
            reservation(d(2026, 7, 6), EventType.WORKSHOP, null));

        // When
        TypeBreakdownDto byType = service.buildStats(athleteId, NOW).byType();

        // Then
        assertEquals(1, byType.personal());
        assertEquals(1, byType.individualSlot());
        assertEquals(1, byType.course());
        assertEquals(1, byType.training());
        assertEquals(1, byType.workshop());
    }

    // ========== RPE ==========

    @Test
    void shouldAverageRpeIgnoringNullsWithThirtyDayBoundary() {
        // Given: rpe 8 and 6 inside the 30-day window, rpe 4 outside, one completed without rpe
        givenTrainings(
            completedRpe(d(2026, 7, 10), 8),
            completedRpe(d(2026, 6, 20), 6),
            completedRpe(d(2026, 5, 1), 4),
            completed(d(2026, 7, 12)));
        givenReservations();

        // When
        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        // Then
        assertEquals(6.0, stats.avgRpeOverall());
        assertEquals(7.0, stats.avgRpeLast30Days());
    }

    @Test
    void shouldAverageRpeFromBothSources() {
        // Given: one rated training (8) + one rated reservation (6) inside the window
        givenTrainings(completedRpe(d(2026, 7, 10), 8));
        givenReservations(ratedReservation(d(2026, 7, 12), 6));

        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        assertEquals(7.0, stats.avgRpeOverall());
    }

    @Test
    void shouldBucketRpeDistributionOverLast90Days() {
        // Given: light (3), medium (6), hard (9,10) within 90 days; an old one (10) outside
        givenTrainings(
            completedRpe(d(2026, 7, 10), 3),
            completedRpe(d(2026, 7, 9), 6),
            completedRpe(d(2026, 6, 20), 9),
            completedRpe(d(2026, 1, 1), 10)); // > 90 days before NOW → excluded
        givenReservations(ratedReservation(d(2026, 7, 8), 10));

        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        assertEquals(1, stats.rpeDistribution().light());
        assertEquals(1, stats.rpeDistribution().medium());
        assertEquals(2, stats.rpeDistribution().hard());
    }

    @Test
    void shouldFlagSustainedHighWhenLastFiveAllNineOrMore() {
        givenTrainings(
            completedRpe(d(2026, 7, 14), 9), completedRpe(d(2026, 7, 12), 10),
            completedRpe(d(2026, 7, 10), 9), completedRpe(d(2026, 7, 8), 9),
            completedRpe(d(2026, 7, 6), 10), completedRpe(d(2026, 7, 1), 3)); // older, ignored
        givenReservations();

        assertTrue(service.buildStats(athleteId, NOW).sustainedHighRpe());
    }

    @Test
    void shouldNotFlagSustainedHighWithFewerThanFiveRatings() {
        givenTrainings(completedRpe(d(2026, 7, 14), 10), completedRpe(d(2026, 7, 12), 10));
        givenReservations();

        assertFalse(service.buildStats(athleteId, NOW).sustainedHighRpe());
    }

    @Test
    void shouldNotFlagSustainedHighWhenOneRecentBelowNine() {
        givenTrainings(
            completedRpe(d(2026, 7, 14), 9), completedRpe(d(2026, 7, 12), 10),
            completedRpe(d(2026, 7, 10), 7), completedRpe(d(2026, 7, 8), 9),
            completedRpe(d(2026, 7, 6), 10));
        givenReservations();

        assertFalse(service.buildStats(athleteId, NOW).sustainedHighRpe());
    }

    @Test
    void shouldCountUnratedPastReservations() {
        // Rated reservations and personal trainings do NOT count toward the nudge
        givenTrainings(completed(d(2026, 7, 10)));
        givenReservations(reservation(d(2026, 7, 12)), ratedReservation(d(2026, 7, 11), 5), reservation(d(2026, 7, 9)));

        assertEquals(2, service.buildStats(athleteId, NOW).unratedActivitiesCount());
    }

    // ========== locations ==========

    @Test
    void shouldRankTopThreeLocationsIgnoringNulls() {
        // Given: B x3, C x2, A x1, D x1, two without location
        givenTrainings();
        givenReservations(
            reservation(d(2026, 7, 1), EventType.TRAINING, "B"),
            reservation(d(2026, 7, 2), EventType.TRAINING, "B"),
            reservation(d(2026, 7, 3), EventType.TRAINING, "B"),
            reservation(d(2026, 6, 1), EventType.COURSE, "C"),
            reservation(d(2026, 6, 2), EventType.COURSE, "C"),
            reservation(d(2026, 5, 1), EventType.WORKSHOP, "A"),
            reservation(d(2026, 5, 2), EventType.WORKSHOP, "D"),
            reservation(d(2026, 4, 1)),
            reservation(d(2026, 4, 2)));

        // When
        List<LocationCountDto> top = service.buildStats(athleteId, NOW).topLocations();

        // Then: top 3 by count, alphabetical tie-break (A before D)
        assertEquals(3, top.size());
        assertEquals(new LocationCountDto("B", 3), top.get(0));
        assertEquals(new LocationCountDto("C", 2), top.get(1));
        assertEquals(new LocationCountDto("A", 1), top.get(2));
    }

    // ========== empty state ==========

    @Test
    void shouldReturnEmptyStatsWhenNoActivity() {
        // Given
        givenTrainings();
        givenReservations();

        // When
        AthleteStatsDto stats = service.buildStats(athleteId, NOW);

        // Then
        assertEquals(0, stats.totalCount());
        assertEquals(0, stats.thisMonthCount());
        assertEquals(0, stats.currentStreakWeeks());
        assertEquals(0, stats.bestStreakWeeks());
        assertNull(stats.firstActivityDate());
        assertNull(stats.avgPerMonth());
        assertNull(stats.attendanceRatePercent());
        assertNull(stats.avgRpeOverall());
        assertNull(stats.avgRpeLast30Days());
        assertTrue(stats.heatmap().isEmpty());
        assertTrue(stats.topLocations().isEmpty());
    }

    // ========== guards ==========

    // The gate itself lives in TrainingCalendarService (flag + GDPR consent) and is tested there.
    // What matters here is that stats go THROUGH it rather than carrying their own copy.

    @Test
    void shouldRefuseMyStatsWhenCalendarGateRejectsTheUser() {
        // Given: the shared gate refuses (not an athlete, or consent not granted)
        UUID userId = UUID.randomUUID();
        when(calendarService.requireAthlete(userId))
            .thenThrow(new IllegalStateException("training.calendar.not.athlete"));

        // When / Then
        assertThrows(IllegalStateException.class, () -> service.getMyStats(userId));
        verify(trainingRepository, never()).findStatsRowsByAthleteId(any());
    }

    @Test
    void shouldRefuseCoachStatsWhenAthleteIsNotFlagged() {
        // Given
        UUID unknownId = UUID.randomUUID();
        when(calendarService.requireFlaggedAthlete(unknownId))
            .thenThrow(new IllegalArgumentException("training.calendar.athlete.not.found"));

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> service.getStatsForAthlete(unknownId));
        verify(trainingRepository, never()).findStatsRowsByAthleteId(any());
    }

    // ========== helpers ==========

    private void givenTrainings(TrainingStatsRow... rows) {
        when(trainingRepository.findStatsRowsByAthleteId(athleteId)).thenReturn(List.of(rows));
    }

    private void givenReservations(ReservationStatsRow... rows) {
        when(reservationRepository.findPastConfirmedStatsRows(eq(athleteId), any(), any()))
            .thenReturn(List.of(rows));
    }

    private static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    private static TrainingStatsRow completed(LocalDate date) {
        return new TrainingStatsRow(date, LocalTime.of(19, 0), DONE, null);
    }

    private static TrainingStatsRow completedRpe(LocalDate date, int rpe) {
        return new TrainingStatsRow(date, LocalTime.of(19, 0), DONE, rpe);
    }

    private static TrainingStatsRow planned(LocalDate date) {
        return new TrainingStatsRow(date, LocalTime.of(19, 0), null, null);
    }

    // Untimed ("all-day"): null endTime → missed derivation falls back to end-of-day
    private static TrainingStatsRow untimedCompleted(LocalDate date) {
        return new TrainingStatsRow(date, null, DONE, null);
    }

    private static TrainingStatsRow untimedPlanned(LocalDate date) {
        return new TrainingStatsRow(date, null, null, null);
    }

    private static ReservationStatsRow reservation(LocalDate date) {
        return new ReservationStatsRow(date, null, null, null);
    }

    private static ReservationStatsRow reservation(LocalDate date, EventType type, String location) {
        return new ReservationStatsRow(date, type, location, null);
    }

    private static ReservationStatsRow ratedReservation(LocalDate date, int rpe) {
        return new ReservationStatsRow(date, null, null, rpe);
    }
}
