package pl.nextsteppro.climbing.api.admin.video;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.admin.video.AdminVideoDtos.*;
import pl.nextsteppro.climbing.domain.video.Video;
import pl.nextsteppro.climbing.domain.video.VideoRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AdminVideoService {

    private final VideoRepository videoRepository;

    public AdminVideoService(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    @Transactional(readOnly = true)
    public List<VideoAdminDto> getAllVideos() {
        return videoRepository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(this::toAdminDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public VideoAdminDto getVideo(UUID id) {
        return toAdminDto(findVideo(id));
    }

    @CacheEvict(value = "videoList", allEntries = true)
    public VideoAdminDto createVideo(CreateVideoRequest request) {
        Video video = new Video(request.title(), request.youtubeUrl());
        video.setExcerpt(request.excerpt());
        video.setContent(request.content());
        video.setDisplayOrder(videoRepository.findMinDisplayOrder().orElse(1) - 1);
        video = videoRepository.save(video);
        return toAdminDto(video);
    }

    @CacheEvict(value = "videoList", allEntries = true)
    public VideoAdminDto updateVideo(UUID id, UpdateVideoRequest request) {
        Video video = findVideo(id);
        if (request.title() != null) {
            video.setTitle(request.title());
        }
        if (request.excerpt() != null) {
            video.setExcerpt(request.excerpt());
        }
        if (request.content() != null) {
            video.setContent(request.content());
        }
        if (request.youtubeUrl() != null) {
            video.setYoutubeUrl(request.youtubeUrl());
        }
        video = videoRepository.save(video);
        return toAdminDto(video);
    }

    @CacheEvict(value = "videoList", allEntries = true)
    public VideoAdminDto setPublished(UUID id, boolean publish) {
        Video video = findVideo(id);
        if (publish && video.getPublishedAt() == null) {
            video.setPublishedAt(Instant.now());
        }
        video.setPublished(publish);
        video = videoRepository.save(video);
        return toAdminDto(video);
    }

    @CacheEvict(value = "videoList", allEntries = true)
    public void deleteVideo(UUID id) {
        Video video = findVideo(id);
        videoRepository.delete(video);
    }

    @CacheEvict(value = "videoList", allEntries = true)
    public List<VideoAdminDto> moveUp(UUID id) {
        List<Video> all = videoRepository.findAllByOrderByDisplayOrderAsc();
        int idx = indexOfId(all, id);
        if (idx > 0) {
            swap(all.get(idx), all.get(idx - 1));
            videoRepository.saveAll(List.of(all.get(idx), all.get(idx - 1)));
        }
        return videoRepository.findAllByOrderByDisplayOrderAsc()
                .stream().map(this::toAdminDto).toList();
    }

    @CacheEvict(value = "videoList", allEntries = true)
    public List<VideoAdminDto> moveDown(UUID id) {
        List<Video> all = videoRepository.findAllByOrderByDisplayOrderAsc();
        int idx = indexOfId(all, id);
        if (idx >= 0 && idx < all.size() - 1) {
            swap(all.get(idx), all.get(idx + 1));
            videoRepository.saveAll(List.of(all.get(idx), all.get(idx + 1)));
        }
        return videoRepository.findAllByOrderByDisplayOrderAsc()
                .stream().map(this::toAdminDto).toList();
    }

    private Video findVideo(UUID id) {
        return videoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Video not found"));
    }

    private int indexOfId(List<Video> list, UUID id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(id)) return i;
        }
        return -1;
    }

    private void swap(Video a, Video b) {
        int tmp = a.getDisplayOrder();
        a.setDisplayOrder(b.getDisplayOrder());
        b.setDisplayOrder(tmp);
    }

    private VideoAdminDto toAdminDto(Video video) {
        return new VideoAdminDto(
                video.getId(),
                video.getTitle(),
                video.getExcerpt(),
                video.getContent(),
                video.getYoutubeUrl(),
                video.getDisplayOrder(),
                video.isPublished(),
                video.getPublishedAt(),
                video.getCreatedAt(),
                video.getUpdatedAt()
        );
    }
}
