package pl.nextsteppro.climbing.api.admin.gallery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.admin.gallery.AdminGalleryDtos.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/gallery")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Gallery", description = "Gallery management (admin only)")
public class AdminGalleryController {

    private final AdminGalleryService adminGalleryService;

    public AdminGalleryController(AdminGalleryService adminGalleryService) {
        this.adminGalleryService = adminGalleryService;
    }

    // ==================== Albums Management ====================

    @Operation(
        summary = "Get all albums",
        description = "Returns all albums with thumbnails and photo counts"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of albums",
            content = @Content(schema = @Schema(implementation = AlbumAdminDto.class))),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping("/albums")
    public ResponseEntity<List<AlbumAdminDto>> getAllAlbums() {
        List<AlbumAdminDto> albums = adminGalleryService.getAllAlbums();
        return ResponseEntity.ok(albums);
    }

    @Operation(
        summary = "Get album details",
        description = "Returns detailed album information along with all its photos"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Album details",
            content = @Content(schema = @Schema(implementation = AlbumDetailAdminDto.class))),
        @ApiResponse(responseCode = "404", description = "Album not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping("/albums/{id}")
    public ResponseEntity<AlbumDetailAdminDto> getAlbum(
            @Parameter(description = "ID albumu") @PathVariable UUID id) {
        AlbumDetailAdminDto album = adminGalleryService.getAlbum(id);
        return ResponseEntity.ok(album);
    }

    @Operation(
        summary = "Create album",
        description = "Adds a new album to the gallery"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Album created",
            content = @Content(schema = @Schema(implementation = AlbumAdminDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/albums")
    public ResponseEntity<AlbumAdminDto> createAlbum(
            @Valid @RequestBody CreateAlbumRequest request) {
        AlbumAdminDto album = adminGalleryService.createAlbum(request);
        return ResponseEntity.ok(album);
    }

    @Operation(
        summary = "Update album",
        description = "Updates the album's name and/or description"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Album updated",
            content = @Content(schema = @Schema(implementation = AlbumAdminDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "404", description = "Album not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PutMapping("/albums/{id}")
    public ResponseEntity<AlbumAdminDto> updateAlbum(
            @Parameter(description = "ID albumu") @PathVariable UUID id,
            @Valid @RequestBody UpdateAlbumRequest request) {
        AlbumAdminDto album = adminGalleryService.updateAlbum(id, request);
        return ResponseEntity.ok(album);
    }

    @Operation(
        summary = "Publish album",
        description = "Marks the album as published — it becomes publicly visible"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Album opublikowany"),
        @ApiResponse(responseCode = "404", description = "Album not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/albums/{id}/publish")
    public ResponseEntity<AlbumAdminDto> publishAlbum(
            @Parameter(description = "ID albumu") @PathVariable UUID id) {
        return ResponseEntity.ok(adminGalleryService.setAlbumPublished(id, true));
    }

    @Operation(
        summary = "Unpublish album",
        description = "Marks the album as a draft — hides it from public view"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Album ukryty"),
        @ApiResponse(responseCode = "404", description = "Album not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/albums/{id}/unpublish")
    public ResponseEntity<AlbumAdminDto> unpublishAlbum(
            @Parameter(description = "ID albumu") @PathVariable UUID id) {
        return ResponseEntity.ok(adminGalleryService.setAlbumPublished(id, false));
    }

    @Operation(
        summary = "Delete album",
        description = "Deletes the album along with all its photos (files and records)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Album deleted"),
        @ApiResponse(responseCode = "404", description = "Album not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @DeleteMapping("/albums/{id}")
    public ResponseEntity<Void> deleteAlbum(
            @Parameter(description = "ID albumu") @PathVariable UUID id) {
        adminGalleryService.deleteAlbum(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Delete all photos from album",
        description = "Deletes all photos from the album but keeps the album itself"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Photos deleted"),
        @ApiResponse(responseCode = "404", description = "Album not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @DeleteMapping("/albums/{id}/photos")
    public ResponseEntity<Void> deleteAllPhotos(
            @Parameter(description = "ID albumu") @PathVariable UUID id) {
        adminGalleryService.deleteAllPhotos(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Reorder albums",
        description = "Updates album display order based on the submitted ID list"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Order updated"),
        @ApiResponse(responseCode = "400", description = "Invalid album IDs"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PutMapping("/albums/reorder")
    public ResponseEntity<Void> reorderAlbums(
            @Valid @RequestBody ReorderAlbumsRequest request) {
        adminGalleryService.reorderAlbums(request.orderedIds());
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Set album thumbnail",
        description = "Marks a specific photo as the album thumbnail. When the photo is deleted, the album automatically falls back to the default choice."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Thumbnail set"),
        @ApiResponse(responseCode = "400", description = "Photo does not belong to this album"),
        @ApiResponse(responseCode = "404", description = "Album or photo not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PutMapping("/albums/{id}/thumbnail-photo")
    public ResponseEntity<Void> setThumbnailPhoto(
            @Parameter(description = "ID albumu") @PathVariable UUID id,
            @Valid @RequestBody SetThumbnailRequest request) {
        adminGalleryService.setThumbnailPhoto(id, request.photoId());
        return ResponseEntity.noContent().build();
    }

    // ==================== Photos Management ====================

    @Operation(
        summary = "Upload photo to album",
        description = "Adds a new photo to the album (max 10MB, JPEG/PNG/WebP)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Photo uploaded",
            content = @Content(schema = @Schema(implementation = UploadPhotoResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid file"),
        @ApiResponse(responseCode = "404", description = "Album not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/albums/{albumId}/photos")
    public ResponseEntity<UploadPhotoResponse> uploadPhoto(
            @Parameter(description = "ID albumu") @PathVariable UUID albumId,
            @Parameter(description = "Image file") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Optional caption") @RequestParam(value = "caption", required = false) @Nullable String caption)
            throws IOException {
        UploadPhotoResponse response = adminGalleryService.uploadPhoto(albumId, file, caption);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Update photo",
        description = "Updates the photo's caption and/or display order"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Photo updated"),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "404", description = "Photo not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PutMapping("/photos/{photoId}")
    public ResponseEntity<Void> updatePhoto(
            @Parameter(description = "Photo ID") @PathVariable UUID photoId,
            @Valid @RequestBody UpdatePhotoRequest request) {
        adminGalleryService.updatePhoto(photoId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Delete photo",
        description = "Deletes the photo from the album (file and record)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Photo deleted"),
        @ApiResponse(responseCode = "404", description = "Photo not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @DeleteMapping("/photos/{photoId}")
    public ResponseEntity<Void> deletePhoto(
            @Parameter(description = "Photo ID") @PathVariable UUID photoId) throws IOException {
        adminGalleryService.deletePhoto(photoId);
        return ResponseEntity.noContent().build();
    }
}
