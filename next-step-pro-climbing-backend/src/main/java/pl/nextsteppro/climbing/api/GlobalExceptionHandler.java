package pl.nextsteppro.climbing.api;

import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import pl.nextsteppro.climbing.api.auth.EmailNotVerifiedException;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final MessageService messageService;

    public GlobalExceptionHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("BAD_REQUEST", ex.getMessage(), Instant.now()));
    }

    /**
     * Still a 409, but named. Sign-in refused for an unconfirmed address is the one conflict the
     * page can act on — it can offer a new confirmation link right where the error appears — and a
     * shared {@code CONFLICT} code left the client nothing to recognise it by but the message text,
     * in three languages. Declared above the {@link IllegalStateException} handler it narrows;
     * Spring dispatches on the most specific type regardless of order, the placement is for readers.
     */
    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ErrorResponse> handleEmailNotVerified(EmailNotVerifiedException ex) {
        log.warn("Login refused, address not confirmed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("EMAIL_NOT_VERIFIED", ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("CONFLICT", ex.getMessage(), Instant.now()));
    }

    /**
     * A losing race on a unique index is a CONFLICT, not a server fault: two tabs writing the
     * same weigh-in day used to surface as a 500. Kept at log level ERROR on purpose — a
     * violation that is NOT a benign race (a broken FK, a missing NOT NULL) still has to be
     * loud in the logs, only the status code was wrong.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("CONFLICT", messageService.get("error.conflict"), Instant.now()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Concurrent modification: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("CONFLICT", messageService.get("training.calendar.conflict"), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));

        log.warn("Validation failed: {}", message);
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_ERROR", message, Instant.now()));
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponse> handleJwtException(JwtException ex) {
        log.warn("JWT error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("UNAUTHORIZED", messageService.get("error.invalid.token"), Instant.now()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("FORBIDDEN", messageService.get("error.forbidden"), Instant.now()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Upload size exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
            .body(new ErrorResponse("PAYLOAD_TOO_LARGE", messageService.get("file.too.large"), Instant.now()));
    }

    /**
     * A path variable or query parameter that will not convert — {@code /ascents/nie-uuid},
     * {@code ?terrain=SPACE}, {@code ?date=wczoraj}. The client sent nonsense, so this is a 400.
     *
     * <p>Without this handler it fell through to the catch-all: a 500 and an ERROR with a stack
     * trace, from a request the server understood perfectly well and was right to refuse. Same
     * reasoning as {@code NoResourceFoundException} below — a scanner probing URLs must not be
     * able to fill the log with false alarms, or real errors stop being findable.
     *
     * <p>The reply carries a generic message on purpose: the exception text names the target type
     * and echoes the offending value, and neither belongs in an HTTP response.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Unconvertible parameter '{}': {}", ex.getName(), ex.getMessage());
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("BAD_REQUEST", messageService.get("error.bad.request"), Instant.now()));
    }

    /**
     * A body that is not readable at all: malformed JSON, a value of the wrong JSON type, or an
     * enum constant that does not exist ({@code "grade": "FR_99Z"}). Bean Validation never gets
     * to run on these, so they used to surface as a 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("BAD_REQUEST", messageService.get("error.bad.request"), Instant.now()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("No resource for request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", messageService.get("error.not.found"), Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError()
            .body(new ErrorResponse("INTERNAL_ERROR", messageService.get("error.internal"), Instant.now()));
    }

    record ErrorResponse(String code, String message, Instant timestamp) {}
}
