package pl.nextsteppro.climbing.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.mail.AuthMailService;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The OAuth boundary had no test. The behaviour worth pinning down is what happens when the
 * provider's e-mail matches an account that already exists: linking hands over that account, so
 * the address has to be proven rather than merely claimed.
 *
 * <p>Drives {@code processOAuth2User} directly — {@code loadUser} would need a live
 * {@code OAuth2UserRequest} and a token endpoint, which tests nothing extra here.
 */
@ExtendWith(MockitoExtension.class)
class OAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminEmailConfig adminEmailConfig;
    @Mock
    private AuthMailService authMailService;

    private OAuth2UserService service;

    @BeforeEach
    void setUp() {
        service = new OAuth2UserService(userRepository, adminEmailConfig, authMailService);
    }

    // ---- linking to an existing account ----

    @Test
    void shouldRefuseToLinkToAnExistingAccountWhenProviderEmailIsUnverified() {
        // Given — an account already owns this address (registered with a password).
        User victim = new User("victim@example.com", "Vic", "Tim", "+48111111111", "victim");
        when(userRepository.findByOauthProviderAndOauthId("google", "sub-attacker")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("victim@example.com")).thenReturn(Optional.of(victim));

        // When / Then — an unverified provider e-mail must not buy that account.
        assertThrows(OAuth2AuthenticationException.class,
            () -> process(oauthUser("victim@example.com", false, "sub-attacker")));

        verify(userRepository, never()).save(any(User.class));
        assertFalse(victim.isEmailVerified(),
            "The rejected attempt must not leave the victim's account marked verified");
    }

    @Test
    void shouldLinkToAnExistingAccountWhenProviderEmailIsVerified() {
        // Given — the ordinary case: Google vouches for the address.
        User existing = new User("owner@example.com", "Own", "Er", "+48222222222", "owner");
        when(userRepository.findByOauthProviderAndOauthId("google", "sub-owner")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(existing));
        when(adminEmailConfig.isAdminEmail("owner@example.com")).thenReturn(false);

        process(oauthUser("owner@example.com", true, "sub-owner"));

        assertEquals("google", existing.getOauthProvider());
        assertEquals("sub-owner", existing.getOauthId());
        assertTrue(existing.isEmailVerified());
        verify(userRepository).save(existing);
    }

    // ---- creating a brand-new account ----

    @Test
    void shouldCreateUnverifiedAccountWhenProviderEmailIsUnverified() {
        // Nobody owns this address yet, so creating is safe — but our own verified flag must not
        // claim more than the provider told us.
        when(userRepository.findByOauthProviderAndOauthId("google", "sub-new")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        process(oauthUser("new@example.com", false, "sub-new"));

        assertFalse(saved().isEmailVerified(),
            "An unverified provider e-mail must not mark the new account as verified");
    }

    @Test
    void shouldCreateVerifiedAccountWhenProviderEmailIsVerified() {
        when(userRepository.findByOauthProviderAndOauthId("google", "sub-new")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        process(oauthUser("new@example.com", true, "sub-new"));

        assertTrue(saved().isEmailVerified());
    }

    @Test
    void shouldTreatAMissingEmailVerifiedClaimAsUnverified() {
        // A provider that does not tell us has not verified anything, as far as we are concerned.
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "sub-quiet");
        attributes.put("email", "quiet@example.com");
        attributes.put("given_name", "Quiet");

        when(userRepository.findByOauthProviderAndOauthId("google", "sub-quiet")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("quiet@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        process(new DefaultOAuth2User(List.of(), attributes, "sub"));

        assertFalse(saved().isEmailVerified());
    }

    @Test
    void shouldSignInAnAlreadyLinkedAccountRegardlessOfTheVerifiedClaim() {
        // Already linked by provider id — the e-mail is not being used to prove anything here, so
        // the claim is irrelevant and a returning user must not be locked out by it.
        User linked = new User("linked@example.com", "Lin", "Ked", "+48333333333", "linked");
        when(userRepository.findByOauthProviderAndOauthId("google", "sub-linked")).thenReturn(Optional.of(linked));
        when(adminEmailConfig.isAdminEmail("linked@example.com")).thenReturn(false);

        OAuth2User result = process(oauthUser("linked@example.com", false, "sub-linked"));

        assertEquals("linked@example.com", ((CustomOAuth2User) result).getUser().getEmail());
    }

    // ---- helpers ----

    private User saved() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    private static OAuth2User oauthUser(String email, boolean emailVerified, String sub) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", sub);
        attributes.put("email", email);
        attributes.put("email_verified", emailVerified);
        attributes.put("given_name", "Test");
        attributes.put("family_name", "User");
        return new DefaultOAuth2User(List.of(), attributes, "sub");
    }

    private OAuth2User process(OAuth2User oAuth2User) {
        try {
            Method method = OAuth2UserService.class
                .getDeclaredMethod("processOAuth2User", String.class, OAuth2User.class);
            method.setAccessible(true);
            return (OAuth2User) method.invoke(service, "google", oAuth2User);
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e);
        }
    }
}
