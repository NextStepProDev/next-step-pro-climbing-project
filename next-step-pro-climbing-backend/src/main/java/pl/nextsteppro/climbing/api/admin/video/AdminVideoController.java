package pl.nextsteppro.climbing.api.admin.video;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.api.admin.video.AdminVideoDtos.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/videos")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Videos", description = "Video management (admin only)")
public class AdminVideoController {

    private final AdminVideoService adminVideoService;

    public AdminVideoController(AdminVideoService adminVideoService) {
        this.adminVideoService = adminVideoService;
    }

    @Operation(summary = "Get all videos (drafts + published)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of videos"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping
    public ResponseEntity<List<VideoAdminDto>> getAll() {
        return ResponseEntity.ok(adminVideoService.getAllVideos());
    }

    @Operation(summary = "Get video details")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Video details"),
        @ApiResponse(responseCode = "400", description = "Video not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping("/{id}")
    public ResponseEntity<VideoAdminDto> getById(
            @Parameter(description = "ID filmu") @PathVariable UUID id) {
        return ResponseEntity.ok(adminVideoService.getVideo(id));
    }

    @Operation(summary = "Create new video (draft)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Video created"),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping
    public ResponseEntity<VideoAdminDto> create(
            @Valid @RequestBody CreateVideoRequest request) {
        return ResponseEntity.ok(adminVideoService.createVideo(request));
    }

    @Operation(summary = "Update video")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Video updated"),
        @ApiResponse(responseCode = "400", description = "Invalid data or video not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PutMapping("/{id}")
    public ResponseEntity<VideoAdminDto> update(
            @Parameter(description = "ID filmu") @PathVariable UUID id,
            @Valid @RequestBody UpdateVideoRequest request) {
        return ResponseEntity.ok(adminVideoService.updateVideo(id, request));
    }

    @Operation(summary = "Delete video")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Video deleted"),
        @ApiResponse(responseCode = "400", description = "Video not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID filmu") @PathVariable UUID id) {
        adminVideoService.deleteVideo(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Publish video")
    @PostMapping("/{id}/publish")
    public ResponseEntity<VideoAdminDto> publish(
            @Parameter(description = "ID filmu") @PathVariable UUID id) {
        return ResponseEntity.ok(adminVideoService.setPublished(id, true));
    }

    @Operation(summary = "Unpublish video")
    @PostMapping("/{id}/unpublish")
    public ResponseEntity<VideoAdminDto> unpublish(
            @Parameter(description = "ID filmu") @PathVariable UUID id) {
        return ResponseEntity.ok(adminVideoService.setPublished(id, false));
    }

    @Operation(summary = "Move video up (lower displayOrder)")
    @PostMapping("/{id}/move-up")
    public ResponseEntity<List<VideoAdminDto>> moveUp(
            @Parameter(description = "ID filmu") @PathVariable UUID id) {
        return ResponseEntity.ok(adminVideoService.moveUp(id));
    }

    @Operation(summary = "Move video down (higher displayOrder)")
    @PostMapping("/{id}/move-down")
    public ResponseEntity<List<VideoAdminDto>> moveDown(
            @Parameter(description = "ID filmu") @PathVariable UUID id) {
        return ResponseEntity.ok(adminVideoService.moveDown(id));
    }
}
