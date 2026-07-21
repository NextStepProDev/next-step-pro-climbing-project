package pl.nextsteppro.climbing.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingStatsRow;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationStatsRow;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Athlete statistics shown under the training calendar (athlete's own view and the
 * coach's view of that athlete). An "activity" = completed personal training OR attended
 * reservation (confirmed, slot already over — same predicate as the "past" reservation list).
 *
 * <p>No caching on purpose: the hard requirement is that uncompleting a training, cancelling
 * a booking or deleting a past entry changes the numbers immediately. The cost is two indexed
 * per-athlete queries plus one in-memory pass over a few hundred tiny rows.
 */
@Service
@Transactional(readOnly = true)
public class TrainingStatsService {

    // Slot times are stored as local Poland time while the container runs UTC —
    // "now" comparisons MUST use this zone (see CLAUDE.md gotcha).
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private static final int HEATMAP_DAYS = 365;
    private static final int AVG_WINDOW_MONTHS = 6;
    private static final int RPE_WINDOW_DAYS = 30;
    private static final int RPE_DISTRIBUTION_DAYS = 90;
    private static final int SUSTAINED_HIGH_SAMPLE = 5;
    private static final int SUSTAINED_HIGH_THRESHOLD = 9;
    private static final int TOP_LOCATIONS = 3;

    private final PersonalTrainingRepository trainingRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final MessageService msg;

    public TrainingStatsService(PersonalTrainingRepository trainingRepository,
                                ReservationRepository reservationRepository,
                                UserRepository userRepository,
                                MessageService msg) {
        this.trainingRepository = trainingRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.msg = msg;
    }

    public AthleteStatsDto getMyStats(UUID userId) {
        requireAthlete(userId);
        LocalDateTime now = LocalDateTime.now(WARSAW);
        return buildStats(userId, now);
    }

    public AthleteStatsDto getStatsForAthlete(UUID athleteId) {
        requireFlaggedAthlete(athleteId);
        LocalDateTime now = LocalDateTime.now(WARSAW);
        return buildStats(athleteId, now);
    }

    // Package-private with "now" as a parameter so tests are deterministic.
    AthleteStatsDto buildStats(UUID athleteId, LocalDateTime nowWarsaw) {
        LocalDate today = nowWarsaw.toLocalDate();
        List<TrainingStatsRow> trainings = trainingRepository.findStatsRowsByAthleteId(athleteId);
        List<ReservationStatsRow> reservations =
            reservationRepository.findPastConfirmedStatsRows(athleteId, today, nowWarsaw.toLocalTime());

        // Activity dates from both sources; TreeMap keeps them sorted for streaks/first-activity
        TreeMap<LocalDate, Integer> countsByDate = new TreeMap<>();
        for (TrainingStatsRow t : trainings) {
            if (t.isCompleted()) countsByDate.merge(t.date(), 1, Integer::sum);
        }
        for (ReservationStatsRow r : reservations) {
            countsByDate.merge(r.date(), 1, Integer::sum);
        }

        long totalCount = countsByDate.values().stream().mapToLong(Integer::intValue).sum();
        LocalDate firstActivityDate = countsByDate.isEmpty() ? null : countsByDate.firstKey();

        YearMonth thisMonth = YearMonth.from(today);
        YearMonth prevMonth = thisMonth.minusMonths(1);
        int thisMonthCount = countInMonth(countsByDate, thisMonth);
        int prevMonthCount = countInMonth(countsByDate, prevMonth);

        int[] streaks = computeStreaks(countsByDate, today);

        // All RPE ratings across both sources: (date, rpe), for averages/distribution/sustained-high
        List<RatedActivity> rated = new ArrayList<>();
        for (TrainingStatsRow t : trainings) {
            if (t.isCompleted() && t.rpe() != null) rated.add(new RatedActivity(t.date(), t.rpe()));
        }
        for (ReservationStatsRow r : reservations) {
            if (r.rpe() != null) rated.add(new RatedActivity(r.date(), r.rpe()));
        }
        // Past attended reservations the athlete hasn't rated yet (personal trainings are handled
        // by the required-RPE completion flow, so they're excluded from the nudge)
        int unratedReservations = (int) reservations.stream().filter(r -> r.rpe() == null).count();

        return new AthleteStatsDto(
            thisMonthCount,
            prevMonthCount,
            totalCount,
            firstActivityDate,
            streaks[0],
            streaks[1],
            computeAvgPerMonth(countsByDate, firstActivityDate, thisMonth),
            heatmap(countsByDate, today),
            typeBreakdown(trainings, reservations),
            attendanceRate(trainings, nowWarsaw),
            avgRpe(rated, null),
            avgRpe(rated, today.minusDays(RPE_WINDOW_DAYS - 1)),
            topLocations(reservations),
            rpeDistribution(rated, today.minusDays(RPE_DISTRIBUTION_DAYS - 1)),
            sustainedHigh(rated),
            unratedReservations
        );
    }

    /** A single RPE rating with its activity date; source-agnostic. */
    private record RatedActivity(LocalDate date, int rpe) {}

    private static int countInMonth(TreeMap<LocalDate, Integer> countsByDate, YearMonth month) {
        return countsByDate.subMap(month.atDay(1), true, month.atEndOfMonth(), true)
            .values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Streaks over ISO weeks (Mon-Sun). Returns {current, best}. The current streak ends at
     * this week if it has an activity, otherwise at last week (grace period: an empty week
     * in progress does not break the streak; once it ends empty, the streak resets).
     */
    private static int[] computeStreaks(TreeMap<LocalDate, Integer> countsByDate, LocalDate today) {
        TreeSet<LocalDate> weeks = new TreeSet<>();
        for (LocalDate date : countsByDate.keySet()) {
            weeks.add(date.with(DayOfWeek.MONDAY));
        }
        if (weeks.isEmpty()) return new int[] {0, 0};

        int best = 0;
        Map<LocalDate, Integer> runEndingAt = new HashMap<>();
        int run = 0;
        LocalDate prev = null;
        for (LocalDate week : weeks) {
            run = (prev != null && prev.plusWeeks(1).equals(week)) ? run + 1 : 1;
            runEndingAt.put(week, run);
            best = Math.max(best, run);
            prev = week;
        }

        LocalDate thisWeek = today.with(DayOfWeek.MONDAY);
        int current = runEndingAt.getOrDefault(thisWeek,
            runEndingAt.getOrDefault(thisWeek.minusWeeks(1), 0));
        return new int[] {current, best};
    }

    /**
     * Average activities per month over the last {@value AVG_WINDOW_MONTHS} FULL calendar
     * months (the current partial month would drag the average down). The window is shortened
     * to start at the first activity's month; null until one full month of history exists.
     */
    @Nullable
    private static Double computeAvgPerMonth(TreeMap<LocalDate, Integer> countsByDate,
                                             @Nullable LocalDate firstActivityDate, YearMonth thisMonth) {
        if (firstActivityDate == null) return null;
        YearMonth firstMonth = YearMonth.from(firstActivityDate);
        if (!firstMonth.isBefore(thisMonth)) return null;

        YearMonth windowStart = thisMonth.minusMonths(AVG_WINDOW_MONTHS);
        if (firstMonth.isAfter(windowStart)) windowStart = firstMonth;
        long months = ChronoUnit.MONTHS.between(windowStart, thisMonth);

        int inWindow = countsByDate
            .subMap(windowStart.atDay(1), true, thisMonth.minusMonths(1).atEndOfMonth(), true)
            .values().stream().mapToInt(Integer::intValue).sum();
        return Math.round(10.0 * inWindow / months) / 10.0;
    }

    private static Map<LocalDate, Integer> heatmap(TreeMap<LocalDate, Integer> countsByDate, LocalDate today) {
        return new TreeMap<>(countsByDate.subMap(today.minusDays(HEATMAP_DAYS - 1), true, today, true));
    }

    private static TypeBreakdownDto typeBreakdown(List<TrainingStatsRow> trainings,
                                                  List<ReservationStatsRow> reservations) {
        long personal = trainings.stream().filter(TrainingStatsRow::isCompleted).count();
        long individualSlot = 0;
        long course = 0;
        long training = 0;
        long workshop = 0;
        for (ReservationStatsRow r : reservations) {
            EventType type = r.eventType();
            if (type == EventType.COURSE) course++;
            else if (type == EventType.TRAINING) training++;
            else if (type == EventType.WORKSHOP) workshop++;
            // null = standalone slot; other types can't hold reservations (blocksEnrollment) —
            // fold defensively into the individual bucket
            else individualSlot++;
        }
        return new TypeBreakdownDto(personal, individualSlot, course, training, workshop);
    }

    /** Personal trainings only: completed / (completed + missed); missed derived like deriveStatus. */
    @Nullable
    private static Integer attendanceRate(List<TrainingStatsRow> trainings, LocalDateTime nowWarsaw) {
        long completed = 0;
        long missed = 0;
        for (TrainingStatsRow t : trainings) {
            if (t.isCompleted()) completed++;
            else if (LocalDateTime.of(t.date(), t.endTime()).isBefore(nowWarsaw)) missed++;
        }
        long ended = completed + missed;
        return ended == 0 ? null : (int) Math.round(100.0 * completed / ended);
    }

    /** Mean RPE across both sources (completed trainings + rated reservations); {@code from} null = all-time. */
    @Nullable
    private static Double avgRpe(List<RatedActivity> rated, @Nullable LocalDate from) {
        double sum = 0;
        int count = 0;
        for (RatedActivity a : rated) {
            if (from != null && a.date().isBefore(from)) continue;
            sum += a.rpe();
            count++;
        }
        return count == 0 ? null : Math.round(10.0 * sum / count) / 10.0;
    }

    /**
     * Intensity balance over the last {@value RPE_DISTRIBUTION_DAYS} days: session counts by band
     * (light 1-4, medium 5-7, hard 8-10). Presented as a balance, not a score.
     */
    private static RpeDistributionDto rpeDistribution(List<RatedActivity> rated, LocalDate from) {
        int light = 0;
        int medium = 0;
        int hard = 0;
        for (RatedActivity a : rated) {
            if (a.date().isBefore(from)) continue;
            if (a.rpe() <= 4) light++;
            else if (a.rpe() <= 7) medium++;
            else hard++;
        }
        return new RpeDistributionDto(light, medium, hard);
    }

    /**
     * True when the last {@value SUSTAINED_HIGH_SAMPLE} ratings (both sources, newest first) are all
     * >= {@value SUSTAINED_HIGH_THRESHOLD} — a hint of overtraining or inflated scoring. Fewer than
     * the sample size of ratings never flags.
     */
    private static boolean sustainedHigh(List<RatedActivity> rated) {
        if (rated.size() < SUSTAINED_HIGH_SAMPLE) return false;
        // Ties within a day are arbitrary (no start time on stats rows) — acceptable
        List<RatedActivity> recent = rated.stream()
            .sorted(Comparator.comparing(RatedActivity::date).reversed())
            .limit(SUSTAINED_HIGH_SAMPLE)
            .toList();
        return recent.stream().allMatch(a -> a.rpe() >= SUSTAINED_HIGH_THRESHOLD);
    }

    private static List<LocationCountDto> topLocations(List<ReservationStatsRow> reservations) {
        Map<String, Long> counts = new HashMap<>();
        for (ReservationStatsRow r : reservations) {
            if (r.location() != null && !r.location().isBlank()) {
                counts.merge(r.location().trim(), 1L, Long::sum);
            }
        }
        List<LocationCountDto> top = new ArrayList<>();
        counts.entrySet().stream()
            .sorted(Comparator.comparingLong(Map.Entry<String, Long>::getValue).reversed()
                .thenComparing(Map.Entry::getKey))
            .limit(TOP_LOCATIONS)
            .forEach(e -> top.add(new LocationCountDto(e.getKey(), e.getValue())));
        return top;
    }

    private void requireAthlete(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!user.isAthlete()) {
            throw new IllegalStateException(msg.get("training.calendar.not.athlete"));
        }
    }

    private void requireFlaggedAthlete(UUID athleteId) {
        userRepository.findById(athleteId)
            .filter(User::isAthlete)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("training.calendar.athlete.not.found")));
    }
}
