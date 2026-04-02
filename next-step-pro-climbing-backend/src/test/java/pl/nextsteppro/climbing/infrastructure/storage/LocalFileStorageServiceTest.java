package pl.nextsteppro.climbing.infrastructure.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageService service;

    @BeforeEach
    void setUp() {
        service = new LocalFileStorageService(tempDir.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        // Cleanup test files
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
        }
    }

    @Test
    void shouldStoreFileWithValidImage() throws IOException {
        // Given
        byte[] content = "fake image content".getBytes();
        MultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                content
        );

        // When
        String filename = service.store(file, "instructors");

        // Then
        assertNotNull(filename);
        assertTrue(filename.matches("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.jpg$"),
                "Filename should match UUID.jpg pattern");
        assertTrue(service.exists(filename, "instructors"));
    }

    @Test
    void shouldRejectFileExceedingSizeLimit() {
        // Given: 11MB file (limit is 10MB)
        byte[] content = new byte[11 * 1024 * 1024];
        MultipartFile file = new MockMultipartFile(
                "file",
                "large.jpg",
                "image/jpeg",
                content
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.store(file, "instructors")
        );
        assertTrue(exception.getMessage().contains("exceeds maximum"));
    }

    @Test
    void shouldRejectInvalidContentType() {
        // Given
        byte[] content = "fake content".getBytes();
        MultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.store(file, "instructors")
        );
        assertTrue(exception.getMessage().contains("Invalid file type"));
    }

    @Test
    void shouldRejectPathTraversalInFolder() {
        // Given
        byte[] content = "fake image".getBytes();
        MultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                content
        );

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> service.store(file, "../etc"));
        assertThrows(IllegalArgumentException.class,
                () -> service.store(file, "folder/subfolder"));
        assertThrows(IllegalArgumentException.class,
                () -> service.store(file, "UPPERCASE"));
    }

    @Test
    void shouldAcceptValidFolderNames() throws IOException {
        // Given
        byte[] content = "fake image".getBytes();
        MultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                content
        );

        // When & Then - should not throw
        assertDoesNotThrow(() -> service.store(file, "instructors"));
        assertDoesNotThrow(() -> service.store(file, "gallery"));
        assertDoesNotThrow(() -> service.store(file, "photos"));
    }

    @Test
    void shouldGetInputStreamForExistingFile() throws IOException {
        // Given
        byte[] content = "test image content".getBytes();
        MultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                content
        );
        String filename = service.store(file, "gallery");

        // When
        InputStream inputStream = service.getInputStream(filename, "gallery");

        // Then
        assertNotNull(inputStream);
        byte[] readContent = inputStream.readAllBytes();
        assertArrayEquals(content, readContent);
        inputStream.close();
    }

    @Test
    void shouldGetCorrectFileSize() throws IOException {
        // Given
        byte[] content = "test content with specific length".getBytes();
        MultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                content
        );
        String filename = service.store(file, "instructors");

        // When
        long size = service.getFileSize(filename, "instructors");

        // Then
        assertEquals(content.length, size);
    }

    @Test
    void shouldReturnNegativeOneForNonExistentFileSize() {
        // Given
        String fakeFilename = UUID.randomUUID() + ".jpg";

        // When
        long size = service.getFileSize(fakeFilename, "instructors");

        // Then
        assertEquals(-1, size);
    }

    @Test
    void shouldRejectInvalidFilenameFormat() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> service.exists("../../etc/passwd", "instructors"),
                "Should reject path traversal");

        assertThrows(IllegalArgumentException.class,
                () -> service.exists("invalid-name.jpg", "instructors"),
                "Should reject non-UUID filename");

        assertThrows(IllegalArgumentException.class,
                () -> service.exists("test.exe", "instructors"),
                "Should reject non-image extension");

        assertThrows(IllegalArgumentException.class,
                () -> service.exists("", "instructors"),
                "Should reject empty filename");
    }

    @Test
    void shouldAcceptValidFilenameFormats() throws IOException {
        // Given - create actual file first
        byte[] content = "test".getBytes();
        MultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", content);
        String filename = service.store(file, "instructors");

        // When & Then - should not throw
        assertDoesNotThrow(() -> service.exists(filename, "instructors"));
        assertTrue(service.exists(filename, "instructors"));
    }

    @Test
    void shouldDeleteExistingFile() throws IOException {
        // Given
        byte[] content = "test content".getBytes();
        MultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                content
        );
        String filename = service.store(file, "gallery");
        assertTrue(service.exists(filename, "gallery"));

        // When
        service.delete(filename, "gallery");

        // Then
        assertFalse(service.exists(filename, "gallery"));
    }

    @Test
    void shouldNotThrowWhenDeletingNonExistentFile() {
        // Given
        String fakeFilename = UUID.randomUUID() + ".jpg";

        // When & Then - should not throw
        assertDoesNotThrow(() -> service.delete(fakeFilename, "instructors"));
    }

    @Test
    void shouldHandleFilesWithoutFolder() throws IOException {
        // Given
        byte[] content = "test content".getBytes();
        MultipartFile file = new MockMultipartFile(
                "file",
                "test.webp",
                "image/webp",
                content
        );

        // When
        String filename = service.store(file, null);

        // Then
        assertTrue(service.exists(filename, null));
        long size = service.getFileSize(filename, null);
        assertEquals(content.length, size);
    }

    @Test
    void shouldSupportAllAllowedImageFormats() throws IOException {
        // Given & When & Then
        String[] formats = {"image/jpeg", "image/png", "image/webp"};
        String[] extensions = {".jpg", ".png", ".webp"};

        for (int i = 0; i < formats.length; i++) {
            byte[] content = ("test " + formats[i]).getBytes();
            String ext = extensions[i];
            MultipartFile file = new MockMultipartFile(
                    "file",
                    "test" + ext,
                    formats[i],
                    content
            );

            String filename = service.store(file, "gallery");
            assertTrue(filename.endsWith(ext), "Should preserve extension: " + ext);
            assertTrue(service.exists(filename, "gallery"));
        }
    }
}
