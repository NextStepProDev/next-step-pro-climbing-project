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

    @Operation(summary = "Get instructor photo")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File found"),
        @ApiResponse(responseCode = "404", description = "File not found")
    })
    @GetMapping("/instructors/{filename}")
    public ResponseEntity<Resource> getInstructorPhoto(
            @Parameter(description = "File name") @PathVariable String filename) throws IOException {
        return serveFile(filename, "instructors");
    }

    @Operation(summary = "Get gallery photo")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File found"),
        @ApiResponse(responseCode = "404", description = "File not found")
    })
    @GetMapping("/gallery/{filename}")
    public ResponseEntity<Resource> getGalleryPhoto(
            @Parameter(description = "File name") @PathVariable String filename) throws IOException {
        return serveFile(filename, "gallery");
    }

    @Operation(summary = "Get a news file (thumbnail or in-content image)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File found"),
        @ApiResponse(responseCode = "404", description = "File not found")
    })
    @GetMapping("/news/{filename}")
    public ResponseEntity<Resource> getNewsFile(
            @Parameter(description = "File name") @PathVariable String filename) throws IOException {
        return serveFile(filename, "news");
    }

    @Operation(summary = "Get a course file (thumbnail or in-content image)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File found"),
        @ApiResponse(responseCode = "404", description = "File not found")
    })
    @GetMapping("/courses/{filename}")
    public ResponseEntity<Resource> getCourseFile(
            @Parameter(description = "File name") @PathVariable String filename) throws IOException {
        return serveFile(filename, "courses");
    }

    @Operation(summary = "Get a file from the media library (shared assets)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File found"),
        @ApiResponse(responseCode = "404", description = "File not found")
    })
    @GetMapping("/assets/{filename}")
    public ResponseEntity<Resource> getAssetFile(
            @Parameter(description = "File name") @PathVariable String filename) throws IOException {
        return serveFile(filename, "assets");
    }

    @Operation(summary = "Get a site settings file (e.g. homepage hero image)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File found"),
        @ApiResponse(responseCode = "404", description = "File not found")
    })
    @GetMapping("/site/{filename}")
    public ResponseEntity<Resource> getSiteFile(
            @Parameter(description = "File name") @PathVariable String filename) throws IOException {
        return serveFile(filename, "site");
    }

    @Operation(summary = "Get user avatar")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File found"),
        @ApiResponse(responseCode = "404", description = "File not found")
    })
    @GetMapping("/avatars/{filename}")
    public ResponseEntity<Resource> getAvatarFile(
            @Parameter(description = "File name") @PathVariable String filename) throws IOException {
        return serveFile(filename, "avatars");
    }

    @Operation(summary = "Get a training material file (uploaded PDF/image)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File found"),
        @ApiResponse(responseCode = "404", description = "File not found")
    })
    @GetMapping("/training/{filename}")
    public ResponseEntity<Resource> getTrainingFile(
            @Parameter(description = "File name") @PathVariable String filename) throws IOException {
        return serveFile(filename, "training");
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
        } else if (lowerFilename.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
