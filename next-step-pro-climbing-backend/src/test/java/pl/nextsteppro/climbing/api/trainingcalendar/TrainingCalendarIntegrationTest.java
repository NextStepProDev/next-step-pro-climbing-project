package pl.nextsteppro.climbing.api.trainingcalendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCalendarReadRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeat;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeatRepository;
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
 * End-to-end flow of the personal training calendar over real PostgreSQL (Flyway V62-V65):
 * athlete creates a training -> coach sees per-athlete badge -> coach comments ->
 * athlete badge -> mark seen -> completion with RPE -> coach badge -> range with overlay.
 *
 * Lives in this package (not integration/) because the DTO records are package-private.
 */
class TrainingCalendarIntegrationTest extends BaseIntegrationTest {

    @Autowired private TrainingCalendarService trainingCalendarService;
    @Autowired private AdminTrainingCalendarService adminTrainingCalendarService;
    @Autowired private PersonalTrainingRepository personalTrainingRepository;
    @Autowired private TrainingCommentRepository trainingCommentRepository;
    @Autowired private TrainingCalendarReadRepository trainingCalendarReadRepository;
    @Autowired private ReservedSeatRepository reservedSeatRepository;

    private User athlete;
    private User coach;

    @BeforeEach
    void setUp() {
        trainingCommentRepository.deleteAll();
        trainingCalendarReadRepository.deleteAll();
        personalTrainingRepository.deleteAll();
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        eventRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        athlete = new User("athlete@example.com", "Anna", "Wspinaczka", "+48123456789", "anna");
        athlete.setRole(UserRole.USER);
        athlete.setEmailVerified(true);
        athlete.setAthlete(true);
        athlete = userRepository.save(athlete);

        coach = new User("coach@example.com", "Trener", "Główny", "+48111111111", "coach");
        coach.setRole(UserRole.ADMIN);
        coach.setEmailVerified(true);
        coach = userRepository.save(coach);
    }

    @Test
    void shouldRunFullAthleteCoachCollaborationFlow() {
        // Yesterday: completion later in the flow requires a training that has started
        LocalDate date = LocalDate.now().minusDays(1);

        // Athlete creates a training
        PersonalTrainingDto created = trainingCalendarService.createMy(athlete.getId(),
            new CreatePersonalTrainingRequest(date, LocalTime.of(18, 0), LocalTime.of(19, 30),
                "Trening siłowy", "Kampus + zwisy"));
        assertEquals("MISSED", created.status());
        assertFalse(created.createdByAdmin());

        // Coach roster shows 1 unread for the athlete
        List<AthleteSummaryDto> summaries = adminTrainingCalendarService.getAthleteSummaries(coach.getId());
        assertEquals(1, summaries.size());
        assertEquals(athlete.getId(), summaries.get(0).id());
        assertEquals(1L, summaries.get(0).newCount());

        // Coach opens the athlete's calendar (mark seen) -> badge clears
        adminTrainingCalendarService.markSeen(coach.getId(), athlete.getId());
        assertEquals(0L, adminTrainingCalendarService.getAthleteSummaries(coach.getId()).get(0).newCount());

        // Coach comments -> athlete unread counter rises
        adminTrainingCalendarService.addComment(coach.getId(), created.id(), "Dodaj 3 serie zwisów");
        assertEquals(1L, trainingCalendarService.getAthleteNotifications(athlete.getId()).newCount());

        // The thread is visible to both sides
        List<TrainingCommentDto> thread = trainingCalendarService.getMyComments(athlete.getId(), created.id());
        assertEquals(1, thread.size());
        assertTrue(thread.get(0).authorIsAdmin());
        assertFalse(thread.get(0).mine());

        // Athlete opens the tab (mark seen) -> counter clears
        trainingCalendarService.markAthleteSeen(athlete.getId());
        assertEquals(0L, trainingCalendarService.getAthleteNotifications(athlete.getId()).newCount());

        // Athlete completes with RPE 7 -> coach badge rises again
        PersonalTrainingDto completed = trainingCalendarService.complete(athlete.getId(), created.id(),
            new CompleteTrainingRequest("Poszło dobrze", 7));
        assertEquals("COMPLETED", completed.status());
        assertEquals(7, completed.rpe());
        assertEquals(1L, adminTrainingCalendarService.getAthleteSummaries(coach.getId()).get(0).newCount());

        // Range returns the completed training + a reservation overlay row
        TimeSlot slot = timeSlotRepository.save(new TimeSlot(date, LocalTime.of(10, 0), LocalTime.of(12, 0), 5));
        reservationRepository.save(new Reservation(athlete, slot));

        CalendarRangeDto range = trainingCalendarService.getMyRange(athlete.getId(), date.minusDays(1), date.plusDays(1));
        assertEquals(1, range.trainings().size());
        assertEquals("COMPLETED", range.trainings().get(0).status());
        assertEquals(1, range.reservations().size());
        assertEquals(date, range.reservations().get(0).date());
    }

