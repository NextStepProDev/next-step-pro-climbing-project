package pl.nextsteppro.climbing.api.admin.note;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTraining;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The owner's private notes over real PostgreSQL (Flyway V89).
 *
 * <p>The DDL is half the point: the XOR over three targets, the partial unique index that makes one
 * note per (author, target) and lets the upsert name a conflict target, the blank-body CHECK, and
 * the cascades that let a note die with the session it describes — none of which a mocked
 * repository can prove.
 *
 * <p>The other half is the privacy rule, which is not a permission check but an absence: nothing
 * outside this feature can read a note. {@code PrivateNoteIsolationTest} guards that from the other
 * side, over the whole source tree.
 *
 * <p>Lives in this package (not integration/) because the DTO records are package-private.
 */
class AdminPrivateNoteIntegrationTest extends BaseIntegrationTest {

    @Autowired private AdminNoteService noteService;
    @Autowired private PersonalTrainingRepository trainingRepository;
    @Autowired private JdbcTemplate jdbc;

    private User owner;
    private User otherAdmin;
    private User athlete;
    private TimeSlot slot;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM admin_private_notes");
        trainingRepository.deleteAll();
        timeSlotRepository.deleteAll();
        eventRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        owner = userRepository.saveAndFlush(admin("owner@example.com", "Właściciel", "Szkoły"));
        otherAdmin = userRepository.saveAndFlush(admin("second@example.com", "Drugi", "Admin"));

        athlete = new User("athlete@example.com", "Anna", "Wspinaczka", "+48123456789", "anna");
        athlete.setRole(UserRole.USER);
        athlete.setEmailVerified(true);
        athlete.setAthlete(true);
        // Coach-side lookups go through requireFlaggedAthlete, which reads the flag (V76 consent
        // is the athlete's own gate and not involved here).
        athlete = userRepository.saveAndFlush(athlete);

