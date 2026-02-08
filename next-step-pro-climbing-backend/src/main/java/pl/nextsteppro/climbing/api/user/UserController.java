package pl.nextsteppro.climbing.api.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.config.CurrentUserService;
import pl.nextsteppro.climbing.config.CustomOAuth2User;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;

import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@Tag(name = "User", description = "Profil użytkownika")
public class UserController {

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final UserService userService;

    public UserController(UserRepository userRepository, CurrentUserService currentUserService, UserService userService) {
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.userService = userService;
    }

    @Operation(
        summary = "Pobierz profil",
        description = "Zwraca dane profilu zalogowanego użytkownika"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dane profilu",
            content = @Content(schema = @Schema(implementation = UserProfileDto.class))),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany")
    })
    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> getCurrentUser(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomOAuth2User oAuth2User,
            HttpServletRequest request) {

        User user = currentUserService.getCurrentUser(oAuth2User, request).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(toProfileDto(user));
    }

    @Operation(
        summary = "Aktualizuj profil",
        description = "Aktualizuje dane profilu użytkownika (telefon, nick)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profil zaktualizowany",
            content = @Content(schema = @Schema(implementation = UserProfileDto.class))),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany"),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe dane")
    })
    @PutMapping("/me")
    public ResponseEntity<UserProfileDto> updateProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomOAuth2User oAuth2User,
            @Valid @RequestBody UpdateProfileRequest request,
            HttpServletRequest httpRequest) {

        User user = currentUserService.getCurrentUser(oAuth2User, httpRequest).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        // Update fields
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        if (request.nickname() != null) {
            user.setNickname(request.nickname());
        }
        user = userRepository.save(user);

        return ResponseEntity.ok(toProfileDto(user));
    }

    @Operation(summary = "Zmień hasło", description = "Zmienia hasło zalogowanego użytkownika")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Hasło zmienione"),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany"),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe dane")
    })
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomOAuth2User oAuth2User,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {

        UUID userId = currentUserService.getCurrentUserId(oAuth2User, httpRequest).orElse(null);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        userService.changePassword(userId, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Usuń konto", description = "Trwale usuwa konto użytkownika i anuluje wszystkie rezerwacje")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Konto usunięte"),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany"),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe hasło")
    })
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomOAuth2User oAuth2User,
            @Valid @RequestBody DeleteAccountRequest request,
            HttpServletRequest httpRequest) {

        UUID userId = currentUserService.getCurrentUserId(oAuth2User, httpRequest).orElse(null);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        userService.deleteAccount(userId, request.password());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Ustawienia powiadomień", description = "Włącza lub wyłącza powiadomienia email")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Preferencje zaktualizowane"),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany")
    })
    @PutMapping("/me/notifications")
    public ResponseEntity<Void> updateNotifications(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomOAuth2User oAuth2User,
            @Valid @RequestBody UpdateNotificationsRequest request,
            HttpServletRequest httpRequest) {

        UUID userId = currentUserService.getCurrentUserId(oAuth2User, httpRequest).orElse(null);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        userService.updateNotificationPreference(userId, request.enabled());
        return ResponseEntity.noContent().build();
    }

    private UserProfileDto toProfileDto(User user) {
        return new UserProfileDto(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getPhone(),
            user.getNickname(),
            user.getRole().name(),
            user.isAdmin(),
            user.isEmailNotificationsEnabled(),
            user.getCreatedAt()
        );
    }
}

@Schema(description = "Profil użytkownika")
record UserProfileDto(
    @Schema(description = "UUID użytkownika") UUID id,
    @Schema(description = "Adres email") String email,
    @Schema(description = "Imię") String firstName,
    @Schema(description = "Nazwisko") String lastName,
    @Schema(description = "Numer telefonu") String phone,
    @Schema(description = "Nick/pseudonim") String nickname,
    @Schema(description = "Rola: USER lub ADMIN") String role,
    @Schema(description = "Czy użytkownik jest adminem") boolean isAdmin,
    @Schema(description = "Czy powiadomienia email są włączone") boolean emailNotificationsEnabled,
    @Schema(description = "Data utworzenia konta") java.time.Instant createdAt
) {}

@Schema(description = "Dane do aktualizacji profilu")
record UpdateProfileRequest(
    @Schema(description = "Numer telefonu", example = "+48123456789") String phone,
    @Schema(description = "Nick/pseudonim", example = "Climber123") String nickname
) {}

@Schema(description = "Zmiana hasła")
record ChangePasswordRequest(
    @Schema(description = "Aktualne hasło") String currentPassword,
    @Schema(description = "Nowe hasło") String newPassword
) {}

@Schema(description = "Usunięcie konta")
record DeleteAccountRequest(
    @Schema(description = "Hasło do potwierdzenia") String password
) {}

@Schema(description = "Ustawienia powiadomień")
record UpdateNotificationsRequest(
    @Schema(description = "Czy włączyć powiadomienia email") boolean enabled
) {}
