package pl.nextsteppro.climbing.api.admin.contact;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.contact.ContactFileSupport;
import pl.nextsteppro.climbing.api.trainingcalendar.PrivateFileResponses;
import pl.nextsteppro.climbing.config.CurrentUserId;

import java.time.Instant;
import java.util.List;
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

    @Operation(summary = "Conversations, newest message in either direction first")
    @GetMapping("/conversations")
    public ResponseEntity<ConversationPageDto> list(
            @Parameter(description = "Only conversations with an unread message") @RequestParam(defaultValue = "false") boolean unread,
            @Parameter(description = "Search the person's name or e-mail address") @RequestParam(required = false) @Nullable String q,
            @Parameter(description = "Page number (from 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(contactService.list(unread, q, page, size));
    }

    @Operation(summary = "Start a conversation with a registered person",
        description = "When the person already has a conversation, the message goes into it. No mail: the person sees it in the app.")
    @PostMapping("/conversations")
    public ResponseEntity<StartConversationResultDto> start(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @Valid @RequestBody StartConversationRequest request) {
        return ResponseEntity.ok(contactService.start(adminId, request.userId(), request.body()));
    }

    @Operation(summary = "The account's conversation, if it has one")
    @GetMapping("/conversations/by-user/{userId}")
    public ResponseEntity<ConversationLookupDto> findForUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(contactService.findForUser(userId));
    }

    @Operation(summary = "Older messages, for scrolling up",
        description = "The page just above the message at (at, id), oldest first. The cursor is that message's createdAt and id.")
    @GetMapping("/conversations/{conversationId}/messages/older")
    public ResponseEntity<AdminMessagePageDto> older(
            @PathVariable UUID conversationId,
            @Parameter(description = "createdAt of the oldest message the window has") @RequestParam Instant at,
            @Parameter(description = "id of that message") @RequestParam UUID id) {
        return ResponseEntity.ok(contactService.older(conversationId, at, id));
    }

    @Operation(summary = "New messages, for polling",
        description = "Only what arrived after the message at (at, id), oldest first, with the conversation's summary.")
    @GetMapping("/conversations/{conversationId}/messages/newer")
    public ResponseEntity<AdminConversationUpdateDto> newer(
            @PathVariable UUID conversationId,
            @Parameter(description = "createdAt of the newest message the window has") @RequestParam Instant at,
            @Parameter(description = "id of that message") @RequestParam UUID id) {
        return ResponseEntity.ok(contactService.newer(conversationId, at, id));
    }

    @Operation(summary = "One conversation: the newest page of messages")
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

    @Operation(summary = "Reply", description = "Saved in the conversation; mailed only to a guest, an account holder sees it in the app.")
    @PostMapping("/conversations/{conversationId}/replies")
    public ResponseEntity<ReplyResultDto> reply(
            @PathVariable UUID conversationId,
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @Valid @RequestBody ReplyRequest request) {
        return ResponseEntity.ok(contactService.reply(conversationId, adminId, request.body()));
    }

    @Operation(summary = "Reply with photos or PDFs",
        description = "Multipart: optional text, up to 3 files (images or PDF, 5 MB each). Text, files or both, "
            + "never neither. Only in an account's chat - a guest is reached by mail alone (400).")
    @PostMapping(value = "/conversations/{conversationId}/replies/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReplyResultDto> replyWithFiles(
            @PathVariable UUID conversationId,
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @RequestParam(required = false) @Nullable String body,
            @RequestParam(value = "files", required = false) @Nullable List<MultipartFile> files) {
        return ResponseEntity.ok(contactService.replyWithFiles(conversationId, adminId, body, files == null ? List.of() : files));
    }

    @Operation(summary = "A file of a conversation", description = "Private stream, never under the public /api/files.")
    @GetMapping("/files/{fileId}")
    public ResponseEntity<Resource> file(@PathVariable UUID fileId) {
        ContactFileSupport.FileStream file = contactService.openFile(fileId);
        return PrivateFileResponses.stream(file.inputStream(), file.size(), file.mimeType(), file.fileName());
    }

    @Operation(summary = "Remove a file", description = "Any file in the chat; a message that was only the file goes with it.")
    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable UUID fileId) {
        contactService.deleteFile(fileId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete a conversation", description = "For spam; the messages go with it.")
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> delete(@PathVariable UUID conversationId) {
        contactService.delete(conversationId);
        return ResponseEntity.noContent().build();
    }
}
