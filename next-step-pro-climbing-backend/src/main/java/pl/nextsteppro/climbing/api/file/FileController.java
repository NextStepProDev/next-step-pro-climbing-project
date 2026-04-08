package pl.nextsteppro.climbing.api.file;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/files")
@Tag(name = "Files", description = "File serving endpoints")
public class FileController {

    private static final int FILE_CACHE_DAYS = 7;

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @Operation(summary = "Pobierz zdjęcie instruktora")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Plik znaleziony"),
        @ApiResponse(responseCode = "404", description = "Plik nie istnieje")
    })
    @GetMapping("/instructors/{filename}")
    public ResponseEntity<Resource> getInstructorPhoto(
            @Parameter(description = "Nazwa pliku") @PathVariable String filename) throws IOException {
        return serveFile(filename, "instructors");
    }

    @Operation(summary = "Pobierz zdjęcie z galerii")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Plik znaleziony"),
        @ApiResponse(responseCode = "404", description = "Plik nie istnieje")
    })
    @GetMapping("/gallery/{filename}")
    public ResponseEntity<Resource> getGalleryPhoto(
            @Parameter(description = "Nazwa pliku") @PathVariable String filename) throws IOException {
        return serveFile(filename, "gallery");
    }

    @Operation(summary = "Pobierz plik aktualności (miniaturka lub zdjęcie w treści)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Plik znaleziony"),
        @ApiResponse(responseCode = "404", description = "Plik nie istnieje")
    })
    @GetMapping("/news/{filename}")
    public ResponseEntity<Resource> getNewsFile(
            @Parameter(description = "Nazwa pliku") @PathVariable String filename) throws IOException {
        return serveFile(filename, "news");
    }

    @Operation(summary = "Pobierz plik kursu (miniaturka lub zdjęcie w treści)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Plik znaleziony"),
        @ApiResponse(responseCode = "404", description = "Plik nie istnieje")
    })
    @GetMapping("/courses/{filename}")
    public ResponseEntity<Resource> getCourseFile(
            @Parameter(description = "Nazwa pliku") @PathVariable String filename) throws IOException {
        return serveFile(filename, "courses");
    }

    @Operation(summary = "Pobierz plik z biblioteki mediów (shared assets)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Plik znaleziony"),
        @ApiResponse(responseCode = "404", description = "Plik nie istnieje")
    })
    @GetMapping("/assets/{filename}")
    public ResponseEntity<Resource> getAssetFile(
            @Parameter(description = "Nazwa pliku") @PathVariable String filename) throws IOException {
        return serveFile(filename, "assets");
    }

    private ResponseEntity<Resource> serveFile(String filename, String folder) throws IOException {
        if (!fileStorageService.exists(filename, folder)) {
            return ResponseEntity.notFound().build();
        }

        InputStream inputStream = fileStorageService.getInputStream(filename, folder);
        long fileSize = fileStorageService.getFileSize(filename, folder);

        return ResponseEntity.ok()
                .contentType(getMediaType(filename))
                .contentLength(fileSize)
                .cacheControl(CacheControl.maxAge(FILE_CACHE_DAYS, TimeUnit.DAYS).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(new InputStreamResource(inputStream));
    }

    private MediaType getMediaType(String filename) {
        String lowerFilename = filename.toLowerCase();
        if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        } else if (lowerFilename.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        } else if (lowerFilename.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