    @Test
    void shouldLetCoachManageTrainingAndKeepDataAfterUnflag() {
        LocalDate date = LocalDate.now().plusDays(5);

        // Coach adds a training to the athlete's calendar
        PersonalTrainingDto byCoach = adminTrainingCalendarService.createForAthlete(coach.getId(), athlete.getId(),
            new CreatePersonalTrainingRequest(date, LocalTime.of(17, 0), LocalTime.of(18, 30), "Obwód trenera", null));
        assertTrue(byCoach.createdByAdmin());
        assertEquals(1L, trainingCalendarService.getAthleteNotifications(athlete.getId()).newCount());

        // Athlete may edit the coach-created entry (shared plan)
        PersonalTrainingDto edited = trainingCalendarService.updateMy(athlete.getId(), byCoach.id(),
            new CreatePersonalTrainingRequest(date, LocalTime.of(17, 30), LocalTime.of(19, 0), "Obwód trenera", "przesunięte"));
        assertEquals(LocalTime.of(17, 30), edited.startTime());
        assertTrue(edited.createdByAdmin(), "provenance survives athlete edits");

        // Un-flagging blocks access but keeps the data
        athlete.setAthlete(false);
        userRepository.save(athlete);
        UUID athleteId = athlete.getId();
        assertThrows(IllegalStateException.class,
            () -> trainingCalendarService.getMyRange(athleteId, date.minusDays(1), date.plusDays(1)));
        assertTrue(adminTrainingCalendarService.getAthleteSummaries(coach.getId()).isEmpty());
        assertEquals(1, personalTrainingRepository.count(), "calendar data must survive un-flagging");
    }

    @Test
    void shouldShowPendingInvitationUntilBookedThenAsReservation() {
        // Coach holds a seat for the athlete on an upcoming slot
        LocalDate date = LocalDate.now().plusDays(3);
        TimeSlot slot = timeSlotRepository.save(new TimeSlot(date, LocalTime.of(10, 0), LocalTime.of(12, 0), 4));
        reservedSeatRepository.save(new ReservedSeat(slot, athlete));

        // Pending invite -> loud invitation overlay, NOT a reservation
        CalendarRangeDto range = trainingCalendarService.getMyRange(athlete.getId(), date, date);
        assertEquals(1, range.invitations().size());
        assertEquals(slot.getId(), range.invitations().get(0).slotId());
        assertTrue(range.reservations().isEmpty(), "an unbooked invite must not look like a reservation");

        // Athlete books the held seat -> the invite disappears, a reservation appears
        reservationRepository.save(new Reservation(athlete, slot));
        range = trainingCalendarService.getMyRange(athlete.getId(), date, date);
        assertTrue(range.invitations().isEmpty(), "booked invite must stop shouting");
        assertEquals(1, range.reservations().size());
        assertFalse(range.reservations().get(0).isNew(), "own booking is never 'new' to the athlete");

        // The fresh booking lights the coach's roster badge and gets the unread dot in the coach view
        assertEquals(1L, adminTrainingCalendarService.getAthleteSummaries(coach.getId()).get(0).newCount());
        CalendarRangeDto coachRange = trainingCalendarService.getRangeForAthlete(
            coach.getId(), athlete.getId(), date, date);
        assertTrue(coachRange.reservations().get(0).isNew(), "coach must see the booking as new");

        // Coach opens the calendar (mark seen) -> badge and dot both clear
        adminTrainingCalendarService.markSeen(coach.getId(), athlete.getId());
        assertEquals(0L, adminTrainingCalendarService.getAthleteSummaries(coach.getId()).get(0).newCount());
        coachRange = trainingCalendarService.getRangeForAthlete(coach.getId(), athlete.getId(), date, date);
        assertFalse(coachRange.reservations().get(0).isNew(), "seen booking must lose the dot");
    }

