package pl.nextsteppro.climbing.api.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.auth.AuthTokenRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.mail.AuthMailService;

import java.util.UUID;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthMailService authMailService;
    private final ReservationRepository reservationRepository;
    private final AuthTokenRepository authTokenRepository;
    private final MessageService msg;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthMailService authMailService,
                       ReservationRepository reservationRepository,
                       AuthTokenRepository authTokenRepository,
                       MessageService msg) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authMailService = authMailService;
        this.reservationRepository = reservationRepository;
        this.authTokenRepository = authTokenRepository;
        this.msg = msg;
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

    public void deleteAccount(UUID userId, String password) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.hasPassword()) {
            throw new IllegalStateException(msg.get("user.no.password"));
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalStateException(msg.get("user.wrong.password"));
        }

        // Cancel all confirmed reservations (bulk UPDATE — avoids Hibernate session conflict with deleted parent)
        reservationRepository.cancelConfirmedByUserId(userId);

        // Remove tokens (bulk DELETE)
        authTokenRepository.deleteAllByUserId(userId);

        // Delete user
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
        userRepository.save(user);
    }
}
