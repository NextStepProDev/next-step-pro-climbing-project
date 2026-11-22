package pl.nextsteppro.climbing.api.admin.contact;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.config.CurrentUserId;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/contact")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Contact", description = "Messages people sent from the site, and the replies")
public class AdminContactController {

    private final AdminContactService contactService;

    public AdminContactController(AdminContactService contactService) {
        this.contactService = contactService;
    }

    @Operation(summary = "Conversations, newest message first")
    @GetMapping("/conversations")
    public ResponseEntity<ConversationPageDto> list(
            @Parameter(description = "Only conversations with an unread message") @RequestParam(defaultValue = "false") boolean unread,
            @Parameter(description = "Page number (from 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(contactService.list(unread, page, size));
    }

    @Operation(summary = "One conversation with all its messages")
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ConversationDetailDto> get(@PathVariable UUID conversationId) {
        return ResponseEntity.ok(contactService.get(conversationId));
    }

    @Operation(summary = "Mark a conversation as read by the team")
    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID conversationId) {
        contactService.markRead(conversationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reply", description = "Saved in the conversation and mailed to the person.")
    @PostMapping("/conversations/{conversationId}/replies")
    public ResponseEntity<ReplyResultDto> reply(
            @PathVariable UUID conversationId,
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @Valid @RequestBody ReplyRequest request) {
        return ResponseEntity.ok(contactService.reply(conversationId, adminId, request.body()));
    }

    @Operation(summary = "Delete a conversation", description = "For spam; the messages go with it.")
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> delete(@PathVariable UUID conversationId) {
        contactService.delete(conversationId);
        return ResponseEntity.noContent().build();
    }
}
