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
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.config.AppConfig;
import pl.nextsteppro.climbing.config.CurrentUserId;
import pl.nextsteppro.climbing.domain.user.User;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@Tag(name = "User", description = "User profile")
public class UserController {

    private final UserService userService;
    private final String siteUrl;

    public UserController(UserService userService, AppConfig appConfig) {
        this.userService = userService;
        this.siteUrl = appConfig.getSiteUrl();
    }

    @Operation(
        summary = "Get profile",
        description = "Returns the logged-in user's profile data"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profile data",
            content = @Content(schema = @Schema(implementation = UserProfileDto.class))),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> getCurrentUser(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        return ResponseEntity.ok(toProfileDto(userService.getProfile(userId)));
    }

    @Operation(
        summary = "Update profile",
        description = "Updates the user's profile data (phone, nickname)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profile updated",
            content = @Content(schema = @Schema(implementation = UserProfileDto.class))),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "400", description = "Invalid data")
    })
    @PutMapping("/me")
    public ResponseEntity<UserProfileDto> updateProfile(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody UpdateProfileRequest request) {

        return ResponseEntity.ok(toProfileDto(
            userService.updateProfile(userId, request.firstName(), request.lastName(), request.phone(), request.nickname())
        ));
    }

    @Operation(summary = "Change password", description = "Changes the logged-in user's password")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Password changed"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "400", description = "Invalid data")
    })
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody ChangePasswordRequest request) {

        userService.changePassword(userId, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete account", description = "Permanently deletes the user account and cancels all reservations")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Account deleted"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "400", description = "Invalid password")
    })
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody DeleteAccountRequest request) {

        userService.deleteAccount(userId, request.password());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Log out of all devices",
        description = "Invalidates all of the user's refresh tokens — logs them out of all devices (within ≤15 min)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Sessions invalidated"),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @PostMapping("/me/logout-all")
    public ResponseEntity<Void> logoutAllDevices(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        userService.logoutAllDevices(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Notification settings", description = "Enables or disables email notifications")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Preferences updated"),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @PutMapping("/me/notifications")
    public ResponseEntity<Void> updateNotifications(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody UpdateNotificationsRequest request) {

        userService.updateNotificationPreference(userId, request.enabled());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Set avatar", description = "Uploads the user's profile photo (replaces the previous one)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Avatar saved",
            content = @Content(schema = @Schema(implementation = UserProfileDto.class))),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "400", description = "Invalid file")
    })
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserProfileDto> uploadAvatar(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @RequestParam("file") MultipartFile file) throws IOException {

        return ResponseEntity.ok(toProfileDto(userService.uploadAvatar(userId, file)));
    }

    @Operation(summary = "Delete avatar", description = "Deletes the user's profile photo")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Avatar deleted",
            content = @Content(schema = @Schema(implementation = UserProfileDto.class))),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @DeleteMapping("/me/avatar")
    public ResponseEntity<UserProfileDto> deleteAvatar(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        return ResponseEntity.ok(toProfileDto(userService.deleteAvatar(userId)));
    }

    @Operation(summary = "Change language", description = "Sets the user's preferred language")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Language changed"),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @PutMapping("/me/language")
    public ResponseEntity<Void> updateLanguage(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody UpdateLanguageRequest request) {

        userService.updateLanguagePreference(userId, request.language());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Unsubscribe from newsletter", description = "Unsubscribes the user from the newsletter without login (link from the email)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Unsubscribed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid token")
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

    @Operation(summary = "Newsletter subscription", description = "Enables or disables the newsletter")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Preference updated"),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @PutMapping("/me/newsletter")
    public ResponseEntity<Void> updateNewsletter(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody UpdateNewsletterRequest request) {

        userService.updateNewsletterSubscription(userId, request.subscribed());
        return ResponseEntity.noContent().build();
    }

    private UserProfileDto toProfileDto(User user) {
        String avatarUrl = user.getAvatarFilename() != null
            ? "/api/files/avatars/" + user.getAvatarFilename()
            : null;
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
            user.hasPassword(),
            avatarUrl,
            user.getCreatedAt()
        );
    }
}

@Schema(description = "User profile")
record UserProfileDto(
    @Schema(description = "User UUID") UUID id,
    @Schema(description = "Email address") String email,
    @Schema(description = "First name") String firstName,
    @Schema(description = "Last name") String lastName,
    @Schema(description = "Phone number") String phone,
    @Schema(description = "Nickname") String nickname,
    @Schema(description = "Role: USER or ADMIN") String role,
    @Schema(description = "Whether the user is an admin") boolean isAdmin,
    @Schema(description = "Whether email notifications are enabled") boolean emailNotificationsEnabled,
    @Schema(description = "Preferred language (pl, en, es)") String preferredLanguage,
    @Schema(description = "Whether subscribed to the newsletter") boolean newsletterSubscribed,
    @Schema(description = "Whether the user has made a newsletter choice") boolean newsletterChoiceMade,
    @Schema(description = "Whether the account has a password (false = OAuth only)") boolean hasPassword,
    @Schema(description = "Avatar URL (null if none)") @org.jspecify.annotations.Nullable String avatarUrl,
    @Schema(description = "Account creation date") java.time.Instant createdAt
) {}

@Schema(description = "Profile update data")
record UpdateProfileRequest(
    @Schema(description = "First name", example = "Jan")
    @jakarta.validation.constraints.Size(min = 3, max = 100, message = "{validation.firstname.size}")
    @jakarta.validation.constraints.Pattern(regexp = "^[\\p{L} .'-]+$", message = "{validation.name.invalid}")
    String firstName,
    @Schema(description = "Last name", example = "Kowalski")
    @jakarta.validation.constraints.Size(min = 3, max = 100, message = "{validation.lastname.size}")
    @jakarta.validation.constraints.Pattern(regexp = "^[\\p{L} .'-]+$", message = "{validation.name.invalid}")
    String lastName,
    @Schema(description = "Phone number", example = "+48123456789")
    @jakarta.validation.constraints.Pattern(regexp = "^\\+[0-9]{1,4}[0-9]{9}$", message = "{validation.phone.invalid}")
    String phone,
    @Schema(description = "Nickname", example = "Climber123")
    @jakarta.validation.constraints.Size(min = 3, max = 50, message = "{validation.nickname.size}")
    @jakarta.validation.constraints.Pattern(regexp = "^[\\p{L}\\p{N} ._-]+$", message = "{validation.nickname.invalid}")
    String nickname
) {}

@Schema(description = "Password change")
record ChangePasswordRequest(
    @Schema(description = "Current password")
    @jakarta.validation.constraints.NotBlank(message = "{validation.password.required}")
    String currentPassword,

    @Schema(description = "New password")
    @jakarta.validation.constraints.NotBlank(message = "{validation.password.required}")
    @jakarta.validation.constraints.Size(min = 10, max = 100, message = "{validation.password.size}")
    String newPassword
) {}

@Schema(description = "Account deletion")
record DeleteAccountRequest(
    @Schema(description = "Password for confirmation") String password
) {}

@Schema(description = "Notification settings")
record UpdateNotificationsRequest(
    @Schema(description = "Whether to enable email notifications") boolean enabled
) {}

@Schema(description = "Language change")
record UpdateLanguageRequest(
    @Schema(description = "Language code (pl, en, es)", example = "pl") String language
) {}

@Schema(description = "Newsletter subscription")
record UpdateNewsletterRequest(
    @Schema(description = "Whether to subscribe to the newsletter") boolean subscribed
) {}