    @Test
    void shouldNotAlertCoachAboutAdminMadeBooking() {
        // A booking the admin added by hand is the coach's own action: no badge, no dot
        LocalDate date = LocalDate.now().plusDays(2);
        TimeSlot slot = timeSlotRepository.save(new TimeSlot(date, LocalTime.of(10, 0), LocalTime.of(12, 0), 4));
        Reservation manual = new Reservation(athlete, slot);
        manual.setCreatedByAdmin(true);
        reservationRepository.save(manual);

        assertEquals(0L, adminTrainingCalendarService.getAthleteSummaries(coach.getId()).get(0).newCount());
        CalendarRangeDto coachRange = trainingCalendarService.getRangeForAthlete(
            coach.getId(), athlete.getId(), date, date);
        assertEquals(1, coachRange.reservations().size());
        assertFalse(coachRange.reservations().get(0).isNew());
    }

    @Test
    void shouldAlertOtherSideOnlyWhenFutureTrainingIsDeleted() {
        // Coach deletes the athlete's FUTURE training -> athlete gets an alert + strip entry
        PersonalTrainingDto future = trainingCalendarService.createMy(athlete.getId(),
            new CreatePersonalTrainingRequest(LocalDate.now().plusDays(4),
                LocalTime.of(18, 0), LocalTime.of(19, 30), "Plan do wycięcia", null));
        trainingCalendarService.markAthleteSeen(athlete.getId());
        adminTrainingCalendarService.delete(coach.getId(), future.id());

        assertEquals(1L, trainingCalendarService.getAthleteNotifications(athlete.getId()).newCount());
        CalendarRangeDto range = trainingCalendarService.getMyRange(
            athlete.getId(), LocalDate.now(), LocalDate.now().plusDays(7));
        assertEquals(1, range.deletions().size());
        assertEquals("Plan do wycięcia", range.deletions().get(0).title());
        assertTrue(range.deletions().get(0).deletedByAdmin());

        // Athlete deletes their own FUTURE training -> coach badge rises
        adminTrainingCalendarService.markSeen(coach.getId(), athlete.getId());
        PersonalTrainingDto mine = trainingCalendarService.createMy(athlete.getId(),
            new CreatePersonalTrainingRequest(LocalDate.now().plusDays(5),
                LocalTime.of(18, 0), LocalTime.of(19, 0), "Odwołany przeze mnie", null));
        adminTrainingCalendarService.markSeen(coach.getId(), athlete.getId());
        trainingCalendarService.deleteMy(athlete.getId(), mine.id());
        assertEquals(1L, adminTrainingCalendarService.getAthleteSummaries(coach.getId()).get(0).newCount());

        // Deleting a PAST training alerts nobody
        trainingCalendarService.markAthleteSeen(athlete.getId());
        adminTrainingCalendarService.markSeen(coach.getId(), athlete.getId());
        PersonalTrainingDto past = trainingCalendarService.createMy(athlete.getId(),
            new CreatePersonalTrainingRequest(LocalDate.now().minusDays(2),
                LocalTime.of(18, 0), LocalTime.of(19, 0), "Stary wpis", null));
        adminTrainingCalendarService.markSeen(coach.getId(), athlete.getId());
        adminTrainingCalendarService.delete(coach.getId(), past.id());
        assertEquals(0L, trainingCalendarService.getAthleteNotifications(athlete.getId()).newCount());
        assertEquals(0L, adminTrainingCalendarService.getAthleteSummaries(coach.getId()).get(0).newCount());
    }

