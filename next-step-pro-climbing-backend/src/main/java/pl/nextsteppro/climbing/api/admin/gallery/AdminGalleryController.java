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
@Tag(name = "Admin - Gallery", description = "Zarządzanie galerią (tylko admin)")
public class AdminGalleryController {

    private final AdminGalleryService adminGalleryService;

    public AdminGalleryController(AdminGalleryService adminGalleryService) {
        this.adminGalleryService = adminGalleryService;
    }

    // ==================== Albums Management ====================

    @Operation(
        summary = "Pobierz wszystkie albumy",
        description = "Zwraca listę wszystkich albumów z miniaturkami i liczbą zdjęć"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista albumów",
            content = @Content(schema = @Schema(implementation = AlbumAdminDto.class))),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping("/albums")
    public ResponseEntity<List<AlbumAdminDto>> getAllAlbums() {
        List<AlbumAdminDto> albums = adminGalleryService.getAllAlbums();
        return ResponseEntity.ok(albums);
    }

    @Operation(
        summary = "Pobierz szczegóły albumu",
        description = "Zwraca szczegółowe informacje o albumie wraz ze wszystkimi zdjęciami"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Szczegóły albumu",
            content = @Content(schema = @Schema(implementation = AlbumDetailAdminDto.class))),
        @ApiResponse(responseCode = "404", description = "Album nie znaleziony"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping("/albums/{id}")
    public ResponseEntity<AlbumDetailAdminDto> getAlbum(
            @Parameter(description = "ID albumu") @PathVariable UUID id) {
        AlbumDetailAdminDto album = adminGalleryService.getAlbum(id);
        return ResponseEntity.ok(album);
    }

    @Operation(
        summary = "Utwórz album",
        description = "Dodaje nowy album do galerii"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Album utworzony",
            content = @Content(schema = @Schema(implementation = AlbumAdminDto.class))),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe dane"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PostMapping("/albums")
    public ResponseEntity<AlbumAdminDto> createAlbum(
            @Valid @RequestBody CreateAlbumRequest request) {
        AlbumAdminDto album = adminGalleryService.createAlbum(request);
        return ResponseEntity.ok(album);
    }

    @Operation(
        summary = "Aktualizuj album",
        description = "Aktualizuje nazwę i/lub opis albumu"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Album zaktualizowany",
            content = @Content(schema = @Schema(implementation = AlbumAdminDto.class))),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe dane"),
        @ApiResponse(responseCode = "404", description = "Album nie znaleziony"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PutMapping("/albums/{id}")
    public ResponseEntity<AlbumAdminDto> updateAlbum(
            @Parameter(description = "ID albumu") @PathVariable UUID id,
            @Valid @RequestBody UpdateAlbumRequest request) {
        AlbumAdminDto album = adminGalleryService.updateAlbum(id, request);
        return ResponseEntity.ok(album);
    }

    @Operation(
        summary = "Usuń album",
        description = "Usuwa album wraz ze wszystkimi zdjęciami (pliki i rekordy)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Album usunięty"),
        @ApiResponse(responseCode = "404", description = "Album nie znaleziony"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @DeleteMapping("/albums/{id}")
    public ResponseEntity<Void> deleteAlbum(
            @Parameter(description = "ID albumu") @PathVariable UUID id) {
        adminGalleryService.deleteAlbum(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Usuń wszystkie zdjęcia z albumu",
        description = "Usuwa wszystkie zdjęcia z albumu, ale zachowuje sam album"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Zdjęcia usunięte"),
        @ApiResponse(responseCode = "404", description = "Album nie znaleziony"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @DeleteMapping("/albums/{id}/photos")
    public ResponseEntity<Void> deleteAllPhotos(
            @Parameter(description = "ID albumu") @PathVariable UUID id) {
        adminGalleryService.deleteAllPhotos(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Zmień kolejność albumów",
        description = "Aktualizuje kolejność wyświetlania albumów na podstawie przesłanej listy ID"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Kolejność zaktualizowana"),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe ID albumów"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PutMapping("/albums/reorder")
    public ResponseEntity<Void> reorderAlbums(
            @Valid @RequestBody ReorderAlbumsRequest request) {
        adminGalleryService.reorderAlbums(request.orderedIds());
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Ustaw miniaturkę albumu",
        description = "Wskazuje konkretne zdjęcie jako miniaturkę albumu. Gdy zdjęcie zostanie usunięte, album automatycznie wraca do domyślnego wyboru."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Miniaturka ustawiona"),
        @ApiResponse(responseCode = "400", description = "Zdjęcie nie należy do tego albumu"),
        @ApiResponse(responseCode = "404", description = "Album lub zdjęcie nie znalezione"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
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
        summary = "Prześlij zdjęcie do albumu",
        description = "Dodaje nowe zdjęcie do albumu (max 10MB, JPEG/PNG/WebP)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Zdjęcie przesłane",
            content = @Content(schema = @Schema(implementation = UploadPhotoResponse.class))),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowy plik"),
        @ApiResponse(responseCode = "404", description = "Album nie znaleziony"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PostMapping("/albums/{albumId}/photos")
    public ResponseEntity<UploadPhotoResponse> uploadPhoto(
            @Parameter(description = "ID albumu") @PathVariable UUID albumId,
            @Parameter(description = "Plik zdjęcia") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Opcjonalny podpis") @RequestParam(value = "caption", required = false) @Nullable String caption)
            throws IOException {
        UploadPhotoResponse response = adminGalleryService.uploadPhoto(albumId, file, caption);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Aktualizuj zdjęcie",
        description = "Aktualizuje podpis i/lub kolejność wyświetlania zdjęcia"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Zdjęcie zaktualizowane"),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe dane"),
        @ApiResponse(responseCode = "404", description = "Zdjęcie nie znalezione"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PutMapping("/photos/{photoId}")
    public ResponseEntity<Void> updatePhoto(
            @Parameter(description = "ID zdjęcia") @PathVariable UUID photoId,
            @Valid @RequestBody UpdatePhotoRequest request) {
        adminGalleryService.updatePhoto(photoId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Usuń zdjęcie",
        description = "Usuwa zdjęcie z albumu (plik i rekord)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Zdjęcie usunięte"),
        @ApiResponse(responseCode = "404", description = "Zdjęcie nie znalezione"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @DeleteMapping("/photos/{photoId}")
    public ResponseEntity<Void> deletePhoto(
            @Parameter(description = "ID zdjęcia") @PathVariable UUID photoId) throws IOException {
        adminGalleryService.deletePhoto(photoId);
        return ResponseEntity.noContent().build();
    }
}
