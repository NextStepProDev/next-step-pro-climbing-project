package pl.nextsteppro.climbing.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String targetUrl = determineTargetUrl(request);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String determineTargetUrl(HttpServletRequest request) {
        String redirectUri = request.getParameter("redirect_uri");
        if (redirectUri != null && isSafeRedirect(redirectUri)) {
            return redirectUri;
        }
        return frontendUrl + "/calendar";
    }

    private boolean isSafeRedirect(String redirectUri) {
        try {
            URI allowed = URI.create(frontendUrl);
            URI target = URI.create(redirectUri);
            return allowed.getHost().equals(target.getHost())
                && allowed.getPort() == target.getPort()
                && allowed.getScheme().equals(target.getScheme());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
