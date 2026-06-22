package pl.nextsteppro.climbing.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private StaticMessageSource messageSource;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        messageSource = new StaticMessageSource();
        messageSource.addMessage("rate.limit.exceeded", Locale.of("pl"), "Zbyt wiele żądań. Spróbuj ponownie za minutę.");
        messageSource.addMessage("rate.limit.exceeded", Locale.of("en"), "Too many requests. Please try again in a minute.");
        messageSource.addMessage("rate.limit.exceeded", Locale.of("es"), "Demasiadas solicitudes. Inténtalo de nuevo en un minuto.");
        filter = new RateLimitFilter(messageSource);
    }

    @Test
    void shouldReturn429WithPolishMessageByDefault() throws Exception {
        MockHttpServletRequest request = createAuthRequest(null);
        MockHttpServletResponse response = exhaustRateLimit(request);

        assertEquals(429, response.getStatus());
        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertEquals("TOO_MANY_REQUESTS", json.get("code").asText());
        assertTrue(json.get("message").asText().contains("Zbyt wiele"));
        assertNotNull(json.get("timestamp"));
    }

    @Test
    void shouldReturn429WithEnglishMessageWhenAcceptLanguageIsEn() throws Exception {
        MockHttpServletRequest request = createAuthRequest("en");
        MockHttpServletResponse response = exhaustRateLimit(request);

        assertEquals(429, response.getStatus());
        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertEquals("Too many requests. Please try again in a minute.", json.get("message").asText());
    }

    @Test
    void shouldReturn429WithSpanishMessageWhenAcceptLanguageIsEs() throws Exception {
        MockHttpServletRequest request = createAuthRequest("es");
        MockHttpServletResponse response = exhaustRateLimit(request);

        assertEquals(429, response.getStatus());
        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertTrue(json.get("message").asText().contains("Demasiadas"));
    }

    @Test
    void shouldPreservePolishCharactersInResponse() throws Exception {
        MockHttpServletRequest request = createAuthRequest("pl");
        MockHttpServletResponse response = exhaustRateLimit(request);

        String content = response.getContentAsString();
        assertTrue(content.contains("żądań"));
        assertTrue(content.contains("minutę"));
        assertEquals("UTF-8", response.getCharacterEncoding());
    }

    @Test
    void shouldReturnMessageFieldInJsonResponse() throws Exception {
        MockHttpServletRequest request = createAuthRequest("en");
        MockHttpServletResponse response = exhaustRateLimit(request);

        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertTrue(json.has("message"));
        assertFalse(json.has("error"));
    }

    @Test
    void shouldAllowRequestsWithinLimit() throws Exception {
        for (int i = 0; i < 15; i++) {
            MockHttpServletRequest request = createAuthRequest("en");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void shouldFallbackToPolishForUnsupportedLanguage() throws Exception {
        MockHttpServletRequest request = createAuthRequest("fr");
        MockHttpServletResponse response = exhaustRateLimit(request);

        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertTrue(json.get("message").asText().contains("Zbyt wiele"));
    }

    @Test
    void shouldNotRateLimitPublicEndpoints() throws Exception {
        for (int i = 0; i < 20; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/calendar/month/2026-04");
            request.setRemoteAddr("192.168.1.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void shouldRateLimitByCfConnectingIpEvenWhenXForwardedForIsSpoofed() throws Exception {
        // Same real client (CF-Connecting-IP) but a different spoofed XFF every request:
        // the limiter must key on CF-Connecting-IP, so the spoofing must NOT grant new buckets.
        for (int i = 0; i < 15; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(cfRequest("203.0.113.7", "1.2.3." + i), response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(cfRequest("203.0.113.7", "9.9.9.9"), blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus(), "spoofed X-Forwarded-For must not bypass the per-IP limit");
    }

    @Test
    void shouldKeyOnCfConnectingIpSoDifferentClientsGetSeparateBuckets() throws Exception {
        // 20 requests, each from a distinct CF-Connecting-IP -> distinct buckets -> never limited.
        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(cfRequest("203.0.113." + i, "1.2.3.4"), response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
    }

    private MockHttpServletRequest cfRequest(String cfConnectingIp, String spoofedXff) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("CF-Connecting-IP", cfConnectingIp);
        request.addHeader("X-Forwarded-For", spoofedXff);
        return request;
    }

    private MockHttpServletRequest createAuthRequest(String language) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("192.168.1.1");
        if (language != null) {
            request.addHeader("Accept-Language", language);
        }
        return request;
    }

    private MockHttpServletResponse exhaustRateLimit(MockHttpServletRequest request) throws Exception {
        for (int i = 0; i < 15; i++) {
            MockHttpServletRequest req = createAuthRequest(request.getHeader("Accept-Language"));
            req.setRemoteAddr(request.getRemoteAddr());
            filter.doFilterInternal(req, new MockHttpServletResponse(), new MockFilterChain());
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());
        return response;
    }
}
