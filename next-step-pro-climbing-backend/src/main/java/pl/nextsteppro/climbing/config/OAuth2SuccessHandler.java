package pl.nextsteppro.climbing.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import pl.nextsteppro.climbing.api.auth.OAuthExchangeCodes;
import pl.nextsteppro.climbing.domain.user.User;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * After Google sign-in: hand the browser a one-time code in the URL FRAGMENT, never the tokens.
 *
 * <p>⚠️ The tokens used to go in the query string, so the seven-day refresh token landed in the
 * nginx access log and the browser history before the page could strip it. A fragment is never sent
 * to any server, and the code it carries is single-use and expires in a minute — see
 * {@link OAuthExchangeCodes}.
 */
@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Value("${app.site-url:http://localhost:5173}")
    private String frontendUrl;

    private final OAuthExchangeCodes exchangeCodes;

    public OAuth2SuccessHandler(OAuthExchangeCodes exchangeCodes) {
        this.exchangeCodes = exchangeCodes;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        User user = ((CustomOAuth2User) authentication.getPrincipal()).getUser();
        String code = exchangeCodes.issue(user);
        String targetUrl = getFrontendBaseUrl() + "/oauth-callback#code="
            + URLEncoder.encode(code, StandardCharsets.UTF_8);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String getFrontendBaseUrl() {
        // Take the first origin (comma-separated list)
        return frontendUrl.split(",")[0].trim();
    }
}
