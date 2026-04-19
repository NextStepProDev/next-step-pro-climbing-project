package pl.nextsteppro.climbing.api.admin.gallery;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminGalleryService {

    private static final Logger logger = LoggerFactory.getLogger(AdminGalleryService.class);

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final FileStorageService fileStorageService;
    private final String baseUrl;

    public AdminGalleryService(AlbumRepository albumRepository,
                               PhotoRepository photoRepository,
                               FileStorageService fileStorageService,
                               @Value("${app.base-url}") String baseUrl) {
        this.albumRepository = albumRepository;
        this.photoRepository = photoRepository;
        this.fileStorageService = fileStorageService;
        this.baseUrl = baseUrl;
    }

    // Album operations
    public List<AlbumAdminDto> getAllAlbums() {
        return albumRepository.findAllAlbumSummaries()
                .stream()
                .map(this::toAlbumAdminDto)
                .toList();
    }

    public AlbumDetailAdminDto getAlbum(UUID id) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Album not found"));

        List<Photo> photos = photoRepository.findByAlbumIdOrderByDisplayOrderAscCreatedAtAsc(id);

        return new AlbumDetailAdminDto(
                album.getId(),
                album.getName(),
                album.getDescription(),
                album.getThumbnailPhotoId(),
                photos.stream().map(this::toPhotoAdminDto).toList(),
                album.getCreatedAt(),
                album.getUpdatedAt()
        );
    }

    public AlbumAdminDto createAlbum(CreateAlbumRequest request) {
        Album album = new Album(request.name());
        album.setDescription(request.description());
        album.setDisplayOrder(albumRepository.findMinDisplayOrder().orElse(1) - 1);

        album = albumRepository.save(album);
        return toAlbumAdminDto(album);
    }

    public void reorderAlbums(List<UUID> orderedIds) {
        List<Album> albums = albumRepository.findAllById(orderedIds);
        if (albums.size() != orderedIds.size()) {
            throw new IllegalArgumentException("One or more album IDs not found");
        }

        Map<UUID, Album> albumMap = albums.stream()
                .collect(Collectors.toMap(Album::getId, a -> a));

        for (int i = 0; i < orderedIds.size(); i++) {
            albumMap.get(orderedIds.get(i)).setDisplayOrder(i);
        }

        albumRepository.saveAll(albums);
    }

    public AlbumAdminDto setAlbumPublished(UUID id, boolean publish) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Album not found"));

        if (publish && album.getPublishedAt() == null) {
            album.setPublishedAt(Instant.now());
        }
        album.setPublished(publish);

        album = albumRepository.save(album);
        return toAlbumAdminDto(album);
    }

    public AlbumAdminDto updateAlbum(UUID id, UpdateAlbumRequest request) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Album not found"));

        if (request.name() != null) {
            album.setName(request.name());
        }
        if (request.description() != null) {
            album.setDescription(request.description());
        }

        album = albumRepository.save(album);
        return toAlbumAdminDto(album);
    }

    public void deleteAllPhotos(UUID albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("Album not found"));

        List<Photo> photos = photoRepository.findByAlbumId(albumId);
        for (Photo photo : photos) {
            try {
                fileStorageService.delete(photo.getFilename(), "gallery");
            } catch (IOException e) {
                logger.warn("Failed to delete photo file: {} - {}", photo.getFilename(), e.getMessage());
            }
        }

        photoRepository.deleteAll(photos);

        // Clear thumbnail reference
        album.setThumbnailPhotoId(null);
        albumRepository.save(album);
    }

    public void deleteAlbum(UUID id) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Album not found"));

        // Delete all photo files first (before CASCADE delete removes DB records)
        List<Photo> photos = photoRepository.findByAlbumId(id);
        for (Photo photo : photos) {
            try {
                fileStorageService.delete(photo.getFilename(), "gallery");
            } catch (IOException e) {
                // Log but continue
                logger.warn("Failed to delete photo file: {} - {}", photo.getFilename(), e.getMessage());
            }
        }

        // Delete photo records explicitly before album (avoids TransientPropertyValueException)
        photoRepository.deleteAll(photos);

        // Delete album
        albumRepository.delete(album);
    }

    public void setThumbnailPhoto(UUID albumId, UUID photoId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("Album not found"));

        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("Photo not found"));

        if (!photo.getAlbum().getId().equals(albumId)) {
            throw new IllegalArgumentException("Photo does not belong to this album");
        }

        album.setThumbnailPhotoId(photoId);
        albumRepository.save(album);
    }

    // Photo operations
    public UploadPhotoResponse uploadPhoto(UUID albumId, MultipartFile file, @Nullable String caption) throws IOException {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("Album not found"));

        String filename = fileStorageService.store(file, "gallery");

        Photo photo = new Photo(album, filename);
        photo.setCaption(caption);

        photo = photoRepository.save(photo);

        return new UploadPhotoResponse(
                photo.getId(),
                photo.getFilename(),
                buildPhotoUrl(photo.getFilename())
        );
    }

    public void updatePhoto(UUID photoId, UpdatePhotoRequest request) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("Photo not found"));

        if (request.caption() != null) {
            photo.setCaption(request.caption());
        }
        if (request.displayOrder() != null) {
            photo.setDisplayOrder(request.displayOrder());
        }
        if (request.focalPointX() != null) {
            photo.setFocalPointX(request.focalPointX());
        }
        if (request.focalPointY() != null) {
            photo.setFocalPointY(request.focalPointY());
        }

        photoRepository.save(photo);
    }

    public void deletePhoto(UUID photoId) throws IOException {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("Photo not found"));

        // Delete file first
        fileStorageService.delete(photo.getFilename(), "gallery");

        // Delete record
        photoRepository.delete(photo);
    }

    // Helper methods
    private AlbumAdminDto toAlbumAdminDto(AlbumSummaryProjection projection) {
        return new AlbumAdminDto(
                projection.getId(),
                projection.getName(),
                projection.getDescription(),
                projection.getFirstPhotoFilename() != null ? buildPhotoUrl(projection.getFirstPhotoFilename()) : null,
                projection.getThumbnailFocalPointX(),
                projection.getThumbnailFocalPointY(),
                projection.getPhotoCount(),
                projection.getDisplayOrder(),
                projection.isPublished(),
                projection.getPublishedAt(),
                projection.getCreatedAt(),
                projection.getUpdatedAt()
        );
    }

    private AlbumAdminDto toAlbumAdminDto(Album album) {
        Photo firstPhoto = photoRepository.findFirstByAlbumId(album.getId()).orElse(null);
        long photoCount = photoRepository.countByAlbumId(album.getId());

        return new AlbumAdminDto(
                album.getId(),
                album.getName(),
                album.getDescription(),
                firstPhoto != null ? buildPhotoUrl(firstPhoto.getFilename()) : null,
                null,
                null,
                photoCount,
                album.getDisplayOrder(),
                album.isPublished(),
                album.getPublishedAt(),
                album.getCreatedAt(),
                album.getUpdatedAt()
        );
    }

    private PhotoAdminDto toPhotoAdminDto(Photo photo) {
        return new PhotoAdminDto(
                photo.getId(),
                photo.getFilename(),
                buildPhotoUrl(photo.getFilename()),
                photo.getCaption(),
                photo.getDisplayOrder(),
                photo.getFocalPointX(),
                photo.getFocalPointY(),
                photo.getCreatedAt(),
                photo.getUpdatedAt()
        );
    }

    private String buildPhotoUrl(String filename) {
        return baseUrl + "/api/files/gallery/" + filename;
    }
}
