package pl.nextsteppro.climbing.api.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.config.AppConfig;
import pl.nextsteppro.climbing.config.CurrentUserId;
import pl.nextsteppro.climbing.domain.user.User;

import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@Tag(name = "User", description = "Profil użytkownika")
public class UserController {

    private final UserService userService;
    private final String siteUrl;

    public UserController(UserService userService, AppConfig appConfig) {
        this.userService = userService;
        this.siteUrl = appConfig.getSiteUrl();
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
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        return ResponseEntity.ok(toProfileDto(userService.getProfile(userId)));
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
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody UpdateProfileRequest request) {

        return ResponseEntity.ok(toProfileDto(
            userService.updateProfile(userId, request.firstName(), request.lastName(), request.phone(), request.nickname())
        ));
    }

    @Operation(summary = "Zmień hasło", description = "Zmienia hasło zalogowanego użytkownika")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Hasło zmienione"),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany"),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe dane")
    })
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody ChangePasswordRequest request) {

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
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody DeleteAccountRequest request) {

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
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody UpdateNotificationsRequest request) {

        userService.updateNotificationPreference(userId, request.enabled());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Zmień język", description = "Ustawia preferowany język użytkownika")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Język zmieniony"),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany")
    })
    @PutMapping("/me/language")
    public ResponseEntity<Void> updateLanguage(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody UpdateLanguageRequest request) {

        userService.updateLanguagePreference(userId, request.language());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Wypisz z newslettera", description = "Wypisuje użytkownika z newslettera bez logowania (link z maila)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Wypisano pomyślnie"),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowy token")
    })
    @GetMapping("/unsubscribe")
    public ResponseEntity<String> unsubscribe(@RequestParam String token) {
        try {
            userService.unsubscribeByToken(token);
            String html = """
                <html><body style="font-family:Arial,sans-serif;text-align:center;padding:60px;background:#1a1816;color:#e0e0e0;">
                <h2 style="color:#3b82f6;">Wypisano z newslettera</h2>
                <p>Zostałeś pomyślnie wypisany z newslettera Next Step Pro Climbing.</p>
                <p><a href="%s" style="color:#3b82f6;">Wróć na stronę główną</a></p>
                </body></html>
                """.formatted(siteUrl);
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
        } catch (IllegalArgumentException e) {
            String html = """
                <html><body style="font-family:Arial,sans-serif;text-align:center;padding:60px;background:#1a1816;color:#e0e0e0;">
                <h2 style="color:#ef4444;">Nieprawidłowy link</h2>
                <p>Link do wypisania jest nieprawidłowy lub wygasł.</p>
                <p><a href="%s" style="color:#3b82f6;">Wróć na stronę główną</a></p>
                </body></html>
                """.formatted(siteUrl);
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_HTML).body(html);
        }
    }

    @Operation(summary = "Subskrypcja newslettera", description = "Włącza lub wyłącza newsletter")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Preferencja zaktualizowana"),
        @ApiResponse(responseCode = "401", description = "Użytkownik niezalogowany")
    })
    @PutMapping("/me/newsletter")
    public ResponseEntity<Void> updateNewsletter(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody UpdateNewsletterRequest request) {

        userService.updateNewsletterSubscription(userId, request.subscribed());
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
            user.getPreferredLanguage(),
            user.isNewsletterSubscribed(),
            user.isNewsletterChoiceMade(),
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
    @Schema(description = "Preferowany język (pl, en, es)") String preferredLanguage,
    @Schema(description = "Czy subskrybuje newsletter") boolean newsletterSubscribed,
    @Schema(description = "Czy podjął decyzję ws. newslettera") boolean newsletterChoiceMade,
    @Schema(description = "Data utworzenia konta") java.time.Instant createdAt
) {}

@Schema(description = "Dane do aktualizacji profilu")
record UpdateProfileRequest(
    @Schema(description = "Imię", example = "Jan")
    @jakarta.validation.constraints.Size(min = 3, max = 100, message = "{validation.firstname.size}")
    String firstName,
    @Schema(description = "Nazwisko", example = "Kowalski")
    @jakarta.validation.constraints.Size(min = 3, max = 100, message = "{validation.lastname.size}")
    String lastName,
    @Schema(description = "Numer telefonu", example = "+48123456789")
    @jakarta.validation.constraints.Pattern(regexp = "^\\+[0-9]{1,4}[0-9]{9}$", message = "{validation.phone.invalid}")
    String phone,
    @Schema(description = "Nick/pseudonim", example = "Climber123") String nickname
) {}

@Schema(description = "Zmiana hasła")
record ChangePasswordRequest(
    @Schema(description = "Aktualne hasło")
    @jakarta.validation.constraints.NotBlank(message = "{validation.password.required}")
    String currentPassword,

    @Schema(description = "Nowe hasło")
    @jakarta.validation.constraints.NotBlank(message = "{validation.password.required}")
    @jakarta.validation.constraints.Size(min = 8, max = 100, message = "{validation.password.size}")
    String newPassword
) {}

@Schema(description = "Usunięcie konta")
record DeleteAccountRequest(
    @Schema(description = "Hasło do potwierdzenia") String password
) {}

@Schema(description = "Ustawienia powiadomień")
record UpdateNotificationsRequest(
    @Schema(description = "Czy włączyć powiadomienia email") boolean enabled
) {}

@Schema(description = "Zmiana języka")
record UpdateLanguageRequest(
    @Schema(description = "Kod języka (pl, en, es)", example = "pl") String language
) {}

@Schema(description = "Subskrypcja newslettera")
record UpdateNewsletterRequest(
    @Schema(description = "Czy subskrybować newsletter") boolean subscribed
) {}