    @Test
    void shouldRejectCompletingTrainingThatHasNotStarted() {
        PersonalTrainingDto future = trainingCalendarService.createMy(athlete.getId(),
            new CreatePersonalTrainingRequest(LocalDate.now().plusDays(3),
                LocalTime.of(18, 0), LocalTime.of(19, 30), "Plan na przyszłość", null));
        assertEquals("PLANNED", future.status());

        assertThrows(IllegalStateException.class,
            () -> trainingCalendarService.complete(athlete.getId(), future.id(),
                new CompleteTrainingRequest(null, 5)));
    }

    @Test
    void shouldSupportRetroactiveTrainingLogging() {
        // Athletes log trainings after the fact, sometimes days later: past-date create,
        // commenting and completing MUST all work on historical entries.
        LocalDate pastDate = LocalDate.now().minusDays(3);

        PersonalTrainingDto created = trainingCalendarService.createMy(athlete.getId(),
            new CreatePersonalTrainingRequest(pastDate, LocalTime.of(18, 0), LocalTime.of(19, 30),
                "Zaległy trening", "wpis po fakcie"));
        assertEquals("MISSED", created.status(), "past uncompleted entry starts as missed");

        // Comments work on historical trainings (both directions)
        adminTrainingCalendarService.addComment(coach.getId(), created.id(), "Jak poszło?");
        trainingCalendarService.addMyComment(athlete.getId(), created.id(), "Spoko, 5 dróg");
        assertEquals(2, trainingCalendarService.getMyComments(athlete.getId(), created.id()).size());

        // Completing days later flips it to COMPLETED with feedback + RPE
        PersonalTrainingDto completed = trainingCalendarService.complete(athlete.getId(), created.id(),
            new CompleteTrainingRequest("Dobre przechwyty", 8));
        assertEquals("COMPLETED", completed.status());
        assertEquals(8, completed.rpe());

        // The historical range returns it and the coach sees the completion
        CalendarRangeDto range = trainingCalendarService.getMyRange(athlete.getId(), pastDate, pastDate);
        assertEquals(1, range.trainings().size());
        assertEquals("COMPLETED", range.trainings().get(0).status());
        assertTrue(adminTrainingCalendarService.getAthleteSummaries(coach.getId()).get(0).newCount() > 0,
            "coach must see activity on the historical entry");
    }

    @Test
    void shouldExcludeCancelledReservationsFromOverlay() {
        LocalDate date = LocalDate.now().plusDays(4);
        TimeSlot slot = timeSlotRepository.save(new TimeSlot(date, LocalTime.of(10, 0), LocalTime.of(12, 0), 5));
        Reservation reservation = reservationRepository.save(new Reservation(athlete, slot));

        CalendarRangeDto range = trainingCalendarService.getMyRange(athlete.getId(), date, date);
        assertEquals(1, range.reservations().size());

        reservation.cancel();
        reservationRepository.save(reservation);

        range = trainingCalendarService.getMyRange(athlete.getId(), date, date);
        assertTrue(range.reservations().isEmpty(), "cancelled bookings must disappear from the overlay");
    }

