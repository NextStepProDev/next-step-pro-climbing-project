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
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    // Documents (training materials): images + PDF
    private static final List<String> ALLOWED_DOCUMENT_TYPES = List.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            PDF_CONTENT_TYPE
    );

    // Strict filename validation: UUID + allowed extension (images + pdf for documents).
    // Prevents path traversal attacks by enforcing expected format
    private static final Pattern VALID_FILENAME_PATTERN = Pattern.compile(
            "^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(jpg|jpeg|png|webp|pdf)$",
            Pattern.CASE_INSENSITIVE
    );

    // Strict folder validation: only lowercase letters (instructors, gallery)
    private static final Pattern VALID_FOLDER_PATTERN = Pattern.compile(
            "^[a-z]+$"
    );

    private final Path rootPath;
    private final ImageOptimizer imageOptimizer;

    public LocalFileStorageService(@Value("${app.storage.root:/app/uploads}") String rootPath,
                                   ImageOptimizer imageOptimizer) {
        this.rootPath = Paths.get(rootPath);
        this.imageOptimizer = imageOptimizer;
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

        // Optimize image (resize/compress if needed)
        var optimized = imageOptimizer.optimize(file.getInputStream(), extension);
        String finalExtension = optimized.extension();
        String filename = UUID.randomUUID() + finalExtension;

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
        Files.copy(optimized.inputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        logger.info("Stored file: {} in folder: {}", filename, folder);
        return filename;
    }

    @Override
    public String storeDocument(MultipartFile file, @Nullable String folder) throws IOException {
        validateDocument(file);
        validateFolderName(folder);

        String contentType = file.getContentType();
        String filename;
        InputStream toWrite;
        if (contentType != null && contentType.equalsIgnoreCase(PDF_CONTENT_TYPE)) {
            // PDFs are stored as-is — the image optimizer only understands images
            filename = UUID.randomUUID() + ".pdf";
            toWrite = file.getInputStream();
        } else {
            String extension = getFileExtension(file.getOriginalFilename());
            var optimized = imageOptimizer.optimize(file.getInputStream(), extension);
            filename = UUID.randomUUID() + optimized.extension();
            toWrite = optimized.inputStream();
        }

        Path targetPath = folder != null
                ? rootPath.resolve(folder).resolve(filename)
                : rootPath.resolve(filename);
        Files.createDirectories(targetPath.getParent());
        Files.copy(toWrite, targetPath, StandardCopyOption.REPLACE_EXISTING);

        logger.info("Stored document: {} in folder: {}", filename, folder);
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

    private void validateDocument(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size (10MB)");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_DOCUMENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Invalid file type. Only PDF, JPEG, PNG, and WebP are allowed");
        }
    }

    private void validateFolderName(@Nullable String folder) {
        if (folder != null && (folder.isBlank() || !VALID_FOLDER_PATTERN.matcher(folder).matches())) {
            throw new IllegalArgumentException(
                    "Invalid folder name. Expected: lowercase letters only (e.g., instructors, gallery)"
            );
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
