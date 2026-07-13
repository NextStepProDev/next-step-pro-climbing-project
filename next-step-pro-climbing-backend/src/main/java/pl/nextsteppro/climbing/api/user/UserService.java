package pl.nextsteppro.climbing.api.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.reservation.EventWaitlistService;
import pl.nextsteppro.climbing.api.reservation.WaitlistService;
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
    private final ReservationRepository reservationRepository;
    private final AuthTokenRepository authTokenRepository;
    private final JwtService jwtService;
    private final MessageService msg;
    private final NewsletterConsentLogRepository consentLogRepository;
    private final WaitlistService waitlistService;
    private final EventWaitlistService eventWaitlistService;
    private final FileStorageService fileStorageService;
    private final PasswordPolicyValidator passwordPolicy;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthMailService authMailService,
                       ReservationRepository reservationRepository,
                       AuthTokenRepository authTokenRepository,
                       JwtService jwtService,
                       MessageService msg,
                       NewsletterConsentLogRepository consentLogRepository,
                       WaitlistService waitlistService,
                       EventWaitlistService eventWaitlistService,
                       FileStorageService fileStorageService,
                       PasswordPolicyValidator passwordPolicy) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authMailService = authMailService;
        this.reservationRepository = reservationRepository;
        this.authTokenRepository = authTokenRepository;
        this.jwtService = jwtService;
        this.msg = msg;
        this.consentLogRepository = consentLogRepository;
        this.waitlistService = waitlistService;
        this.eventWaitlistService = eventWaitlistService;
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
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void deleteAccount(UUID userId, @Nullable String password) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.hasPassword()) {
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new IllegalStateException(msg.get("user.wrong.password"));
            }
        }

        // 1. Collect affected slots/events BEFORE cancelling reservations (projections — no entities loaded into the session).
        List<UUID> affectedSlotIds = reservationRepository.findConfirmedSlotIdsByUserId(userId);
        List<UUID> affectedEventIds = reservationRepository.findConfirmedEventIdsByUserId(userId);
        int cancelledReservations = affectedSlotIds.size();

        // 2. Cancel all confirmed reservations (bulk UPDATE → CANCELLED).
        //    Bulk update instead of loading entities — avoids a Hibernate session conflict with the parent (user) being deleted.
        //    After this step the seats are freed in the database (status != CONFIRMED).
        reservationRepository.cancelConfirmedByUserId(userId);

        // 3. Notify waitlists about the freed seats (slots first, then events).
        //    notifyAll counts free seats with a fresh aggregate query, so it already sees the cancelled reservations.
        for (UUID slotId : affectedSlotIds) {
            waitlistService.notifyAll(slotId);
        }
        for (UUID eventId : affectedEventIds) {
            eventWaitlistService.notifyAll(eventId);
        }

        // 4. Remove the user from waitlists THEY were waiting on.
        //    If they had an active offer (PENDING), the freed seat goes to the remaining waiters.
        waitlistService.removeUserFromAllWaitlists(userId);
        eventWaitlistService.removeUserFromAllWaitlists(userId);

        // 5. Notify the admin about the account deletion (async, does not block the transaction).
        authMailService.sendAccountSelfDeletedAdminNotification(user, cancelledReservations);

        // 6. Delete the avatar file from disk (if any) — the entity cascades, the file does not.
        if (user.getAvatarFilename() != null) {
            deleteAvatarFileQuietly(user.getAvatarFilename());
        }

        // 7. Delete tokens (bulk DELETE) and the user themselves.
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

    public void unsubscribeByToken(String token) {
        String tokenHash = jwtService.hashToken(token);
        AuthToken authToken = authTokenRepository.findValidToken(tokenHash, TokenType.NEWSLETTER_UNSUBSCRIBE, Instant.now())
            .orElseThrow(() -> new IllegalArgumentException("Invalid unsubscribe token"));
        User user = authToken.getUser();
        user.setNewsletterSubscribed(false);
        user.setNewsletterChoiceMade(true);
        userRepository.save(user);
        consentLogRepository.save(new NewsletterConsentLog(user, "UNSUBSCRIBED", "EMAIL_LINK"));
    }

    public String generateNewsletterUnsubscribeToken(User user) {
        authTokenRepository.deleteByUserIdAndTokenType(user.getId(), TokenType.NEWSLETTER_UNSUBSCRIBE);
        String token = jwtService.generateSecureToken();
        String tokenHash = jwtService.hashToken(token);
        Instant expiry = Instant.now().plus(Duration.ofDays(3650));
        authTokenRepository.save(new AuthToken(user, tokenHash, TokenType.NEWSLETTER_UNSUBSCRIBE, expiry));
        return token;
    }
}
