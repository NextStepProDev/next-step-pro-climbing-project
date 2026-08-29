package pl.nextsteppro.climbing.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import pl.nextsteppro.climbing.domain.climbingascent.AscentTerrain;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private final MessageService messageService = mock(MessageService.class);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(messageService);

    @Test
    void shouldReturn413WithLocalizedMessageWhenUploadSizeExceeded() {
        when(messageService.get("file.too.large")).thenReturn("Plik jest za duży. Maksymalny rozmiar to 10 MB.");
        var ex = new MaxUploadSizeExceededException(10 * 1024 * 1024);

        var response = handler.handleMaxUploadSize(ex);

        assertEquals(HttpStatus.CONTENT_TOO_LARGE, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("PAYLOAD_TOO_LARGE", body.code());
        assertEquals("Plik jest za duży. Maksymalny rozmiar to 10 MB.", body.message());
        assertNotNull(body.timestamp());
    }

    @Test
    void shouldReturn413WithEnglishMessageWhenLocaleIsEnglish() {
        when(messageService.get("file.too.large")).thenReturn("File is too large. Maximum size is 10 MB.");
        var ex = new MaxUploadSizeExceededException(10 * 1024 * 1024);

        var response = handler.handleMaxUploadSize(ex);

        var body = response.getBody();
        assertNotNull(body);
        assertEquals("File is too large. Maximum size is 10 MB.", body.message());
    }

    @Test
    void shouldReturn413WhenUploadSizeExceededWithCause() {
        when(messageService.get("file.too.large")).thenReturn("Plik jest za duży.");
        var cause = new RuntimeException("size exceeds configured maximum");
        var ex = new MaxUploadSizeExceededException(50 * 1024 * 1024, cause);

        var response = handler.handleMaxUploadSize(ex);

        assertEquals(HttpStatus.CONTENT_TOO_LARGE, response.getStatusCode());
        assertEquals("PAYLOAD_TOO_LARGE", response.getBody().code());
    }

    @Test
    void shouldReturn400ForIllegalArgument() {
        var response = handler.handleIllegalArgument(new IllegalArgumentException("bad input"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.code());
        assertEquals("bad input", body.message());
    }

    @Test
    void shouldReturn409ForIllegalState() {
        var response = handler.handleIllegalState(new IllegalStateException("conflict"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("CONFLICT", response.getBody().code());
    }

    @Test
    void shouldReturn404WhenNoResourceFoundForUnmappedPath() {
        when(messageService.get("error.not.found")).thenReturn("Nie znaleziono zasobu");
        var ex = new NoResourceFoundException(HttpMethod.GET, "/api/settings", "api/settings");

        var response = handler.handleNoResourceFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("NOT_FOUND", body.code());
        assertEquals("Nie znaleziono zasobu", body.message());
        assertNotNull(body.timestamp());
    }

    /**
     * A path variable or query parameter the client wrote wrong — {@code /ascents/nie-uuid},
     * {@code ?terrain=SPACE}. Before this handler existed these reached the catch-all and came
     * back as a 500 with an ERROR and a stack trace, which is both the wrong answer and a way for
     * anybody probing URLs to bury real errors in the log.
     */
    @Test
    void shouldReturn400WhenAPathOrQueryParameterCannotBeConverted() {
        when(messageService.get("error.bad.request")).thenReturn("Nieprawidłowe żądanie");
        var ex = new MethodArgumentTypeMismatchException("SPACE", AscentTerrain.class, "terrain", null, null);

        var response = handler.handleTypeMismatch(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.code());
        assertEquals("Nieprawidłowe żądanie", body.message());
    }

    /** The rejected value must not be echoed back — it is attacker-controlled text. */
    @Test
    void shouldNotEchoTheRejectedValueBack() {
        when(messageService.get("error.bad.request")).thenReturn("Nieprawidłowe żądanie");
        var ex = new MethodArgumentTypeMismatchException(
            "<script>alert(1)</script>", AscentTerrain.class, "terrain", null, null);

        var body = handler.handleTypeMismatch(ex).getBody();

        assertNotNull(body);
        assertFalse(body.message().contains("script"));
    }

    /**
     * Malformed JSON, or an enum constant that does not exist ({@code "grade": "FR_99Z"}).
     * Jackson fails before Bean Validation runs, so this is the only place it can be caught.
     */
    @Test
    void shouldReturn400WhenTheBodyCannotBeRead() {
        when(messageService.get("error.bad.request")).thenReturn("Invalid request");
        var ex = new HttpMessageNotReadableException("no enum constant FR_99Z", (HttpInputMessage) null);

        var response = handler.handleUnreadableBody(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("BAD_REQUEST", response.getBody().code());
        assertEquals("Invalid request", response.getBody().message());
    }

    @Test
    void shouldReturn500ForUnexpectedException() {
        when(messageService.get("error.internal")).thenReturn("Internal error");
        var response = handler.handleGeneric(new RuntimeException("unexpected"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
    }

    /* ---------------------------------------------------------------------------------------
     * The three doors that were still open after the 2026-08-14 round closed the other two.
     * Each is a request the server understands and is right to refuse, and each used to leave
     * a 500 plus an ERROR-level stack trace behind — the same log noise the NoResourceFound
     * handler exists to prevent.
     * ------------------------------------------------------------------------------------ */

    @Test
    void shouldReturn400WhenAMultipartPartIsMissing() {
        when(messageService.get("error.bad.request")).thenReturn("Nieprawidłowe żądanie");
        var ex = new MissingServletRequestPartException("file");

        var response = handler.handleMissingInput(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.code());
    }

    @Test
    void shouldReturn400WhenARequiredQueryParameterIsMissing() {
        when(messageService.get("error.bad.request")).thenReturn("Nieprawidłowe żądanie");
        var ex = new MissingServletRequestParameterException("from", "LocalDate");

        var response = handler.handleMissingInput(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_REQUEST", response.getBody().code());
    }

    @Test
    void shouldReturn415WhenTheBodyArrivesUnderAContentTypeTheEndpointDoesNotRead() {
        when(messageService.get("error.bad.request")).thenReturn("Nieprawidłowe żądanie");
        var ex = new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, java.util.List.of(MediaType.APPLICATION_JSON));

        var response = handler.handleUnsupportedMediaType(ex);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", response.getBody().code());
    }

    @Test
    void shouldReturn405AndNameTheAllowedVerbsWhenThePathExistsUnderADifferentMethod() {
        when(messageService.get("error.bad.request")).thenReturn("Nieprawidłowe żądanie");
        var ex = new HttpRequestMethodNotSupportedException("POST", java.util.List.of("GET", "PUT"));

        var response = handler.handleMethodNotSupported(ex);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("METHOD_NOT_ALLOWED", response.getBody().code());
        // Allow is the whole point: it tells the caller what to send instead.
        assertTrue(response.getHeaders().getAllow().contains(HttpMethod.GET));
        assertTrue(response.getHeaders().getAllow().contains(HttpMethod.PUT));
    }

    @Test
    void shouldSurviveAMethodNotSupportedExceptionThatNamesNoAlternative() {
        when(messageService.get("error.bad.request")).thenReturn("Nieprawidłowe żądanie");
        var ex = new HttpRequestMethodNotSupportedException("TRACE");

        var response = handler.handleMethodNotSupported(ex);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
    }

}