    @Test
    void shouldNotLightAthleteBadgeForOwnCompletionAfterCoachEdit() {
        // Started training (yesterday) — a future one could not be completed
        LocalDate date = LocalDate.now().minusDays(1);
        // Coach creates and thus "last modified by admin" is true
        PersonalTrainingDto training = adminTrainingCalendarService.createForAthlete(coach.getId(), athlete.getId(),
            new CreatePersonalTrainingRequest(date, LocalTime.of(18, 0), LocalTime.of(19, 0), "Obwód", null));
        assertEquals(1L, trainingCalendarService.getAthleteNotifications(athlete.getId()).newCount());

        // Athlete reads, then completes their training — their own action must NOT re-light the badge
        trainingCalendarService.markAthleteSeen(athlete.getId());
        trainingCalendarService.complete(athlete.getId(), training.id(), new CompleteTrainingRequest(null, 6));
        assertEquals(0L, trainingCalendarService.getAthleteNotifications(athlete.getId()).newCount());
    }

    @Test
    void shouldKeepPerAdminReadMarkersIndependent() {
        User secondCoach = new User("coach2@example.com", "Druga", "Trenerka", "+48333333333", "coach2");
        secondCoach.setRole(UserRole.ADMIN);
        secondCoach.setEmailVerified(true);
        secondCoach = userRepository.save(secondCoach);

        trainingCalendarService.createMy(athlete.getId(),
            new CreatePersonalTrainingRequest(LocalDate.now().plusDays(1),
                LocalTime.of(18, 0), LocalTime.of(19, 0), "Trening", null));

        // Coach A reads; coach B's badge must stay lit
        adminTrainingCalendarService.markSeen(coach.getId(), athlete.getId());
        assertEquals(0L, adminTrainingCalendarService.getAthleteSummaries(coach.getId()).get(0).newCount());
        assertEquals(1L, adminTrainingCalendarService.getAthleteSummaries(secondCoach.getId()).get(0).newCount());
    }

    @Test
    void shouldRejectDoubleMarkSeenGracefully() {
        // Upsert path: repeated mark-seen must not violate the primary key
        trainingCalendarService.markAthleteSeen(athlete.getId());
        trainingCalendarService.markAthleteSeen(athlete.getId());
        assertEquals(0L, trainingCalendarService.getAthleteNotifications(athlete.getId()).newCount());
    }

    @Test
    void shouldIsolateTrainingsBetweenAthletes() {
        User otherAthlete = new User("athlete2@example.com", "Piotr", "Blokowy", "+48444444444", "piotr");
        otherAthlete.setRole(UserRole.USER);
        otherAthlete.setEmailVerified(true);
        otherAthlete.setAthlete(true);
        otherAthlete = userRepository.save(otherAthlete);

        LocalDate date = LocalDate.now().plusDays(1);
        PersonalTrainingDto training = trainingCalendarService.createMy(athlete.getId(),
            new CreatePersonalTrainingRequest(date, LocalTime.of(18, 0), LocalTime.of(19, 0), "Prywatny", null));

        // Other athlete's range does not contain it, and direct access is denied as not-found
        UUID otherId = otherAthlete.getId();
        assertTrue(trainingCalendarService.getMyRange(otherId, date, date).trainings().isEmpty());
        assertThrows(IllegalArgumentException.class,
            () -> trainingCalendarService.getMyComments(otherId, training.id()));
        assertThrows(IllegalArgumentException.class,
            () -> trainingCalendarService.deleteMy(otherId, training.id()));
    }

    @Test
    void shouldRejectCalendarAccessForNonAthlete() {
        User regular = new User("user@example.com", "Jan", "Kowalski", "+48222222222", "jan");
        regular.setRole(UserRole.USER);
        regular.setEmailVerified(true);
        User saved = userRepository.save(regular);

        assertThrows(IllegalStateException.class,
            () -> trainingCalendarService.getMyRange(saved.getId(), LocalDate.now(), LocalDate.now().plusDays(7)));
    }
}
