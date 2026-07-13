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
@Tag(name = "Dev Auth", description = "Developer authentication (dev profile only)")
public class DevAuthController {

    private final UserRepository userRepository;

    public DevAuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Operation(
        summary = "Log in as a test user",
        description = "Creates a session for a test user. Use this to test endpoints that require login."
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
        summary = "Log out",
        description = "Ends the developer session"
    )
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> devLogout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Wylogowano"));
    }

    @Operation(
        summary = "Check session",
        description = "Checks whether you are logged in and as whom"
    )
    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> checkSession(HttpSession session) {
        UUID userId = (UUID) session.getAttribute("DEV_USER_ID");

        if (userId == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", false);
            response.put("message", "You are not logged in. Use POST /api/dev/login");
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
                response.put("message", "User not found");
                return ResponseEntity.ok(response);
            });
    }

    @Operation(
        summary = "List of test users",
        description = "Returns a list of users for quick login"
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
