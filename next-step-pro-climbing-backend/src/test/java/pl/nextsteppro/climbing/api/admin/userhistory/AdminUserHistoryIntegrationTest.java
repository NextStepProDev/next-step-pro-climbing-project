package pl.nextsteppro.climbing.api.admin.userhistory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogDto;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.domain.activitylog.ActivityLogRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The admin's read-only user card, over real PostgreSQL.
 *
 * <p>Lives in this package because the card's DTO records are package-private.
 *
 * <p>Two things here are assertions about decisions rather than about plumbing: that the training
 * counts are {@code null} — not zero — for a user without the athlete flag, and that an admin's
 * cancellation lands in the affected user's timeline. Both are easy to "fix" into something wrong.
 */
class AdminUserHistoryIntegrationTest extends BaseIntegrationTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Autowired private AdminUserHistoryService service;
    @Autowired private ActivityLogService activityLogService;
    @Autowired private ActivityLogRepository activityLogRepository;

    private User user;

    @BeforeEach
    void setUp() {
        activityLogRepository.deleteAll();
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        user = new User("historia@example.com", "Jan", "Kowalski", "+48123456789", "janek");
        user.setRole(UserRole.USER);
        user.setEmailVerified(true);
        user = userRepository.save(user);
    }

    /** Fixtures are built from the Warsaw clock, never the JVM default: the runner is UTC and the
     * past/upcoming split asks Warsaw, so a bare now() drifts the boundary by two hours in summer. */
    private static LocalDateTime nowWarsaw() {
        return LocalDateTime.now(WARSAW);
    }

    private TimeSlot slotAt(LocalDate date, LocalTime start, LocalTime end) {
        return timeSlotRepository.save(new TimeSlot(date, start, end, 10));
    }

    private Reservation bookingOn(TimeSlot slot) {
        return reservationRepository.save(new Reservation(user, slot));
    }

    /**
     * The card sends a lot of account state, so the risk is not authorisation (a gate checks that
     * every admin controller is annotated) but over-sharing. Asserted on the SERIALISED JSON, not
     * on the record's components: a field added to {@code UserDetailDto} later ships to the browser
     * whether or not anyone remembered this test existed.
     *
     * <p>The unsubscribe token is the sharp one — it is permanent, {@code updatable = false} and
     * cannot be revoked, so anything that echoes it stays leaked for the life of the account.
     */
    @Test
    @DisplayName("shouldNotSerialiseSecretsIntoTheUserCard")
    void shouldNotSerialiseSecretsIntoTheUserCard() throws Exception {
        user.setPasswordHash("$2a$12$notarealhashbutlongenoughtospot");
        user.setOauthId("google-oauth-subject-12345");
        user = userRepository.save(user);

        String json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .writeValueAsString(service.getUserDetail(user.getId()).orElseThrow());

        for (String forbidden : List.of(
                "passwordHash", "$2a$12$notarealhashbutlongenoughtospot",
                "oauthId", "google-oauth-subject-12345",
                "newsletterUnsubscribeToken",
                user.getNewsletterUnsubscribeToken().toString())) {
            assertFalse(json.contains(forbidden),
                "The user card serialised '" + forbidden + "' — it must never reach the browser");
        }

        // ...while still carrying the harmless substitutes the Account tab actually renders
        assertTrue(json.contains("\"hasPassword\":true"));
        assertTrue(json.contains("\"oauthProvider\""));
    }

    @Test
    @DisplayName("shouldReturnEmptyWhenUserDoesNotExist")
    void shouldReturnEmptyWhenUserDoesNotExist() {
        UUID unknown = UUID.randomUUID();

        assertTrue(service.getUserDetail(unknown).isEmpty());
        assertTrue(service.getActivity(unknown, 0, 20).isEmpty());
        assertTrue(service.getReservationHistory(unknown, 0, 25).isEmpty());
    }

    @Test
    @DisplayName("shouldLeaveTrainingCountNullWhenUserIsNotAnAthlete")
    void shouldLeaveTrainingCountNullWhenUserIsNotAnAthlete() {
        UserDetailDto detail = service.getUserDetail(user.getId()).orElseThrow();

        assertFalse(detail.athlete());
        // Null, not 0: the calendar is unreadable for the admin here, and "nothing to show" must
        // not render as a genuine zero. The logbook is a separate boundary — visible by default.
        assertNull(detail.counts().trainingsCompleted());
        assertTrue(detail.ascentsReadable());
        assertEquals(0L, detail.counts().ascents());
    }

    @Test
    @DisplayName("shouldLeaveAscentCountNullWhenUserHidTheirLogbook")
    void shouldLeaveAscentCountNullWhenUserHidTheirLogbook() {
        user.setAscentsPublic(false);
        user = userRepository.save(user);

        UserDetailDto detail = service.getUserDetail(user.getId()).orElseThrow();

        assertFalse(detail.ascentsReadable());
        assertNull(detail.counts().ascents());
    }

    /** Coaching outranks the switch — see {@code User.isLogbookVisibleToCoach}. */
    @Test
    @DisplayName("shouldStillReadTheLogbookOfAnAthleteWhoHidTheirAscents")
    void shouldStillReadTheLogbookOfAnAthleteWhoHidTheirAscents() {
        user.setAthlete(true);
        user.setAscentsPublic(false);
        user = userRepository.save(user);

        UserDetailDto detail = service.getUserDetail(user.getId()).orElseThrow();

        assertTrue(detail.ascentsReadable());
        assertEquals(0L, detail.counts().ascents());
    }

    @Test
    @DisplayName("shouldReportZeroTrainingCountsWhenUserIsAnAthleteWithNoData")
    void shouldReportZeroTrainingCountsWhenUserIsAnAthleteWithNoData() {
        user.setAthlete(true);
        user = userRepository.save(user);

        UserDetailDto detail = service.getUserDetail(user.getId()).orElseThrow();

        assertTrue(detail.athlete());
        assertEquals(0L, detail.counts().trainingsCompleted());
        assertEquals(0L, detail.counts().ascents());
    }

    @Test
    @DisplayName("shouldCountConfirmedAndCancelledReservationsSeparately")
    void shouldCountConfirmedAndCancelledReservationsSeparately() {
        LocalDate day = nowWarsaw().toLocalDate().minusDays(7);
        bookingOn(slotAt(day, LocalTime.of(10, 0), LocalTime.of(12, 0)));

        Reservation cancelled = bookingOn(slotAt(day, LocalTime.of(14, 0), LocalTime.of(16, 0)));
        cancelled.cancel();
        reservationRepository.save(cancelled);

        Reservation byAdmin = bookingOn(slotAt(day, LocalTime.of(17, 0), LocalTime.of(19, 0)));
        byAdmin.cancelByAdmin();
        reservationRepository.save(byAdmin);

        UserCountsDto counts = service.getUserDetail(user.getId()).orElseThrow().counts();

        assertEquals(1L, counts.reservationsConfirmed());
        // Both cancellation flavours count as cancelled — who clicked is a separate question
        assertEquals(2L, counts.reservationsCancelled());
    }

    @Test
    @DisplayName("shouldSplitReservationsIntoUpcomingAndPastUsingWarsawTime")
    void shouldSplitReservationsIntoUpcomingAndPastUsingWarsawTime() {
        LocalDateTime now = nowWarsaw();
        // Whole days away from "now" on both sides, so the split cannot land on the boundary
        // and the assertion cannot depend on the hour CI happens to run at.
        bookingOn(slotAt(now.toLocalDate().plusDays(3), LocalTime.of(18, 0), LocalTime.of(20, 0)));
        bookingOn(slotAt(now.toLocalDate().minusDays(3), LocalTime.of(18, 0), LocalTime.of(20, 0)));

        UserReservationHistoryDto history = service.getReservationHistory(user.getId(), 0, 25).orElseThrow();

        assertEquals(1, history.upcoming().size());
        assertEquals(1, history.past().size());
        assertTrue(history.upcoming().getFirst().date().isAfter(now.toLocalDate()));
        assertTrue(history.past().getFirst().date().isBefore(now.toLocalDate()));
    }

    /**
     * Past bookings are the one section with no natural ceiling — one row per attended session,
     * and one per DAY of a multi-day event. Left unbounded, a long-standing account would ship its
     * whole history in a single response and render it as one enormous list.
     */
    @Test
    @DisplayName("shouldPageThePastSectionAndReportTheFullTotal")
    void shouldPageThePastSectionAndReportTheFullTotal() {
        LocalDate day = nowWarsaw().toLocalDate();
        for (int i = 1; i <= 30; i++) {
            bookingOn(slotAt(day.minusDays(i), LocalTime.of(10, 0), LocalTime.of(12, 0)));
        }

        UserReservationHistoryDto firstPage = service.getReservationHistory(user.getId(), 0, 10).orElseThrow();

        assertEquals(10, firstPage.past().size());
        assertEquals(30L, firstPage.pastTotal(), "the total must describe the whole history, not the page");
        // Newest first, so page 0 starts at yesterday
        assertEquals(day.minusDays(1), firstPage.past().getFirst().date());

        UserReservationHistoryDto lastPage = service.getReservationHistory(user.getId(), 2, 10).orElseThrow();
        assertEquals(10, lastPage.past().size());
        assertEquals(day.minusDays(30), lastPage.past().getLast().date());

        // A caller asking for everything still gets a bounded page
        UserReservationHistoryDto greedy = service.getReservationHistory(user.getId(), 0, 100_000).orElseThrow();
        assertTrue(greedy.past().size() <= AdminUserHistoryService.MAX_PAST_SIZE,
            "past section must stay bounded no matter what the client asks for");
    }

    @Test
    @DisplayName("shouldReturnEmptyHistorySectionsForUserWithNoActivity")
    void shouldReturnEmptyHistorySectionsForUserWithNoActivity() {
        UserReservationHistoryDto history = service.getReservationHistory(user.getId(), 0, 25).orElseThrow();

        assertTrue(history.upcoming().isEmpty());
        assertTrue(history.past().isEmpty());
        assertTrue(history.waitlist().isEmpty());
        assertTrue(history.invitations().isEmpty());
        assertTrue(history.trainingRequests().isEmpty());
    }

    @Test
    @DisplayName("shouldShowAdminCancellationInTheAffectedUsersTimeline")
    void shouldShowAdminCancellationInTheAffectedUsersTimeline() {
        User admin = userRepository.save(adminUser());
        TimeSlot slot = slotAt(nowWarsaw().toLocalDate().plusDays(2), LocalTime.of(18, 0), LocalTime.of(20, 0));

        // The user booked; then an admin cancelled it. logCancelledByAdmin files the entry under
        // the affected user on purpose — that is what makes the card's timeline useful without a
        // target-user column, and it is the one thing worth pinning down here.
        activityLogService.logReservationCreated(user, slot, 1);
        activityLogService.logCancelledByAdmin(user, slot, 1);
        activityLogService.logAdminUserForceLogout(admin, user.getFullName() + " (" + user.getEmail() + ")");

        List<ActivityLogDto> timeline = service.getActivity(user.getId(), 0, 20).orElseThrow();

        assertEquals(2, timeline.size());
        assertTrue(timeline.stream().anyMatch(l -> l.actionType().equals("RESERVATION_CANCELLED_BY_ADMIN")));
        assertTrue(timeline.stream().anyMatch(l -> l.actionType().equals("RESERVATION_CREATED")));
        // The forced logout was performed BY the admin, so it is filed under the admin
        assertTrue(timeline.stream().noneMatch(l -> l.actionType().equals("ADMIN_USER_FORCE_LOGOUT")));
    }

    @Test
    @DisplayName("shouldReturnTimelineNewestFirst")
    void shouldReturnTimelineNewestFirst() {
        TimeSlot slot = slotAt(nowWarsaw().toLocalDate().plusDays(2), LocalTime.of(18, 0), LocalTime.of(20, 0));
        activityLogService.logReservationCreated(user, slot, 1);
        activityLogService.logReservationCancelled(user, slot, 1);

        List<ActivityLogDto> timeline = service.getActivity(user.getId(), 0, 20).orElseThrow();

        assertEquals(2, timeline.size());
        assertFalse(timeline.get(0).createdAt().isBefore(timeline.get(1).createdAt()));
    }

    @Test
    @DisplayName("shouldNotLeakAnotherUsersTimeline")
    void shouldNotLeakAnotherUsersTimeline() {
        User other = userRepository.save(
            new User("inny@example.com", "Ewa", "Nowak", "+48987654321", "ewa"));
        TimeSlot slot = slotAt(nowWarsaw().toLocalDate().plusDays(2), LocalTime.of(18, 0), LocalTime.of(20, 0));

        activityLogService.logReservationCreated(other, slot, 1);

        Optional<List<ActivityLogDto>> timeline = service.getActivity(user.getId(), 0, 20);

        assertTrue(timeline.orElseThrow().isEmpty());
    }

    private static User adminUser() {
        User admin = new User("admin@example.com", "Adam", "Trener", "+48111222333", "adam");
        admin.setRole(UserRole.ADMIN);
        admin.setEmailVerified(true);
        return admin;
    }
}
