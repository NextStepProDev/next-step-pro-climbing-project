package pl.nextsteppro.climbing.domain.personaltraining;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * A single message in the athlete &lt;-&gt; coach thread.
 *
 * <p>The thread hangs on exactly one of three things: an entry in the 1:1 plan, a booked slot, or
 * a booked event. The last two are what lets the pair keep talking under a session that came from
 * the public calendar — before that, whether a conversation was possible at all depended on which
 * tool the coach had used to create the session, which is invisible from the athlete's side.
 *
 * <p>The address of a conversation is the PAIR (target, athlete), never the target alone: two
 * people can stand on one slot and each thread is private. {@code athlete} is therefore stored on
 * every row, including the ones a training already implies — the unread queries used to reach the
 * athlete through {@code training.athlete}, and a row without a training has no such road. Spelling
 * the owner out leaves those queries with nothing to branch on.
 *
 * <p>⚠️ The target is deliberately NOT a reservation, the same rule money follows: booking a
 * multi-day event lays down one reservation row PER DAY, so a thread on the reservation would split
 * a three-day course into three conversations, and cancelling plus re-booking (a new row) would
 * lose the history. A reservation is only the address on the wire — it is the one thing carrying
 * both halves of the pair, so the service resolves it into (target, athlete).
 *
 * <p>{@code authorIsAdmin} records the author's role at the time of writing (robust against
 * later role changes) and drives the unread counters: the athlete counts coach messages,
 * the coach counts athlete messages.
 *
 * <p>{@code editedAt} is not audit metadata — it feeds those same counters. The queries used to ask
 * {@code createdAt > seen} alone, which would have made an edit completely silent: the reader who
 * already saw "3x10" would never learn it now says "4x8". Since the author may edit at any time, the
 * re-raised unread mark is the only thing that keeps editing honest — the shape
 * {@link PersonalTraining} already uses for its own edits.
 */
@Entity
@Table(name = "training_comments")
public class TrainingComment {

    public static final int MAX_BODY_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Whose calendar this conversation belongs to. Redundant for a training-attached message and
    // deliberately stored anyway — see the class comment.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private User athlete;

    // Exactly one of the three is set (chk_training_comments_single_target). Three real FKs rather
    // than a (target_type, target_id) pair, because ON DELETE CASCADE is the mechanism here: a
    // discriminator pair would leave somebody's conversation behind a deleted slot with nothing to
    // sweep it and no screen left to delete it from.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id")
    @Nullable
    private PersonalTraining training;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id")
    @Nullable
    private TimeSlot timeSlot;

    // A multi-day event carries ONE conversation, so the thread hangs on the event and never on the
    // per-day slots the first booking lays down for it (the service refuses a slot with an event).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    @Nullable
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "author_is_admin", nullable = false)
    private boolean authorIsAdmin;

    // Nullable since V80: a message can be nothing but an attachment ("look, this is how it went").
    // "Neither text nor file" is refused by TrainingCommentFileService — the condition reaches
    // another table, so it cannot be a CHECK.
    @Column(length = MAX_BODY_LENGTH)
    @Nullable
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Null until the author corrects their own words. NOT stamped on insert: "never edited" is the
    // fact both the "(edited)" badge and the unread queries need, and a timestamp equal to createdAt
    // would make each of them reconstruct it by comparing two clocks.
    @Column(name = "edited_at")
    @Nullable
    private Instant editedAt;

    protected TrainingComment() {}

    private TrainingComment(User athlete, User author, boolean authorIsAdmin, @Nullable String body) {
        this.athlete = athlete;
        this.author = author;
        this.authorIsAdmin = authorIsAdmin;
        this.body = body;
    }

    /** Message under an entry of the 1:1 plan. The owner is read off the entry, never passed in. */
    public static TrainingComment onTraining(PersonalTraining training, User author,
                                             boolean authorIsAdmin, @Nullable String body) {
        TrainingComment comment = new TrainingComment(training.getAthlete(), author, authorIsAdmin, body);
        comment.training = training;
        return comment;
    }

    /** Message under a booked slot of the public calendar. */
    public static TrainingComment onSlot(User athlete, TimeSlot slot, User author,
                                         boolean authorIsAdmin, @Nullable String body) {
        TrainingComment comment = new TrainingComment(athlete, author, authorIsAdmin, body);
        comment.timeSlot = slot;
        return comment;
    }

    /** Message under a booked event — one conversation for the whole event, however many days. */
    public static TrainingComment onEvent(User athlete, Event event, User author,
                                          boolean authorIsAdmin, @Nullable String body) {
        TrainingComment comment = new TrainingComment(athlete, author, authorIsAdmin, body);
        comment.event = event;
        return comment;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /**
     * Replaces the text with an already-sanitized body and stamps the edit.
     *
     * <p>A named method rather than a setter plus {@code @PreUpdate}: the entity is write-once apart
     * from this one path, so the single way to mutate it should be visible at the call site instead
     * of happening inside a lifecycle hook.
     */
    public void edit(String sanitizedBody) {
        this.body = sanitizedBody;
        this.editedAt = Instant.now();
    }

    /** HTML-escapes and trims the message (UTF-8 variant — keeps Polish diacritics intact). */
    @Nullable
    public static String sanitizeBody(@Nullable String body) {
        if (body == null || body.isBlank()) return null;
        String escaped = HtmlUtils.htmlEscape(body.trim(), java.nio.charset.StandardCharsets.UTF_8.name());
        return escaped.length() > MAX_BODY_LENGTH ? escaped.substring(0, MAX_BODY_LENGTH) : escaped;
    }

    public UUID getId() {
        return id;
    }

    public User getAthlete() {
        return athlete;
    }

    @Nullable
    public PersonalTraining getTraining() {
        return training;
    }

    public User getAuthor() {
        return author;
    }

    public boolean isAuthorIsAdmin() {
        return authorIsAdmin;
    }

    @Nullable
    public String getBody() {
        return body;
    }

    /**
     * FK access without initialising the lazy proxy. NOT named {@code getTrainingId()} — a
     * "trainingId" bean property makes Spring Data derive {@code findByTrainingId} as the invalid
     * path {@code c.trainingId} instead of {@code c.training.id} (see {@link TrainingAttachment}).
     *
     * <p>Null since V97: the message may hang on a booked session instead.
     */
    @Nullable
    public UUID trainingId() {
        return training != null ? training.getId() : null;
    }

    public UUID athleteId() {
        return athlete.getId();
    }

    @Nullable
    public UUID timeSlotId() {
        return timeSlot != null ? timeSlot.getId() : null;
    }

    @Nullable
    public UUID eventId() {
        return event != null ? event.getId() : null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public Instant getEditedAt() {
        return editedAt;
    }
}
