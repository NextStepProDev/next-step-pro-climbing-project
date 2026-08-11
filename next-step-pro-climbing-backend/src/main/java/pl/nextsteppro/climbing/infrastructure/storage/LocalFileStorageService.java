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

    /**
     * Comment attachments, narrower than documents by one format: this JVM ships no WebP reader,
     * so a WebP could be neither re-encoded (no EXIF strip) nor measured (no dimensions for the
     * UI to reserve space with). Accepting a format we cannot actually inspect would mean storing
     * somebody's photo exactly as it arrived — location metadata included.
     */
    private static final List<String> ALLOWED_ATTACHMENT_TYPES = List.of(
            "image/jpeg",
            "image/png",
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

        requireSignature(file, extension, ALLOWED_CONTENT_TYPES);

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
        return writeDocument(file, folder).filename();
    }

    @Override
    public StoredFile storeAttachment(MultipartFile file, String folder, long maxBytes) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size ("
                    + (maxBytes / (1024 * 1024)) + "MB)");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_ATTACHMENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Invalid file type. Only PDF, JPEG and PNG are allowed");
        }
        validateFolderName(folder);

        String extension = getFileExtension(file.getOriginalFilename());
        FileSignatures.Format format = requireSignature(file, extension, ALLOWED_ATTACHMENT_TYPES);

        String filename;
        InputStream toWrite;
        Integer width = null;
        Integer height = null;

        if (format == FileSignatures.Format.PDF) {
            // Stored byte for byte — a PDF cannot be re-encoded, so it keeps its own metadata.
            // Said plainly in the privacy policy rather than quietly assumed.
            filename = UUID.randomUUID() + ".pdf";
            toWrite = file.getInputStream();
        } else {
            var optimized = imageOptimizer.reencodeAsJpeg(file.getInputStream());
            filename = UUID.randomUUID() + optimized.extension();
            toWrite = optimized.inputStream();
            width = optimized.width();
            height = optimized.height();
        }

        Path targetPath = rootPath.resolve(folder).resolve(filename);
        Files.createDirectories(targetPath.getParent());
        long written = Files.copy(toWrite, targetPath, StandardCopyOption.REPLACE_EXISTING);

        logger.info("Stored attachment: {} in folder: {}", filename, folder);
        return new StoredFile(filename, contentTypeForExtension(filename), written, width, height);
    }

    private StoredFile writeDocument(MultipartFile file, @Nullable String folder) throws IOException {
        String extension = getFileExtension(file.getOriginalFilename());
        FileSignatures.Format format = requireSignature(file, extension, ALLOWED_DOCUMENT_TYPES);

        String filename;
        InputStream toWrite;

        if (format == FileSignatures.Format.PDF) {
            // PDFs are stored as-is — the image optimizer only understands images
            filename = UUID.randomUUID() + ".pdf";
            toWrite = file.getInputStream();
        } else {
            var optimized = imageOptimizer.optimize(file.getInputStream(), extension);
            filename = UUID.randomUUID() + optimized.extension();
            toWrite = optimized.inputStream();
        }

        Path targetPath = folder != null
                ? rootPath.resolve(folder).resolve(filename)
                : rootPath.resolve(filename);
        Files.createDirectories(targetPath.getParent());
        long written = Files.copy(toWrite, targetPath, StandardCopyOption.REPLACE_EXISTING);

        logger.info("Stored document: {} in folder: {}", filename, folder);
        return new StoredFile(filename, contentTypeForExtension(filename), written, null, null);
    }

    /**
     * The declared content type and the extension are both written by the client; the leading bytes
     * are not. Rejecting a mismatch is what stops an arbitrary payload from being stored as
     * {@code .pdf} and later served back described as a PDF.
     *
     * <p>The extension is only compared when the upload has one — an extensionless file with
     * honest bytes and an honest content type was accepted before this check existed and still is.
     */
    private FileSignatures.Format requireSignature(MultipartFile file, String extension,
                                                   List<String> allowedContentTypes) throws IOException {
        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = FileSignatures.readHeader(in);
        }
        FileSignatures.Format format = FileSignatures.sniff(header);
        if (format == null || !allowedContentTypes.contains(format.contentType())) {
            throw new IllegalArgumentException("File content is not a supported image or PDF");
        }
        if (!format.matchesContentType(file.getContentType())) {
            throw new IllegalArgumentException("File content does not match its declared type");
        }
        if (!extension.isEmpty() && !format.matchesExtension(extension)) {
            throw new IllegalArgumentException("File content does not match its extension");
        }
        return format;
    }

    private static String contentTypeForExtension(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".pdf")) return PDF_CONTENT_TYPE;
        return "image/jpeg";
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

    @Override
    public List<String> listFilenames(@Nullable String folder) {
        validateFolderName(folder);
        Path folderPath = folder != null ? rootPath.resolve(folder) : rootPath;
        if (!Files.isDirectory(folderPath)) {
            return List.of();
        }
        try (var stream = Files.list(folderPath)) {
            return stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .toList();
        } catch (IOException e) {
            logger.warn("Failed to list files in folder {}", folder, e);
            return List.of();
        }
    }

    @Override
    public long getLastModifiedMillis(String filename, @Nullable String folder) {
        Path filePath = getFilePath(filename, folder);
        try {
            return Files.getLastModifiedTime(filePath).toMillis();
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
