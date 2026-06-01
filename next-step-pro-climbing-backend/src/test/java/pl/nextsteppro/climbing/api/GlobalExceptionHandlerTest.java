package pl.nextsteppro.climbing.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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
    void shouldReturn500ForUnexpectedException() {
        when(messageService.get("error.internal")).thenReturn("Internal error");
        var response = handler.handleGeneric(new RuntimeException("unexpected"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
    }
}
