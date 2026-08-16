package pl.nextsteppro.climbing.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.reservation.UserSeatReleaseService;
import pl.nextsteppro.climbing.api.trainingcalendar.CommentFileSupport;
import pl.nextsteppro.climbing.domain.auth.AuthTokenRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.mail.AuthMailService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Retires accounts that were registered and never confirmed. Such an account cannot log in, so it
 * is not a dormant user but a row nobody can ever act on — and until now it stayed forever, showing
 * up in every user picker in the panel as if it were real.
 *
 * <p><b>Why no {@code reminder_sent_at} column:</b> the reminder covers the age band
 * {@code [6d, 7d)} and the deletion everything at {@code >= 7d}. The job runs once a day and the
 * band is exactly a day wide, so every account falls into it on exactly one run, and the run that
 * follows is the one that deletes it. A marker in the database would be a second source of truth
 * about something the clock already answers.
 *
 * <p>Both passes are public and transactional on this bean because the scheduler only calls them:
 * a {@code @Scheduled} method invoking {@code @Transactional} methods of its own class bypasses the
 * Spring proxy and silently runs without a transaction.
 */
@Service
public class UnverifiedAccountRetentionService {

    private static final Logger log = LoggerFactory.getLogger(UnverifiedAccountRetentionService.class);

    /** How long an unconfirmed account is kept before it is deleted. */
    static final Duration RETENTION = Duration.ofDays(7);

    /** How far ahead of the deletion the warning mail goes out. */
    static final Duration REMINDER_LEAD = Duration.ofDays(1);

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final AuthMailService authMailService;
    private final UserSeatReleaseService userSeatReleaseService;
    private final CommentFileSupport commentFileSupport;

    public UnverifiedAccountRetentionService(UserRepository userRepository,
                                             AuthTokenRepository authTokenRepository,
                                             AuthMailService authMailService,
                                             UserSeatReleaseService userSeatReleaseService,
                                             CommentFileSupport commentFileSupport) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.authMailService = authMailService;
        this.userSeatReleaseService = userSeatReleaseService;
        this.commentFileSupport = commentFileSupport;
    }

    @Transactional
    public int sendReminders() {
        return sendReminders(Instant.now());
    }

    /**
     * Warns everyone whose account is about to be deleted. The mail carries no verification token
     * on purpose — see {@link AuthMailService#sendVerificationReminder}.
     *
     * @param now taken explicitly so the band can be tested without waiting a week
     * @return how many reminders were sent
     */
    @Transactional
    public int sendReminders(Instant now) {
        Instant bandStart = now.minus(RETENTION);
        Instant bandEnd = now.minus(RETENTION.minus(REMINDER_LEAD));

        List<User> due = userRepository.findAllByEmailVerifiedFalseAndCreatedAtBefore(bandEnd).stream()
            .filter(u -> u.getCreatedAt().isAfter(bandStart))
            .filter(u -> !u.isAdmin())
            .toList();

        for (User user : due) {
            authMailService.sendVerificationReminder(user);
        }
        return due.size();
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true),
        @CacheEvict(value = "publicAscents", allEntries = true)
    })
    public int deleteExpired() {
        return deleteExpired(Instant.now());
    }

    /**
     * Deletes what has outlived the retention window.
     *
     * @param now taken explicitly so the window can be tested without waiting a week
     * @return how many accounts were removed
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true),
        @CacheEvict(value = "publicAscents", allEntries = true)
    })
    public int deleteExpired(Instant now) {
        List<User> expired = userRepository.findAllByEmailVerifiedFalseAndCreatedAtBefore(now.minus(RETENTION));

        int deleted = 0;
        for (User user : expired) {
            // An unconfirmed ADMIN means the address in ADMIN_EMAIL registered and never finished.
            // That is a signal worth seeing in the logs, not litter to sweep away — and the manual
            // path refuses to delete admins for the same reason.
            if (user.isAdmin()) {
                log.warn("Unverified ADMIN account kept: {} (registered {})", user.getEmail(), user.getCreatedAt());
                continue;
            }

            // Same sequence as UserService#deleteAccount and AdminService#deleteUser, through the
            // same shared service: cancelling the reservations is only half the job, the freed
            // seats have to reach the queues. A third hand-rolled copy is how those two drifted
            // apart the first time. No "your account was deleted" mail — nobody proved they own
            // this address, which is the whole reason the account is going.
            userSeatReleaseService.releaseSeatsAndNotifyWaitlists(user.getId());
            commentFileSupport.purgeForUser(user.getId());
            authTokenRepository.deleteAllByUserId(user.getId());
            userRepository.delete(user);
            deleted++;
        }
        return deleted;
    }
}
