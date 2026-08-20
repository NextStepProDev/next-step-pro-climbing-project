package pl.nextsteppro.climbing.api.admin.note;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.trainingcalendar.TrainingCalendarService;
import pl.nextsteppro.climbing.domain.adminnote.AdminPrivateNote;
import pl.nextsteppro.climbing.domain.adminnote.AdminPrivateNoteRepository;
import pl.nextsteppro.climbing.domain.event.EventRepository;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTraining;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The owner's private notebook. Every operation is scoped to (author, target); no note is ever
 * addressed by its own id, so there is no branch in which the author comparison could be forgotten.
 *
 * <p><b>No cache and no activity log, both on purpose.</b> No cache because the author edits and
 * must see the result immediately, and one note per request is not a cost worth defending against.
 * No activity log because the Activity tab would then announce the existence of notes ("admin added
 * a note to slot X") — that log records actions that touch people, and this one touches nobody.
 * A new {@code ActivityActionType} is also a four-file change with a white-screen failure mode.
 */
@Service
@Transactional
public class AdminNoteService {

    private final AdminPrivateNoteRepository noteRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final EventRepository eventRepository;
    private final PersonalTrainingRepository trainingRepository;
    private final TrainingCalendarService calendarService;
    private final MessageService msg;

    public AdminNoteService(AdminPrivateNoteRepository noteRepository,
                            TimeSlotRepository timeSlotRepository,
                            EventRepository eventRepository,
                            PersonalTrainingRepository trainingRepository,
                            TrainingCalendarService calendarService,
                            MessageService msg) {
        this.noteRepository = noteRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.eventRepository = eventRepository;
        this.trainingRepository = trainingRepository;
        this.calendarService = calendarService;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public AdminNoteDto getNote(UUID adminId, String targetSegment, UUID targetId) {
        NoteTarget target = parseTarget(targetSegment);
        requireTargetExists(target, targetId);
        Optional<AdminPrivateNote> note = switch (target) {
            case SLOT -> noteRepository.findForSlot(adminId, targetId);
            case EVENT -> noteRepository.findForEvent(adminId, targetId);
            case TRAINING -> noteRepository.findForTraining(adminId, targetId);
        };
        return note.map(AdminNoteDto::of).orElse(AdminNoteDto.EMPTY);
    }

    public void saveNote(UUID adminId, String targetSegment, UUID targetId, SaveAdminNoteRequest request) {
        NoteTarget target = parseTarget(targetSegment);
        requireTargetExists(target, targetId);

        // Bean Validation already rejected a blank body; this catches text that is only markup-free
        // whitespace once trimmed, and mirrors the CHECK in V89.
        String body = AdminPrivateNote.sanitizeBody(request.body());
        if (body == null) {
            throw new IllegalArgumentException(msg.get("admin.note.empty"));
        }

        // Single statement rather than read-then-save: a second tab loses the race on the unique
        // index and surfaces as a 500. Overwriting is correct — the author is correcting himself.
        Instant now = Instant.now();
        switch (target) {
            case SLOT -> noteRepository.upsertForSlot(adminId, targetId, body, now);
            case EVENT -> noteRepository.upsertForEvent(adminId, targetId, body, now);
            case TRAINING -> noteRepository.upsertForTraining(adminId, targetId, body, now);
        }
    }

    /** Idempotent: deleting a note that is not there is a success, not a 404. */
    public void deleteNote(UUID adminId, String targetSegment, UUID targetId) {
        NoteTarget target = parseTarget(targetSegment);
        requireTargetExists(target, targetId);
        switch (target) {
            case SLOT -> noteRepository.deleteForSlot(adminId, targetId);
            case EVENT -> noteRepository.deleteForEvent(adminId, targetId);
            case TRAINING -> noteRepository.deleteForTraining(adminId, targetId);
        }
    }

    private NoteTarget parseTarget(String segment) {
        return NoteTarget.tryFrom(segment)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("admin.note.target.unknown")));
    }

    /**
     * The single gate in front of every operation. Notes on things that do not exist are worth
     * refusing even though the foreign keys would refuse them too: the client learns what is wrong
     * instead of receiving a 409 from a constraint name.
     */
    private void requireTargetExists(NoteTarget target, UUID targetId) {
        switch (target) {
            case SLOT -> {
                TimeSlot slot = timeSlotRepository.findById(targetId)
                    .orElseThrow(() -> new IllegalArgumentException(msg.get("admin.note.target.not.found")));
                // An event carries ONE note however many days it spans. Its per-day slots are
                // bookkeeping created by the first booking — the admin never sees them (both admin
                // listings filter belongsToEvent out), so a note here would be a second, invisible
                // "note about the event".
                if (slot.belongsToEvent()) {
                    throw new IllegalArgumentException(msg.get("admin.note.slot.belongs.to.event"));
                }
            }
            case EVENT -> {
                if (!eventRepository.existsById(targetId)) {
                    throw new IllegalArgumentException(msg.get("admin.note.target.not.found"));
                }
            }
            case TRAINING -> {
                PersonalTraining training = trainingRepository.findById(targetId)
                    .orElseThrow(() -> new IllegalArgumentException(msg.get("admin.note.target.not.found")));
                // Same privacy boundary as the rest of the coach side: dropping the athlete flag
                // wipes the GDPR consent, and everything behind it has to stop being reachable.
                calendarService.requireFlaggedAthlete(training.getAthlete().getId());
            }
        }
    }
}
