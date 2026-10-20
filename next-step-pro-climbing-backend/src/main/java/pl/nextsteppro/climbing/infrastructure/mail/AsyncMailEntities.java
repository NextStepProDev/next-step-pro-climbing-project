package pl.nextsteppro.climbing.infrastructure.mail;

import org.hibernate.Hibernate;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;

/**
 * Entities about to be handed to an {@code @Async} mail, loaded on the CALLER's thread.
 *
 * <p>The mail thread reads them after the call returns. A lazy proxy then initialises itself
 * through the caller's session — which the caller is still using, or has already closed. The first
 * is two threads in one Hibernate session ("Illegal pop() with non-matching
 * JdbcValuesSourceProcessingState", seen on CI 2026-10-20 right after {@code confirmOffer}); the
 * second is a LazyInitializationException the async decorator swallows, i.e. a mail that never
 * arrives with nothing in the log. {@code Reservation.touchForAsyncMail} is the same idea for the
 * reservation mails.
 *
 * <p>Each method returns the real object, not the proxy, so the mail thread never meets a session.
 */
public final class AsyncMailEntities {

    private AsyncMailEntities() {}

    public static User loaded(User user) {
        return Hibernate.unproxy(user, User.class);
    }

    /** The slot and its event: {@code getDisplayTitle()} falls back to the event's title. */
    public static TimeSlot loaded(TimeSlot slot) {
        TimeSlot real = Hibernate.unproxy(slot, TimeSlot.class);
        if (real.getEvent() != null) {
            Hibernate.initialize(real.getEvent());
        }
        return real;
    }

    public static Event loaded(Event event) {
        return Hibernate.unproxy(event, Event.class);
    }
}
