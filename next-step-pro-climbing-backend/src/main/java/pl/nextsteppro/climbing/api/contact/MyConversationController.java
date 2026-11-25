package pl.nextsteppro.climbing.api.contact;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.trainingcalendar.PrivateFileResponses;
import pl.nextsteppro.climbing.config.CurrentUserId;

import java.time.Instant;
import java.util.List;
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

    @Operation(summary = "My conversation with the team", description = "The newest page of messages, oldest first.")
    @GetMapping
    public ResponseEntity<MyConversationDto> get(@Parameter(hidden = true) @CurrentUserId UUID userId) {
        return ResponseEntity.ok(contactService.getMine(userId));
    }

    @Operation(summary = "Write in my chat, with optional photos or PDFs",
        description = "Multipart: optional text, optional language, up to 3 files (images or PDF, 5 MB each). "
            + "Text, files or both, never neither. Returns the message, so the chat can show it at once.")
    @PostMapping(value = "/messages/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ConversationMessageDto> send(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @RequestParam(required = false) @Nullable String body,
            @RequestParam(required = false) @Nullable String language,
            @RequestParam(value = "files", required = false) @Nullable List<MultipartFile> files) {
        return ResponseEntity.ok(contactService.sendInChat(userId, body, language, files == null ? List.of() : files));
    }

    @Operation(summary = "A file of my conversation",
        description = "Private stream: only the conversation's own account. Never under the public /api/files.")
    @GetMapping("/files/{fileId}")
    public ResponseEntity<Resource> file(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID fileId) {
        ContactFileSupport.FileStream file = contactService.openMyFile(userId, fileId);
        return PrivateFileResponses.stream(file.inputStream(), file.size(), file.mimeType(), file.fileName());
    }

    @Operation(summary = "Withdraw a file I sent", description = "Only what the caller sent; a message that was only the file goes with it.")
    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID fileId) {
        contactService.deleteMyFile(userId, fileId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Older messages, for scrolling up",
        description = "The page just above the message at (at, id), oldest first.")
    @GetMapping("/messages/older")
    public ResponseEntity<MyMessagePageDto> older(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Parameter(description = "createdAt of the oldest message the chat has") @RequestParam Instant at,
            @Parameter(description = "id of that message") @RequestParam UUID id) {
        return ResponseEntity.ok(contactService.olderMine(userId, at, id));
    }

    @Operation(summary = "New messages, for polling",
        description = "Only what arrived after the message at (at, id), oldest first.")
    @GetMapping("/messages/newer")
    public ResponseEntity<MyConversationUpdateDto> newer(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Parameter(description = "createdAt of the newest message the chat has") @RequestParam Instant at,
            @Parameter(description = "id of that message") @RequestParam UUID id) {
        return ResponseEntity.ok(contactService.newerMine(userId, at, id));
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
