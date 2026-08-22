package pl.nextsteppro.climbing.api.ascent;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.climbingascent.AreaUsageRow;
import pl.nextsteppro.climbing.domain.climbingascent.AscentDiscipline;
import pl.nextsteppro.climbing.domain.climbingascent.AscentStyle;
import pl.nextsteppro.climbing.domain.climbingascent.AscentTerrain;
import pl.nextsteppro.climbing.domain.climbingascent.ClimbingGrade;
import pl.nextsteppro.climbing.domain.climbingascent.GradeScale;
import pl.nextsteppro.climbing.domain.climbingascent.ClimbingAscent;
import pl.nextsteppro.climbing.domain.climbingascent.ClimbingAscentRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * A climber's own logbook, and a read-only window into it for the coach.
 *
 * <p><b>Open to every logged-in user.</b> The logbook is not part of the training calendar: it
 * holds no health data (no weight, no RPE, no feedback), so it sits behind neither the athlete
 * flag nor the GDPR art. 9 consent. Anyone with an account keeps their own — which is also why
 * this lives in its own package rather than in {@code api/trainingcalendar}.
 *
 * <p><b>The coach reads a logbook the owner has not hidden.</b> {@link #requireReadableLogbook}
 * is the whole rule: a designated athlete's logbook is always open to the coach (that relationship
 * is itself a decision somebody made, and switching off the public list is about strangers, not
 * about the trainer), and everybody else's is open exactly while {@code users.ascents_public} is
 * on. That switch therefore answers two questions with one answer, which is why the Settings copy
 * and the privacy policy name both of them.
 *
 * <p><b>Only the owner writes the CONTENT.</b> No admin path creates, edits or deletes somebody
 * else's entry — crediting somebody with an ascent is not the coach's call. Same shape as
 * {@code AthleteWeightService}.
 *
 * <p><b>The one exception is publication, and it is a different verb.</b>
 * {@link #setPublicVisibility} lets the site owner take one entry off the public feed. It changes
 * nothing about the ascent itself: the row stays in the author's logbook and keeps counting in
 * their statistics, because it is still their ascent. What changed is whether it hangs on the
 * owner's noticeboard. The line the whole feature rests on: <b>the logbook is the author's, the
 * public list is the owner's.</b>
 *
 * <p>Every entry is a completed ascent. Attempts and open projects are not modelled at all: the
 * pyramid and the onsight rate count ascents, so a "tried it" row would land in the denominator
 * of both and turn a record of achievements into a record of attendance.
 */
@Service
@Transactional
public class AscentService {

    // Container runs UTC; "today" for a Polish climber has to be Warsaw's
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    /** The one non-year value the year filter accepts. A closed set, like {@code WeightRange}. */
    static final String ALL_YEARS = "all";

    /** Wide enough for any logbook, narrow enough that a typo cannot ask for year 900000. */
    private static final int MIN_YEAR = 1900;
    private static final int MAX_YEAR = 2200;

    private final ClimbingAscentRepository ascentRepository;
    private final UserRepository userRepository;
    private final MessageService msg;

    public AscentService(ClimbingAscentRepository ascentRepository,
                         UserRepository userRepository,
                         MessageService msg) {
        this.ascentRepository = ascentRepository;
        this.userRepository = userRepository;
        this.msg = msg;
    }

    /**
     * No gate beyond being logged in: {@code SecurityConfig} already requires authentication, and
     * the logbook is the user's own. Ownership of individual rows is enforced by
     * {@link #requireOwnAscent}, which looks up by (id, owner) rather than checking afterwards.
     */
    @Transactional(readOnly = true)
    public AscentLogDto getMyLog(UUID userId, AscentTerrain terrain, @Nullable String year) {
        return buildLog(userId, terrain, year);
    }

    /** Coach path — see {@link #requireReadableLogbook} for who is readable. */
    @Transactional(readOnly = true)
    public AscentLogDto getLogForAthlete(UUID athleteId, AscentTerrain terrain, @Nullable String year) {
        requireReadableLogbook(athleteId);
        return buildLog(athleteId, terrain, year);
    }

    /**
     * The coach-side gate for one logbook. Who qualifies is decided by
     * {@link User#isLogbookVisibleToCoach()} — the rule is asked from here and from the admin user
     * card, so it lives on the entity rather than being spelled out twice.
     *
     * <p>Hidden and non-existent get the same message on purpose: whether a given account exists
     * is not something an error should confirm, and the admin has the user list for that anyway.
     */
    @Transactional(readOnly = true)
    public User requireReadableLogbook(UUID athleteId) {
        return userRepository.findById(athleteId)
                .filter(User::isLogbookVisibleToCoach)
                .orElseThrow(() -> new IllegalArgumentException(msg.get("ascent.logbook.unavailable")));
    }

    /**
     * Every write drops the public feed's cache. Without it a fresh send would sit invisible for
     * the TTL — and the whole point of the list is that it looks alive.
     */
    @CacheEvict(value = PublicAscentService.CACHE, allEntries = true)
    public AscentDto createMyAscent(UUID userId, SaveAscentRequest request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        validate(request, request.terrainOrRock());

        ClimbingAscent ascent = request.terrainOrRock() == AscentTerrain.MOUNTAIN
                ? ClimbingAscent.mountain(owner, request.climbedOn(), Boolean.TRUE.equals(request.winter()),
                        request.grade(), request.style(),
                        request.area(), request.crag(), request.routeName())
                : ClimbingAscent.rock(owner, request.climbedOn(), requireDiscipline(request),
                        request.grade(), request.style(),
                        request.area(), request.crag(), request.routeName());
        applyOptionalFields(ascent, request);

        return toDto(ascentRepository.save(ascent));
    }

    @CacheEvict(value = PublicAscentService.CACHE, allEntries = true)
    public AscentDto updateMyAscent(UUID userId, UUID ascentId, SaveAscentRequest request) {
        ClimbingAscent ascent = requireOwnAscent(ascentId, userId);
        // The terrain is fixed at creation, like a training's kind: switching it would have to
        // throw away either the discipline or the whole mountain half of the entry, and losing
        // data as a side effect of a dropdown is a bad trade. Change of terrain = delete and re-add.
        if (request.terrain() != null && request.terrain() != ascent.getTerrain()) {
            throw new IllegalArgumentException(msg.get("ascent.terrain.immutable"));
        }
        // Validated against the STORED terrain, not the request's. The request's is optional and
        // falls back to ROCK, so a mountain entry updated by a client that omits the field used to
        // be told "choose a discipline" — an error about a field its terrain does not even have.
        // The row is the authority here anyway: the terrain cannot change, so nothing else can be.
        validate(request, ascent.getTerrain());
        ascent.setClimbedOn(request.climbedOn());
        ascent.setStyle(request.style());
        if (ascent.getTerrain() == AscentTerrain.MOUNTAIN) {
            ascent.setGrade(request.grade());
            ascent.setWinter(Boolean.TRUE.equals(request.winter()));
        } else {
            // Discipline and grade move together: the discipline picks the scale, so setting them
            // one at a time would pass through a state the entity is right to refuse
            ascent.setDisciplineAndGrade(requireDiscipline(request), request.grade());
        }
        ascent.setArea(request.area());
        ascent.setCrag(request.crag());
        ascent.setRouteName(request.routeName());
        applyOptionalFields(ascent, request);

        return toDto(ascent);
    }

    @CacheEvict(value = PublicAscentService.CACHE, allEntries = true)
    public void deleteMyAscent(UUID userId, UUID ascentId) {
        ascentRepository.delete(requireOwnAscent(ascentId, userId));
    }

    /**
     * Admin only: takes one entry off the public feed, or puts it back.
     *
     * <p>The site owner's remedy for something they do not want on their own front page. Before
     * this existed the only ways out were asking the author or running SQL on production — and an
     * incident is the worst moment to be doing surgery on the live database.
     *
     * <p><b>Deliberately not implemented as flipping the author's {@code ascents_public}</b>, which
     * looks like the cheap way and costs twice. That column also gates the admin's view of the
     * logbook, so using it would blind the admin about the very person they just moderated; and
     * the author sees it in their own Settings, so they would undo the takedown with one click.
     * Moderation the moderated party can reverse is not moderation.
     *
     * <p>Lives in this class rather than a service of its own so that it cannot forget the eviction
     * every other write here performs: a takedown that stays visible for the cache TTL is exactly
     * the failure the button exists to prevent.
     *
     * <p>Gated on the logbook being readable at all, like every other admin path here. That is
     * belt-and-braces rather than a real filter — an entry belonging to a hidden logbook is not on
     * the public list in the first place, so there is nothing there to take down.
     */
    @CacheEvict(value = PublicAscentService.CACHE, allEntries = true)
    public AscentDto setPublicVisibility(UUID ascentId, boolean hidden) {
        ClimbingAscent ascent = ascentRepository.findById(ascentId)
                .orElseThrow(() -> new IllegalArgumentException(msg.get("ascent.not.found")));
        requireReadableLogbook(ascent.getAthlete().getId());
        ascent.setHiddenFromPublic(hidden, Instant.now());
        return toDto(ascent);
    }

    /**
     * The grade/style catalogue. Deliberately ungated: it carries no personal data and serves
     * both roles from one path — a twin admin route would be a second copy of the same list to
     * keep in step. Same reasoning as the private-file endpoints.
     */
    @Transactional(readOnly = true)
    public AscentOptionsDto getOptions() {
        return AscentOptionsDto.current();
    }

    private void validate(SaveAscentRequest request, AscentTerrain terrain) {
        if (request.climbedOn().isAfter(LocalDate.now(WARSAW))) {
            throw new IllegalArgumentException(msg.get("ascent.date.future"));
        }
        if (terrain == AscentTerrain.MOUNTAIN) {
            validateMountain(request);
        } else {
            validateRock(request);
        }
    }

    private void validateRock(SaveAscentRequest request) {
        AscentDiscipline discipline = requireDiscipline(request);
        // The scale is a property of the discipline, so these two fields are not independent.
        // Checked here as well as by the DB CHECK because "boulder graded 7a" is a plausible
        // typo, and a 500 from a constraint violation tells the athlete nothing
        if (!discipline.allows(request.grade())) {
            throw new IllegalArgumentException(msg.get("ascent.grade.scale.mismatch"));
        }
        if (!discipline.allows(request.style())) {
            throw new IllegalArgumentException(msg.get("ascent.style.discipline"));
        }
        // A0 is a mountain style: on a crag the same day is a redpoint attempt, not an ascent
        if (request.style() == AscentStyle.A0) {
            throw new IllegalArgumentException(msg.get("ascent.style.terrain.rock"));
        }
        // Refused rather than ignored: a silently dropped field is a value the athlete believes
        // they saved. The DB CHECK says the same thing, but this says it in their language.
        if (request.winter() != null || request.lengthMeters() != null || request.pitches() != null
                || request.durationMinutes() != null || request.ledGrade() != null
                || request.ledPitches() != null || request.partners() != null
                || request.originalGrade() != null) {
            throw new IllegalArgumentException(msg.get("ascent.rock.fields"));
        }
    }

    private void validateMountain(SaveAscentRequest request) {
        if (request.winter() == null) {
            throw new IllegalArgumentException(msg.get("ascent.mountain.season.required"));
        }
        // The unified grade is French in the mountains too — that is what keeps one pyramid honest
        if (request.grade().scale() != GradeScale.FRENCH_ROUTE) {
            throw new IllegalArgumentException(msg.get("ascent.grade.scale.mismatch"));
        }
        if (request.ledGrade() != null && request.ledGrade().scale() != GradeScale.FRENCH_ROUTE) {
            throw new IllegalArgumentException(msg.get("ascent.grade.scale.mismatch"));
        }
        if (request.discipline() != null || request.attempts() != null || request.qualityStars() != null) {
            throw new IllegalArgumentException(msg.get("ascent.mountain.fields"));
        }
        // Toprope is the one style the mountains refuse — there is nothing to hang a rope from
        if (!AscentDiscipline.MOUNTAIN_STYLES.contains(request.style())) {
            throw new IllegalArgumentException(msg.get("ascent.style.terrain"));
        }
        // Counting led pitches without saying how many the route has answers "4 out of what?"
        if (request.ledPitches() != null
                && (request.pitches() == null || request.ledPitches() > request.pitches())) {
            throw new IllegalArgumentException(msg.get("ascent.led.pitches"));
        }
    }

    private AscentDiscipline requireDiscipline(SaveAscentRequest request) {
        AscentDiscipline discipline = request.discipline();
        if (discipline == null) {
            throw new IllegalArgumentException(msg.get("ascent.discipline.required"));
        }
        return discipline;
    }

    private static void applyOptionalFields(ClimbingAscent ascent, SaveAscentRequest request) {
        ascent.setComment(request.comment());
        if (ascent.getTerrain() == AscentTerrain.MOUNTAIN) {
            ascent.setOriginalGrade(request.originalGrade());
            ascent.setLengthMeters(request.lengthMeters());
            ascent.setPitches(request.pitches());
            ascent.setDurationMinutes(request.durationMinutes());
            ascent.setLedGrade(request.ledGrade());
            ascent.setLedPitches(request.ledPitches());
            ascent.setPartners(request.partners());
        } else {
            // setAttempts normalizes OS/FLASH to a single attempt — the form hides the field for
            // those styles, so a 400 would punish the athlete for the UI's own bookkeeping
            ascent.setAttempts(request.attempts());
            ascent.setQualityStars(request.qualityStars());
        }
    }

    private ClimbingAscent requireOwnAscent(UUID ascentId, UUID userId) {
        return ascentRepository.findByIdAndAthleteId(ascentId, userId)
                // Same message as not-found: somebody else's ascent ids are not worth probing
                .orElseThrow(() -> new IllegalArgumentException(msg.get("ascent.not.found")));
    }

    /** One terrain at a time: rock and mountain entries answer different questions. */
    private AscentLogDto buildLog(UUID athleteId, AscentTerrain terrain, @Nullable String yearParam) {
        List<Integer> availableYears = ascentRepository.findYearsWithData(athleteId, terrain);
        Integer selectedYear = resolveYear(availableYears, yearParam);

        List<ClimbingAscent> entries = selectedYear == null
                ? ascentRepository.findAllForAthlete(athleteId, terrain)
                : ascentRepository.findRange(athleteId, terrain,
                        LocalDate.of(selectedYear, 1, 1), LocalDate.of(selectedYear, 12, 31));

        return new AscentLogDto(
                entries.stream().map(AscentService::toDto).toList(),
                availableYears,
                selectedYear,
                ascentRepository.countByAthleteIdAndTerrain(athleteId, terrain),
                // Place suggestions span both terrains on purpose: an area is an area, and
                // somebody logging Tatry in both forms should get the same autocomplete
                buildPlaceSuggestions(athleteId));
    }

    /**
     * Which year the response covers. {@code null} return means "all years".
     *
     * <p>No parameter defaults to the newest year with data rather than the current one: in
     * January an empty 2027 looks like the logbook lost everything. The chosen value is echoed
     * back in the DTO so the picker cannot drift from what the server actually filtered on.
     */
    @Nullable Integer resolveYear(List<Integer> availableYears, @Nullable String yearParam) {
        if (yearParam == null || yearParam.isBlank()) {
            return availableYears.isEmpty() ? null : availableYears.getFirst();
        }
        String trimmed = yearParam.trim();
        if (ALL_YEARS.equalsIgnoreCase(trimmed)) {
            return null;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(msg.get("ascent.year.invalid"));
        }
        if (parsed < MIN_YEAR || parsed > MAX_YEAR) {
            throw new IllegalArgumentException(msg.get("ascent.year.invalid"));
        }
        return parsed;
    }

    /**
     * Areas and their crags, most-used first, computed across every year — the athlete looking
     * at 2026 still needs the crag they last logged in 2019 to autocomplete.
     *
     * <p>When one normalized key has several spellings, the most recently used one is shown:
     * the grouping is on the key, so the label is a display choice and the newest is the one
     * the athlete is currently typing.
     */
    private List<PlaceSuggestionDto> buildPlaceSuggestions(UUID athleteId) {
        List<AreaUsageRow> rows = ascentRepository.findPlaceUsage(athleteId);

        Map<String, String> areaLabels = pickLabels(rows, AreaUsageRow::areaKey, AreaUsageRow::area);
        Map<String, String> cragLabels = pickLabels(rows, AreaUsageRow::cragKey, AreaUsageRow::crag);

        Map<String, Long> areaUsage = new LinkedHashMap<>();
        Map<String, Map<String, Long>> cragUsage = new LinkedHashMap<>();
        for (AreaUsageRow row : rows) {
            areaUsage.merge(row.areaKey(), row.usageCount(), Long::sum);
            cragUsage.computeIfAbsent(row.areaKey(), key -> new LinkedHashMap<>())
                    .merge(row.cragKey(), row.usageCount(), Long::sum);
        }

        return areaUsage.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(area -> new PlaceSuggestionDto(
                        areaLabels.get(area.getKey()),
                        cragUsage.getOrDefault(area.getKey(), Map.of()).entrySet().stream()
                                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                                .map(crag -> cragLabels.get(crag.getKey()))
                                .toList()))
                .toList();
    }

    private static Map<String, String> pickLabels(List<AreaUsageRow> rows,
                                                  Function<AreaUsageRow, String> key,
                                                  Function<AreaUsageRow, String> label) {
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, LocalDate> seenOn = new LinkedHashMap<>();
        for (AreaUsageRow row : rows) {
            String rowKey = key.apply(row);
            LocalDate previous = seenOn.get(rowKey);
            if (previous == null || row.lastUsedOn().isAfter(previous)) {
                seenOn.put(rowKey, row.lastUsedOn());
                labels.put(rowKey, label.apply(row));
            }
        }
        return labels;
    }

    private static AscentDto toDto(ClimbingAscent ascent) {
        ClimbingGrade led = ascent.getLedGrade();
        return new AscentDto(
                ascent.getId(),
                ascent.getTerrain().name(),
                ascent.getClimbedOn(),
                ascent.getDiscipline() != null ? ascent.getDiscipline().name() : null,
                ascent.getGrade().scale().name(),
                ascent.getGrade().name(),
                ascent.getGrade().label(),
                ascent.getGrade().rank(),
                ascent.getStyle().name(),
                ascent.getArea(),
                ascent.getCrag(),
                ascent.getRouteName(),
                ascent.getAttempts(),
                ascent.getQualityStars(),
                ascent.getComment(),
                ascent.getWinter(),
                ascent.getOriginalGrade(),
                ascent.getLengthMeters(),
                ascent.getPitches(),
                ascent.getDurationMinutes(),
                led != null ? led.name() : null,
                led != null ? led.label() : null,
                led != null ? led.rank() : null,
                ascent.getLedPitches(),
                ascent.getPartners(),
                ascent.getCreatedAt(),
                ascent.getHiddenFromPublicAt());
    }
}
