package pl.nextsteppro.climbing.api.user;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class UserService {

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

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthMailService authMailService,
                       ReservationRepository reservationRepository,
                       AuthTokenRepository authTokenRepository,
                       JwtService jwtService,
                       MessageService msg,
                       NewsletterConsentLogRepository consentLogRepository,
                       WaitlistService waitlistService,
                       EventWaitlistService eventWaitlistService) {
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

        // 1. Zbierz dotknięte sloty/wydarzenia ZANIM anulujemy rezerwacje (projekcje — nie ładują encji do sesji).
        List<UUID> affectedSlotIds = reservationRepository.findConfirmedSlotIdsByUserId(userId);
        List<UUID> affectedEventIds = reservationRepository.findConfirmedEventIdsByUserId(userId);
        int cancelledReservations = affectedSlotIds.size();

        // 2. Anuluj wszystkie potwierdzone rezerwacje (bulk UPDATE → CANCELLED).
        //    Bulk zamiast ładowania encji — unika konfliktu sesji Hibernate z usuwanym rodzicem (user).
        //    Po tym kroku miejsca są w bazie zwolnione (status != CONFIRMED).
        reservationRepository.cancelConfirmedByUserId(userId);

        // 3. Powiadom listy oczekujących o zwolnionych miejscach (najpierw sloty, potem wydarzenia).
        //    notifyAll liczy wolne miejsca świeżym zapytaniem agregującym, więc widzi już anulowane rezerwacje.
        for (UUID slotId : affectedSlotIds) {
            waitlistService.notifyAll(slotId);
        }
        for (UUID eventId : affectedEventIds) {
            eventWaitlistService.notifyAll(eventId);
        }

        // 4. Usuń użytkownika z list oczekujących, na których SAM czekał.
        //    Jeśli miał aktywną ofertę (PENDING), zwolnione miejsce trafia do pozostałych oczekujących.
        waitlistService.removeUserFromAllWaitlists(userId);
        eventWaitlistService.removeUserFromAllWaitlists(userId);

        // 5. Powiadom administratora o usunięciu konta (async, nie blokuje transakcji).
        authMailService.sendAccountSelfDeletedAdminNotification(user, cancelledReservations);

        // 6. Usuń tokeny (bulk DELETE) i samego użytkownika.
        //    DB kaskaduje (ON DELETE CASCADE): anulowane rezerwacje, wpisy waitlisty, logi, gwiazdki.
        authTokenRepository.deleteAllByUserId(userId);
        userRepository.delete(user);
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
