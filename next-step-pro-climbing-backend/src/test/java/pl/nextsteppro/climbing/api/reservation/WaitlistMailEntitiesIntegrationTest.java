package pl.nextsteppro.climbing.api.reservation;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlist;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlistRepository;
import pl.nextsteppro.climbing.domain.waitlist.Waitlist;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistRepository;
import pl.nextsteppro.climbing.infrastructure.mail.WaitlistMailService;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Waitlist mails are {@code @Async}: they run on the mail thread and read the user and the slot
 * there. Handed a lazy proxy, that thread initialises it through the CALLER's session — while the
 * caller is still using it. Found when a test using the session right after {@code confirmOffer}
 * failed on CI with Hibernate's "Illegal pop() with non-matching JdbcValuesSourceProcessingState"
 * (two threads in one session); in production the same race ends either like that or as a swallowed
 * LazyInitializationException, i.e. a mail that silently never arrives.
 *
 * <p>Deterministic, not a race: with the mail service mocked, the arguments are what the async
 * method would receive, and every one of them must already be loaded.
 */
class WaitlistMailEntitiesIntegrationTest extends BaseIntegrationTest {

    @MockitoBean private WaitlistMailService waitlistMailService;

    @PersistenceContext private EntityManager entityManager;
    @Autowired private ReservationService reservationService;
    @Autowired private WaitlistService waitlistService;
    @Autowired private EventWaitlistService eventWaitlistService;
    @Autowired private WaitlistRepository waitlistRepository;
    @Autowired private EventWaitlistRepository eventWaitlistRepository;

    private User holder;
    private User waiter;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        holder = verifiedUser("wme-holder@test.local");
        waiter = verifiedUser("wme-waiter@test.local");
        day = LocalDate.now().plusDays(21);
    }

    @Test
    void shouldHandTheOfferMailLoadedEntities() {
        TimeSlot slot = timeSlotRepository.save(new TimeSlot(day, LocalTime.of(10, 0), LocalTime.of(12, 0), 1));
        UUID booking = reservationRepository.save(new Reservation(holder, slot)).getId();
        waitlistRepository.save(new Waitlist(waiter, slot, 1));
        flushAndClear();

        reservationService.cancelReservation(booking, holder.getId());

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<TimeSlot> offered = ArgumentCaptor.forClass(TimeSlot.class);
        verify(waitlistMailService).sendWaitlistOfferNotification(user.capture(), offered.capture(), any());
        assertLoaded(user.getValue());
        assertLoaded(offered.getValue());
    }

    @Test
    void shouldHandTheConfirmationMailsLoadedEntities() {
        TimeSlot slot = timeSlotRepository.save(new TimeSlot(day, LocalTime.of(10, 0), LocalTime.of(12, 0), 1));
        Waitlist entry = new Waitlist(waiter, slot, 1);
        entry.offerSpot(Instant.now().plus(2, ChronoUnit.HOURS));
        UUID entryId = waitlistRepository.save(entry).getId();
        flushAndClear();

        waitlistService.confirmOffer(entryId, waiter.getId());

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<TimeSlot> booked = ArgumentCaptor.forClass(TimeSlot.class);
        verify(waitlistMailService).sendWaitlistReservationConfirmed(user.capture(), booked.capture());
        assertLoaded(user.getValue());
        assertLoaded(booked.getValue());
        verify(waitlistMailService).sendWaitlistAdminNotification(eq(user.getValue()), any());
    }

    @Test
    void shouldHandTheEventConfirmationMailsLoadedEntities() {
        Event course = eventRepository.save(new Event("Kurs", EventType.COURSE, day, day.plusDays(1), 1));
        timeSlotRepository.save(TimeSlot.forEventDay(course, day));
        timeSlotRepository.save(TimeSlot.forEventDay(course, day.plusDays(1)));
        EventWaitlist entry = new EventWaitlist(waiter, course, 1);
        entry.offerSpot(Instant.now().plus(2, ChronoUnit.HOURS));
        UUID entryId = eventWaitlistRepository.save(entry).getId();
        flushAndClear();

        eventWaitlistService.confirmEventOffer(entryId, waiter.getId());

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<Event> booked = ArgumentCaptor.forClass(Event.class);
        verify(waitlistMailService).sendEventWaitlistReservationConfirmed(user.capture(), booked.capture());
        assertLoaded(user.getValue());
        assertLoaded(booked.getValue());
    }

    private static void assertLoaded(Object entity) {
        assertTrue(Hibernate.isInitialized(entity), "an uninitialised proxy reached the mail thread: " + entity.getClass());
        if (entity instanceof TimeSlot slot && slot.getEvent() != null) {
            assertTrue(Hibernate.isInitialized(slot.getEvent()), "the slot's event is read for its title");
        }
    }

    private User verifiedUser(String email) {
        User user = new User(email, "Te", "St", "+48123123123", email.substring(0, email.indexOf('@')));
        user.setRole(UserRole.USER);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
