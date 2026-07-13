package pl.nextsteppro.climbing.api.admin.news;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.admin.news.AdminNewsDtos.*;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/news")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - News", description = "News management (admin only)")
public class AdminNewsController {

    private final AdminNewsService adminNewsService;

    public AdminNewsController(AdminNewsService adminNewsService) {
        this.adminNewsService = adminNewsService;
    }

    // ==================== Articles ====================

    @Operation(summary = "Get all news articles (drafts + published)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of news articles"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping
    public ResponseEntity<AdminNewsPageDto> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminNewsService.getAllNews(page, size));
    }

    @Operation(summary = "Get news article details with blocks")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "News article details"),
        @ApiResponse(responseCode = "400", description = "News article not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping("/{id}")
    public ResponseEntity<NewsDetailAdminDto> getById(
            @Parameter(description = "News article ID") @PathVariable UUID id) {
        return ResponseEntity.ok(adminNewsService.getNews(id));
    }

    @Operation(summary = "Create new news article (draft)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "News article created"),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping
    public ResponseEntity<NewsAdminDto> create(
            @Valid @RequestBody CreateNewsRequest request) {
        return ResponseEntity.ok(adminNewsService.createNews(request));
    }

    @Operation(summary = "Update news article title and excerpt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Metadata updated"),
        @ApiResponse(responseCode = "400", description = "News article not found or invalid data"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PutMapping("/{id}/meta")
    public ResponseEntity<NewsAdminDto> updateMeta(
            @Parameter(description = "News article ID") @PathVariable UUID id,
            @Valid @RequestBody UpdateNewsMetaRequest request) {
        return ResponseEntity.ok(adminNewsService.updateNewsMeta(id, request));
    }

    @Operation(summary = "Publish news article")
    @PostMapping("/{id}/publish")
    public ResponseEntity<NewsAdminDto> publish(
            @Parameter(description = "News article ID") @PathVariable UUID id) {
        return ResponseEntity.ok(adminNewsService.setPublished(id, true));
    }

    @Operation(summary = "Unpublish news article")
    @PostMapping("/{id}/unpublish")
    public ResponseEntity<NewsAdminDto> unpublish(
            @Parameter(description = "News article ID") @PathVariable UUID id) {
        return ResponseEntity.ok(adminNewsService.setPublished(id, false));
    }

    @Operation(summary = "Change news article publication date")
    @PutMapping("/{id}/published-at")
    public ResponseEntity<NewsAdminDto> updatePublishedAt(
            @Parameter(description = "News article ID") @PathVariable UUID id,
            @Valid @RequestBody AdminNewsDtos.UpdatePublishedAtRequest request) {
        return ResponseEntity.ok(adminNewsService.updatePublishedAt(id, request.publishedAt()));
    }

    @Operation(summary = "Send the news article as a newsletter to subscribers")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Newsletter sent (recipient count)"),
        @ApiResponse(responseCode = "400", description = "News article is not published"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/{id}/send-newsletter")
    public ResponseEntity<AdminNewsDtos.NewsletterSentDto> sendNewsletter(
            @Parameter(description = "News article ID") @PathVariable UUID id) {
        return ResponseEntity.ok(adminNewsService.sendNewsNewsletter(id));
    }

    @Operation(summary = "Duplicate news article as translation")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "News article duplicated as translation"),
        @ApiResponse(responseCode = "400", description = "Invalid data or translation already exists"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/{id}/duplicate-translation")
    public ResponseEntity<NewsDetailAdminDto> duplicateAsTranslation(
            @Parameter(description = "News article ID") @PathVariable UUID id,
            @Valid @RequestBody AdminNewsDtos.DuplicateAsTranslationRequest request) {
        return ResponseEntity.ok(adminNewsService.duplicateAsTranslation(id, request.targetLanguage()));
    }

    @Operation(summary = "Sync media blocks (IMAGE/VIDEO) to all translations")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Media synchronized"),
        @ApiResponse(responseCode = "400", description = "News article not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/{id}/sync-media-to-translations")
    public ResponseEntity<AdminNewsDtos.SyncMediaResultDto> syncMediaToTranslations(
            @Parameter(description = "Source news article ID") @PathVariable UUID id) {
        return ResponseEntity.ok(adminNewsService.syncMediaToTranslations(id));
    }

    @Operation(summary = "Delete news article along with its files")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "News article deleted"),
        @ApiResponse(responseCode = "400", description = "News article not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "News article ID") @PathVariable UUID id) {
        adminNewsService.deleteNews(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Thumbnail ====================

    @Operation(summary = "Upload news article thumbnail")
    @PostMapping("/{id}/thumbnail")
    public ResponseEntity<NewsDetailAdminDto> uploadThumbnail(
            @Parameter(description = "News article ID") @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(adminNewsService.uploadThumbnail(id, file));
    }

    @Operation(summary = "Set news article thumbnail focal point")
    @PutMapping("/{id}/thumbnail-focal-point")
    public ResponseEntity<Void> updateThumbnailFocalPoint(
            @Parameter(description = "News article ID") @PathVariable UUID id,
            @RequestBody UpdateThumbnailFocalPointRequest request) {
        adminNewsService.updateThumbnailFocalPoint(id, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Set news article thumbnail from the media library (URL)")
    @PutMapping("/{id}/thumbnail-url")
    public ResponseEntity<Void> setThumbnailUrl(
            @Parameter(description = "News article ID") @PathVariable UUID id,
            @RequestBody AdminNewsDtos.SetThumbnailUrlRequest request) {
        adminNewsService.setThumbnailUrl(id, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete news article thumbnail")
    @DeleteMapping("/{id}/thumbnail")
    public ResponseEntity<Void> deleteThumbnail(
            @Parameter(description = "News article ID") @PathVariable UUID id) throws IOException {
        adminNewsService.deleteThumbnail(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Content blocks ====================

    @Operation(summary = "Add text block")
    @PostMapping("/{id}/blocks/text")
    public ResponseEntity<ContentBlockAdminDto> addTextBlock(
            @Parameter(description = "News article ID") @PathVariable UUID id,
            @Valid @RequestBody AddTextBlockRequest request) {
        return ResponseEntity.ok(adminNewsService.addTextBlock(id, request));
    }

    @Operation(summary = "Add image block")
    @PostMapping("/{id}/blocks/image")
    public ResponseEntity<UploadBlockImageResponse> addImageBlock(
            @Parameter(description = "News article ID") @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) @Nullable String caption) throws IOException {
        return ResponseEntity.ok(adminNewsService.addImageBlock(id, file, caption));
    }

    @Operation(summary = "Add video block (YouTube / Instagram embed)")
    @PostMapping("/{id}/blocks/video")
    public ResponseEntity<ContentBlockAdminDto> addVideoEmbedBlock(
            @Parameter(description = "News article ID") @PathVariable UUID id,
            @Valid @RequestBody AdminNewsDtos.AddVideoEmbedBlockRequest request) {
        return ResponseEntity.ok(adminNewsService.addVideoEmbedBlock(id, request));
    }

    @Operation(summary = "Edit video block URL")
    @PutMapping("/blocks/{blockId}/video")
    public ResponseEntity<Void> updateVideoEmbedBlock(
            @Parameter(description = "ID bloku") @PathVariable UUID blockId,
            @Valid @RequestBody AdminNewsDtos.UpdateVideoEmbedBlockRequest request) {
        adminNewsService.updateVideoEmbedBlock(blockId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add image block from the media library (URL)")
    @PostMapping("/{id}/blocks/image-from-url")
    public ResponseEntity<AdminNewsDtos.ContentBlockAdminDto> addImageBlockFromUrl(
            @Parameter(description = "News article ID") @PathVariable UUID id,
            @RequestBody AdminNewsDtos.AddImageBlockFromUrlRequest request) {
        return ResponseEntity.ok(adminNewsService.addImageBlockFromUrl(id, request));
    }

    @Operation(summary = "Edit text block content")
    @PutMapping("/blocks/{blockId}/text")
    public ResponseEntity<Void> updateTextBlock(
            @Parameter(description = "ID bloku") @PathVariable UUID blockId,
            @Valid @RequestBody UpdateTextBlockRequest request) {
        adminNewsService.updateTextBlock(blockId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Edit image block caption")
    @PutMapping("/blocks/{blockId}/image")
    public ResponseEntity<Void> updateImageBlock(
            @Parameter(description = "ID bloku") @PathVariable UUID blockId,
            @RequestBody UpdateImageBlockRequest request) {
        adminNewsService.updateImageBlock(blockId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Move block up or down")
    @PostMapping("/blocks/{blockId}/move")
    public ResponseEntity<Void> moveBlock(
            @Parameter(description = "ID bloku") @PathVariable UUID blockId,
            @RequestBody MoveBlockRequest request) {
        adminNewsService.moveBlock(blockId, request.direction());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete content block")
    @DeleteMapping("/blocks/{blockId}")
    public ResponseEntity<Void> deleteBlock(
            @Parameter(description = "ID bloku") @PathVariable UUID blockId) {
        adminNewsService.deleteBlock(blockId);
        return ResponseEntity.noContent().build();
    }
}
