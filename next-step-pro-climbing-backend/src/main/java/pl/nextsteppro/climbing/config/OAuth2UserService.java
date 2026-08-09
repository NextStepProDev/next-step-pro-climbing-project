package pl.nextsteppro.climbing.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.infrastructure.mail.AuthMailService;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class OAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(OAuth2UserService.class);
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("pl", "en", "es");

    private final UserRepository userRepository;
    private final AdminEmailConfig adminEmailConfig;
    private final AuthMailService authMailService;

    public OAuth2UserService(UserRepository userRepository, AdminEmailConfig adminEmailConfig, AuthMailService authMailService) {
        this.userRepository = userRepository;
        this.adminEmailConfig = adminEmailConfig;
        this.authMailService = authMailService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId();

        return processOAuth2User(provider, oAuth2User);
    }

    private OAuth2User processOAuth2User(String provider, OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String oauthId = extractOAuthId(provider, attributes);
        String email = extractEmail(attributes);
        boolean emailVerified = extractEmailVerified(attributes);
        String firstName = extractFirstName(provider, attributes);
        String lastName = extractLastName(provider, attributes);

        // Try to find user by OAuth provider and ID
        Optional<User> existingUser = userRepository.findByOauthProviderAndOauthId(provider, oauthId);

        User user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
            promoteToAdminIfConfigured(user);
        } else {
            // Try to find by email and link OAuth
            Optional<User> userByEmail = userRepository.findByEmail(email);
            if (userByEmail.isPresent()) {
                // Linking by e-mail hands over an existing account — including one that was
                // registered with a password — so the address has to be PROVEN, not merely
                // asserted by the provider. Without this check, anyone able to obtain a provider
                // account carrying an unverified copy of someone's address takes over that
                // account. Google sets email_verified=true for consumer accounts, so this rejects
                // nobody today; it exists so a second provider cannot open the hole silently.
                if (!emailVerified) {
                    log.warn("OAUTH-LINK-REJECTED: {} presented an unverified {} e-mail matching an existing account",
                        email, provider);
                    throw new OAuth2AuthenticationException(
                        "This e-mail is already registered. Verify it with your provider, or sign in with your password.");
                }
                user = userByEmail.get();
                user.setOauthProvider(provider);
                user.setOauthId(oauthId);
                if (!user.isEmailVerified()) {
                    user.markEmailVerified();
                }
                promoteToAdminIfConfigured(user);
                userRepository.save(user);
            } else {
                // Create new user
                user = createNewUser(provider, oauthId, email, firstName, lastName, emailVerified);
            }
        }

        return new CustomOAuth2User(oAuth2User, user);
    }

    private User createNewUser(String provider, String oauthId, String email, String firstName, String lastName,
                               boolean emailVerified) {
        User user = new User(
            email,
            firstName,
            lastName,
            "",
            generateNickname(firstName, lastName)
        );
        user.setOauthProvider(provider);
        user.setOauthId(oauthId);
        // A fresh account is safe to create either way — nobody else owns this address yet. But we
        // only inherit "verified" when the provider actually vouched for it; otherwise the user
        // goes through the normal e-mail verification, because our own flag must not claim more
        // than we know.
        if (emailVerified) {
            user.markEmailVerified();
        }
        user.setPreferredLanguage(detectLanguage());
        user.setRole(adminEmailConfig.isAdminEmail(email) ? UserRole.ADMIN : UserRole.USER);
        if (user.isAdmin()) {
            log.info("AUTO-ADMIN-PROMOTION: {} promoted to ADMIN during OAuth2 registration", email);
        }
        User savedUser = userRepository.save(user);
        authMailService.sendWelcomeEmail(savedUser);
        return savedUser;
    }

    private void promoteToAdminIfConfigured(User user) {
        if (!user.isAdmin() && adminEmailConfig.isAdminEmail(user.getEmail())) {
            user.setRole(UserRole.ADMIN);
            userRepository.save(user);
            log.info("AUTO-ADMIN-PROMOTION: {} promoted to ADMIN during OAuth2 login", user.getEmail());
        }
    }

    private String extractOAuthId(String provider, Map<String, Object> attributes) {
        return switch (provider) {
            case "google" -> (String) attributes.get("sub");
            default -> throw new OAuth2AuthenticationException("Unsupported provider: " + provider);
        };
    }

    private String extractEmail(Map<String, Object> attributes) {
        String email = (String) attributes.get("email");
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("Email not provided by OAuth provider");
        }
        return email;
    }

    /**
     * Whether the provider states it has verified this address.
     *
     * <p>Defaults to {@code false} for anything we do not recognise: a provider that does not tell
     * us has not verified anything as far as we are concerned, and this decision gates account
     * linking. Google returns the claim under the {@code email} scope, which we already request.
     */
    private boolean extractEmailVerified(Map<String, Object> attributes) {
        Object value = attributes.get("email_verified");
        return switch (value) {
            case Boolean b -> b;
            // Some providers serialise the claim as a string.
            case String s -> Boolean.parseBoolean(s);
            case null, default -> false;
        };
    }

    private String extractFirstName(String provider, Map<String, Object> attributes) {
        return switch (provider) {
            case "google" -> getStringOrDefault(attributes, "given_name", "User");
            default -> "User";
        };
    }

    private String extractLastName(String provider, Map<String, Object> attributes) {
        return switch (provider) {
            case "google" -> getStringOrDefault(attributes, "family_name", "");
            default -> "";
        };
    }

    private String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value instanceof String s ? s : defaultValue;
    }

    private String detectLanguage() {
        String lang = LocaleContextHolder.getLocale().getLanguage();
        return SUPPORTED_LANGUAGES.contains(lang) ? lang : "en";
    }

    private String generateNickname(String firstName, String lastName) {
        String base = firstName.toLowerCase();
        if (!lastName.isBlank()) {
            base += lastName.substring(0, 1).toLowerCase();
        }
        return base + System.currentTimeMillis() % 1000;
    }
}
