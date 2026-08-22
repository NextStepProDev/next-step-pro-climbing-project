package pl.nextsteppro.climbing.domain.climbingascent;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.user.User;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One completed ascent in an athlete's logbook.
 *
 * <p>Completed is the whole definition: there are no attempts and no open projects here. The
 * pyramid and the onsight rate count ascents, so a "tried it" row would land in the denominator
 * of both and turn a record of achievements into a record of attendance.
 *
 * <p>Only the athlete writes these. The coach reads and exports them but has no write endpoint,
 * exactly as with body weight — logging somebody else's send is not the coach's call.
 *
 * <p>{@link #grade} and {@link #discipline} are not independent: the discipline picks the scale
 * (V82 enforces it through the {@code FR_}/{@code FB_} prefix), which is why {@link #setGrade}
 * and {@link #setDiscipline} refuse a mismatched pair rather than letting the constraint catch it
 * on flush.
 */
@Entity
@Table(name = "climbing_ascents")
public class ClimbingAscent {

    public static final int MAX_PLACE_LENGTH = 120;
    public static final int MAX_ROUTE_NAME_LENGTH = 160;
    public static final int MAX_COMMENT_LENGTH = 2000;
    public static final int MAX_ORIGINAL_GRADE_LENGTH = 40;
    public static final int MAX_PARTNERS_LENGTH = 300;

    /** Wide enough for any wall on earth, narrow enough to catch a slipped digit. */
    public static final int MAX_LENGTH_METERS = 4000;
    public static final int MAX_PITCHES = 60;
    /** 30 days — longer than the longest recorded pushes. */
    public static final int MAX_DURATION_MINUTES = 43200;

    /** An ascent took at least the one attempt that worked; the ceiling catches a slipped digit. */
    public static final int MIN_ATTEMPTS = 1;
    public static final int MAX_ATTEMPTS = 9999;

    public static final int MIN_STARS = 0;
    public static final int MAX_STARS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private User athlete;

    @Column(name = "climbed_on", nullable = false)
    private LocalDate climbedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "terrain", nullable = false, length = 10)
    private AscentTerrain terrain = AscentTerrain.ROCK;

    /** Rock only. In the mountains the season tells entries apart, not the kind of crag. */
    @Enumerated(EnumType.STRING)
    @Column(name = "discipline", length = 20)
    private @Nullable AscentDiscipline discipline;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false, length = 20)
    private ClimbingGrade grade;

    @Enumerated(EnumType.STRING)
    @Column(name = "style", nullable = false, length = 10)
    private AscentStyle style;

    @Column(name = "area", nullable = false, length = MAX_PLACE_LENGTH)
    private String area;

    @Column(name = "area_key", nullable = false, length = MAX_PLACE_LENGTH)
    private String areaKey;

    @Column(name = "crag", nullable = false, length = MAX_PLACE_LENGTH)
    private String crag;

    @Column(name = "crag_key", nullable = false, length = MAX_PLACE_LENGTH)
    private String cragKey;

    @Column(name = "route_name", nullable = false, length = MAX_ROUTE_NAME_LENGTH)
    private String routeName;

    @Column(name = "attempts")
    private @Nullable Integer attempts;

    @Column(name = "quality_stars")
    private @Nullable Integer qualityStars;

    @Column(name = "comment", columnDefinition = "TEXT")
    private @Nullable String comment;

    // ---- mountain-only fields ------------------------------------------------------------

    /** Winter ascents of the same route are a different undertaking, so this is stored, not guessed. */
    @Column(name = "winter")
    private @Nullable Boolean winter;

    /** The guidebook's own grade ("V", "UIAA VI", "WI4") — a quotation, never a grouping key. */
    @Column(name = "original_grade", length = MAX_ORIGINAL_GRADE_LENGTH)
    private @Nullable String originalGrade;

    @Column(name = "length_meters")
    private @Nullable Integer lengthMeters;

    @Column(name = "pitches")
    private @Nullable Integer pitches;

    /** Stored in minutes though entered in hours — minutes add up without fractions. */
    @Column(name = "duration_minutes")
    private @Nullable Integer durationMinutes;

    /** What the AUTHOR led, as opposed to what the route was graded. */
    @Enumerated(EnumType.STRING)
    @Column(name = "led_grade", length = 20)
    private @Nullable ClimbingGrade ledGrade;

    @Column(name = "led_pitches")
    private @Nullable Integer ledPitches;

    @Column(name = "partners", length = MAX_PARTNERS_LENGTH)
    private @Nullable String partners;

    /**
     * When the site owner took this entry off the public list; {@code null} means it is published
     * normally. Not the author's field: their wish is {@code users.ascents_public}, which covers
     * their whole logbook. This one says "this row does not belong on my noticeboard" and only
     * the admin can set or clear it — see V90.
     */
    @Column(name = "hidden_from_public_at")
    private @Nullable Instant hiddenFromPublicAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClimbingAscent() {}

    private ClimbingAscent(User athlete, AscentTerrain terrain, LocalDate climbedOn,
                           ClimbingGrade grade, AscentStyle style,
                           String area, String crag, String routeName) {
        this.athlete = athlete;
        this.terrain = terrain;
        this.climbedOn = climbedOn;
        this.grade = grade;
        this.style = style;
        setArea(area);
        setCrag(crag);
        this.routeName = routeName.trim();
    }

    /** A crag or boulder ascent: carries a discipline, and the discipline picks the grade scale. */
    public static ClimbingAscent rock(User athlete, LocalDate climbedOn, AscentDiscipline discipline,
                                      ClimbingGrade grade, AscentStyle style,
                                      String area, String crag, String routeName) {
        requireMatchingScale(AscentTerrain.ROCK, discipline, grade);
        requireAllowedStyle(discipline, style);
        ClimbingAscent ascent = new ClimbingAscent(athlete, AscentTerrain.ROCK, climbedOn,
                grade, style, area, crag, routeName);
        ascent.discipline = discipline;
        return ascent;
    }

    /**
     * A mountain ascent: no discipline, a season instead, and the unified grade is French — the
     * same axis the crags use, which is what lets one pyramid describe both.
     *
     * <p>{@code crag} carries the summit here. It is the same slot in the same place hierarchy
     * ("Tatry Wysokie" → "Mnich"), so autocomplete and the area ranking work unchanged; only the
     * label in the form differs.
     */
    public static ClimbingAscent mountain(User athlete, LocalDate climbedOn, boolean winter,
                                          ClimbingGrade grade, AscentStyle style,
                                          String area, String summit, String routeName) {
        requireMatchingScale(AscentTerrain.MOUNTAIN, null, grade);
        ClimbingAscent ascent = new ClimbingAscent(athlete, AscentTerrain.MOUNTAIN, climbedOn,
                grade, style, area, summit, routeName);
        ascent.winter = winter;
        return ascent;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public User getAthlete() {
        return athlete;
    }

    public LocalDate getClimbedOn() {
        return climbedOn;
    }

    public void setClimbedOn(LocalDate climbedOn) {
        this.climbedOn = climbedOn;
    }

    public AscentTerrain getTerrain() {
        return terrain;
    }

    public @Nullable AscentDiscipline getDiscipline() {
        return discipline;
    }

    /**
     * Changing the discipline changes the scale, so the grade has to move with it. The caller
     * passes both because there is no honest way to convert one grade into the other — a
     * conversion table between French and Font is guesswork, and applying it silently would
     * credit the athlete with an ascent they never made.
     */
    public void setDisciplineAndGrade(AscentDiscipline discipline, ClimbingGrade grade) {
        requireMatchingScale(AscentTerrain.ROCK, discipline, grade);
        requireAllowedStyle(discipline, style);
        this.discipline = discipline;
        this.grade = grade;
    }

    public ClimbingGrade getGrade() {
        return grade;
    }

    public void setGrade(ClimbingGrade grade) {
        requireMatchingScale(terrain, discipline, grade);
        this.grade = grade;
    }

    public AscentStyle getStyle() {
        return style;
    }

    public void setStyle(AscentStyle style) {
        requireAllowedStyle(discipline, style);
        this.style = style;
    }

    // ---- mountain-only accessors ---------------------------------------------------------

    public @Nullable Boolean getWinter() {
        return winter;
    }

    public void setWinter(@Nullable Boolean winter) {
        this.winter = winter;
    }

    public @Nullable String getOriginalGrade() {
        return originalGrade;
    }

    public void setOriginalGrade(@Nullable String originalGrade) {
        this.originalGrade = blankToNull(originalGrade);
    }

    public @Nullable Integer getLengthMeters() {
        return lengthMeters;
    }

    public void setLengthMeters(@Nullable Integer lengthMeters) {
        this.lengthMeters = lengthMeters;
    }

    public @Nullable Integer getPitches() {
        return pitches;
    }

    public void setPitches(@Nullable Integer pitches) {
        this.pitches = pitches;
    }

    public @Nullable Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(@Nullable Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public @Nullable ClimbingGrade getLedGrade() {
        return ledGrade;
    }

    /** What the author led. Same axis as the route itself, so a Font grade here is a bug. */
    public void setLedGrade(@Nullable ClimbingGrade ledGrade) {
        if (ledGrade != null && ledGrade.scale() != GradeScale.FRENCH_ROUTE) {
            throw new IllegalArgumentException("Led grade must be on the French route scale");
        }
        this.ledGrade = ledGrade;
    }

    public @Nullable Integer getLedPitches() {
        return ledPitches;
    }

    public void setLedPitches(@Nullable Integer ledPitches) {
        this.ledPitches = ledPitches;
    }

    public @Nullable String getPartners() {
        return partners;
    }

    public void setPartners(@Nullable String partners) {
        this.partners = blankToNull(partners);
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    public String getArea() {
        return area;
    }

    /** Keeps {@code areaKey} in step, so a row with a stale grouping key cannot be persisted. */
    public void setArea(String area) {
        this.area = area.trim();
        this.areaKey = AscentTextKey.normalize(area);
    }

    public String getAreaKey() {
        return areaKey;
    }

    public String getCrag() {
        return crag;
    }

    public void setCrag(String crag) {
        this.crag = crag.trim();
        this.cragKey = AscentTextKey.normalize(crag);
    }

    public String getCragKey() {
        return cragKey;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName.trim();
    }

    public @Nullable Integer getAttempts() {
        return attempts;
    }

    /**
     * OS and FLASH mean "first go" by definition, so the value is normalized rather than
     * refused — the form hides the field for those styles, and a 400 for something the athlete
     * never typed would be a penalty for the UI's own bookkeeping.
     */
    public void setAttempts(@Nullable Integer attempts) {
        // Written as an if rather than a ternary on purpose: mixing int and Integer in the two
        // branches unboxes BOTH, so `first ? MIN_ATTEMPTS : attempts` throws on a null attempts
        if (attempts != null && style.isFirstTry()) {
            this.attempts = MIN_ATTEMPTS;
        } else {
            this.attempts = attempts;
        }
    }

    public @Nullable Integer getQualityStars() {
        return qualityStars;
    }

    public void setQualityStars(@Nullable Integer qualityStars) {
        this.qualityStars = qualityStars;
    }

    public @Nullable String getComment() {
        return comment;
    }

    public void setComment(@Nullable String comment) {
        this.comment = (comment == null || comment.isBlank()) ? null : comment.trim();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable Instant getHiddenFromPublicAt() {
        return hiddenFromPublicAt;
    }

    /**
     * Takes this entry off the public list, or puts it back. Deliberately the only thing an admin
     * may change about somebody else's ascent: publication is the owner's call, the content never
     * is. Re-taking down an already hidden entry keeps the original timestamp — the interesting
     * moment is when it came off, not when the button was last pressed.
     */
    public void setHiddenFromPublic(boolean hidden, Instant now) {
        if (!hidden) {
            hiddenFromPublicAt = null;
        } else if (hiddenFromPublicAt == null) {
            hiddenFromPublicAt = now;
        }
    }

    /**
     * The unified grade is French everywhere except bouldering — including in the mountains,
     * which is what lets one pyramid cover both terrains.
     */
    private static void requireMatchingScale(AscentTerrain terrain,
                                             @Nullable AscentDiscipline discipline,
                                             ClimbingGrade grade) {
        GradeScale expected = (terrain == AscentTerrain.ROCK && discipline != null)
                ? discipline.scale()
                : GradeScale.FRENCH_ROUTE;
        if (grade.scale() != expected) {
            throw new IllegalArgumentException(
                    "Grade " + grade + " does not belong to " + expected);
        }
    }

    private static void requireAllowedStyle(@Nullable AscentDiscipline discipline, AscentStyle style) {
        if (discipline == null) {
            // Mountains: every style applies, so there is nothing to refuse
            return;
        }
        if (!discipline.allows(style)) {
            throw new IllegalArgumentException("Style " + style + " does not apply to " + discipline);
        }
    }
}