        // saveAndFlush, not save: the raw-JDBC tests below write foreign keys to these rows, and
        // an unflushed persist is invisible to a statement that bypasses the persistence context.
        slot = timeSlotRepository.saveAndFlush(
            new TimeSlot(LocalDate.now().minusDays(3), LocalTime.of(17, 0), LocalTime.of(19, 0), 6));
    }

    /**
     * Asserts the DDL refused for the stated reason. A bare DataIntegrityViolationException would
     * also be thrown by an unrelated foreign key, so a constraint test that only checks the
     * exception type can pass while proving nothing.
     */
    private static void assertViolates(String constraint, org.junit.jupiter.api.function.Executable write) {
        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class, write);
        String detail = String.valueOf(thrown.getMostSpecificCause().getMessage());
        assertTrue(detail.contains(constraint),
            "Expected " + constraint + " to refuse this row, but the database said: " + detail);
    }

    private static User admin(String email, String firstName, String lastName) {
        User user = new User(email, firstName, lastName, "+48111111111", "pwd");
        user.setRole(UserRole.ADMIN);
        user.setEmailVerified(true);
        return user;
    }

    private PersonalTraining trainingForAthlete() {
        return trainingRepository.saveAndFlush(new PersonalTraining(
            athlete, LocalDate.now().minusDays(1), LocalTime.of(18, 0), LocalTime.of(20, 0),
            "Wytrzymałość", null, true));
    }

    private long noteCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM admin_private_notes", Long.class);
        return count == null ? 0 : count;
    }

    // ---------- the rule the feature exists for ----------

    @Test
    @DisplayName("shouldKeepEachAdminsNotePrivateFromTheOther")
    void shouldKeepEachAdminsNotePrivateFromTheOther() {
        // Given: the owner writes about a session
        noteService.saveNote(owner.getId(), "slot", slot.getId(),
            new SaveAdminNoteRequest("Marek dalej boi się wyklepania"));

        // When: a second admin opens the very same slot
        AdminNoteDto seenByOther = noteService.getNote(otherAdmin.getId(), "slot", slot.getId());

        // Then: he sees his own notebook, which is empty — not the owner's
        assertNull(seenByOther.body());
        assertNull(seenByOther.updatedAt());
        assertEquals("Marek dalej boi się wyklepania",
            noteService.getNote(owner.getId(), "slot", slot.getId()).body());
    }

    @Test
    @DisplayName("shouldLetTheSecondAdminKeepHisOwnNoteOnTheSameSession")
    void shouldLetTheSecondAdminKeepHisOwnNoteOnTheSameSession() {
        // Given / When: two admins each write about the same slot
        noteService.saveNote(owner.getId(), "slot", slot.getId(), new SaveAdminNoteRequest("Moja wersja"));
        noteService.saveNote(otherAdmin.getId(), "slot", slot.getId(), new SaveAdminNoteRequest("Jego wersja"));

        // Then: the unique index is on (author, target), so both rows live
        assertEquals(2, noteCount());
        assertEquals("Moja wersja", noteService.getNote(owner.getId(), "slot", slot.getId()).body());
        assertEquals("Jego wersja", noteService.getNote(otherAdmin.getId(), "slot", slot.getId()).body());
    }

    @Test
    @DisplayName("shouldTreatASecondSaveAsACorrection")
    void shouldTreatASecondSaveAsACorrection() {
        // Given
        noteService.saveNote(owner.getId(), "slot", slot.getId(), new SaveAdminNoteRequest("Pierwsza myśl"));

        // When: the author saves again — a double-click, a second tab, or an actual edit
        noteService.saveNote(owner.getId(), "slot", slot.getId(), new SaveAdminNoteRequest("Po namyśle"));

        // Then: one row, the newer text. Read-then-save would have lost the race and thrown a 500.
        assertEquals(1, noteCount());
        assertEquals("Po namyśle", noteService.getNote(owner.getId(), "slot", slot.getId()).body());
    }

    @Test
    @DisplayName("shouldReadAnEmptyNoteRatherThanNothingWhenNoneWasWritten")
    void shouldReadAnEmptyNoteRatherThanNothingWhenNoneWasWritten() {
        // A 200 with an empty shape, so the client never has to interpret a missing body
        AdminNoteDto dto = noteService.getNote(owner.getId(), "slot", slot.getId());
        assertNull(dto.body());
        assertNull(dto.updatedAt());
    }

    @Test
    @DisplayName("shouldDeleteIdempotently")
    void shouldDeleteIdempotently() {
        noteService.saveNote(owner.getId(), "slot", slot.getId(), new SaveAdminNoteRequest("Do skasowania"));

        noteService.deleteNote(owner.getId(), "slot", slot.getId());
        // Deleting again is a success, not a 404 — the caller's intent is already satisfied
        assertDoesNotThrow(() -> noteService.deleteNote(owner.getId(), "slot", slot.getId()));
        assertEquals(0, noteCount());
    }

    @Test
    @DisplayName("shouldNotLetOneAdminDeleteAnothersNote")
    void shouldNotLetOneAdminDeleteAnothersNote() {
        noteService.saveNote(owner.getId(), "slot", slot.getId(), new SaveAdminNoteRequest("Moje"));

        noteService.deleteNote(otherAdmin.getId(), "slot", slot.getId());

        // The delete is scoped to (author, target), so it matched nothing
        assertEquals("Moje", noteService.getNote(owner.getId(), "slot", slot.getId()).body());
    }

    // ---------- one note per event, however many days ----------

    @Test
    @DisplayName("shouldKeepOneNotePerEventHoweverManyDaysItSpans")
    void shouldKeepOneNotePerEventHoweverManyDaysItSpans() {
        Event event = eventRepository.save(new Event("Kurs skalny", EventType.COURSE,
            LocalDate.now().minusDays(5), LocalDate.now().minusDays(3), 10));

        noteService.saveNote(owner.getId(), "event", event.getId(), new SaveAdminNoteRequest("Dzień 2 padał deszcz"));
        noteService.saveNote(owner.getId(), "event", event.getId(), new SaveAdminNoteRequest("Dzień 2 padał deszcz, dzień 3 super"));

        assertEquals(1, noteCount());
        assertEquals("Dzień 2 padał deszcz, dzień 3 super",
            noteService.getNote(owner.getId(), "event", event.getId()).body());
    }

    @Test
    @DisplayName("shouldRejectANoteOnASlotThatBelongsToAnEvent")
    void shouldRejectANoteOnASlotThatBelongsToAnEvent() {
        // Given: the per-day bookkeeping slot that the first booking creates for an event.
        // The admin never sees it — both admin listings filter belongsToEvent out.
        Event event = eventRepository.save(new Event("Kurs skalny", EventType.COURSE,
            LocalDate.now().minusDays(5), LocalDate.now().minusDays(3), 10));
        TimeSlot eventDay = timeSlotRepository.save(new TimeSlot(
            event, LocalDate.now().minusDays(4), LocalTime.of(9, 0), LocalTime.of(17, 0), 10));

        // When / Then: writing here would be a second, invisible "note about the event"
        assertThrows(IllegalArgumentException.class, () ->
            noteService.saveNote(owner.getId(), "slot", eventDay.getId(), new SaveAdminNoteRequest("Nie tu")));
        assertEquals(0, noteCount());
    }

    // ---------- the athlete privacy boundary ----------

    @Test
    @DisplayName("shouldAcceptATrainingNoteForAFlaggedAthlete")
    void shouldAcceptATrainingNoteForAFlaggedAthlete() {
        PersonalTraining training = trainingForAthlete();

        noteService.saveNote(owner.getId(), "training", training.getId(),
            new SaveAdminNoteRequest("Nogi martwe, następnym razem lżej"));

        assertEquals("Nogi martwe, następnym razem lżej",
            noteService.getNote(owner.getId(), "training", training.getId()).body());
    }

    @Test
    @DisplayName("shouldStillLetTheAuthorEraseANoteAfterTheAthleteFlagIsGone")
    void shouldStillLetTheAuthorEraseANoteAfterTheAthleteFlagIsGone() {
        // Given: a note written while the athlete was still flagged
        PersonalTraining training = trainingForAthlete();
        noteService.saveNote(owner.getId(), "training", training.getId(), new SaveAdminNoteRequest("Do usunięcia"));
        athlete.setAthlete(false);
        userRepository.saveAndFlush(athlete);

        // When / Then: erasure must stay reachable. Gating delete would strand the row —
        // invisible (the coach calendar needs the flag) and undeletable, which is somebody's
        // personal data with no path out.
        assertDoesNotThrow(() -> noteService.deleteNote(owner.getId(), "training", training.getId()));
        assertEquals(0, noteCount());
    }

    @Test
    @DisplayName("shouldRejectATrainingNoteOnceTheAthleteFlagIsGone")
    void shouldRejectATrainingNoteOnceTheAthleteFlagIsGone() {
        // Given: a training whose athlete has since been de-flagged (which also wipes the consent)
        PersonalTraining training = trainingForAthlete();
        athlete.setAthlete(false);
        userRepository.saveAndFlush(athlete);

        // When / Then: same boundary as the rest of the coach side — the plan is no longer reachable
        assertThrows(IllegalArgumentException.class, () ->
            noteService.saveNote(owner.getId(), "training", training.getId(), new SaveAdminNoteRequest("Nie wolno")));
        assertThrows(IllegalArgumentException.class, () ->
            noteService.getNote(owner.getId(), "training", training.getId()));
    }

    // ---------- targets ----------

    @Test
    @DisplayName("shouldRejectAnUnknownTargetType")
    void shouldRejectAnUnknownTargetType() {
        assertThrows(IllegalArgumentException.class, () ->
            noteService.getNote(owner.getId(), "reservation", slot.getId()));
    }

    @Test
    @DisplayName("shouldRejectANoteOnASessionThatDoesNotExist")
    void shouldRejectANoteOnASessionThatDoesNotExist() {
        assertThrows(IllegalArgumentException.class, () ->
            noteService.saveNote(owner.getId(), "slot", UUID.randomUUID(), new SaveAdminNoteRequest("Donikąd")));
    }

    @Test
    @DisplayName("shouldRejectAWhitespaceOnlyNote")
    void shouldRejectAWhitespaceOnlyNote() {
        // Bean Validation catches "" at the controller; this is the trim-to-nothing case behind it
        assertThrows(IllegalArgumentException.class, () ->
            noteService.saveNote(owner.getId(), "slot", slot.getId(), new SaveAdminNoteRequest("   \n  ")));
    }

    @Test
    @DisplayName("shouldKeepTheAuthorsPunctuationExactlyAsTyped")
    void shouldKeepTheAuthorsPunctuationExactlyAsTyped() {
        // Deliberately not HTML-escaped: the author is the only reader, and escaping at write is
        // what forces decodeHtmlEntities on render everywhere else in the app.
        String typed = "Kasia & Marek: \"projekt\" 7a+ <ciężko>, ale 'da się'";

        noteService.saveNote(owner.getId(), "slot", slot.getId(), new SaveAdminNoteRequest(typed));

        assertEquals(typed, noteService.getNote(owner.getId(), "slot", slot.getId()).body());
    }

    // ---------- markers: where the calendar draws the icon ----------

    @Test
    @DisplayName("shouldMarkOnlyTheSessionsInsideTheAskedRange")
    void shouldMarkOnlyTheSessionsInsideTheAskedRange() {
        // Given: notes on a slot inside the window and on one well outside it
        TimeSlot outside = timeSlotRepository.saveAndFlush(new TimeSlot(
            LocalDate.now().minusDays(40), LocalTime.of(17, 0), LocalTime.of(19, 0), 6));
        noteService.saveNote(owner.getId(), "slot", slot.getId(), new SaveAdminNoteRequest("W oknie"));
        noteService.saveNote(owner.getId(), "slot", outside.getId(), new SaveAdminNoteRequest("Poza oknem"));

        // When: the calendar asks for the week around the near slot
        AdminNoteMarkersDto markers = noteService.getMarkers(
            owner.getId(), LocalDate.now().minusDays(7), LocalDate.now());

        // Then
        assertEquals(List.of(slot.getId()), markers.slotIds());
        assertEquals(List.of(slot.getDate()), markers.slotDates());
    }

    @Test
    @DisplayName("shouldMarkAnEventThatMerelyOVERLAPSTheRange")
    void shouldMarkAnEventThatMerelyOverlapsTheRange() {
        // Given: a course that starts before the window and ends inside it — an event is a span,
        // so "in range" has to be an overlap, not a containment
        Event event = eventRepository.saveAndFlush(new Event("Kurs skalny", EventType.COURSE,
            LocalDate.now().minusDays(9), LocalDate.now().minusDays(5), 10));
        noteService.saveNote(owner.getId(), "event", event.getId(), new SaveAdminNoteRequest("Padał deszcz"));

        AdminNoteMarkersDto markers = noteService.getMarkers(
            owner.getId(), LocalDate.now().minusDays(7), LocalDate.now());

        assertEquals(List.of(event.getId()), markers.eventIds());
    }

    @Test
    @DisplayName("shouldNeverMarkAnotherAdminsNotes")
    void shouldNeverMarkAnotherAdminsNotes() {
        // Given: only the other admin wrote something
        noteService.saveNote(otherAdmin.getId(), "slot", slot.getId(), new SaveAdminNoteRequest("Jego notatka"));

        // When: the owner's calendar asks where its markers go
        AdminNoteMarkersDto markers = noteService.getMarkers(
            owner.getId(), LocalDate.now().minusDays(7), LocalDate.now());

        // Then: an icon here would advertise that somebody else wrote about this session
        assertEquals(List.of(), markers.slotIds());
        assertEquals(List.of(), markers.slotDates());
    }

    @Test
    @DisplayName("shouldMarkTrainingsSeparatelyFromBookings")
    void shouldMarkTrainingsSeparatelyFromBookings() {
        PersonalTraining training = trainingForAthlete();
        noteService.saveNote(owner.getId(), "training", training.getId(), new SaveAdminNoteRequest("Nogi martwe"));

        AdminNoteMarkersDto markers = noteService.getMarkers(
            owner.getId(), LocalDate.now().minusDays(7), LocalDate.now());

        // The two calendars are different screens; a training note must not light up a slot
        assertEquals(List.of(training.getId()), markers.trainingIds());
        assertEquals(List.of(), markers.slotIds());
    }

    @Test
    @DisplayName("shouldRefuseARangeWiderThanTheCalendarEverAsksFor")
    void shouldRefuseARangeWiderThanTheCalendarEverAsksFor() {
        // The month grid asks for 42 days and the booking month for at most 31
        assertThrows(IllegalArgumentException.class, () -> noteService.getMarkers(
            owner.getId(), LocalDate.now().minusDays(400), LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () -> noteService.getMarkers(
            owner.getId(), LocalDate.now(), LocalDate.now().minusDays(1)));
    }

    // ---------- DDL guarantees ----------

    @Test
    @DisplayName("a note must have exactly one target — chk_admin_private_notes_single_target")
    void shouldRejectANoteWithTwoTargets() {
        Event event = eventRepository.saveAndFlush(new Event("Kurs", EventType.COURSE,
            LocalDate.now(), LocalDate.now(), 5));

        // Bypasses the service's own guards to prove the constraint, not the switch, does the work
        assertViolates("chk_admin_private_notes_single_target", () -> jdbc.update(
            "INSERT INTO admin_private_notes (author_id, time_slot_id, event_id, body) VALUES (?, ?, ?, ?)",
            owner.getId(), slot.getId(), event.getId(), "Dwa cele"));
    }

    @Test
    @DisplayName("a note must have at least one target — chk_admin_private_notes_single_target")
    void shouldRejectANoteWithNoTarget() {
        assertViolates("chk_admin_private_notes_single_target", () -> jdbc.update(
            "INSERT INTO admin_private_notes (author_id, body) VALUES (?, ?)",
            owner.getId(), "Bez celu"));
    }

    @Test
    @DisplayName("a blank note is a deleted note — chk_admin_private_notes_body_not_blank")
    void shouldRejectABlankBodyAtTheDatabase() {
        assertViolates("chk_admin_private_notes_body_not_blank", () -> jdbc.update(
            "INSERT INTO admin_private_notes (author_id, time_slot_id, body) VALUES (?, ?, ?)",
            owner.getId(), slot.getId(), "   "));
    }

    @Test
    @DisplayName("one note per author per target — uq_admin_private_notes_slot")
    void shouldRejectASecondNoteByTheSameAuthorOnTheSameSlot() {
        jdbc.update("INSERT INTO admin_private_notes (author_id, time_slot_id, body) VALUES (?, ?, ?)",
            owner.getId(), slot.getId(), "Pierwsza");

        assertViolates("uq_admin_private_notes_slot", () -> jdbc.update(
            "INSERT INTO admin_private_notes (author_id, time_slot_id, body) VALUES (?, ?, ?)",
            owner.getId(), slot.getId(), "Druga"));
    }

    @Test
    @DisplayName("shouldDropTheNoteWhenTheSessionIsDeleted")
    void shouldDropTheNoteWhenTheSessionIsDeleted() {
        noteService.saveNote(owner.getId(), "slot", slot.getId(), new SaveAdminNoteRequest("Zniknie ze slotem"));

        timeSlotRepository.deleteById(slot.getId());
        timeSlotRepository.flush();

        // The cascade is the whole reason the target is three real foreign keys and not a
        // (type, id) pair: nothing sweeps orphaned notes, because nothing has to.
        assertEquals(0, noteCount());
    }

    @Test
    @DisplayName("shouldDropTheNoteWhenTheAuthorsAccountIsDeleted")
    void shouldDropTheNoteWhenTheAuthorsAccountIsDeleted() {
        noteService.saveNote(owner.getId(), "slot", slot.getId(), new SaveAdminNoteRequest("Zniknie z kontem"));

        jdbc.update("DELETE FROM users WHERE id = ?", owner.getId());

        assertEquals(0, noteCount());
    }
}
