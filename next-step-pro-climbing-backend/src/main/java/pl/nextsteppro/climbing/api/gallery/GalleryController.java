package pl.nextsteppro.climbing.api.gallery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.api.gallery.GalleryDtos.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/gallery")
@Tag(name = "Gallery", description = "Publiczny dostęp do galerii zdjęć")
public class GalleryController {

    private final GalleryService galleryService;

    public GalleryController(GalleryService galleryService) {
        this.galleryService = galleryService;
    }

    @Operation(
        summary = "Pobierz listę albumów",
        description = "Zwraca listę wszystkich albumów z miniaturkami i liczbą zdjęć"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista albumów",
            content = @Content(schema = @Schema(implementation = AlbumSummaryDto.class)))
    })
    @GetMapping("/albums")
    public ResponseEntity<List<AlbumSummaryDto>> getAllAlbums() {
        List<AlbumSummaryDto> albums = galleryService.getAllAlbums();
        return ResponseEntity.ok(albums);
    }

    @Operation(
        summary = "Pobierz szczegóły albumu",
        description = "Zwraca szczegółowe informacje o albumie wraz ze wszystkimi zdjęciami"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Szczegóły albumu",
            content = @Content(schema = @Schema(implementation = AlbumDetailDto.class))),
        @ApiResponse(responseCode = "404", description = "Album nie znaleziony")
    })
    @GetMapping("/albums/{id}")
    public ResponseEntity<AlbumDetailDto> getAlbum(
            @Parameter(description = "ID albumu") @PathVariable UUID id) {
        AlbumDetailDto album = galleryService.getAlbum(id);
        return ResponseEntity.ok(album);
    }
}
