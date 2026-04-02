package pl.nextsteppro.climbing.api.admin.gallery;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.admin.gallery.AdminGalleryDtos.*;
import pl.nextsteppro.climbing.domain.gallery.Album;
import pl.nextsteppro.climbing.domain.gallery.AlbumRepository;
import pl.nextsteppro.climbing.domain.gallery.Photo;
import pl.nextsteppro.climbing.domain.gallery.PhotoRepository;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AdminGalleryService {

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
        return albumRepository.findAllByOrderByCreatedAtDesc()
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
                photos.stream().map(this::toPhotoAdminDto).toList(),
                album.getCreatedAt(),
                album.getUpdatedAt()
        );
    }

    public AlbumAdminDto createAlbum(CreateAlbumRequest request) {
        Album album = new Album(request.name());
        album.setDescription(request.description());

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
                System.err.println("Failed to delete photo file: " + photo.getFilename() + " - " + e.getMessage());
            }
        }

        // Delete album (CASCADE will delete photo records)
        albumRepository.delete(album);
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
    private AlbumAdminDto toAlbumAdminDto(Album album) {
        Photo firstPhoto = photoRepository.findFirstByAlbumId(album.getId()).orElse(null);
        long photoCount = photoRepository.countByAlbumId(album.getId());

        return new AlbumAdminDto(
                album.getId(),
                album.getName(),
                album.getDescription(),
                firstPhoto != null ? buildPhotoUrl(firstPhoto.getFilename()) : null,
                photoCount,
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
                photo.getCreatedAt(),
                photo.getUpdatedAt()
        );
    }

    private String buildPhotoUrl(String filename) {
        return baseUrl + "/api/files/gallery/" + filename;
    }
}
