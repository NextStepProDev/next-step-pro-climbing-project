package pl.nextsteppro.climbing.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.web.filter.ForwardedHeaderFilter;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The backend sits behind nginx and takes the public scheme and host from the X-Forwarded-* headers
 * nginx sets (next-step-pro-climbing-frontend/nginx.conf). That only happens while Spring's
 * ForwardedHeaderFilter is registered, which application.yml asks for with the "framework" strategy.
 * Boot 4.2 renamed the property (server.forward-headers-strategy became
 * server.request.header.forwarded.strategy), and a typo or a future rename drops the filter without
 * a single error. The first thing to break would be Google sign-in: the OAuth redirect URI is
 * "{baseUrl}/login/oauth2/code/{registrationId}". nginx passes the public Host through, but TLS
 * ends before it (Cloudflare), so without the filter the scheme is the plain-http hop and {baseUrl}
 * becomes http://nextsteppro.pl, which Google rejects: only the https redirect URI is registered.
 */
class ForwardedHeaderStrategyTest extends BaseIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldRegisterForwardedHeaderFilterWhenFrameworkStrategyIsConfigured() {
        boolean registered = context.getBeansOfType(FilterRegistrationBean.class).values().stream()
            .anyMatch(bean -> bean.getFilter() instanceof ForwardedHeaderFilter);
        assertTrue(registered, "ForwardedHeaderFilter is not registered: check the forwarded strategy in application.yml");
    }
}
