package pl.nextsteppro.climbing.api.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.api.auth.AuthDtos.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for user authentication")
public class AuthController {

    private final AuthService authService;
    private final OAuthExchangeCodes exchangeCodes;

    public AuthController(AuthService authService, OAuthExchangeCodes exchangeCodes) {
        this.authService = authService;
        this.exchangeCodes = exchangeCodes;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new account and sends verification email")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password", description = "Returns JWT tokens for authenticated user")
    public ResponseEntity<AuthTokensResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email address", description = "Verifies the email using token from verification email")
    public ResponseEntity<MessageResponse> verifyEmail(@Valid @RequestBody TokenRequest request) {
        return ResponseEntity.ok(authService.verifyEmail(request.token()));
    }

    @PostMapping("/verify-email/resend")
    @Operation(summary = "Renew an expired verification link",
        description = "Takes the token from a link that no longer works and mails a fresh one to the same account")
    public ResponseEntity<MessageResponse> resendVerificationByToken(@Valid @RequestBody TokenRequest request) {
        return ResponseEntity.ok(authService.resendVerificationByToken(request.token()));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend verification email", description = "Sends another verification email if account exists")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        return ResponseEntity.ok(authService.resendVerification(request));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset", description = "Sends password reset email if account exists")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Sets new password using token from reset email")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh tokens", description = "Returns new JWT tokens using a valid refresh token")
    public ResponseEntity<AuthTokensResponse> refreshTokens(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshTokens(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out", description = "Revokes this refresh token on the server. Idempotent; "
        + "the token in the body is the credential, so no Authorization header is needed.")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/oauth/exchange")
    @Operation(summary = "Swap the one-time Google sign-in code for tokens",
        description = "The code arrives in the URL fragment of /oauth-callback, works once and for a minute.")
    public ResponseEntity<AuthTokensResponse> exchangeOAuthCode(@Valid @RequestBody OAuthExchangeRequest request) {
        return ResponseEntity.ok(exchangeCodes.exchange(request.code()));
    }
}
