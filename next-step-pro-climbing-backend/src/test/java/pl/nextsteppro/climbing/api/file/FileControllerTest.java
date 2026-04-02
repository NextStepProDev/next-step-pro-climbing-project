package pl.nextsteppro.climbing.api.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private FileStorageService fileStorageService;

    private FileController controller;

    @BeforeEach
    void setUp() {
        controller = new FileController(fileStorageService);
    }

    @Test
    void shouldServeInstructorPhotoWithStreaming() throws Exception {
        // Given
        String filename = UUID.randomUUID() + ".jpg";
        byte[] imageContent = "fake jpeg content".getBytes();
        InputStream inputStream = new ByteArrayInputStream(imageContent);

        when(fileStorageService.exists(filename, "instructors")).thenReturn(true);
        when(fileStorageService.getInputStream(filename, "instructors")).thenReturn(inputStream);
        when(fileStorageService.getFileSize(filename, "instructors")).thenReturn((long) imageContent.length);

        // When
        ResponseEntity<Resource> response = controller.getInstructorPhoto(filename);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof InputStreamResource);
        assertEquals(imageContent.length, response.getHeaders().getContentLength());
        assertEquals("image/jpeg", response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().getCacheControl().contains("max-age=604800"));
        assertTrue(response.getHeaders().getCacheControl().contains("public"));

        // Verify streaming methods were called (not byte[] load)
        verify(fileStorageService).getInputStream(filename, "instructors");
        verify(fileStorageService).getFileSize(filename, "instructors");
    }

    @Test
    void shouldServeGalleryPhotoWithStreaming() throws Exception {
        // Given
        String filename = UUID.randomUUID() + ".png";
        byte[] imageContent = "fake png content".getBytes();
        InputStream inputStream = new ByteArrayInputStream(imageContent);

        when(fileStorageService.exists(filename, "gallery")).thenReturn(true);
        when(fileStorageService.getInputStream(filename, "gallery")).thenReturn(inputStream);
        when(fileStorageService.getFileSize(filename, "gallery")).thenReturn((long) imageContent.length);

        // When
        ResponseEntity<Resource> response = controller.getGalleryPhoto(filename);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("image/png", response.getHeaders().getContentType().toString());
        assertEquals(imageContent.length, response.getHeaders().getContentLength());

        verify(fileStorageService).getInputStream(filename, "gallery");
        verify(fileStorageService).getFileSize(filename, "gallery");
    }

    @Test
    void shouldReturn404WhenFileDoesNotExist() throws Exception {
        // Given
        String filename = UUID.randomUUID() + ".jpg";
        when(fileStorageService.exists(filename, "instructors")).thenReturn(false);

        // When
        ResponseEntity<Resource> response = controller.getInstructorPhoto(filename);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(fileStorageService, never()).getInputStream(any(), any());
    }

    @Test
    void shouldHandleWebPContentType() throws Exception {
        // Given
        String filename = UUID.randomUUID() + ".webp";
        byte[] imageContent = "fake webp content".getBytes();
        InputStream inputStream = new ByteArrayInputStream(imageContent);

        when(fileStorageService.exists(filename, "gallery")).thenReturn(true);
        when(fileStorageService.getInputStream(filename, "gallery")).thenReturn(inputStream);
        when(fileStorageService.getFileSize(filename, "gallery")).thenReturn((long) imageContent.length);

        // When
        ResponseEntity<Resource> response = controller.getGalleryPhoto(filename);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("image/webp", response.getHeaders().getContentType().toString());
    }

    @Test
    void shouldHandleJpegExtension() throws Exception {
        // Given
        String filename = UUID.randomUUID() + ".jpeg";
        byte[] imageContent = "fake jpeg content".getBytes();
        InputStream inputStream = new ByteArrayInputStream(imageContent);

        when(fileStorageService.exists(filename, "instructors")).thenReturn(true);
        when(fileStorageService.getInputStream(filename, "instructors")).thenReturn(inputStream);
        when(fileStorageService.getFileSize(filename, "instructors")).thenReturn((long) imageContent.length);

        // When
        ResponseEntity<Resource> response = controller.getInstructorPhoto(filename);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("image/jpeg", response.getHeaders().getContentType().toString());
    }

    @Test
    void shouldSetCorrectCacheHeaders() throws Exception {
        // Given
        String filename = UUID.randomUUID() + ".jpg";
        byte[] imageContent = "content".getBytes();
        InputStream inputStream = new ByteArrayInputStream(imageContent);

        when(fileStorageService.exists(filename, "instructors")).thenReturn(true);
        when(fileStorageService.getInputStream(filename, "instructors")).thenReturn(inputStream);
        when(fileStorageService.getFileSize(filename, "instructors")).thenReturn((long) imageContent.length);

        // When
        ResponseEntity<Resource> response = controller.getInstructorPhoto(filename);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String cacheControl = response.getHeaders().getCacheControl();
        assertTrue(cacheControl.contains("max-age=604800"));
        assertTrue(cacheControl.contains("public"));
    }

    @Test
    void shouldSetInlineContentDisposition() throws Exception {
        // Given
        String filename = UUID.randomUUID() + ".jpg";
        byte[] imageContent = "content".getBytes();
        InputStream inputStream = new ByteArrayInputStream(imageContent);

        when(fileStorageService.exists(filename, "gallery")).thenReturn(true);
        when(fileStorageService.getInputStream(filename, "gallery")).thenReturn(inputStream);
        when(fileStorageService.getFileSize(filename, "gallery")).thenReturn((long) imageContent.length);

        // When
        ResponseEntity<Resource> response = controller.getGalleryPhoto(filename);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String contentDisposition = response.getHeaders().getContentDisposition().toString();
        assertTrue(contentDisposition.contains("inline"));
        assertTrue(contentDisposition.contains(filename));
    }
}
