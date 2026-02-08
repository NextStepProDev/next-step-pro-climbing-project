package pl.nextsteppro.climbing.api.dev;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.domain.user.UserRole;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Development-only authentication controller.
 * Allows testing authenticated endpoints without OAuth2.
 *
 * WARNING: This controller is only active in 'dev' profile!
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
@Tag(name = "Dev Auth", description = "Autoryzacja deweloperska (tylko profil dev)")
public class DevAuthController {

    private final UserRepository userRepository;

    public DevAuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Operation(
        summary = "Zaloguj jako testowy użytkownik",
        description = "Tworzy sesję dla testowego użytkownika. Użyj tego do testowania endpointów wymagających logowania."
    )
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> devLogin(
            @RequestParam(defaultValue = "test@example.com") String email,
            @RequestParam(defaultValue = "false") boolean asAdmin,
            HttpSession session) {

        User user = userRepository.findByEmail(email)
            .orElseGet(() -> createDevUser(email, asAdmin));

        // Store user ID in session (simulates OAuth2 login)
        session.setAttribute("DEV_USER_ID", user.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Zalogowano jako " + user.getEmail());
        response.put("userId", user.getId().toString());
        response.put("email", user.getEmail());
        response.put("firstName", user.getFirstName());
        response.put("lastName", user.getLastName());
        response.put("role", user.getRole().name());
        response.put("isAdmin", user.isAdmin());
        response.put("sessionId", session.getId());

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Wyloguj",
        description = "Kończy sesję deweloperską"
    )
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> devLogout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Wylogowano"));
    }

    @Operation(
        summary = "Sprawdź sesję",
        description = "Sprawdza czy jesteś zalogowany i jako kto"
    )
    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> checkSession(HttpSession session) {
        UUID userId = (UUID) session.getAttribute("DEV_USER_ID");

        if (userId == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", false);
            response.put("message", "Nie jesteś zalogowany. Użyj POST /api/dev/login");
            return ResponseEntity.ok(response);
        }

        return userRepository.findById(userId)
            .map(user -> {
                Map<String, Object> response = new HashMap<>();
                response.put("authenticated", true);
                response.put("userId", user.getId().toString());
                response.put("email", user.getEmail());
                response.put("firstName", user.getFirstName());
                response.put("lastName", user.getLastName());
                response.put("role", user.getRole().name());
                response.put("isAdmin", user.isAdmin());
                return ResponseEntity.ok(response);
            })
            .orElseGet(() -> {
                Map<String, Object> response = new HashMap<>();
                response.put("authenticated", false);
                response.put("message", "Użytkownik nie istnieje");
                return ResponseEntity.ok(response);
            });
    }

    @Operation(
        summary = "Lista testowych użytkowników",
        description = "Zwraca listę użytkowników do szybkiego logowania"
    )
    @GetMapping("/users")
    public ResponseEntity<?> listDevUsers() {
        var users = userRepository.findAll().stream()
            .map(u -> Map.of(
                "id", u.getId().toString(),
                "email", u.getEmail(),
                "name", u.getFirstName() + " " + u.getLastName(),
                "role", u.getRole().name()
            ))
            .toList();
        return ResponseEntity.ok(users);
    }

    private User createDevUser(String email, boolean asAdmin) {
        User user = new User(
            email,
            "Test",
            "User",
            "+48123456789",
            "TestUser"
        );
        user.setOauthProvider("dev");
        user.setOauthId("dev-" + UUID.randomUUID());
        if (asAdmin) {
            user.setRole(UserRole.ADMIN);
        }
        return userRepository.save(user);
    }
}
