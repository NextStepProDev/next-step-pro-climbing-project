package pl.nextsteppro.climbing.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for file storage flow:
 * upload → verify exists → stream download → delete
 *
 * Tests the complete lifecycle and verifies memory-efficient streaming.
 */
class StorageIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldHandleCompleteFileLifecycle() throws IOException {
        // Given
        LocalFileStorageService service = new LocalFileStorageService(tempDir.toString(), new ImageOptimizer());
        byte[] originalContent = TestImages.jpeg();
        MultipartFile file = new MockMultipartFile(
                "photo",
                "test-photo.jpg",
                "image/jpeg",
                originalContent
        );

        // When: Upload
        String filename = service.store(file, "gallery");

        // Then: Verify file exists
        assertTrue(service.exists(filename, "gallery"),
                "File should exist after upload");

        // When: Get file size
        long size = service.getFileSize(filename, "gallery");

        // Then: Verify size matches
        assertEquals(originalContent.length, size,
                "File size should match uploaded content");

        // When: Stream download
        InputStream inputStream = service.getInputStream(filename, "gallery");
        byte[] downloadedContent = inputStream.readAllBytes();
        inputStream.close();

        // Then: Verify content matches
        assertArrayEquals(originalContent, downloadedContent,
                "Downloaded content should match uploaded content");

        // When: Delete file
        service.delete(filename, "gallery");

        // Then: Verify file no longer exists
        assertFalse(service.exists(filename, "gallery"),
                "File should not exist after deletion");
    }

    @Test
    void shouldHandleMultipleFilesInDifferentFolders() throws IOException {
        // Given
        LocalFileStorageService service = new LocalFileStorageService(tempDir.toString(), new ImageOptimizer());

        byte[] instructorPhoto = TestImages.jpeg();
        byte[] galleryPhoto1 = TestImages.png();
        byte[] galleryPhoto2 = TestImages.webp();

        MultipartFile file1 = new MockMultipartFile("f1", "i.jpg", "image/jpeg", instructorPhoto);
        MultipartFile file2 = new MockMultipartFile("f2", "g1.png", "image/png", galleryPhoto1);
        MultipartFile file3 = new MockMultipartFile("f3", "g2.webp", "image/webp", galleryPhoto2);

        // When: Upload to different folders
        String filename1 = service.store(file1, "instructors");
        String filename2 = service.store(file2, "gallery");
        String filename3 = service.store(file3, "gallery");

        // Then: All files exist in their respective folders
        assertTrue(service.exists(filename1, "instructors"));
        assertTrue(service.exists(filename2, "gallery"));
        assertTrue(service.exists(filename3, "gallery"));

        // Files don't exist in wrong folders
        assertFalse(service.exists(filename1, "gallery"));
        assertFalse(service.exists(filename2, "instructors"));

        // When: Stream all files
        try (InputStream is1 = service.getInputStream(filename1, "instructors");
             InputStream is2 = service.getInputStream(filename2, "gallery");
             InputStream is3 = service.getInputStream(filename3, "gallery")) {

            // Then: Verify content
            assertArrayEquals(instructorPhoto, is1.readAllBytes());
            assertArrayEquals(galleryPhoto1, is2.readAllBytes());
            assertArrayEquals(galleryPhoto2, is3.readAllBytes());
        }

        // Cleanup
        service.delete(filename1, "instructors");
        service.delete(filename2, "gallery");
        service.delete(filename3, "gallery");

        assertFalse(service.exists(filename1, "instructors"));
        assertFalse(service.exists(filename2, "gallery"));
        assertFalse(service.exists(filename3, "gallery"));
    }

    @Test
    void shouldStreamLargeFileWithoutLoadingIntoMemory() throws IOException {
        // Given: Simulate large file (5MB)
        LocalFileStorageService service = new LocalFileStorageService(tempDir.toString(), new ImageOptimizer());
        // A real 3000x2000 JPEG: it has to be a decodable image now that the signature is checked,
        // and it is large enough to exercise the resize path on the way in.
        byte[] largeContent = TestImages.jpeg(3000, 2000);

        MultipartFile file = new MockMultipartFile(
                "large",
                "large.jpg",
                "image/jpeg",
                largeContent
        );

        // When: Upload and stream back
        String filename = service.store(file, "gallery");
        long fileSize = service.getFileSize(filename, "gallery");

        // Then: stored and non-empty. NOT byte-identical to the upload: an image over 1920px is
        // resized on the way in, so the stored file is deliberately smaller than what was sent.
        assertTrue(fileSize > 0);
        assertTrue(fileSize < largeContent.length);

        // When: Stream download (this should NOT load entire file into memory at once)
        try (InputStream stream = service.getInputStream(filename, "gallery")) {
            // Verify we got an InputStream (not byte[])
            assertNotNull(stream);

            // Read in chunks to simulate streaming behavior. The old version checked a synthetic
            // byte pattern, which only worked while uploads were stored verbatim; a real image is
            // re-encoded on the way in, so what is verifiable here is that the whole stored file
            // comes back through a chunked read.
            byte[] buffer = new byte[8192]; // 8KB buffer
            long totalRead = 0;
            int bytesRead;

            while ((bytesRead = stream.read(buffer)) != -1) {
                totalRead += bytesRead;
            }

            assertEquals(fileSize, totalRead,
                    "Total bytes read should match file size");
        }

        // Cleanup
        service.delete(filename, "gallery");
    }
}
