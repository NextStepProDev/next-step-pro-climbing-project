package pl.nextsteppro.climbing.api.auth;

import jakarta.validation.constraints.*;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
        @NotBlank(message = "Email jest wymagany")
        @Email(message = "Nieprawidłowy format email")
        String email,

        @NotBlank(message = "Hasło jest wymagane")
        @Size(min = 4, max = 100, message = "Hasło musi mieć od 4 do 100 znaków")
        String password,

        @NotBlank(message = "Imię jest wymagane")
        @Size(min = 2, max = 100, message = "Imię musi mieć od 2 do 100 znaków")
        String firstName,

        @NotBlank(message = "Nazwisko jest wymagane")
        @Size(min = 2, max = 100, message = "Nazwisko musi mieć od 2 do 100 znaków")
        String lastName,

        @NotBlank(message = "Numer telefonu jest wymagany")
        @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Nieprawidłowy format numeru telefonu")
        String phone
    ) {}

    public record LoginRequest(
        @NotBlank(message = "Email jest wymagany")
        @Email(message = "Nieprawidłowy format email")
        String email,

        @NotBlank(message = "Hasło jest wymagane")
        String password
    ) {}

    public record AuthTokensResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
    ) {}

    public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token jest wymagany")
        String refreshToken
    ) {}

    public record ForgotPasswordRequest(
        @NotBlank(message = "Email jest wymagany")
        @Email(message = "Nieprawidłowy format email")
        String email
    ) {}

    public record ResetPasswordRequest(
        @NotBlank(message = "Token jest wymagany")
        String token,

        @NotBlank(message = "Hasło jest wymagane")
        @Size(min = 4, max = 100, message = "Hasło musi mieć od 4 do 100 znaków")
        String newPassword
    ) {}

    public record ResendVerificationRequest(
        @NotBlank(message = "Email jest wymagany")
        @Email(message = "Nieprawidłowy format email")
        String email
    ) {}

    public record MessageResponse(
        String message
    ) {}
}
