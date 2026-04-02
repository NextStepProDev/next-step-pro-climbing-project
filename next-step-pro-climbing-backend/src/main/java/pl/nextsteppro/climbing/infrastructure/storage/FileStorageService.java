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
     * Load a file as byte array
     * @deprecated Use {@link #getInputStream(String, String)} for better memory efficiency
     * @param filename the filename to load
     * @param folder optional subfolder
     * @return file content as bytes
     * @throws IOException if file cannot be read
     */
    @Deprecated
    byte[] load(String filename, @Nullable String folder) throws IOException;

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
}
