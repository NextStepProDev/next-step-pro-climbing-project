package pl.nextsteppro.climbing.api.contact;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.config.CurrentUserId;

import java.util.UUID;

/**
 * The signed-in person's conversation with the team. Addressed by the token alone - there is no
 * id in the path, so there is nobody else's conversation to ask for.
 */
@RestController
@RequestMapping("/api/conversation")
@Tag(name = "Contact", description = "Messages sent to the team from the site")
public class MyConversationController {

    private final ContactService contactService;

    public MyConversationController(ContactService contactService) {
        this.contactService = contactService;
    }

    @Operation(summary = "My conversation with the team", description = "All messages, oldest first.")
    @GetMapping
    public ResponseEntity<MyConversationDto> get(@Parameter(hidden = true) @CurrentUserId UUID userId) {
        return ResponseEntity.ok(contactService.getMine(userId));
    }

    @Operation(summary = "Is there an answer I have not seen", description = "Polled by the navbar badge.")
    @GetMapping("/unseen")
    public ResponseEntity<UnseenReplyDto> unseen(@Parameter(hidden = true) @CurrentUserId UUID userId) {
        return ResponseEntity.ok(contactService.unseenReply(userId));
    }

    @Operation(summary = "Mark the team's answers as seen")
    @PostMapping("/seen")
    public ResponseEntity<Void> seen(@Parameter(hidden = true) @CurrentUserId UUID userId) {
        contactService.markSeen(userId);
        return ResponseEntity.noContent().build();
    }
}
