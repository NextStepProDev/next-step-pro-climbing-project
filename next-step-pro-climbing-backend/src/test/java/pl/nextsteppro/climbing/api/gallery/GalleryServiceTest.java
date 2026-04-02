package pl.nextsteppro.climbing.api.gallery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.nextsteppro.climbing.api.gallery.GalleryDtos.AlbumSummaryDto;
import pl.nextsteppro.climbing.domain.gallery.Album;
import pl.nextsteppro.climbing.domain.gallery.AlbumRepository;
import pl.nextsteppro.climbing.domain.gallery.AlbumSummaryProjection;
import pl.nextsteppro.climbing.domain.gallery.PhotoRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GalleryServiceTest {

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private PhotoRepository photoRepository;

    private GalleryService service;

    private static final String BASE_URL = "http://localhost:8080";

    @BeforeEach
    void setUp() {
        service = new GalleryService(albumRepository, photoRepository, BASE_URL);
    }

    @Test
    void shouldGetAllAlbumsUsingOptimizedQuery() {
        // Given
        UUID albumId = UUID.randomUUID();
        String photoFilename = UUID.randomUUID() + ".jpg";

        AlbumSummaryProjection projection = new AlbumSummaryProjection() {
            @Override
            public UUID getId() {
                return albumId;
            }

            @Override
            public String getName() {
                return "Test Album";
            }

            @Override
            public String getDescription() {
                return "Test Description";
            }

            @Override
            public Instant getCreatedAt() {
                return Instant.now();
            }

            @Override
            public String getFirstPhotoFilename() {
                return photoFilename;
            }

            @Override
            public Long getPhotoCount() {
                return 5L;
            }
        };

        when(albumRepository.findAllAlbumSummaries()).thenReturn(List.of(projection));

        // When
        List<AlbumSummaryDto> result = service.getAllAlbums();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());

        AlbumSummaryDto dto = result.get(0);
        assertEquals(albumId, dto.id());
        assertEquals("Test Album", dto.name());
        assertEquals("Test Description", dto.description());
        assertEquals(5L, dto.photoCount());
        assertTrue(dto.thumbnailUrl().contains(photoFilename));
        assertTrue(dto.thumbnailUrl().startsWith(BASE_URL + "/api/files/gallery/"));

        // Verify optimized query was used (not the old N+1 approach)
        verify(albumRepository, times(1)).findAllAlbumSummaries();
        verify(photoRepository, never()).findFirstByAlbumId(any());
        verify(photoRepository, never()).countByAlbumId(any());
    }

    @Test
    void shouldHandleAlbumWithNoPhotos() {
        // Given
        UUID albumId = UUID.randomUUID();

        AlbumSummaryProjection projection = new AlbumSummaryProjection() {
            @Override
            public UUID getId() {
                return albumId;
            }

            @Override
            public String getName() {
                return "Empty Album";
            }

            @Override
            public String getDescription() {
                return null;
            }

            @Override
            public Instant getCreatedAt() {
                return Instant.now();
            }

            @Override
            public String getFirstPhotoFilename() {
                return null; // No photos
            }

            @Override
            public Long getPhotoCount() {
                return 0L;
            }
        };

        when(albumRepository.findAllAlbumSummaries()).thenReturn(List.of(projection));

        // When
        List<AlbumSummaryDto> result = service.getAllAlbums();

        // Then
        assertEquals(1, result.size());
        AlbumSummaryDto dto = result.get(0);
        assertNull(dto.thumbnailUrl(), "Should have null thumbnail when no photos");
        assertEquals(0L, dto.photoCount());
    }

    @Test
    void shouldReturnEmptyListWhenNoAlbums() {
        // Given
        when(albumRepository.findAllAlbumSummaries()).thenReturn(List.of());

        // When
        List<AlbumSummaryDto> result = service.getAllAlbums();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldGetAlbumById() {
        // Given
        UUID albumId = UUID.randomUUID();
        Album album = mock(Album.class);
        when(album.getId()).thenReturn(albumId);
        when(album.getName()).thenReturn("Test Album");
        when(album.getDescription()).thenReturn("Description");
        when(album.getCreatedAt()).thenReturn(Instant.now());
        when(album.getUpdatedAt()).thenReturn(Instant.now());

        when(albumRepository.findById(albumId)).thenReturn(java.util.Optional.of(album));
        when(photoRepository.findByAlbumIdOrderByDisplayOrderAscCreatedAtAsc(albumId))
                .thenReturn(List.of());

        // When
        var result = service.getAlbum(albumId);

        // Then
        assertNotNull(result);
        assertEquals(albumId, result.id());
        assertEquals("Test Album", result.name());
        assertEquals("Description", result.description());
        assertTrue(result.photos().isEmpty());
    }

    @Test
    void shouldThrowExceptionWhenAlbumNotFound() {
        // Given
        UUID albumId = UUID.randomUUID();
        when(albumRepository.findById(albumId)).thenReturn(java.util.Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> service.getAlbum(albumId));
    }

    @Test
    void shouldBuildCorrectPhotoUrls() {
        // Given
        UUID albumId = UUID.randomUUID();
        String filename = UUID.randomUUID() + ".png";

        AlbumSummaryProjection projection = new AlbumSummaryProjection() {
            @Override
            public UUID getId() {
                return albumId;
            }

            @Override
            public String getName() {
                return "Album";
            }

            @Override
            public String getDescription() {
                return null;
            }

            @Override
            public Instant getCreatedAt() {
                return Instant.now();
            }

            @Override
            public String getFirstPhotoFilename() {
                return filename;
            }

            @Override
            public Long getPhotoCount() {
                return 1L;
            }
        };

        when(albumRepository.findAllAlbumSummaries()).thenReturn(List.of(projection));

        // When
        List<AlbumSummaryDto> result = service.getAllAlbums();

        // Then
        String expectedUrl = BASE_URL + "/api/files/gallery/" + filename;
        assertEquals(expectedUrl, result.get(0).thumbnailUrl());
    }

    @Test
    void shouldHandleMultipleAlbumsEfficiently() {
        // Given - simulate 100 albums
        List<AlbumSummaryProjection> projections = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            UUID albumId = UUID.randomUUID();
            String filename = UUID.randomUUID() + ".jpg";
            int photoCount = i; // Different count for each

            projections.add(new AlbumSummaryProjection() {
                @Override
                public UUID getId() {
                    return albumId;
                }

                @Override
                public String getName() {
                    return "Album " + photoCount;
                }

                @Override
                public String getDescription() {
                    return null;
                }

                @Override
                public Instant getCreatedAt() {
                    return Instant.now();
                }

                @Override
                public String getFirstPhotoFilename() {
                    return photoCount > 0 ? filename : null;
                }

                @Override
                public Long getPhotoCount() {
                    return (long) photoCount;
                }
            });
        }

        when(albumRepository.findAllAlbumSummaries()).thenReturn(projections);

        // When
        List<AlbumSummaryDto> result = service.getAllAlbums();

        // Then
        assertEquals(100, result.size());

        // CRITICAL: Verify only 1 query was executed (not 1+200 queries)
        verify(albumRepository, times(1)).findAllAlbumSummaries();
        verify(photoRepository, never()).findFirstByAlbumId(any());
        verify(photoRepository, never()).countByAlbumId(any());

        // Verify first album has no thumbnail (0 photos)
        assertNull(result.get(0).thumbnailUrl());
        assertEquals(0L, result.get(0).photoCount());

        // Verify last album has thumbnail and correct count
        assertNotNull(result.get(99).thumbnailUrl());
        assertEquals(99L, result.get(99).photoCount());
    }
}
