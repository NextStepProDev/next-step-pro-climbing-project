package pl.nextsteppro.climbing.infrastructure.storage;

import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

public interface FileStorageService {

    /**
     * Store a file in the specified folder
     * @param file the file to store
     * @param folder optional subfolder (e.g., "instructors", "gallery")
     * @return the generated filename
     * @throws IOException if file cannot be stored
     */
    String store(MultipartFile file, @Nullable String folder) throws IOException;

    /**
     * Store a document (PDF or image). Unlike {@link #store}, PDFs are written as-is (no image
     * optimization) and the PDF content type is accepted. Images are still optimized.
     * @return the generated filename (UUID + extension)
     */
    String storeDocument(MultipartFile file, @Nullable String folder) throws IOException;

    /**
     * Store an attachment somebody uploaded about themselves (comment attachments). Differs from
     * {@link #storeDocument} in two ways that matter:
     *
     * <ul>
     *   <li>images are <b>always</b> re-encoded to JPEG, which strips EXIF — a phone photo carries
     *       the GPS coordinates of wherever it was taken, and nobody attaching a picture of a route
     *       means to publish that. JPEG rather than the input format because this JVM has no WebP
     *       reader, so a WebP could be neither stripped nor measured;</li>
     *   <li>the caller sets its own size ceiling, lower than the 10 MB for coach materials, because
     *       these accumulate for a year.</li>
     * </ul>
     *
     * @return what was actually written — content type and dimensions come from the stored bytes,
     *   never from the request.
     */
    StoredFile storeAttachment(MultipartFile file, String folder, long maxBytes) throws IOException;

    /** Dimensions are null for PDFs. */
    record StoredFile(String filename, String mimeType, long sizeBytes,
                      @Nullable Integer width, @Nullable Integer height) {}

    /**
     * Delete a file from the specified folder
     * @param filename the filename to delete
     * @param folder optional subfolder
     * @throws IOException if file cannot be deleted
     */
    void delete(String filename, @Nullable String folder) throws IOException;

    /**
     * Check if a file exists
     * @param filename the filename to check
     * @param folder optional subfolder
     * @return true if file exists
     */
    boolean exists(String filename, @Nullable String folder);

    /**
     * Get an InputStream for a file (memory-efficient for large files)
     * @param filename the filename to load
     * @param folder optional subfolder
     * @return InputStream for the file
     * @throws IOException if file cannot be read
     */
    InputStream getInputStream(String filename, @Nullable String folder) throws IOException;

    /**
     * Get the file size in bytes
     * @param filename the filename to check
     * @param folder optional subfolder
     * @return file size in bytes, or -1 if file doesn't exist
     */
    long getFileSize(String filename, @Nullable String folder);

    /** List filenames of regular files in a folder (empty if the folder does not exist). */
    java.util.List<String> listFilenames(@Nullable String folder);

    /** Last-modified time in epoch millis, or -1 if the file does not exist. */
    long getLastModifiedMillis(String filename, @Nullable String folder);
}
