package pl.nextsteppro.climbing.infrastructure.storage;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class LocalFileStorageService implements FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileStorageService.class);

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    // Strict filename validation: UUID + allowed image extension
    // Prevents path traversal attacks by enforcing expected format
    private static final Pattern VALID_FILENAME_PATTERN = Pattern.compile(
            "^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(jpg|jpeg|png|webp)$",
            Pattern.CASE_INSENSITIVE
    );

    // Strict folder validation: only lowercase letters (instructors, gallery)
    private static final Pattern VALID_FOLDER_PATTERN = Pattern.compile(
            "^[a-z]+$"
    );

    private final Path rootPath;

    public LocalFileStorageService(@Value("${app.storage.root:/app/uploads}") String rootPath) {
        this.rootPath = Paths.get(rootPath);
        try {
            Files.createDirectories(this.rootPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create storage root directory: " + rootPath, e);
        }
    }

    @Override
    public String store(MultipartFile file, @Nullable String folder) throws IOException {
        // Validate file
        validateFile(file);

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String filename = UUID.randomUUID() + extension;

        // Validate folder name (strict: only lowercase letters)
        if (folder != null) {
            if (folder.isBlank() || !VALID_FOLDER_PATTERN.matcher(folder).matches()) {
                throw new IllegalArgumentException(
                        "Invalid folder name. Expected: lowercase letters only (e.g., instructors, gallery)"
                );
            }
        }

        // Determine target path
        Path targetPath = folder != null
                ? rootPath.resolve(folder).resolve(filename)
                : rootPath.resolve(filename);

        // Create folder if needed
        Files.createDirectories(targetPath.getParent());

        // Copy file
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        logger.info("Stored file: {} in folder: {}", filename, folder);
        return filename;
    }

    @Override
    public void delete(String filename, @Nullable String folder) throws IOException {
        // Prevent directory traversal
        validateFilename(filename);

        Path filePath = folder != null
                ? rootPath.resolve(folder).resolve(filename)
                : rootPath.resolve(filename);

        if (Files.exists(filePath)) {
            Files.delete(filePath);
            logger.info("Deleted file: {} from folder: {}", filename, folder);
        }
    }

    @Override
    public boolean exists(String filename, @Nullable String folder) {
        validateFilename(filename);

        Path filePath = folder != null
                ? rootPath.resolve(folder).resolve(filename)
                : rootPath.resolve(filename);

        return Files.exists(filePath);
    }


    @Override
    public InputStream getInputStream(String filename, @Nullable String folder) throws IOException {
        Path filePath = getFilePath(filename, folder);

        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + filename);
        }

        return Files.newInputStream(filePath);
    }

    @Override
    public long getFileSize(String filename, @Nullable String folder) {
        Path filePath = getFilePath(filename, folder);

        try {
            return Files.size(filePath);
        } catch (IOException e) {
            return -1;
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size (10MB)");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Invalid file type. Only JPEG, PNG, and WebP are allowed");
        }
    }

    private String getFileExtension(@Nullable String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    private void validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }

        // Strict validation: must match UUID + extension format
        // This prevents ALL path traversal attacks by enforcing expected format
        if (!VALID_FILENAME_PATTERN.matcher(filename).matches()) {
            throw new IllegalArgumentException(
                    "Invalid filename format. Expected: UUID.extension (e.g., 550e8400-e29b-41d4-a716-446655440000.jpg)"
            );
        }
    }

    private Path getFilePath(String filename, @Nullable String folder) {
        validateFilename(filename);

        // Strict folder validation: only lowercase letters
        if (folder != null) {
            if (folder.isBlank() || !VALID_FOLDER_PATTERN.matcher(folder).matches()) {
                throw new IllegalArgumentException(
                        "Invalid folder name. Expected: lowercase letters only (e.g., instructors, gallery)"
                );
            }
        }

        return folder != null
                ? rootPath.resolve(folder).resolve(filename)
                : rootPath.resolve(filename);
    }
}
