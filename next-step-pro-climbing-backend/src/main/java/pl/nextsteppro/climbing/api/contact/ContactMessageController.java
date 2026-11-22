package pl.nextsteppro.climbing.api.contact;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.config.CurrentUserId;
import pl.nextsteppro.climbing.config.RateLimitFilter;

import java.util.UUID;

/**
 * The public "write to us" form. Open to anonymous visitors on purpose, which is why it has a
 * bucket of its own, an hourly cap and a honeypot. A signed-in sender's message goes into their
 * conversation instead.
 */
@RestController
@RequestMapping("/api/contact-messages")
@Tag(name = "Contact", description = "Messages sent to the team from the site")
public class ContactMessageController {

    private final ContactService contactService;

    public ContactMessageController(ContactService contactService) {
        this.contactService = contactService;
    }

    @Operation(summary = "Send a message to the team",
        description = "Guests give a name, an address and consent; a signed-in sender writes into their conversation.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Message received"),
        @ApiResponse(responseCode = "400", description = "Missing or invalid fields"),
        @ApiResponse(responseCode = "409", description = "Too many messages in the last hour")
    })
    @PostMapping
    public ResponseEntity<ContactSentDto> send(
            @Parameter(hidden = true) @CurrentUserId @Nullable UUID userId,
            @Valid @RequestBody SendContactMessageRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(contactService.send(userId, RateLimitFilter.getClientIp(httpRequest), request));
    }
}
