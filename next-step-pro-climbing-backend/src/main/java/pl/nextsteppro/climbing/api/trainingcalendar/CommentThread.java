package pl.nextsteppro.climbing.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTraining;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingComment;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;

import java.util.List;
import java.util.Objects;

/**
 * One athlete &lt;-&gt; coach conversation, already resolved and already authorised: whose calendar
 * it belongs to and which of the three things it hangs on.
 *
 * <p>Exists so the branch over the target shape is written once. Reading a thread, posting into it
 * and counting what it holds all have to answer "which column", and three copies of that question
 * is precisely the shape this codebase has already been bitten by — the twin slot/event paths,
 * where the failure mode is always a fix landing in one copy.
 *
 * <p>Constructed only by {@link TrainingCalendarService}'s guards. Nothing here re-checks who may
 * look: a value that carried its own permissions would invite being built somewhere the guards are
 * not.
 */
record CommentThread(User athlete,
                     @Nullable PersonalTraining training,
                     @Nullable TimeSlot slot,
                     @Nullable Event event) {

    static CommentThread of(PersonalTraining training) {
        return new CommentThread(training.getAthlete(), training, null, null);
    }

    static CommentThread of(User athlete, TimeSlot slot) {
        return new CommentThread(athlete, null, slot, null);
    }

    static CommentThread of(User athlete, Event event) {
        return new CommentThread(athlete, null, null, event);
    }

    /** A new, unsaved message in this thread. */
    TrainingComment newMessage(User author, boolean authorIsAdmin, @Nullable String body) {
        if (training != null) return TrainingComment.onTraining(training, author, authorIsAdmin, body);
        if (slot != null) return TrainingComment.onSlot(athlete, slot, author, authorIsAdmin, body);
        return TrainingComment.onEvent(
            athlete, Objects.requireNonNull(event), author, authorIsAdmin, body);
    }

    /** Everything said here, oldest first. */
    List<TrainingComment> read(TrainingCommentRepository repository) {
        if (training != null) return repository.findThread(training.getId());
        if (slot != null) return repository.findSlotThread(slot.getId(), athlete.getId());
        return repository.findEventThread(Objects.requireNonNull(event).getId(), athlete.getId());
    }
}
