package pl.nextsteppro.climbing.api.calendar;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.nextsteppro.climbing.domain.course.Course;
import pl.nextsteppro.climbing.domain.course.CourseRepository;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeat;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeatRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Findings from the line-by-line read of {@code CalendarService} (audit 2026-10). */
class CalendarReviewIntegrationTest extends BaseIntegrationTest {

    @PersistenceContext private EntityManager entityManager;
    @Autowired private CalendarService calendarService;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ReservedSeatRepository reservedSeatRepository;

    private User stranger;
    private User participant;
    private User admin;
    private Event hidden;

    @BeforeEach
    void setUp() {
        stranger = user("cal-stranger@test.local", UserRole.USER);
        participant = user("cal-participant@test.local", UserRole.USER);
        admin = user("cal-admin@test.local", UserRole.ADMIN);
        LocalDate day = LocalDate.now().plusDays(20);
        hidden = new Event("Ukryte", EventType.TRAINING, day, day, 6);
        hidden.setActive(false);
        hidden = eventRepository.save(hidden);
        reservationRepository.save(new Reservation(participant, timeSlotRepository.save(TimeSlot.forEventDay(hidden, day))));
        flushAndClear();
    }

    @Test
    void shouldNotShowAHiddenEventToAnyoneItDoesNotConcern() {
        assertThrows(IllegalArgumentException.class, () -> calendarService.getEventSummary(hidden.getId(), null));
        assertThrows(IllegalArgumentException.class, () -> calendarService.getEventSummary(hidden.getId(), stranger.getId()));
    }

    @Test
    void shouldStillShowAHiddenEventToItsParticipantsAndTheAdmin() {
        assertDoesNotThrow(() -> calendarService.getEventSummary(hidden.getId(), participant.getId()));
        assertDoesNotThrow(() -> calendarService.getEventSummary(hidden.getId(), admin.getId()));
    }

    @Test
    void shouldCountHeldInvitationSeatsOnTheCoursePage() {
        Course course = new Course("Kurs skałkowy");
        UUID group = UUID.randomUUID();
        course.setTranslationGroupId(group);
        course = courseRepository.save(course);
        LocalDate day = LocalDate.now().plusDays(30);
        Event dated = new Event("Kurs skałkowy", EventType.COURSE, day, day, 2);
        dated.setCourse(course);
        dated = eventRepository.save(dated);
        reservedSeatRepository.save(new ReservedSeat(dated, stranger));
        reservedSeatRepository.save(new ReservedSeat(dated, participant));
        flushAndClear();

        CourseEventDto term = calendarService.getCourseEventsByTranslationGroup(group).getFirst();

        assertEquals(0, term.availableSpots(), "both seats are promised to named invitees");
        assertEquals(SlotStatus.FULL, term.status());
    }

    private User user(String email, UserRole role) {
        User user = new User(email, "Te", "St", "+48123123123", email.substring(0, email.indexOf('@')));
        user.setRole(role);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
