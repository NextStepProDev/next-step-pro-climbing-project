package pl.nextsteppro.climbing.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The authentication boundary had no test at all. The contract worth pinning down is that the
 * filter fails <em>closed</em>: on anything it cannot verify it leaves the SecurityContext empty
 * and lets the chain continue, so {@code anyRequest().authenticated()} turns it into a 401. A
 * future edit that "helpfully" authenticates on a malformed token would open the whole API.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, userRepository);
        userId = UUID.randomUUID();
        user = new User("climber@example.com", "Ala", "Kowalska", "+48123456789", "ala");
        setIdViaReflection(user, userId);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNotAuthenticateWhenAuthorizationHeaderIsMissing() throws Exception {
        doFilter(request(null));

        assertNull(currentAuth());
        verify(filterChain).doFilter(any(), any());
        verify(jwtService, never()).validateToken(anyString());
    }

    @Test
    void shouldNotAuthenticateWhenHeaderIsNotBearer() throws Exception {
        doFilter(request("Basic dXNlcjpwYXNz"));

        assertNull(currentAuth());
        verify(jwtService, never()).validateToken(anyString());
    }

    @Test
    void shouldNotAuthenticateWhenTokenIsInvalid() throws Exception {
        when(jwtService.validateToken("bad.token")).thenReturn(false);

        doFilter(request("Bearer bad.token"));

        // Fails closed: chain continues unauthenticated rather than throwing or authenticating.
        assertNull(currentAuth());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void shouldNotAuthenticateWhenTokenIsARefreshToken() throws Exception {
        // A refresh token is valid but must never buy access to the API — it only buys a new
        // access token at /api/auth/refresh.
        when(jwtService.validateToken("refresh.token")).thenReturn(true);
        when(jwtService.isAccessToken("refresh.token")).thenReturn(false);

        doFilter(request("Bearer refresh.token"));

        assertNull(currentAuth());
    }

    @Test
    void shouldNotAuthenticateWhenUserNoLongerExists() throws Exception {
        // A token outliving its user (deleted account) must not authenticate anyone.
        stubValidAccessToken("good.token");
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        doFilter(request("Bearer good.token"));

        assertNull(currentAuth());
    }

    @Test
    void shouldAuthenticateWithUserRoleWhenTokenIsValid() throws Exception {
        stubValidAccessToken("good.token");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        doFilter(request("Bearer good.token"));

        Authentication auth = currentAuth();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void shouldGrantAdminAuthorityWhenUserIsAdmin() throws Exception {
        user.setRole(UserRole.ADMIN);
        stubValidAccessToken("admin.token");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        doFilter(request("Bearer admin.token"));

        assertTrue(currentAuth().getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void shouldReadUserFromCacheOnRepeatedRequests() throws Exception {
        stubValidAccessToken("good.token");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        doFilter(request("Bearer good.token"));
        SecurityContextHolder.clearContext();
        doFilter(request("Bearer good.token"));

        // One DB read for two requests — this cache is why every authenticated call does not hit
        // the users table.
        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    void shouldReloadUserAfterEvictionWhenForceLoggedOutOrDeleted() throws Exception {
        stubValidAccessToken("good.token");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        doFilter(request("Bearer good.token"));
        // forceLogout and deleteUser both call this; without it a revoked session would keep
        // working for up to the 60 s cache TTL.
        filter.evictUser(userId);
        SecurityContextHolder.clearContext();
        doFilter(request("Bearer good.token"));

        verify(userRepository, times(2)).findById(userId);
    }

    @Test
    void shouldNotAuthenticateWhenExtractingTheUserIdBlowsUp() throws Exception {
        when(jwtService.validateToken("weird.token")).thenReturn(true);
        when(jwtService.isAccessToken("weird.token")).thenReturn(true);
        when(jwtService.extractUserId("weird.token")).thenThrow(new IllegalArgumentException("boom"));

        doFilter(request("Bearer weird.token"));

        // Swallowed deliberately, but it must not leave a half-built authentication behind.
        assertNull(currentAuth());
        verify(filterChain).doFilter(any(), any());
    }

    // ---- helpers ----

    private void stubValidAccessToken(String token) {
        when(jwtService.validateToken(token)).thenReturn(true);
        when(jwtService.isAccessToken(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    private MockHttpServletRequest request(String authHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authHeader != null) {
            request.addHeader("Authorization", authHeader);
        }
        return request;
    }

    private void doFilter(HttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), filterChain);
    }

    private Authentication currentAuth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static void setIdViaReflection(Object entity, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
