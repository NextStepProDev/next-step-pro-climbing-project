package pl.nextsteppro.climbing.api.auth;

import jakarta.validation.constraints.*;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        String email,

        @NotBlank(message = "{validation.password.required}")
        @Size(min = 8, max = 100, message = "{validation.password.size}")
        String password,

        @NotBlank(message = "{validation.firstname.required}")
        @Size(min = 2, max = 100, message = "{validation.firstname.size}")
        String firstName,

        @NotBlank(message = "{validation.lastname.required}")
        @Size(min = 2, max = 100, message = "{validation.lastname.size}")
        String lastName,

        @NotBlank(message = "{validation.phone.required}")
        @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "{validation.phone.invalid}")
        String phone
    ) {}

    public record LoginRequest(
        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        String email,

        @NotBlank(message = "{validation.password.required}")
        String password
    ) {}

    public record AuthTokensResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
    ) {}

    public record RefreshTokenRequest(
        @NotBlank(message = "{validation.refresh.token.required}")
        String refreshToken
    ) {}

    public record ForgotPasswordRequest(
        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        String email
    ) {}

    public record ResetPasswordRequest(
        @NotBlank(message = "{validation.token.required}")
        String token,

        @NotBlank(message = "{validation.password.required}")
        @Size(min = 8, max = 100, message = "{validation.password.size}")
        String newPassword
    ) {}

    public record ResendVerificationRequest(
        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        String email
    ) {}

    public record MessageResponse(
        String message
    ) {}
}
