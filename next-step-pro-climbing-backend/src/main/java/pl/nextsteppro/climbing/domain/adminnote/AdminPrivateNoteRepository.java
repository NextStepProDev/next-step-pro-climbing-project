package pl.nextsteppro.climbing.domain.adminnote;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every read and every delete is addressed by the pair (author, target) — never by the note's own
 * id. That is the point: a note cannot be fetched and then compared against its owner, because
 * there is no code path that fetches one without the owner in the WHERE clause. Same trick as
 * {@code findByIdAndAthleteId} in the ascent log.
 *
 * <p>Writes are single-statement upserts rather than read-then-save, for the reason spelled out on
 * {@code AthleteWeightRepository.upsertReading}: a second tab or a double-click loses the race on
 * the unique index and surfaces as a 500. Overwriting is the correct outcome — the author is
 * correcting their own note.
 *
 * <p>The three near-identical upserts are the price of real foreign keys: a single
 * {@code (target_type, target_id)} column would collapse them into one statement, but would also
 * outlive the slot, event or training it describes. See the header of V89.
 */
public interface AdminPrivateNoteRepository extends JpaRepository<AdminPrivateNote, UUID> {

    @Query("SELECT n FROM AdminPrivateNote n WHERE n.author.id = :authorId AND n.timeSlot.id = :slotId")
    Optional<AdminPrivateNote> findForSlot(@Param("authorId") UUID authorId, @Param("slotId") UUID slotId);

    @Query("SELECT n FROM AdminPrivateNote n WHERE n.author.id = :authorId AND n.event.id = :eventId")
    Optional<AdminPrivateNote> findForEvent(@Param("authorId") UUID authorId, @Param("eventId") UUID eventId);

    @Query("SELECT n FROM AdminPrivateNote n WHERE n.author.id = :authorId AND n.training.id = :trainingId")
    Optional<AdminPrivateNote> findForTraining(@Param("authorId") UUID authorId, @Param("trainingId") UUID trainingId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO admin_private_notes (author_id, time_slot_id, body, updated_at)
        VALUES (:authorId, :slotId, :body, :updatedAt)
        ON CONFLICT (author_id, time_slot_id) WHERE time_slot_id IS NOT NULL
        DO UPDATE SET body = :body, updated_at = :updatedAt
        """, nativeQuery = true)
    void upsertForSlot(@Param("authorId") UUID authorId,
                       @Param("slotId") UUID slotId,
                       @Param("body") String body,
                       @Param("updatedAt") Instant updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO admin_private_notes (author_id, event_id, body, updated_at)
        VALUES (:authorId, :eventId, :body, :updatedAt)
        ON CONFLICT (author_id, event_id) WHERE event_id IS NOT NULL
        DO UPDATE SET body = :body, updated_at = :updatedAt
        """, nativeQuery = true)
    void upsertForEvent(@Param("authorId") UUID authorId,
                        @Param("eventId") UUID eventId,
                        @Param("body") String body,
                        @Param("updatedAt") Instant updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO admin_private_notes (author_id, training_id, body, updated_at)
        VALUES (:authorId, :trainingId, :body, :updatedAt)
        ON CONFLICT (author_id, training_id) WHERE training_id IS NOT NULL
        DO UPDATE SET body = :body, updated_at = :updatedAt
        """, nativeQuery = true)
    void upsertForTraining(@Param("authorId") UUID authorId,
                           @Param("trainingId") UUID trainingId,
                           @Param("body") String body,
                           @Param("updatedAt") Instant updatedAt);

    /**
     * Which sessions in a date range the author has already written about — ids only, never text.
     * The calendar draws a marker from this so the owner can see where a note exists without
     * opening every session; the text stays behind the per-session read.
     *
     * <p>Each query navigates into its target, so the implicit inner join drops the rows whose
     * target is one of the other two — no separate "is a slot note" predicate to keep in sync.
     */
    @Query("SELECT n.timeSlot.id FROM AdminPrivateNote n "
        + "WHERE n.author.id = :authorId AND n.timeSlot.date BETWEEN :from AND :to")
    List<UUID> findSlotIdsWithNote(@Param("authorId") UUID authorId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    /**
     * The days those slots sit on. Not derivable on the client: a month cell knows its date but
     * not which slots belong to it — the month payload carries counts, not slot ids — so this is
     * the only thing that cell can match a marker against.
     */
    @Query("SELECT DISTINCT n.timeSlot.date FROM AdminPrivateNote n "
        + "WHERE n.author.id = :authorId AND n.timeSlot.date BETWEEN :from AND :to")
    List<LocalDate> findSlotDatesWithNote(@Param("authorId") UUID authorId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    /** Events are spans, so "in range" is an overlap, not a containment. */
    @Query("SELECT n.event.id FROM AdminPrivateNote n "
        + "WHERE n.author.id = :authorId AND n.event.startDate <= :to AND n.event.endDate >= :from")
    List<UUID> findEventIdsWithNote(@Param("authorId") UUID authorId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);

    @Query("SELECT n.training.id FROM AdminPrivateNote n "
        + "WHERE n.author.id = :authorId AND n.training.trainingDate BETWEEN :from AND :to")
    List<UUID> findTrainingIdsWithNote(@Param("authorId") UUID authorId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AdminPrivateNote n WHERE n.author.id = :authorId AND n.timeSlot.id = :slotId")
    int deleteForSlot(@Param("authorId") UUID authorId, @Param("slotId") UUID slotId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AdminPrivateNote n WHERE n.author.id = :authorId AND n.event.id = :eventId")
    int deleteForEvent(@Param("authorId") UUID authorId, @Param("eventId") UUID eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AdminPrivateNote n WHERE n.author.id = :authorId AND n.training.id = :trainingId")
    int deleteForTraining(@Param("authorId") UUID authorId, @Param("trainingId") UUID trainingId);
}
