package pl.nextsteppro.climbing.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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
}
