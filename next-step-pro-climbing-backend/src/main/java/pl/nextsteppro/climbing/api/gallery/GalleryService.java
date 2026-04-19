package pl.nextsteppro.climbing.api.gallery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.gallery.GalleryDtos.*;
import pl.nextsteppro.climbing.domain.gallery.Album;
import pl.nextsteppro.climbing.domain.gallery.AlbumRepository;
import pl.nextsteppro.climbing.domain.gallery.Photo;
import pl.nextsteppro.climbing.domain.gallery.PhotoRepository;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GalleryService {

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final String baseUrl;

    public GalleryService(AlbumRepository albumRepository,
                          PhotoRepository photoRepository,
                          @Value("${app.base-url}") String baseUrl) {
        this.albumRepository = albumRepository;
        this.photoRepository = photoRepository;
        this.baseUrl = baseUrl;
    }

    public List<AlbumSummaryDto> getAllAlbums() {
        // Use optimized query with projection to avoid N+1 problem
        // (1 query instead of 1+2N queries)
        // Use optimized query with projection to avoid N+1 problem
        // (1 query instead of 1+2N queries)
        // Only published albums are visible publicly
        return albumRepository.findAllPublishedAlbumSummaries()
                .stream()
                .map(this::toSummaryDto)
                .toList();
    }

    public AlbumDetailDto getAlbum(UUID id) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Album not found"));

        if (!album.isPublished()) {
            throw new IllegalArgumentException("Album not found");
        }

        List<Photo> photos = photoRepository.findByAlbumIdOrderByDisplayOrderAscCreatedAtAsc(id);

        return new AlbumDetailDto(
                album.getId(),
                album.getName(),
                album.getDescription(),
                photos.stream().map(this::toPhotoDto).toList(),
                album.getCreatedAt(),
                album.getUpdatedAt()
        );
    }

    private AlbumSummaryDto toSummaryDto(pl.nextsteppro.climbing.domain.gallery.AlbumSummaryProjection projection) {
        return new AlbumSummaryDto(
                projection.getId(),
                projection.getName(),
                projection.getDescription(),
                projection.getFirstPhotoFilename() != null ? buildPhotoUrl(projection.getFirstPhotoFilename()) : null,
                projection.getThumbnailFocalPointX(),
                projection.getThumbnailFocalPointY(),
                projection.getPhotoCount(),
                projection.getCreatedAt()
        );
    }

    private PhotoDto toPhotoDto(Photo photo) {
        return new PhotoDto(
                photo.getId(),
                buildPhotoUrl(photo.getFilename()),
                photo.getCaption(),
                photo.getFocalPointX(),
                photo.getFocalPointY(),
                photo.getCreatedAt()
        );
    }

    private String buildPhotoUrl(String filename) {
        return baseUrl + "/api/files/gallery/" + filename;
    }
}
