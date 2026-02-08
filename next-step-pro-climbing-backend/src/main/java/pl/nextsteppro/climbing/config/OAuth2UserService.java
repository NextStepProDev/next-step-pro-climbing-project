package pl.nextsteppro.climbing.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.domain.user.UserRole;

import java.util.Map;
import java.util.Optional;

@Service
public class OAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(OAuth2UserService.class);

    private final UserRepository userRepository;
    private final AdminEmailConfig adminEmailConfig;

    public OAuth2UserService(UserRepository userRepository, AdminEmailConfig adminEmailConfig) {
        this.userRepository = userRepository;
        this.adminEmailConfig = adminEmailConfig;
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
                user = userByEmail.get();
                user.setOauthProvider(provider);
                user.setOauthId(oauthId);
                promoteToAdminIfConfigured(user);
                userRepository.save(user);
            } else {
                // Create new user
                user = createNewUser(provider, oauthId, email, firstName, lastName);
            }
        }

        return new CustomOAuth2User(oAuth2User, user);
    }

    private User createNewUser(String provider, String oauthId, String email, String firstName, String lastName) {
        User user = new User(
            email,
            firstName,
            lastName,
            "",
            generateNickname(firstName, lastName)
        );
        user.setOauthProvider(provider);
        user.setOauthId(oauthId);
        user.setRole(adminEmailConfig.isAdminEmail(email) ? UserRole.ADMIN : UserRole.USER);
        if (user.isAdmin()) {
            log.info("AUTO-ADMIN-PROMOTION: {} promoted to ADMIN during OAuth2 registration", email);
        }
        return userRepository.save(user);
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
            case "apple" -> (String) attributes.get("sub");
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

    private String extractFirstName(String provider, Map<String, Object> attributes) {
        return switch (provider) {
            case "google" -> getStringOrDefault(attributes, "given_name", "User");
            case "apple" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> name = (Map<String, Object>) attributes.get("name");
                yield name != null ? getStringOrDefault(name, "firstName", "User") : "User";
            }
            default -> "User";
        };
    }

    private String extractLastName(String provider, Map<String, Object> attributes) {
        return switch (provider) {
            case "google" -> getStringOrDefault(attributes, "family_name", "");
            case "apple" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> name = (Map<String, Object>) attributes.get("name");
                yield name != null ? getStringOrDefault(name, "lastName", "") : "";
            }
            default -> "";
        };
    }

    private String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value instanceof String s ? s : defaultValue;
    }

    private String generateNickname(String firstName, String lastName) {
        String base = firstName.toLowerCase();
        if (!lastName.isBlank()) {
            base += lastName.substring(0, 1).toLowerCase();
        }
        return base + System.currentTimeMillis() % 1000;
    }
}
