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

    /**
     * The public calendar used to be exempt entirely. It is the heaviest public read in the app and
     * the month/week/day cache only covers anonymous viewers, so a logged-in one recomputes
     * availability per request — 20 in a row is still ordinary browsing, 61 is not.
     */
    @Test
    void shouldAllowOrdinaryCalendarBrowsingButThrottleAFlood() throws Exception {
        for (int i = 0; i < 60; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(get("/api/calendar/month/2026-04", "192.168.1.1"), response, new MockFilterChain());
            assertEquals(200, response.getStatus(), "request " + i + " is within the calendar limit");
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(get("/api/calendar/month/2026-05", "192.168.1.1"), blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());
    }

    /**
     * Reading your own reservations is polled (navbar badge, reservations page) and used to share
     * the write limit, so an ordinary session could be throttled out of BOOKING by having looked
     * at the list. Reads get their own roomier bucket; writes keep the tight one.
     */
    @Test
    void shouldNotSpendTheBookingLimitOnReadingYourOwnReservations() throws Exception {
        for (int i = 0; i < 21; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(get("/api/reservations/my/invitations", "192.168.1.70"), response, new MockFilterChain());
            assertEquals(200, response.getStatus(), "read " + i + " is within the read limit");
        }

        // The write bucket is untouched by those reads.
        MockHttpServletRequest booking = new MockHttpServletRequest("POST", "/api/reservations");
        booking.setRemoteAddr("192.168.1.70");
        MockHttpServletResponse bookingResponse = new MockHttpServletResponse();
        filter.doFilterInternal(booking, bookingResponse, new MockFilterChain());
        assertEquals(200, bookingResponse.getStatus(), "booking must not be blocked by reads");
    }

    @Test
    void shouldStillThrottleAFloodOfReservationReads() throws Exception {
        for (int i = 0; i < 60; i++) {
            filter.doFilterInternal(get("/api/reservations/my/upcoming", "192.168.1.71"), new MockHttpServletResponse(), new MockFilterChain());
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(get("/api/reservations/my/upcoming", "192.168.1.71"), blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());
    }

    /**
     * Proposing a time is a write. The "3 PENDING per user" rule caps what survives, not what the
     * endpoint has to validate and query first.
     */
    @Test
    void shouldThrottleTrainingRequestsOnTheBareBasePath() throws Exception {
        for (int i = 0; i < 20; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/training-requests");
            request.setRemoteAddr("192.168.1.60");
            filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());
        }
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/training-requests");
        request.setRemoteAddr("192.168.1.60");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());
        assertEquals(429, response.getStatus());
    }

    /** Google sign-in lives outside /api, so it used to miss every prefix in the filter. */
    @Test
    void shouldThrottleOauth2LoginIntoTheSameBucketAsPasswordLogin() throws Exception {
        for (int i = 0; i < 15; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(get("/oauth2/authorization/google", "192.168.1.61"), response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
        // Same bucket as /api/auth/**: a sign-in attempt is a sign-in attempt either way.
        MockHttpServletRequest passwordLogin = new MockHttpServletRequest("POST", "/api/auth/login");
        passwordLogin.setRemoteAddr("192.168.1.61");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(passwordLogin, response, new MockFilterChain());
        assertEquals(429, response.getStatus());
    }

    /**
     * A gallery page is one request per photo, so this bucket has to be roomy — but not endless.
     */
    @Test
    void shouldGivePublicMediaItsOwnRoomyBucket() throws Exception {
        for (int i = 0; i < 300; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(get("/api/files/gallery/photo-" + i + ".jpg", "192.168.1.62"), response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(get("/api/files/gallery/last.jpg", "192.168.1.62"), blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());
    }

    /**
     * The point of denying by default: a path nobody wrote a rule for is throttled anyway. Before,
     * an unrecognised path meant no limit, so every new controller started life unthrottled.
     */
    @Test
    void shouldThrottleAnUnrecognisedApiPathUnderTheDefaultBucket() throws Exception {
        for (int i = 0; i < 120; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(get("/api/whatever-comes-next", "192.168.1.63"), response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(get("/api/whatever-comes-next", "192.168.1.63"), blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());
    }

    /**
     * The catch-all is scoped to /api on purpose: the container healthcheck polls this every few
     * seconds from one address, and a 429 there would flap the container to unhealthy.
     */
    @Test
    void shouldNeverThrottleTheHealthEndpoint() throws Exception {
        for (int i = 0; i < 300; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(get("/actuator/health", "192.168.1.64"), response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
    }

    /** Buckets are independent: exhausting one must not lock a client out of the rest of the site. */
    @Test
    void shouldKeepBucketsIndependentForTheSameClient() throws Exception {
        for (int i = 0; i < 61; i++) {
            filter.doFilterInternal(get("/api/calendar/month/2026-04", "192.168.1.65"), new MockHttpServletResponse(), new MockFilterChain());
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(get("/api/news", "192.168.1.65"), response, new MockFilterChain());
        assertEquals(200, response.getStatus(), "a flood of calendar reads must not block the news list");
    }

    /** Load tests drive every request from one address; measuring the filter is not the point. */
    @Test
    void shouldNotThrottleAnythingWhenDisabled() throws Exception {
        RateLimitFilter disabled = new RateLimitFilter(messageSource, false);
        for (int i = 0; i < 50; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            disabled.doFilterInternal(createAuthRequest("en"), response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void shouldTellTheClientWhenToComeBack() throws Exception {
        MockHttpServletResponse response = exhaustRateLimit(createAuthRequest("en"));

        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
    }

    private MockHttpServletRequest get(String path, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(remoteAddr);
        return request;
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

    /**
     * The calendar RANGE endpoint is mapped on the bare base path, so its URI has no trailing
     * slash. A prefix test of "/api/training-calendar/" missed it entirely, leaving the heaviest
     * query of the whole feature unthrottled.
     */
    @Test
    void shouldRateLimitTheTrainingCalendarRangeOnTheBarePath() throws Exception {
        for (int i = 0; i < 40; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(trainingRangeRequest(), response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(trainingRangeRequest(), blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus(), "the bare /api/training-calendar path must be throttled too");
    }

    @Test
    void shouldCountTheBarePathAndSubPathsIntoTheSameTrainingBucket() throws Exception {
        // 40 on the bare range path, then one on a sub-path: same bucket -> already exhausted
        for (int i = 0; i < 40; i++) {
            filter.doFilterInternal(trainingRangeRequest(), new MockHttpServletResponse(), new MockFilterChain());
        }
        MockHttpServletRequest subPath = new MockHttpServletRequest("GET", "/api/training-calendar/stats");
        subPath.setRemoteAddr("192.168.1.50");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(subPath, response, new MockFilterChain());
        assertEquals(429, response.getStatus());
    }

    /** A path that merely starts with the same characters is a different resource. */
    @Test
    void shouldNotTreatALongerSiblingPathAsTheTrainingCalendar() throws Exception {
        for (int i = 0; i < 45; i++) {
            MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/training-calendars-export");
            request.setRemoteAddr("192.168.1.51");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
    }

    private MockHttpServletRequest trainingRangeRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/training-calendar");
        request.setQueryString("from=2026-08-03&to=2026-08-09");
        request.setRemoteAddr("192.168.1.50");
        return request;
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
