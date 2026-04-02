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

        if (!fileStorageService.exists(filename, "instructors")) {
            return ResponseEntity.notFound().build();
        }

        InputStream inputStream = fileStorageService.getInputStream(filename, "instructors");
        long fileSize = fileStorageService.getFileSize(filename, "instructors");
        MediaType mediaType = getMediaType(filename);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(fileSize)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(new InputStreamResource(inputStream));
    }

    @Operation(summary = "Pobierz zdjęcie z galerii")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Plik znaleziony"),
        @ApiResponse(responseCode = "404", description = "Plik nie istnieje")
    })
    @GetMapping("/gallery/{filename}")
    public ResponseEntity<Resource> getGalleryPhoto(
            @Parameter(description = "Nazwa pliku") @PathVariable String filename) throws IOException {

        if (!fileStorageService.exists(filename, "gallery")) {
            return ResponseEntity.notFound().build();
        }

        InputStream inputStream = fileStorageService.getInputStream(filename, "gallery");
        long fileSize = fileStorageService.getFileSize(filename, "gallery");
        MediaType mediaType = getMediaType(filename);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(fileSize)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
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
