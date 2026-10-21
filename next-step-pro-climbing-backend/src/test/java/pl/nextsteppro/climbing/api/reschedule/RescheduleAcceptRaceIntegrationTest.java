package pl.nextsteppro.climbing.api.reschedule;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.admin.reschedule.AdminRescheduleTestAccess;
import pl.nextsteppro.climbing.domain.reschedule.RescheduleRequestRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.infrastructure.mail.MailService;
import pl.nextsteppro.climbing.infrastructure.mail.RescheduleMailService;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Two answers to one request at the same moment — a double click on Accept, two admin tabs. Both
 * used to read PENDING, so the term was moved and the client mailed twice. Commits for real (a
 * row lock means nothing inside one rolled-back test transaction) and removes its rows by id.
 *
 * <p>The accepted-mail mock sleeps while the first transaction is still open, which holds the
 * window wide enough that the second request is guaranteed to read the row inside it.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RescheduleAcceptRaceIntegrationTest extends BaseIntegrationTest {

    @MockitoBean private RescheduleMailService rescheduleMailService;
    @MockitoBean private MailService mailService;

    @Autowired private RescheduleService rescheduleService;
    @Autowired private AdminRescheduleTestAccess admin;
    @Autowired private RescheduleRequestRepository requestRepository;

    private final List<UUID> users = new ArrayList<>();
    private UUID slotId;

    @Test
    void shouldMoveTheTermOnlyOnceWhenAcceptedTwiceAtTheSameMoment() throws Exception {
        User coach = user("race-coach@test.local", UserRole.ADMIN);
        User client = user("race-client@test.local", UserRole.USER);
        LocalDate day = LocalDate.now(ZoneId.of("Europe/Warsaw")).plusDays(21);
        TimeSlot slot = timeSlotRepository.save(new TimeSlot(day, LocalTime.of(18, 0), LocalTime.of(20, 0), 2));
        slotId = slot.getId();
        UUID booking = reservationRepository.save(new Reservation(client, slot)).getId();
        UUID requestId = rescheduleService.create(client.getId(), new CreateRescheduleRequest(
            booking, null, day.plusDays(2), null, LocalTime.of(18, 0), LocalTime.of(20, 0), null)).id();

        doAnswer(inv -> { Thread.sleep(1500); return null; })
            .when(rescheduleMailService).sendAccepted(any(), any(), any(), any(), any());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Boolean> accept = () -> {
            try {
                admin.accept(coach.getId(), requestId);
                return true;
            } catch (IllegalStateException refused) {
                return false;
            }
        };
        Future<Boolean> a = pool.submit(accept);
        Thread.sleep(300);
        Future<Boolean> b = pool.submit(accept);
        int succeeded = (a.get() ? 1 : 0) + (b.get() ? 1 : 0);
        pool.shutdown();

        assertEquals(1, succeeded, "exactly one of the two answers may go through");
        verify(rescheduleMailService, times(1)).sendAccepted(any(), any(), any(), any(), any());
    }

    @AfterEach
    void cleanUp() {
        // Slot first: its reservations, the request and the activity rows go with it by cascade.
        if (slotId != null) timeSlotRepository.deleteById(slotId);
        users.forEach(userRepository::deleteById);
    }

    private User user(String email, UserRole role) {
        User user = new User(email, "Ra", "Ce", "+48123123123", email.substring(0, email.indexOf('@')));
        user.setRole(role);
        user.setEmailVerified(true);
        user = userRepository.save(user);
        users.add(user.getId());
        return user;
    }
}
