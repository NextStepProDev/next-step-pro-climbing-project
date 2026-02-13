package pl.nextsteppro.climbing.api.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.auth.AuthTokenRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
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

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthMailService authMailService,
                       ReservationRepository reservationRepository,
                       AuthTokenRepository authTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authMailService = authMailService;
        this.reservationRepository = reservationRepository;
        this.authTokenRepository = authTokenRepository;
    }

    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.hasPassword()) {
            throw new IllegalStateException("Konto nie posiada hasła (logowanie przez OAuth)");
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalStateException("Nieprawidłowe aktualne hasło");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        authMailService.sendPasswordChangedNotification(user);
    }

    public void deleteAccount(UUID userId, String password) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.hasPassword()) {
            throw new IllegalStateException("Konto nie posiada hasła (logowanie przez OAuth)");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalStateException("Nieprawidłowe hasło");
        }

        // Cancel all confirmed reservations
        for (Reservation reservation : reservationRepository.findByUserId(userId)) {
            if (reservation.isConfirmed()) {
                reservation.cancel();
                reservationRepository.save(reservation);
            }
        }

        // Remove tokens
        authTokenRepository.deleteByUserId(userId);

        // Delete user
        userRepository.delete(user);
    }

    public void updateNotificationPreference(UUID userId, boolean enabled) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setEmailNotificationsEnabled(enabled);
        userRepository.save(user);
    }
}
