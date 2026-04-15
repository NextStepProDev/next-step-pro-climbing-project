package pl.nextsteppro.climbing.api.video;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.video.VideoDtos.VideoDto;
import pl.nextsteppro.climbing.domain.video.Video;
import pl.nextsteppro.climbing.domain.video.VideoRepository;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class VideoService {

    private final VideoRepository videoRepository;

    public VideoService(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    @Cacheable("videoList")
    public List<VideoDto> getAllPublished() {
        return videoRepository.findAllByPublishedTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    private VideoDto toDto(Video video) {
        return new VideoDto(
                video.getId(),
                video.getTitle(),
                video.getExcerpt(),
                video.getContent(),
                video.getYoutubeUrl(),
                video.getDisplayOrder(),
                video.getPublishedAt()
        );
    }
}
