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
