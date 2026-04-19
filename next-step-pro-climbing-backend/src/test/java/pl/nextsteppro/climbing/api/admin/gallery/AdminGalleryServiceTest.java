package pl.nextsteppro.climbing.api.admin.gallery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.admin.gallery.AdminGalleryDtos.*;
import pl.nextsteppro.climbing.domain.gallery.Album;
import pl.nextsteppro.climbing.domain.gallery.AlbumRepository;
import pl.nextsteppro.climbing.domain.gallery.AlbumSummaryProjection;
import pl.nextsteppro.climbing.domain.gallery.Photo;
import pl.nextsteppro.climbing.domain.gallery.PhotoRepository;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for AdminGalleryService - manages albums and photos with file storage.
 *
 * Test coverage:
 * - Album CRUD operations
 * - Photo upload with file storage
 * - Photo update (caption, display order)
 * - Photo deletion with cleanup
 * - Album deletion with CASCADE photo cleanup
 * - Empty albums handling
 * - Edge cases: not found, file storage failures, null values
 */
@ExtendWith(MockitoExtension.class)
class AdminGalleryServiceTest {

    @Mock
    private AlbumRepository albumRepository;
    @Mock
    private PhotoRepository photoRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private MultipartFile mockFile;

    private AdminGalleryService adminGalleryService;
    private Album testAlbum;
    private Photo testPhoto;
    private UUID albumId;
    private UUID photoId;
    private static final String BASE_URL = "https://nextsteppro.pl";

    @BeforeEach
    void setUp() {
        adminGalleryService = new AdminGalleryService(
            albumRepository,
            photoRepository,
            fileStorageService,
            BASE_URL
        );

        albumId = UUID.randomUUID();
        photoId = UUID.randomUUID();

        testAlbum = new Album("Test Album");
        setEntityIdViaReflection(testAlbum, albumId);
        testAlbum.setDescription("Test album description");

        testPhoto = new Photo(testAlbum, "photo123.jpg");
        setEntityIdViaReflection(testPhoto, photoId);
        testPhoto.setCaption("Test photo");
    }

    // ========== ALBUM CREATION TESTS ==========

    @Test
    void shouldCreateAlbumSuccessfully() {
        // Given
        CreateAlbumRequest request = new CreateAlbumRequest("New Album", "New description");

        when(albumRepository.findMinDisplayOrder()).thenReturn(Optional.of(2));
        when(albumRepository.save(any(Album.class))).thenAnswer(inv -> {
            Album album = inv.getArgument(0);
            setEntityIdViaReflection(album, UUID.randomUUID());
            return album;
        });

        // When
        AlbumAdminDto result = adminGalleryService.createAlbum(request);

        // Then
        assertNotNull(result);
        assertEquals("New Album", result.name());
        assertEquals("New description", result.description());
        assertEquals(0, result.photoCount());

        ArgumentCaptor<Album> captor = ArgumentCaptor.forClass(Album.class);
        verify(albumRepository).save(captor.capture());

        Album saved = captor.getValue();
        assertEquals("New Album", saved.getName());
        assertEquals("New description", saved.getDescription());
    }

    @Test
    void shouldCreateAlbumWithNullDescription() {
        // Given
        CreateAlbumRequest request = new CreateAlbumRequest("Album", null);

        when(albumRepository.findMinDisplayOrder()).thenReturn(Optional.empty());
        when(albumRepository.save(any(Album.class))).thenAnswer(inv -> {
            Album album = inv.getArgument(0);
            setEntityIdViaReflection(album, UUID.randomUUID());
            return album;
        });

        // When
        AlbumAdminDto result = adminGalleryService.createAlbum(request);

        // Then
        assertNotNull(result);
        assertNull(result.description());
    }

    // ========== GET ALBUM TESTS ==========

