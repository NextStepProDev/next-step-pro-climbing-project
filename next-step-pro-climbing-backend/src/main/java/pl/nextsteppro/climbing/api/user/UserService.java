package pl.nextsteppro.climbing.api.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.reservation.UserSeatReleaseService;
import pl.nextsteppro.climbing.api.trainingcalendar.CommentFileSupport;
import pl.nextsteppro.climbing.api.trainingcalendar.AttachmentSupport;
import pl.nextsteppro.climbing.domain.auth.AuthToken;
import pl.nextsteppro.climbing.domain.auth.AuthTokenRepository;
import pl.nextsteppro.climbing.domain.auth.TokenType;
import pl.nextsteppro.climbing.domain.newsletter.NewsletterConsentLog;
import pl.nextsteppro.climbing.domain.newsletter.NewsletterConsentLogRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.mail.AuthMailService;
import pl.nextsteppro.climbing.infrastructure.security.JwtService;
import pl.nextsteppro.climbing.infrastructure.security.PasswordPolicyValidator;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final String AVATAR_FOLDER = "avatars";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthMailService authMailService;
    private final AuthTokenRepository authTokenRepository;
    private final JwtService jwtService;
    private final MessageService msg;
    private final NewsletterConsentLogRepository consentLogRepository;
    private final UserSeatReleaseService userSeatReleaseService;
    private final CommentFileSupport commentFileSupport;
    private final AttachmentSupport attachmentSupport;
    private final FileStorageService fileStorageService;
    private final PasswordPolicyValidator passwordPolicy;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthMailService authMailService,
                       AuthTokenRepository authTokenRepository,
                       JwtService jwtService,
                       MessageService msg,
                       NewsletterConsentLogRepository consentLogRepository,
                       UserSeatReleaseService userSeatReleaseService,
                       CommentFileSupport commentFileSupport,
                       AttachmentSupport attachmentSupport,
                       FileStorageService fileStorageService,
                       PasswordPolicyValidator passwordPolicy) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authMailService = authMailService;
        this.authTokenRepository = authTokenRepository;
        this.jwtService = jwtService;
        this.msg = msg;
        this.consentLogRepository = consentLogRepository;
        this.userSeatReleaseService = userSeatReleaseService;
        this.commentFileSupport = commentFileSupport;
        this.attachmentSupport = attachmentSupport;
        this.fileStorageService = fileStorageService;
        this.passwordPolicy = passwordPolicy;
    }

    @Transactional
    public User uploadAvatar(UUID userId, MultipartFile file) throws IOException {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String oldFilename = user.getAvatarFilename();
        String filename = fileStorageService.store(file, AVATAR_FOLDER);
        user.setAvatarFilename(filename);
        userRepository.save(user);
        if (oldFilename != null) {
            deleteAvatarFileQuietly(oldFilename);
        }
        return user;
    }

    @Transactional
    public User deleteAvatar(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String oldFilename = user.getAvatarFilename();
        if (oldFilename != null) {
            user.setAvatarFilename(null);
            userRepository.save(user);
            deleteAvatarFileQuietly(oldFilename);
        }
        return user;
    }

    private void deleteAvatarFileQuietly(String filename) {
        try {
            fileStorageService.delete(filename, AVATAR_FOLDER);
        } catch (IOException e) {
            logger.warn("Failed to delete avatar file {}: {}", filename, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public User getProfile(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public User updateProfile(UUID userId, String firstName, String lastName, String phone, String nickname) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (firstName != null) user.setFirstName(firstName);
        if (lastName != null) user.setLastName(lastName);
        if (phone != null) user.setPhone(phone);
        if (nickname != null) user.setNickname(nickname);

        return userRepository.save(user);
    }

    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.hasPassword()) {
            throw new IllegalStateException(msg.get("user.no.password"));
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalStateException(msg.get("user.wrong.current.password"));
        }

        passwordPolicy.validate(newPassword, user.getEmail(), user.getFirstName(), user.getLastName());

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        authMailService.sendPasswordChangedNotification(user);
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarWeek", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true),
        // The ascents go with the account (ON DELETE CASCADE), but the public feed is cached for
        // five minutes — without this the name of somebody who just erased their account keeps
        // being served. Same reasoning as the opt-out switch: "you disappear within five minutes"
        // is not an answer to a request for removal.
        @CacheEvict(value = "publicAscents", allEntries = true)
    })
    public void deleteAccount(UUID userId, @Nullable String password) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.hasPassword()) {
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new IllegalStateException(msg.get("user.wrong.password"));
            }
        }

        // 1-4. Free the seats and hand them to the queues. The ordering inside is load-bearing —
        //       see UserSeatReleaseService. Shared with the admin-side deletion so the two cannot drift.
        int cancelledReservations = userSeatReleaseService.releaseSeatsAndNotifyWaitlists(userId);

        // 5. Notify the admin about the account deletion (async, does not block the transaction).
        authMailService.sendAccountSelfDeletedAdminNotification(user, cancelledReservations);

        // 6. Delete the avatar file from disk (if any) — the entity cascades, the file does not.
        if (user.getAvatarFilename() != null) {
            deleteAvatarFileQuietly(user.getAvatarFilename());
        }

        // 7. Unlink the files attached to their thread messages — on their own trainings and in
        //    anyone else's. The rows go with the cascade, the files on disk do not. Shared with the
        //    admin-side deletion; the orphan sweep would catch a miss, but not for hours.
        commentFileSupport.purgeForUser(userId);

        // 7b. Same for the coach's materials on their plan. Reference-counted, because one file can
        //     also hang off a template or a copy on somebody else's calendar. These used to wait for
        //     the six-hourly orphan sweep — "eventually" is the wrong answer to an erasure request.
        attachmentSupport.purgeForUser(userId);

        // 8. Delete tokens (bulk DELETE) and the user themselves.
        //    The DB cascades (ON DELETE CASCADE): cancelled reservations, waitlist entries, logs, stars.
        authTokenRepository.deleteAllByUserId(userId);
        userRepository.delete(user);
    }

    /**
     * Logs the user out of all devices — deletes all their refresh tokens.
     * Access tokens (15 min) stay valid until expiry (stateless JWT), but afterwards
     * no device can refresh the session → real logout everywhere within ≤15 min.
     */
    public void logoutAllDevices(UUID userId) {
        authTokenRepository.deleteByUserIdAndTokenType(userId, TokenType.REFRESH_TOKEN);
    }

    public void updateNotificationPreference(UUID userId, boolean enabled) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setEmailNotificationsEnabled(enabled);
        userRepository.save(user);
    }

    /**
     * Adds or removes this climber from the public recent-ascents list.
     *
     * <p>Evicts that list's cache: somebody switching visibility off has asked to disappear, and
     * "in up to five minutes" is not an answer to that request.
     */
    @CacheEvict(value = "publicAscents", allEntries = true)
    public void updateAscentsVisibility(UUID userId, boolean publicVisible) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setAscentsPublic(publicVisible);
        userRepository.save(user);
    }

    public void updateLanguagePreference(UUID userId, String language) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setPreferredLanguage(language);
        userRepository.save(user);
    }

    public void updateNewsletterSubscription(UUID userId, boolean subscribed) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setNewsletterSubscribed(subscribed);
        user.setNewsletterChoiceMade(true);
        if (subscribed) {
            user.setNewsletterSubscribedAt(Instant.now());
        }
        userRepository.save(user);
        consentLogRepository.save(new NewsletterConsentLog(user, subscribed ? "SUBSCRIBED" : "UNSUBSCRIBED", "SETTINGS"));
    }

    /**
     * Checks the unsubscribe link is real without acting on it and answers with the recipient's
     * language, so the confirmation page speaks the same one the email did. Read-only on purpose:
     * this page is fetched by mail scanners as often as by people.
     */
    @Transactional(readOnly = true)
    public String resolveUnsubscribeLanguage(String token) {
        return findUnsubscribeRecipient(token).getPreferredLanguage();
    }

    /** @return the recipient's language, for the confirmation page. */
    public String unsubscribeByToken(String token) {
        User user = findUnsubscribeRecipient(token);
        user.setNewsletterSubscribed(false);
        user.setNewsletterChoiceMade(true);
        userRepository.save(user);
        consentLogRepository.save(new NewsletterConsentLog(user, "UNSUBSCRIBED", "EMAIL_LINK"));
        return user.getPreferredLanguage();
    }

    private User findUnsubscribeRecipient(String token) {
        UUID parsed;
        try {
            parsed = UUID.fromString(token);
        } catch (IllegalArgumentException e) {
            // Anything that is not a UUID is a mangled link, not a lookup — same answer either way.
            throw new IllegalArgumentException("Invalid unsubscribe token");
        }
        return userRepository.findByNewsletterUnsubscribeToken(parsed)
            .orElseThrow(() -> new IllegalArgumentException("Invalid unsubscribe token"));
    }

    /**
     * The unsubscribe link that goes into every newsletter. It is the recipient's permanent token,
     * not a freshly minted one — see {@link User#getNewsletterUnsubscribeToken()} for why every
     * email carries the same value.
     */
    @Transactional(readOnly = true)
    public String newsletterUnsubscribeToken(User user) {
        return user.getNewsletterUnsubscribeToken().toString();
    }
}