    @Test
    void shouldGetAlbumById() {
        // Given
        when(albumRepository.findById(albumId)).thenReturn(Optional.of(testAlbum));
        when(photoRepository.findByAlbumIdOrderByDisplayOrderAscCreatedAtAsc(albumId))
            .thenReturn(List.of(testPhoto));

        // When
        AlbumDetailAdminDto result = adminGalleryService.getAlbum(albumId);

        // Then
        assertNotNull(result);
        assertEquals(albumId, result.id());
        assertEquals("Test Album", result.name());
        assertEquals("Test album description", result.description());
        assertEquals(1, result.photos().size());

        PhotoAdminDto photo = result.photos().get(0);
        assertEquals(photoId, photo.id());
        assertEquals("photo123.jpg", photo.filename());
    }

    @Test
    void shouldThrowExceptionWhenAlbumNotFound() {
        // Given
        when(albumRepository.findById(albumId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> adminGalleryService.getAlbum(albumId)
        );
        assertEquals("Album not found", exception.getMessage());
    }

    @Test
    void shouldGetAllAlbumsOrderedByCreatedDate() {
        // Given
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Instant now = Instant.now();

        when(albumRepository.findAllAlbumSummaries()).thenReturn(List.of(
            mockProjection(id2, "Album 2", null, now, now, null, 0L),
            mockProjection(id1, "Album 1", null, now, now, null, 0L)
        ));

        // When
        List<AlbumAdminDto> result = adminGalleryService.getAllAlbums();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Album 2", result.get(0).name());
        assertEquals("Album 1", result.get(1).name());
    }

    @Test
    void shouldGetAlbumWithCoverPhoto() {
        // Given
        String photoFilename = "photo123.jpg";
        Instant now = Instant.now();

        when(albumRepository.findAllAlbumSummaries()).thenReturn(List.of(
            mockProjection(albumId, "Test Album", null, now, now, photoFilename, 5L)
        ));

        // When
        List<AlbumAdminDto> result = adminGalleryService.getAllAlbums();

        // Then
        assertEquals(1, result.size());
        AlbumAdminDto album = result.get(0);
        assertEquals(5L, album.photoCount());
        assertTrue(album.thumbnailUrl().contains(photoFilename));
    }

    private AlbumSummaryProjection mockProjection(UUID id, String name, String description,
            Instant createdAt, Instant updatedAt, String firstPhotoFilename, long photoCount) {
        return new AlbumSummaryProjection() {
            @Override public UUID getId() { return id; }
            @Override public String getName() { return name; }
            @Override public String getDescription() { return description; }
            @Override public Instant getCreatedAt() { return createdAt; }
            @Override public Instant getUpdatedAt() { return updatedAt; }
            @Override public int getDisplayOrder() { return 0; }
            @Override public String getFirstPhotoFilename() { return firstPhotoFilename; }
            @Override public Float getThumbnailFocalPointX() { return null; }
            @Override public Float getThumbnailFocalPointY() { return null; }
            @Override public Long getPhotoCount() { return photoCount; }
            @Override public boolean isPublished() { return true; }
            @Override public java.time.Instant getPublishedAt() { return null; }
        };
    }

    // ========== UPDATE ALBUM TESTS ==========

    @Test
    void shouldUpdateAlbumAllFields() {
        // Given
        UpdateAlbumRequest request = new UpdateAlbumRequest("Updated Name", "Updated Description");

        when(albumRepository.findById(albumId)).thenReturn(Optional.of(testAlbum));
        when(albumRepository.save(any(Album.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        AlbumAdminDto result = adminGalleryService.updateAlbum(albumId, request);

        // Then
        assertNotNull(result);
        assertEquals("Updated Name", result.name());
        assertEquals("Updated Description", result.description());

        verify(albumRepository).save(testAlbum);
    }

    @Test
    void shouldUpdateAlbumPartialFields() {
        // Given
        UpdateAlbumRequest request = new UpdateAlbumRequest("Updated Name", null);

        when(albumRepository.findById(albumId)).thenReturn(Optional.of(testAlbum));
        when(albumRepository.save(any(Album.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        AlbumAdminDto result = adminGalleryService.updateAlbum(albumId, request);

        // Then
        assertEquals("Updated Name", result.name());
        assertEquals("Test album description", result.description()); // Not updated

        verify(albumRepository).save(testAlbum);
    }

    @Test
    void shouldThrowExceptionWhenUpdatingNonExistentAlbum() {
        // Given
        UpdateAlbumRequest request = new UpdateAlbumRequest("Name", "Description");

        when(albumRepository.findById(albumId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> adminGalleryService.updateAlbum(albumId, request)
        );
        assertEquals("Album not found", exception.getMessage());
    }

    // ========== DELETE ALBUM TESTS ==========

    @Test
    void shouldDeleteAlbumWithoutPhotos() throws IOException {
        // Given
        when(albumRepository.findById(albumId)).thenReturn(Optional.of(testAlbum));
        when(photoRepository.findByAlbumId(albumId)).thenReturn(List.of());

        // When
        adminGalleryService.deleteAlbum(albumId);

        // Then
        verify(albumRepository).delete(testAlbum);
        verify(fileStorageService, never()).delete(anyString(), anyString());
    }

    @Test
    void shouldDeleteAlbumWithPhotosAndCleanupFiles() throws IOException {
        // Given
        Photo photo1 = new Photo(testAlbum, "photo1.jpg");
        Photo photo2 = new Photo(testAlbum, "photo2.jpg");

        when(albumRepository.findById(albumId)).thenReturn(Optional.of(testAlbum));
        when(photoRepository.findByAlbumId(albumId)).thenReturn(List.of(photo1, photo2));
        doNothing().when(fileStorageService).delete(anyString(), eq("gallery"));

        // When
        adminGalleryService.deleteAlbum(albumId);

        // Then
        verify(fileStorageService).delete("photo1.jpg", "gallery");
        verify(fileStorageService).delete("photo2.jpg", "gallery");
        verify(albumRepository).delete(testAlbum);
    }

    @Test
    void shouldDeleteAlbumEvenIfPhotoFileDeleteFails() throws IOException {
        // Given
        Photo photo1 = new Photo(testAlbum, "photo1.jpg");

        when(albumRepository.findById(albumId)).thenReturn(Optional.of(testAlbum));
        when(photoRepository.findByAlbumId(albumId)).thenReturn(List.of(photo1));
        doThrow(new IOException("File not found")).when(fileStorageService).delete("photo1.jpg", "gallery");

        // When
        adminGalleryService.deleteAlbum(albumId);

        // Then
        verify(fileStorageService).delete("photo1.jpg", "gallery");
        verify(albumRepository).delete(testAlbum); // Still deletes album
    }

    @Test
    void shouldThrowExceptionWhenDeletingNonExistentAlbum() {
        // Given
        when(albumRepository.findById(albumId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> adminGalleryService.deleteAlbum(albumId)
        );
        assertEquals("Album not found", exception.getMessage());
    }

    // ========== UPLOAD PHOTO TESTS ==========

    @Test
    void shouldUploadPhotoSuccessfully() throws IOException {
        // Given
        when(albumRepository.findById(albumId)).thenReturn(Optional.of(testAlbum));
        when(fileStorageService.store(mockFile, "gallery")).thenReturn("new-photo.jpg");
        when(photoRepository.save(any(Photo.class))).thenAnswer(inv -> {
            Photo photo = inv.getArgument(0);
            setEntityIdViaReflection(photo, UUID.randomUUID());
            return photo;
        });

        // When
        UploadPhotoResponse result = adminGalleryService.uploadPhoto(albumId, mockFile, "Test caption");

        // Then
        assertNotNull(result);
        assertEquals("new-photo.jpg", result.filename());
        assertTrue(result.url().contains("new-photo.jpg"));

        ArgumentCaptor<Photo> captor = ArgumentCaptor.forClass(Photo.class);
        verify(photoRepository).save(captor.capture());

        Photo saved = captor.getValue();
        assertEquals("new-photo.jpg", saved.getFilename());
        assertEquals("Test caption", saved.getCaption());

        verify(fileStorageService).store(mockFile, "gallery");
    }

    @Test
    void shouldUploadPhotoWithNullCaption() throws IOException {
        // Given
        when(albumRepository.findById(albumId)).thenReturn(Optional.of(testAlbum));
        when(fileStorageService.store(mockFile, "gallery")).thenReturn("photo.jpg");
        when(photoRepository.save(any(Photo.class))).thenAnswer(inv -> {
            Photo photo = inv.getArgument(0);
            setEntityIdViaReflection(photo, UUID.randomUUID());
            return photo;
        });

        // When
        UploadPhotoResponse result = adminGalleryService.uploadPhoto(albumId, mockFile, null);

        // Then
        assertNotNull(result);

        ArgumentCaptor<Photo> captor = ArgumentCaptor.forClass(Photo.class);
        verify(photoRepository).save(captor.capture());

        Photo saved = captor.getValue();
        assertNull(saved.getCaption());
    }

    @Test
    void shouldThrowExceptionWhenUploadingToNonExistentAlbum() {
        // Given
        when(albumRepository.findById(albumId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> adminGalleryService.uploadPhoto(albumId, mockFile, null)
        );
        assertEquals("Album not found", exception.getMessage());
    }

    @Test
    void shouldPropagateIOExceptionWhenPhotoStorageFails() throws IOException {
        // Given
        when(albumRepository.findById(albumId)).thenReturn(Optional.of(testAlbum));
        when(fileStorageService.store(mockFile, "gallery")).thenThrow(new IOException("Storage error"));

        // When & Then
        assertThrows(IOException.class, () -> adminGalleryService.uploadPhoto(albumId, mockFile, null));

        verify(photoRepository, never()).save(any(Photo.class));
    }

    // ========== UPDATE PHOTO TESTS ==========

    @Test
    void shouldUpdatePhotoCaptionAndDisplayOrder() {
        // Given
        UpdatePhotoRequest request = new UpdatePhotoRequest("New caption", 5, null, null);

        when(photoRepository.findById(photoId)).thenReturn(Optional.of(testPhoto));
        when(photoRepository.save(any(Photo.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        adminGalleryService.updatePhoto(photoId, request);

        // Then
        assertEquals("New caption", testPhoto.getCaption());
        assertEquals(5, testPhoto.getDisplayOrder());

        verify(photoRepository).save(testPhoto);
    }

    @Test
    void shouldUpdatePhotoPartialFields() {
        // Given
        testPhoto.setDisplayOrder(3);
        UpdatePhotoRequest request = new UpdatePhotoRequest("New caption", null, null, null);

        when(photoRepository.findById(photoId)).thenReturn(Optional.of(testPhoto));
        when(photoRepository.save(any(Photo.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        adminGalleryService.updatePhoto(photoId, request);

        // Then
        assertEquals("New caption", testPhoto.getCaption());
        assertEquals(3, testPhoto.getDisplayOrder()); // Not updated

        verify(photoRepository).save(testPhoto);
    }

    @Test
    void shouldUpdateDisplayOrderOnly() {
        // Given
        UpdatePhotoRequest request = new UpdatePhotoRequest(null, 10, null, null);

        when(photoRepository.findById(photoId)).thenReturn(Optional.of(testPhoto));
        when(photoRepository.save(any(Photo.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        adminGalleryService.updatePhoto(photoId, request);

        // Then
        assertEquals(10, testPhoto.getDisplayOrder());
        assertEquals("Test photo", testPhoto.getCaption()); // Not updated

        verify(photoRepository).save(testPhoto);
    }

    @Test
    void shouldThrowExceptionWhenUpdatingNonExistentPhoto() {
        // Given
        UpdatePhotoRequest request = new UpdatePhotoRequest("Caption", 1, null, null);

        when(photoRepository.findById(photoId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> adminGalleryService.updatePhoto(photoId, request)
        );
        assertEquals("Photo not found", exception.getMessage());
    }

    // ========== DELETE PHOTO TESTS ==========

    @Test
    void shouldDeletePhotoSuccessfully() throws IOException {
        // Given
        when(photoRepository.findById(photoId)).thenReturn(Optional.of(testPhoto));
        doNothing().when(fileStorageService).delete("photo123.jpg", "gallery");

        // When
        adminGalleryService.deletePhoto(photoId);

        // Then
        verify(fileStorageService).delete("photo123.jpg", "gallery");
        verify(photoRepository).delete(testPhoto);
    }

    @Test
    void shouldThrowExceptionWhenDeletingNonExistentPhoto() {
        // Given
        when(photoRepository.findById(photoId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> adminGalleryService.deletePhoto(photoId)
        );
        assertEquals("Photo not found", exception.getMessage());
    }

    @Test
    void shouldPropagateIOExceptionWhenPhotoDeleteFails() throws IOException {
        // Given
        when(photoRepository.findById(photoId)).thenReturn(Optional.of(testPhoto));
        doThrow(new IOException("Delete failed")).when(fileStorageService).delete("photo123.jpg", "gallery");

        // When & Then
        assertThrows(IOException.class, () -> adminGalleryService.deletePhoto(photoId));

        verify(photoRepository, never()).delete(any(Photo.class));
    }

    // ========== PHOTO URL BUILDING TESTS ==========

    @Test
    void shouldBuildCorrectPhotoUrl() {
        // Given
        when(albumRepository.findById(albumId)).thenReturn(Optional.of(testAlbum));
        when(photoRepository.findByAlbumIdOrderByDisplayOrderAscCreatedAtAsc(albumId))
            .thenReturn(List.of(testPhoto));

        // When
        AlbumDetailAdminDto result = adminGalleryService.getAlbum(albumId);

        // Then
        PhotoAdminDto photo = result.photos().get(0);
        assertTrue(photo.url().startsWith(BASE_URL));
        assertTrue(photo.url().endsWith("photo123.jpg"));
        assertTrue(photo.url().contains("/api/files/gallery/"));
        assertEquals(BASE_URL + "/api/files/gallery/photo123.jpg", photo.url());
    }

    @Test
    void shouldHandleEmptyAlbum() {
        // Given
        when(albumRepository.findById(albumId)).thenReturn(Optional.of(testAlbum));
        when(photoRepository.findByAlbumIdOrderByDisplayOrderAscCreatedAtAsc(albumId))
            .thenReturn(List.of());

        // When
        AlbumDetailAdminDto result = adminGalleryService.getAlbum(albumId);

        // Then
        assertNotNull(result);
        assertTrue(result.photos().isEmpty());
    }

    @Test
    void shouldOrderPhotosByDisplayOrderThenCreatedAt() {
        // Given
        Photo photo1 = new Photo(testAlbum, "photo1.jpg");
        setEntityIdViaReflection(photo1, UUID.randomUUID());
        photo1.setDisplayOrder(2);

        Photo photo2 = new Photo(testAlbum, "photo2.jpg");
        setEntityIdViaReflection(photo2, UUID.randomUUID());
        photo2.setDisplayOrder(1);

        Photo photo3 = new Photo(testAlbum, "photo3.jpg");
        setEntityIdViaReflection(photo3, UUID.randomUUID());
        photo3.setDisplayOrder(1);

        when(albumRepository.findById(albumId)).thenReturn(Optional.of(testAlbum));
        when(photoRepository.findByAlbumIdOrderByDisplayOrderAscCreatedAtAsc(albumId))
            .thenReturn(List.of(photo2, photo3, photo1)); // Ordered by display order, then created

        // When
        AlbumDetailAdminDto result = adminGalleryService.getAlbum(albumId);

        // Then
        assertEquals(3, result.photos().size());
        assertEquals("photo2.jpg", result.photos().get(0).filename());
        assertEquals("photo3.jpg", result.photos().get(1).filename());
        assertEquals("photo1.jpg", result.photos().get(2).filename());
    }

    // ========== HELPER METHODS ==========

    private void setEntityIdViaReflection(Object entity, UUID id) {
        try {
            var idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);

            var createdAtField = entity.getClass().getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(entity, Instant.now());

            var updatedAtField = entity.getClass().getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(entity, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException("Failed to set entity ID", e);
        }
    }
}
